package com.zify.engine.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.dto.ChatMessage;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import com.zify.engine.api.dto.TokenUsage;
import com.zify.engine.config.ReactLoopProperties;
import com.zify.model.api.ModelFacade;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.chat.ToolCallDTO;
import com.zify.model.api.dto.chat.ToolDefinitionDTO;
import com.zify.tool.api.ToolFacade;
import com.zify.tool.api.dto.ToolExecutionCommand;
import com.zify.tool.api.dto.ToolExecutionResultDTO;
import com.zify.tool.api.dto.ToolExecContext;
import com.zify.tool.api.dto.ToolViewDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * ReAct 多轮循环（P2 §八核心，glm-docs/02 §2.1）。
 * <p>
 * 手动驱动：取 Agent 绑定的可用工具 → 每轮调 ModelFacade.chatStream（下发 tool 定义）→
 * 有 toolCall 则并行执行 → 结果回灌为 TOOL 消息 → 再调，直到无 toolCall 或触发终止。
 * engine 全程不碰 DB；工具事件经 sink 推送；并行工具在 toolExecutor 上调度。
 * <p>
 * 不变：每轮 ASSISTANT 独立 roundId；ASSISTANT(toolCalls)↔TOOL 成对（上下文序列合法）；
 * executeTool 失败已转 ERROR DTO，回灌为 TOOL 消息让模型自决策；循环在虚拟线程同步阻塞。
 */
