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
import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话流式编排 Service（chat 持有 SSE 与消息持久化）。
 * <p>
 * 两步流式的第二步：以 userMessageId 标识本轮，加载历史 → 调 {@link EngineFacade}（ReAct 多轮）流式生成 →
 * 逐 token 经 SSE message_delta 推出、工具事件经 tool_call_start/tool_call_end 推出 →
 * 生成完成后短事务批量落库 newMessages（persistTurn）→ 发 done。
 * <p>
 * 不在事务内调用 LLM/工具。失败（engine 抛 LlmException）发 run_error 不落库；取消（中断）且已产出
 * 部分文本则落库已产出分段（finishReason=CANCELLED）并发 done。
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
        AtomicReference<String> currentAssistantId = new AtomicReference<>();
        java.util.Map<String, StringBuilder> segmentTexts = Collections.synchronizedMap(new LinkedHashMap<>());
        java.util.concurrent.atomic.AtomicBoolean clientGone = new java.util.concurrent.atomic.AtomicBoolean(false);

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

            // 2. running summary
            String summary = conversation.getSummaryText();
            String summaryCoveredId = conversation.getSummaryCoveredMessageId();

            // 3. 活窗口历史（含本轮 user）
            List<ChatMessage> history = loadActiveWindow(conversationId, summaryCoveredId);

            String assistantMessageId = java.util.UUID.randomUUID().toString();
            currentAssistantId.set(assistantMessageId);

            final String convId = conversationId;
            TextStreamSink sink = new TextStreamSink() {
                @Override
                public void onDelta(String delta) {
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    String aid = currentAssistantId.get();
                    segmentTexts.computeIfAbsent(aid, k -> new StringBuilder()).append(delta);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("conversationId", convId);
                    payload.put("assistantMessageId", aid);
                    payload.put("delta", delta);
                    sendEvent(emitter, clientGone, "message_delta", payload);
                }

                @Override
                public void onAssistantSegment(String assistantMessageId) {
                    currentAssistantId.set(assistantMessageId);
                    segmentTexts.computeIfAbsent(assistantMessageId, k -> new StringBuilder());
                }

                @Override
                public void onToolCallStart(String assistantMessageId, String toolCallId, String toolName, String argsJson) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("conversationId", convId);
                    payload.put("assistantMessageId", assistantMessageId);
                    payload.put("toolCallId", toolCallId);
                    payload.put("toolName", toolName);
                    payload.put("args", argsJson);
                    sendEvent(emitter, clientGone, "tool_call_start", payload);
                }

                @Override
                public void onToolCallEnd(String assistantMessageId, String toolCallId, String toolName,
                                          String status, String output, long durationMs, String toolCallLogId) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("conversationId", convId);
                    payload.put("assistantMessageId", assistantMessageId);
                    payload.put("toolCallId", toolCallId);
                    payload.put("toolName", toolName);
                    payload.put("status", status);
                    payload.put("output", output);
                    payload.put("durationMs", durationMs);
                    payload.put("toolCallLogId", toolCallLogId);
                    sendEvent(emitter, clientGone, "tool_call_end", payload);
                }
            };

            ChatTurnCommand command = new ChatTurnCommand();
            command.setAgentId(conversation.getAgentId());
            command.setConversationId(conversationId);
            command.setHistory(history);
            command.setAssistantMessageId(assistantMessageId);
            command.setSummary(summary);
            command.setSummaryCoveredMessageId(summaryCoveredId);

            // 调 engine ReAct 流式生成（不在事务内）
            ChatTurnResult result = engineFacade.runChatTurn(command, sink);

            // 短事务批量落库 newMessages
            long durationMs = System.currentTimeMillis() - start;
            messageService.persistTurn(convId, result.getNewMessages(), result.getFinalAssistantMessageId(),
                    agent, result.getUsage(), result.getFinishReason(), durationMs);

            // 摘要落库（engine 本轮触发压缩时）
            if (result.getNewSummary() != null) {
                conversationService.updateSummary(convId, result.getNewSummary(),
                        result.getNewSummaryCoveredMessageId(), summaryCoveredId);
            }

            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("conversationId", convId);
            donePayload.put("assistantMessageId", result.getFinalAssistantMessageId());
            sendEvent(emitter, clientGone, "done", donePayload);
            emitter.complete();
            log.info("Chat turn done: conversationId={}, finalAssistantMessageId={}, durationMs={}",
                    convId, result.getFinalAssistantMessageId(), durationMs);

        } catch (LlmCancelledException e) {
            // 用户取消（中断）：清除中断标志，落库已产出分段（CANCELLED）
            Thread.interrupted();
            handleCancel(emitter, segmentTexts, clientGone, conversationId, start);
        } catch (LlmException e) {
            log.warn("Chat turn failed: conversationId={}, retryable={}, error={}",
                    conversationId, e.isRetryable(), e.getMessage());
            sendRunError(emitter, friendly(e.getMessage()), e.isRetryable());
        } catch (Exception e) {
            log.error("Chat turn unexpected error: conversationId={}", conversationId, e);
            sendRunError(emitter, "对话生成失败", false);
        }
    }

    /**
     * 取消处理：已产出文本分段 → 落库部分（CANCELLED）+ done；未产出 → 不落库、不发 run_error。
     */
    private void handleCancel(SseEmitter emitter, Map<String, StringBuilder> segmentTexts,
                              java.util.concurrent.atomic.AtomicBoolean clientGone,
                              String conversationId, long start) {
        if (conversationId == null) {
            safeComplete(emitter);
            return;
        }
        List<ChatMessage> partial = new ArrayList<>();
        String lastAid = null;
        for (Map.Entry<String, StringBuilder> e : segmentTexts.entrySet()) {
            if (e.getValue() == null || e.getValue().length() == 0) {
                continue;
            }
            partial.add(new ChatMessage("ASSISTANT", e.getValue().toString(), e.getKey()));
            lastAid = e.getKey();
        }
        if (partial.isEmpty()) {
            safeComplete(emitter);
            return;
        }
        try {
            long durationMs = System.currentTimeMillis() - start;
            messageService.persistTurn(conversationId, partial, lastAid, null, null, "CANCELLED", durationMs);
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("conversationId", conversationId);
            donePayload.put("assistantMessageId", lastAid);
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
     * <p>
     * P2 §17 扩展为含 TOOL（turn 级摘要）；本任务先 USER/ASSISTANT（TOOL 接入在 §17）。
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

    private void sendEvent(SseEmitter emitter, java.util.concurrent.atomic.AtomicBoolean clientGone,
                           String name, Map<String, Object> payload) {
        if (clientGone.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开 / emitter 已完成：静默标记，取消应由 future.cancel → LlmCancelledException 路径处理。
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
