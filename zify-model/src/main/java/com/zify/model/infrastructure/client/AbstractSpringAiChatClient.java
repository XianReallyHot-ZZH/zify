package com.zify.model.infrastructure.client;

import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatMessage;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.chat.TokenUsage;
import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;
import com.zify.model.infrastructure.client.exception.LlmNonRetryableException;
import com.zify.model.infrastructure.client.exception.LlmRetryableException;
import com.zify.model.infrastructure.client.exception.LlmTimeoutException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring AI 的流式 Chat 客户端公共基类。
 * <p>
 * 子类（{@link OpenAiChatClient} / {@link AnthropicChatClient}）只负责按协议程序化构造
 * {@link ChatModel} 与 {@link ChatOptions}；本类负责把 reactive 的 {@code stream(Prompt)}
 * 在虚拟线程上用 {@link CountDownLatch} 阻塞桥接到 {@link TextStreamSink}，并处理
 * deadline / 中断取消（dispose 订阅）与异常分类（对齐 glm-docs/07 §七）。
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

        StringBuilder acc = new StringBuilder();
        AtomicReference<TokenUsage> usageRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Disposable disposable = flux.subscribe(
                resp -> {
                    Generation gen = resp.getResult();
                    if (gen != null && gen.getOutput() != null) {
                        String delta = gen.getOutput().getText();
                        if (delta != null && !delta.isEmpty()) {
                            acc.append(delta);
                            sink.onDelta(delta);
                        }
                    }
                    if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                        usageRef.set(toTokenUsage(resp.getMetadata().getUsage()));
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
        return new ChatCompletionResult(acc.toString(), "STOP", usageRef.get());
    }

    /** 程序化构造该协议的 ChatOptions（含 model 与合并后的参数）。 */
    protected abstract ChatOptions buildOptions(ChatCallContext ctx);

    /** 程序化构造该协议的 ChatModel（含 baseUrl / 解密 apiKey）。 */
    protected abstract ChatModel buildChatModel(ChatCallContext ctx, ChatOptions chatOptions);

    protected List<Message> toSpringMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            String role = m.getRole() == null ? "" : m.getRole().toUpperCase();
            switch (role) {
                case "SYSTEM" -> result.add(new SystemMessage(m.getContent()));
                case "ASSISTANT" -> result.add(new AssistantMessage(m.getContent()));
                default -> result.add(new UserMessage(m.getContent()));
            }
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
}
