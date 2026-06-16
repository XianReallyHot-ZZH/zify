package com.zify.model.infrastructure.client;

import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatMessage;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.chat.ToolCallDTO;
import com.zify.model.api.dto.chat.ToolDefinitionDTO;
import com.zify.model.api.dto.chat.TokenUsage;
import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;
import com.zify.model.infrastructure.client.exception.LlmNonRetryableException;
import com.zify.model.infrastructure.client.exception.LlmRetryableException;
import com.zify.model.infrastructure.client.exception.LlmTimeoutException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring AI 的流式 Chat 客户端公共基类。
 * <p>
 * 子类（{@link OpenAiChatClient} / {@link AnthropicChatClient}）只负责按协议程序化构造
 * {@link ChatModel} 与 {@link ChatOptions}（含 toolCallbacks）；本类负责把 reactive 的
 * {@code stream(Prompt)} 在虚拟线程上用 {@link CountDownLatch} 阻塞桥接到 {@link TextStreamSink}，
 * 用 {@link MessageAggregator} 聚合（含 tool_calls），并处理 deadline / 中断取消（dispose）与异常分类。
 *
 * <p><b>spike 记录（P2 §14.1）</b>：因无可用 LLM 凭据未做 live spike，按 Spring AI 2.0 API 形态实现 ——
 * ChatModel.stream() 携带 toolCallbacks 时<b>不自动执行</b>（internalToolExecutionEnabled 已于 2.0 移除），
 * 经 MessageAggregator 聚合后 AssistantMessage.getToolCalls() 得到模型请求的工具调用；dispose 取消上游。
 * live 联调时若行为不符再以实测修正。
 */
public abstract class AbstractSpringAiChatClient implements LlmChatClient {

    /** 从异常信息中抽取 HTTP 状态码的启发式正则。 */
    private static final Pattern STATUS_PATTERN = Pattern.compile("(?:status[ :]*)?(\\b(?:4\\d{2}|5\\d{2})\\b)");

    @Override
    public ChatCompletionResult streamChat(ChatCallContext ctx, TextStreamSink sink) {
        ChatOptions chatOptions = buildOptions(ctx);
        ChatModel chatModel = buildChatModel(ctx, chatOptions);

        List<Message> messages = toSpringMessages(ctx.getMessages());
        Flux<ChatResponse> flux = chatModel.stream(new Prompt(messages, chatOptions));

        AtomicReference<Usage> usageRef = new AtomicReference<>();
        AtomicReference<AssistantMessage> finalMsg = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        // 用 MessageAggregator 聚合（含 tool_calls 跨 chunk 合并）；consumer 逐 chunk 把 delta 流给 sink
        Flux<ChatResponse> aggregated = new MessageAggregator().aggregate(flux, chunk -> {
            Generation gen = chunk.getResult();
            if (gen != null && gen.getOutput() != null) {
                String delta = gen.getOutput().getText();
                if (delta != null && !delta.isEmpty()) {
                    sink.onDelta(delta);
                }
            }
            if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                usageRef.set(chunk.getMetadata().getUsage());
            }
        });