@Component
public class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String ROLE_TOOL = "TOOL";
    private static final Duration MIN_ROUND_BUDGET = Duration.ofSeconds(2);

    private final AgentFacade agentFacade;
    private final ToolFacade toolFacade;
    private final ModelFacade modelFacade;
    private final ContextManager contextManager;
    private final ExecutorService toolExecutor;
    private final ReactLoopProperties properties;

    public ReActLoop(AgentFacade agentFacade, ToolFacade toolFacade, ModelFacade modelFacade,
                     ContextManager contextManager,
                     @Qualifier("toolExecutor") ExecutorService toolExecutor,
                     ReactLoopProperties properties) {
        this.agentFacade = agentFacade;
        this.toolFacade = toolFacade;
        this.modelFacade = modelFacade;
        this.contextManager = contextManager;
        this.toolExecutor = toolExecutor;
        this.properties = properties;
    }

    public ChatTurnResult run(ChatTurnCommand cmd, TextStreamSink sink) {
        AgentConfigDTO agent = agentFacade.getAgentConfig(cmd.getAgentId());

        // 首轮取可用工具（缓存）
        List<ToolViewDTO> tools = toolFacade.listAvailableTools(agent.getBoundToolIds());
        Map<String, String> nameToToolId = new HashMap<>();
        List<ToolDefinitionDTO> toolDefs = new ArrayList<>();
        for (ToolViewDTO t : tools) {
            nameToToolId.put(t.getName(), t.getId());
            toolDefs.add(new ToolDefinitionDTO(t.getName(), t.getDescription(), t.getInputSchema()));
        }

        Instant deadline = Instant.now().plus(properties.getLoopDeadline());
        int maxTurns = properties.getMaxTurns();
        int dupThreshold = properties.getDupToolCallThreshold();

        // 上下文：system + summary + 活窗口（含本轮 user）
        ContextManager.PrepareResult prepared = contextManager.prepare(agent, cmd.getSummary(), cmd.getHistory());
        List<com.zify.model.api.dto.chat.ChatMessage> messages = new ArrayList<>(prepared.getMessages());

        List<ChatMessage> newMessages = new ArrayList<>();
        Map<String, Integer> dupMap = new HashMap<>();
        String finalAssistantId = cmd.getAssistantMessageId();
        String lastContent = "";
        TokenUsage lastUsage = null;

        for (int turn = 1; turn <= maxTurns; turn++) {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.compareTo(MIN_ROUND_BUDGET) < 0) {
                return finish("TIMEOUT", lastContent, lastUsage, newMessages, finalAssistantId, prepared);
            }

            String roundId = (turn == 1) ? cmd.getAssistantMessageId() : IdGenerator.uuid();
            sink.onAssistantSegment(roundId);

            // 每轮 sink：仅转发 delta（chat 按 currentAssistantId 分组）
            TextStreamSink roundSink = delta -> {
                if (delta != null && !delta.isEmpty()) {
                    sink.onDelta(delta);
                }
            };

            ChatCompletionCommand modelCmd = new ChatCompletionCommand(agent.getModelId(), messages, null);
            modelCmd.setToolDefinitions(toolDefs.isEmpty() ? null : toolDefs);
            ChatCompletionResult r = modelFacade.chatStream(modelCmd, roundSink);

            lastContent = r.getContent() == null ? "" : r.getContent();
            lastUsage = toUsage(r.getUsage());

            // 本轮 ASSISTANT 消息（含 toolCalls）→ newMessages + messages
            ChatMessage engineAssistant = new ChatMessage(ROLE_ASSISTANT, lastContent, roundId);
            engineAssistant.setToolCalls(r.getToolCalls());
            newMessages.add(engineAssistant);
            com.zify.model.api.dto.chat.ChatMessage modelAssistant =
                    new com.zify.model.api.dto.chat.ChatMessage(ROLE_ASSISTANT, lastContent);
            modelAssistant.setToolCalls(r.getToolCalls());
            messages.add(modelAssistant);

            if (r.getToolCalls() == null || r.getToolCalls().isEmpty()) {
                // 无工具调用：正常结束
                finalAssistantId = roundId;
                return finish("STOP", lastContent, lastUsage, newMessages, finalAssistantId, prepared);
            }

            // 死循环兜底：相同 (name,args) 重复
            boolean abortDup = false;
            for (ToolCallDTO tc : r.getToolCalls()) {
                String key = tc.getName() + "|" + argsHash(tc.getArgs());
                int count = dupMap.merge(key, 1, Integer::sum);
                if (count > dupThreshold) {
                    abortDup = true;
                }
            }

            // 并行执行工具（含死循环回灌提示）
            executeToolCalls(r.getToolCalls(), cmd, agent, turn, nameToToolId,
                    dupMap, dupThreshold, sink, roundId, newMessages, messages);

            if (abortDup) {
                // 重复达阈值（提示后仍重复）：截断
                return finish("MAX_TURNS", lastContent, lastUsage, newMessages, roundId, prepared);
            }
        }
        // 达最大轮次：正常截断
        return finish("MAX_TURNS", lastContent, lastUsage, newMessages, finalAssistantId, prepared);
    }

    private void executeToolCalls(List<ToolCallDTO> toolCalls, ChatTurnCommand cmd, AgentConfigDTO agent,
                                  int turn, Map<String, String> nameToToolId,
                                  Map<String, Integer> dupMap, int dupThreshold,
                                  TextStreamSink sink, String roundId,
                                  List<ChatMessage> newMessages,
                                  List<com.zify.model.api.dto.chat.ChatMessage> messages) {
        List<CompletableFuture<Executed>> futures = new ArrayList<>();
        for (ToolCallDTO tc : toolCalls) {
            String toolId = nameToToolId.get(tc.getName());
            Map<String, Object> args = parseArgs(tc.getArgs());
            String toolCallId = tc.getId();
            // 死循环回灌：相同 (name,args) 达阈值 → 以提示作为 TOOL 结果（不实际调用），给模型换方法的机会
            String key = tc.getName() + "|" + argsHash(tc.getArgs());
            boolean hint = dupMap.getOrDefault(key, 0) >= dupThreshold;

            sink.onToolCallStart(roundId, toolCallId, tc.getName(), tc.getArgs());

            final ToolExecContext ctx = new ToolExecContext(cmd.getConversationId(), agent.getId(), turn, toolCallId);
            CompletableFuture<Executed> f = CompletableFuture.supplyAsync(() -> {
                ToolExecutionResultDTO result;
                if (hint) {
                    result = hintResult(tc.getName());
                } else if (toolId == null) {
                    result = notFoundResult(tc.getName());
                } else {
                    result = toolFacade.executeTool(new ToolExecutionCommand(toolId, args, ctx));
                }
                return new Executed(tc, result);
            }, toolExecutor);
            f.whenComplete((exec, ex) -> {
                ToolExecutionResultDTO res = ex != null || exec == null ? errorFallback(tc.getName(), ex) : exec.result;
                sink.onToolCallEnd(roundId, toolCallId, tc.getName(), res.getStatus(),
                        res.getOutput(), res.getDurationMs(), res.getToolCallLogId());
            });
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 按 toolCallId 配对落 TOOL 消息（newMessages + messages）
        for (CompletableFuture<Executed> f : futures) {
            Executed exec = f.join();
            String output = exec.result.getOutput() == null ? "" : exec.result.getOutput();
            ChatMessage engineTool = new ChatMessage(ROLE_TOOL, output, IdGenerator.uuid());
            engineTool.setToolCallId(exec.tc.getId());
            engineTool.setToolName(exec.tc.getName());
            engineTool.setToolCallLogId(exec.result.getToolCallLogId());
            newMessages.add(engineTool);
            com.zify.model.api.dto.chat.ChatMessage modelTool =
                    new com.zify.model.api.dto.chat.ChatMessage(ROLE_TOOL, output);
            modelTool.setToolCallId(exec.tc.getId());
            messages.add(modelTool);
        }
    }

    private ChatTurnResult finish(String reason, String content, TokenUsage usage,
                                  List<ChatMessage> newMessages, String finalAssistantId,
                                  ContextManager.PrepareResult prepared) {
        ChatTurnResult result = new ChatTurnResult(content, reason, usage,
                prepared.getNewSummary(), prepared.getNewSummaryCoveredMessageId());
        result.setNewMessages(newMessages);
        result.setFinalAssistantMessageId(finalAssistantId);
        log.info("ReAct turn finished: reason={}, newMessages={}, finalAssistantId={}",
                reason, newMessages.size(), finalAssistantId);
        return result;
    }

    private TokenUsage toUsage(com.zify.model.api.dto.chat.TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(argsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool args json, using empty map: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String argsHash(String argsJson) {
        return argsJson == null ? "" : Integer.toHexString(argsJson.hashCode());
    }

    private ToolExecutionResultDTO hintResult(String toolName) {
        return new ToolExecutionResultDTO("ERROR",
                "检测到工具 " + toolName + " 被重复调用相同参数，请尝试换一种方法或直接回答。",
                0, null, "duplicate tool call");
    }

    private ToolExecutionResultDTO notFoundResult(String toolName) {
        return new ToolExecutionResultDTO("ERROR", "工具 " + toolName + " 不存在或当前不可用",
                0, null, "tool not found");
    }

    private ToolExecutionResultDTO errorFallback(String toolName, Throwable ex) {
        return new ToolExecutionResultDTO("ERROR", "工具 " + toolName + " 执行失败",
                0, null, ex == null ? "unknown" : ex.getClass().getSimpleName());
    }

    /** 工具执行配对：toolCall + 结果。 */
    private static final class Executed {
        final ToolCallDTO tc;
        final ToolExecutionResultDTO result;

        Executed(ToolCallDTO tc, ToolExecutionResultDTO result) {
            this.tc = tc;
            this.result = result;
        }
    }
}
