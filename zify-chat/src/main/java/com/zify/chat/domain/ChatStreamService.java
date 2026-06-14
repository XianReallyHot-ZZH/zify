package com.zify.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.chat.infrastructure.entity.ConversationEntity;
import com.zify.chat.infrastructure.entity.MessageEntity;
import com.zify.chat.infrastructure.mapper.ConversationMapper;
import com.zify.chat.infrastructure.mapper.MessageMapper;
import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.EngineFacade;
import com.zify.engine.api.dto.ChatMessage;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import com.zify.engine.api.dto.TokenUsage;
import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;
import com.zify.common.persistence.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话流式编排 Service（chat 持有 SSE 与消息持久化）。
 * <p>
 * 两步流式的第二步：以 userMessageId 标识本轮，加载历史 → 调 {@link EngineFacade} 流式生成 →
 * 逐 token 经 SSE message_delta 推出 → 生成完成后短事务落库 ASSISTANT → 发 done。
 * <p>
 * 不在事务内调用 LLM。失败（engine 抛 LlmException）发 run_error 不落库；取消（中断）且已产出
 * 部分文本则落库部分（finishReason=CANCELLED）并发 done。
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String AGENT_TYPE_REACT = "REACT";
    private static final int RECENT_WINDOW_LIMIT = 100;

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final AgentFacade agentFacade;
    private final EngineFacade engineFacade;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ExecutorService llmTaskExecutor;

    public ChatStreamService(MessageMapper messageMapper, ConversationMapper conversationMapper,
                             AgentFacade agentFacade, EngineFacade engineFacade, MessageService messageService,
                             ConversationService conversationService,
                             @Qualifier("llmTaskExecutor") ExecutorService llmTaskExecutor) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.agentFacade = agentFacade;
        this.engineFacade = engineFacade;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.llmTaskExecutor = llmTaskExecutor;
    }

    /**
     * 启动一轮流式生成：提交虚拟线程任务，绑定 SSE 断连/超时/错误 → 取消上游。
     */
    public void startChatStream(String userMessageId, SseEmitter emitter) {
        Future<?> future = llmTaskExecutor.submit(() -> runTurn(userMessageId, emitter));
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(e -> future.cancel(true));
    }

    private void runTurn(String userMessageId, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        StringBuilder accumulated = new StringBuilder();
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        AtomicBoolean clientGone = new AtomicBoolean(false);
        String assistantMessageId = IdGenerator.uuid();

        String conversationId = null;
        try {
            // 1. 定位会话 + 校验
            MessageEntity userMessage = messageMapper.selectById(userMessageId);
            if (userMessage == null) {
                sendRunError(emitter, "消息不存在", false);
                return;
            }
            conversationId = userMessage.getConversationId();
            ConversationEntity conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !STATUS_ACTIVE.equals(conversation.getStatus())) {
                sendRunError(emitter, "会话不可用", false);
                return;
            }
            AgentConfigDTO agent = agentFacade.getAgentConfig(conversation.getAgentId());
            if (!AGENT_TYPE_REACT.equalsIgnoreCase(agent.getAgentType())
                    || !STATUS_ACTIVE.equalsIgnoreCase(agent.getStatus())) {
                sendRunError(emitter, "Agent 不可用", false);
                return;
            }

            // 2. 当前会话 running summary
            String summary = conversation.getSummaryText();
            String summaryCoveredId = conversation.getSummaryCoveredMessageId();

            // 3. 加载活窗口历史（ASC，含本轮 user）
            List<ChatMessage> history = loadActiveWindow(conversationId, summaryCoveredId);

            // 5. 构造 sink：delta → SSE message_delta
            final String convId = conversationId;
            TextStreamSink sink = delta -> {
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                deltaSent.set(true);
                accumulated.append(delta);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("conversationId", convId);
                payload.put("assistantMessageId", assistantMessageId);
                payload.put("delta", delta);
                sendEvent(emitter, clientGone, "message_delta", payload);
            };

            ChatTurnCommand command = new ChatTurnCommand();
            command.setAgentId(conversation.getAgentId());
            command.setHistory(history);
            command.setAssistantMessageId(assistantMessageId);
            command.setSummary(summary);
            command.setSummaryCoveredMessageId(summaryCoveredId);

            // 6. 调 engine 流式生成（不在事务内）
            ChatTurnResult result = engineFacade.runChatTurn(command, sink);

            // 7. 成功：短事务落库 ASSISTANT + 更新计数/时间 → done
            long durationMs = System.currentTimeMillis() - start;
            Map<String, Object> metadata = buildMetadata(agent, result.getUsage(),
                    result.getFinishReason(), durationMs);
            messageService.persistAssistantTurn(convId, assistantMessageId,
                    accumulated.toString(), metadata);

            // 摘要落库：仅当 engine 本轮触发了压缩（newSummary 非空），幂等 CAS
            if (result.getNewSummary() != null) {
                conversationService.updateSummary(convId, result.getNewSummary(),
                        result.getNewSummaryCoveredMessageId(), summaryCoveredId);
            }

            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("conversationId", convId);
            donePayload.put("assistantMessageId", assistantMessageId);
            sendEvent(emitter, clientGone, "done", donePayload);
            emitter.complete();
            log.info("Chat turn done: conversationId={}, assistantMessageId={}, durationMs={}",
                    convId, assistantMessageId, durationMs);

        } catch (LlmCancelledException e) {
            // 用户取消（中断）：清除中断标志——后续 DB 落库不能在 interrupted 状态，
            // 否则 HikariPool 拿连接时 socket 被 "Closed by interrupt" → 连接 broken → 落库失败。
            Thread.interrupted();
            handleCancel(emitter, accumulated, deltaSent, clientGone, conversationId, assistantMessageId, start);
        } catch (LlmException e) {
            // Provider 错误（含首 chunk 后失败 / 重试耗尽）：不落库，发 run_error
            log.warn("Chat turn failed: conversationId={}, retryable={}, error={}",
                    conversationId, e.isRetryable(), e.getMessage());
            sendRunError(emitter, friendly(e.getMessage()), e.isRetryable());
        } catch (Exception e) {
            log.error("Chat turn unexpected error: conversationId={}", conversationId, e);
            sendRunError(emitter, "对话生成失败", false);
        }
    }

    /**
     * 取消处理：已产出文本 → 落库部分（CANCELLED）+ done；未产出 → 不落库、不发 run_error（视为用户主动放弃）。
     */
    private void handleCancel(SseEmitter emitter, StringBuilder accumulated, AtomicBoolean deltaSent,
                              AtomicBoolean clientGone,
                              String conversationId, String assistantMessageId, long start) {
        if (conversationId == null || !deltaSent.get() || accumulated.length() == 0) {
            safeComplete(emitter);
            return;
        }
        try {
            long durationMs = System.currentTimeMillis() - start;
            Map<String, Object> metadata = buildMetadata(null, null, "CANCELLED", durationMs);
            messageService.persistAssistantTurn(conversationId, assistantMessageId,
                    accumulated.toString(), metadata);
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("conversationId", conversationId);
            donePayload.put("assistantMessageId", assistantMessageId);
            sendEvent(emitter, clientGone, "done", donePayload);
        } catch (Exception ex) {
            log.warn("Failed to persist cancelled partial turn: {}", ex.getMessage());
        } finally {
            safeComplete(emitter);
        }
    }

    /**
     * 幂等关闭 emitter：客户端已断开/已完成时 complete() 会抛 IllegalStateException，这里吞掉。
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // emitter 已完成/超时/客户端断开
        }
    }

    /**
     * 加载活窗口历史（USER/ASSISTANT，ASC，含本轮 user）。
     * summaryCoveredId 非空 → 其之后的消息；为空 → 最近 RECENT_WINDOW_LIMIT 条。
     */
    private List<ChatMessage> loadActiveWindow(String conversationId, String summaryCoveredId) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getConversationId, conversationId);
        wrapper.in(MessageEntity::getRole, "USER", "ASSISTANT");

        List<MessageEntity> entities;
        if (summaryCoveredId != null && !summaryCoveredId.isBlank()) {
            MessageEntity covered = messageMapper.selectById(summaryCoveredId);
            if (covered != null) {
                wrapper.gt(MessageEntity::getCreatedAt, covered.getCreatedAt());
            }
            wrapper.orderByAsc(MessageEntity::getCreatedAt);
            entities = messageMapper.selectList(wrapper);
        } else {
            wrapper.orderByDesc(MessageEntity::getCreatedAt);
            wrapper.last("LIMIT " + RECENT_WINDOW_LIMIT);
            List<MessageEntity> desc = messageMapper.selectList(wrapper);
            java.util.Collections.reverse(desc);
            entities = desc;
        }

        return entities.stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent(), m.getId()))
                .toList();
    }

    private Map<String, Object> buildMetadata(AgentConfigDTO agent, TokenUsage usage,
                                              String finishReason, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        if (agent != null) {
            metadata.put("modelId", agent.getModelId());
        }
        if (usage != null) {
            metadata.put("promptTokens", usage.getPromptTokens());
            metadata.put("completionTokens", usage.getCompletionTokens());
            metadata.put("totalTokens", usage.getTotalTokens());
        }
        metadata.put("finishReason", finishReason);
        metadata.put("durationMs", durationMs);
        return metadata;
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean clientGone, String name, Map<String, Object> payload) {
        if (clientGone.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开（ClientAbortException）/ emitter 已完成：静默标记，不再发送。
            // 不抛出，避免在 reactor 线程污染 error path（取消应由 future.cancel → LlmCancelledException 路径处理）。
            clientGone.set(true);
        }
    }

    private void sendRunError(SseEmitter emitter, String message, boolean retryable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("retryable", retryable);
        try {
            emitter.send(SseEmitter.event().name("run_error").data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            // 客户端已断开，无法送达错误事件
        }
        emitter.complete();
    }

    private String friendly(String message) {
        if (message == null || message.isBlank()) {
            return "模型调用失败，请稍后重试";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
