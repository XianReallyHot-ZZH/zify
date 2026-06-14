package com.zify.model.infrastructure.client;

import com.zify.common.security.SecretEncryptor;
import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.chat.ChatOptions;
import com.zify.model.config.LlmTimeoutProperties;
import com.zify.model.domain.ProviderType;
import com.zify.model.infrastructure.client.exception.LlmException;
import com.zify.model.infrastructure.client.exception.LlmNonRetryableException;
import com.zify.model.infrastructure.entity.ModelEntity;
import com.zify.model.infrastructure.entity.ModelProviderEntity;
import com.zify.model.infrastructure.mapper.ModelMapper;
import com.zify.model.infrastructure.mapper.ModelProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM 流式 Chat 网关：解析模型→供应商→解密 Key→选 Client→并发保护→重试→超时，并暴露给
 * {@code ModelFacade}（glm-docs/07 §二/§三/§四/§五）。
 * <p>
 * API Key 全程不记录、不入异常；结构化日志（event=llm_call）覆盖成功/失败/取消。
 */
@Component
public class LlmChatGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmChatGateway.class);
    private static final String SCENARIO = "chat_stream";

    private final ModelMapper modelMapper;
    private final ModelProviderMapper providerMapper;
    private final SecretEncryptor secretEncryptor;
    private final OpenAiChatClient openAiClient;
    private final AnthropicChatClient anthropicClient;
    private final LlmRetryWrapper retryWrapper;
    private final LlmTimeoutProperties timeoutProperties;
    private final int maxConcurrent;
    private final java.time.Duration acquireTimeout;
    private final ConcurrentHashMap<String, ProviderBulkhead> bulkheads = new ConcurrentHashMap<>();

    public LlmChatGateway(ModelMapper modelMapper,
                          ModelProviderMapper providerMapper,
                          SecretEncryptor secretEncryptor,
                          OpenAiChatClient openAiClient,
                          AnthropicChatClient anthropicClient,
                          LlmRetryWrapper retryWrapper,
                          LlmTimeoutProperties timeoutProperties,
                          com.zify.common.config.properties.LlmProperties llmProperties) {
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
        this.secretEncryptor = secretEncryptor;
        this.openAiClient = openAiClient;
        this.anthropicClient = anthropicClient;
        this.retryWrapper = retryWrapper;
        this.timeoutProperties = timeoutProperties;
        this.maxConcurrent = llmProperties.getProviderDefaults().getMaxConcurrent();
        this.acquireTimeout = llmProperties.getProviderDefaults().getAcquireTimeout();
    }

    public ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink) {
        // 1. 解析模型
        ModelEntity model = modelMapper.selectById(command.getModelId());
        if (model == null) {
            throw new LlmNonRetryableException("model not found: " + command.getModelId(),
                    null, null, "resolve_model");
        }
        if (model.getEnabled() == null || model.getEnabled() != 1) {
            throw new LlmNonRetryableException("model disabled: " + command.getModelId(),
                    null, model.getModelName(), "resolve_model");
        }

        // 2. 解析供应商
        ModelProviderEntity provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || provider.getIsDeleted() == null || provider.getIsDeleted() != 0
                || !"ACTIVE".equals(provider.getStatus())) {
            throw new LlmNonRetryableException("provider unavailable: " + model.getProviderId(),
                    model.getProviderId(), model.getModelName(), "resolve_model");
        }

        String providerId = provider.getId();
        String providerType = provider.getProviderType();
        String modelName = model.getModelName();

        // 3. 解密 apiKey（明文仅在本方法与 client 内存在）
        String apiKey = provider.getApiKey() != null ? secretEncryptor.decrypt(provider.getApiKey()) : null;

        // 4. 合并 options：command.options 覆盖 model.default_params
        ChatOptions merged = mergeOptions(command.getOptions(), model.getDefaultParams());

        // 5. 选 client
        LlmChatClient client = selectClient(providerType, providerId, modelName);

        // 6. deadline
        Instant deadline = Instant.now().plus(timeoutProperties.getChatStream().getTotal());

        // 7. 上下文
        ChatCallContext ctx = new ChatCallContext(
                providerType, provider.getBaseUrl(), apiKey, modelName,
                provider.getExtraConfig(), providerId,
                command.getMessages(), merged, deadline, SCENARIO);

        // 8. 跟踪 sink：记录是否已发 delta（供重试决策）
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        TextStreamSink trackingSink = delta -> {
            deltaSent.set(true);
            sink.onDelta(delta);
        };

        // 9. 并发保护 + 重试 + 调用
        long start = System.currentTimeMillis();
        ProviderBulkhead bulkhead = bulkheads.computeIfAbsent(providerId,
                id -> new ProviderBulkhead(maxConcurrent, acquireTimeout, id));
        try {
            ChatCompletionResult result = bulkhead.execute(
                    () -> retryWrapper.withRetry(
                            () -> client.streamChat(ctx, trackingSink),
                            deltaSent::get, deadline, providerId, modelName, SCENARIO),
                    modelName, SCENARIO);
            logSuccess(providerId, modelName, System.currentTimeMillis() - start, result);
            return result;
        } catch (LlmException e) {
            logFailure(providerId, modelName, System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    private LlmChatClient selectClient(String providerType, String providerId, String modelName) {
        ProviderType type;
        try {
            type = ProviderType.fromString(providerType);
        } catch (IllegalArgumentException e) {
            throw new LlmNonRetryableException("unsupported providerType: " + providerType,
                    providerId, modelName, "resolve_model", e);
        }
        return switch (type) {
            case OPENAI, OPENAI_COMPATIBLE -> openAiClient;
            case ANTHROPIC -> anthropicClient;
        };
    }

    private ChatOptions mergeOptions(ChatOptions commandOptions, Map<String, Object> defaults) {
        // model.default_params 可空（创建模型时不填），按无默认值处理
        if (defaults == null) {
            defaults = Map.of();
        }
        ChatOptions merged = new ChatOptions();
        merged.setTemperature(firstNonNull(asDouble(commandOptions, ChatOptions::getTemperature),
                asDouble(defaults.get("temperature"))));
        merged.setMaxTokens(firstNonNull(asInt(commandOptions, ChatOptions::getMaxTokens),
                asInt(defaults.get("maxTokens"))));
        merged.setTopP(firstNonNull(asDouble(commandOptions, ChatOptions::getTopP),
                asDouble(defaults.get("topP"))));
        return merged;
    }

    private Double asDouble(Object value) {
        return (value instanceof Number n) ? n.doubleValue() : null;
    }

    private Integer asInt(Object value) {
        return (value instanceof Number n) ? n.intValue() : null;
    }

    private Double asDouble(ChatOptions opts, java.util.function.Function<ChatOptions, Double> getter) {
        return opts == null ? null : getter.apply(opts);
    }

    private Integer asInt(ChatOptions opts, java.util.function.Function<ChatOptions, Integer> getter) {
        return opts == null ? null : getter.apply(opts);
    }

    private <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private void logSuccess(String providerId, String modelName, long durationMs, ChatCompletionResult result) {
        Integer prompt = result.getUsage() != null ? result.getUsage().getPromptTokens() : null;
        Integer completion = result.getUsage() != null ? result.getUsage().getCompletionTokens() : null;
        log.info("event=llm_call scenario={} provider={} model={} status=success durationMs={} promptTokens={} completionTokens={} finishReason={}",
                SCENARIO, providerId, modelName, durationMs, prompt, completion, result.getFinishReason());
    }

    private void logFailure(String providerId, String modelName, long durationMs, LlmException e) {
        if (e instanceof com.zify.model.infrastructure.client.exception.LlmCancelledException) {
            log.info("event=llm_call scenario={} provider={} model={} status=cancelled durationMs={} reason={}",
                    SCENARIO, providerId, modelName, durationMs, e.getMessage());
        } else {
            log.warn("event=llm_call scenario={} provider={} model={} status=failed durationMs={} retryable={} error={}",
                    SCENARIO, providerId, modelName, durationMs, e.isRetryable(), e.getMessage());
        }
    }
}