        Disposable disposable = aggregated.subscribe(
                resp -> {
                    Generation gen = resp.getResult();
                    if (gen != null && gen.getOutput() != null) {
                        finalMsg.set(gen.getOutput());
                    }
                    if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                        usageRef.set(resp.getMetadata().getUsage());
                    }
                },
                e -> {
                    err.set(e);
                    done.countDown();
                },
                done::countDown
        );

        try {
            Duration remaining = Duration.between(Instant.now(), ctx.getDeadline());
            if (remaining.isNegative() || remaining.isZero()
                    || !done.await(Math.max(1, remaining.toMillis()), TimeUnit.MILLISECONDS)) {
                disposable.dispose();
                throw new LlmTimeoutException("LLM stream total deadline exceeded",
                        ctx.getProviderId(), ctx.getModelName(), ctx.getScenario(), false);
            }
        } catch (InterruptedException e) {
            disposable.dispose();
            Thread.currentThread().interrupt();
            throw new LlmCancelledException("LLM stream cancelled",
                    ctx.getProviderId(), ctx.getModelName(), ctx.getScenario(), e);
        }

        if (err.get() != null) {
            throw wrap(err.get(), ctx);
        }

        AssistantMessage am = finalMsg.get();
        List<ToolCallDTO> toolCalls = extractToolCalls(am);
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        String content = am != null && am.getText() != null ? am.getText() : "";
        String finishReason = hasToolCalls ? "TOOL_CALLS" : "STOP";
        ChatCompletionResult result = new ChatCompletionResult(content, finishReason, usageRef.get() != null
                ? toTokenUsage(usageRef.get()) : null);
        result.setToolCalls(hasToolCalls ? toolCalls : null);
        return result;
    }

    /** 程序化构造该协议的 ChatOptions（含 model、合并参数、toolCallbacks）。 */
    protected abstract ChatOptions buildOptions(ChatCallContext ctx);

    /** 程序化构造该协议的 ChatModel（含 baseUrl / 解密 apiKey）。 */
    protected abstract ChatModel buildChatModel(ChatCallContext ctx, ChatOptions chatOptions);

    /**
     * 把中立 {@link ChatMessage} 映射为 Spring AI {@link Message}：含 TOOL / ASSISTANT(toolCalls)。
     */
    protected List<Message> toSpringMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            String role = m.getRole() == null ? "" : m.getRole().toUpperCase();
            switch (role) {
                case "SYSTEM" -> result.add(new SystemMessage(nullSafe(m.getContent())));
                case "ASSISTANT" -> {
                    AssistantMessage.Builder<?> b = AssistantMessage.builder().content(nullSafe(m.getContent()));
                    if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                        List<AssistantMessage.ToolCall> tcs = new ArrayList<>();
                        for (ToolCallDTO tc : m.getToolCalls()) {
                            tcs.add(new AssistantMessage.ToolCall(
                                    tc.getId(), "function", tc.getName(), tc.getArgs() == null ? "" : tc.getArgs()));
                        }
                        b.toolCalls(tcs);
                    }
                    result.add(b.build());
                }
                case "TOOL" -> result.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                m.getToolCallId(), nullSafe(m.getToolCallId()), nullSafe(m.getContent()))))
                        .build());
                default -> result.add(new UserMessage(nullSafe(m.getContent())));
            }
        }
        return result;
    }

    /** 工具定义 → Spring AI ToolCallback（ad-hoc，不被 Spring AI 自动执行；call 不应被调用）。 */
    protected List<ToolCallback> toToolCallbacks(List<ToolDefinitionDTO> defs) {
        if (defs == null || defs.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> list = new ArrayList<>(defs.size());
        for (ToolDefinitionDTO d : defs) {
            ToolDefinition td = DefaultToolDefinition.builder()
                    .name(d.getName())
                    .description(d.getDescription() == null ? "" : d.getDescription())
                    .inputSchema(d.getInputSchema() == null ? "{}" : d.getInputSchema())
                    .build();
            list.add(new AdHocToolCallback(td));
        }
        return list;
    }

    private List<ToolCallDTO> extractToolCalls(AssistantMessage am) {
        if (am == null || !am.hasToolCalls()) {
            return null;
        }
        List<ToolCallDTO> result = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            result.add(new ToolCallDTO(tc.id(), tc.name(), tc.arguments()));
        }
        return result;
    }

    protected TokenUsage toTokenUsage(Usage usage) {
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 把底层异常分类为可重试 / 不可重试（启发式：按 HTTP 状态码与异常类名）。
     */
    protected LlmException wrap(Throwable t, ChatCallContext ctx) {
        if (t instanceof LlmException le) {
            return le;
        }
        String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        int status = extractStatus(message);
        boolean retryable;
        if (status == 429 || status >= 500) {
            retryable = true;
        } else if (status >= 400) {
            retryable = false;
        } else if (isConnectionError(t)) {
            retryable = true;
        } else {
            retryable = false;
        }
        String detail = "LLM call failed (" + ctx.getProviderType() + "): " + truncate(message);
        if (retryable) {
            return new LlmRetryableException(detail, ctx.getProviderId(), ctx.getModelName(), ctx.getScenario(), t);
        }
        return new LlmNonRetryableException(detail, ctx.getProviderId(), ctx.getModelName(), ctx.getScenario(), t);
    }

    protected boolean isConnectionError(Throwable t) {
        String cn = t.getClass().getName();
        return cn.contains("Timeout") || cn.contains("ConnectException")
                || cn.contains("IOException") || cn.contains("timeout");
    }

    private int extractStatus(String message) {
        if (message == null) {
            return -1;
        }
        Matcher matcher = STATUS_PATTERN.matcher(message);
        int last = -1;
        while (matcher.find()) {
            try {
                last = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return last;
    }

    protected String truncate(String message) {
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Ad-hoc ToolCallback：仅承载 ToolDefinition 喂给模型；Spring AI 2.0 stream 不自动执行，
     * 故 call() 不应被调用（防御性返回空串）。
     */
    private static final class AdHocToolCallback implements ToolCallback {

        private final ToolDefinition definition;

        AdHocToolCallback(ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return "";
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return "";
        }
    }
}
