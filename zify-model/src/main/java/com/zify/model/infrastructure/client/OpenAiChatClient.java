package com.zify.model.infrastructure.client;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import com.zify.model.domain.ProviderType;
import com.zify.model.infrastructure.client.exception.LlmNonRetryableException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * OpenAI / OPENAI_COMPATIBLE 流式 Chat 客户端（Spring AI 2.0）。
 * <p>
 * 程序化构造 OpenAIClient（baseUrl + 解密 apiKey + SpringAiOpenAiHttpClient），包装为
 * {@link OpenAiChatModel}；流式桥接与异常分类由 {@link AbstractSpringAiChatClient} 统一处理。
 */
@Component
public class OpenAiChatClient extends AbstractSpringAiChatClient {

    @Override
    protected ChatOptions buildOptions(ChatCallContext ctx) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(ctx.getModelName());
        applyOptions(builder, ctx.getOptions());
        if (ctx.getToolDefinitions() != null && !ctx.getToolDefinitions().isEmpty()) {
            builder.toolCallbacks(toToolCallbacks(ctx.getToolDefinitions()));
        }
        return builder.build();
    }

    @Override
    protected ChatModel buildChatModel(ChatCallContext ctx, ChatOptions chatOptions) {
        Duration total = Duration.between(Instant.now(), ctx.getDeadline());
        SpringAiOpenAiHttpClient httpClient = SpringAiOpenAiHttpClient.builder()
                .timeout(total)
                .build();

        ClientOptions.Builder builder = new ClientOptions.Builder()
                .baseUrl(ctx.getBaseUrl())
                .httpClient(httpClient)
                .timeout(total);
        String apiKey = ctx.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // apiKey 缺失：给明确错误，而非让 SDK 抛模糊的 "credential source must be specified"
            throw new LlmNonRetryableException("provider has no apiKey; configure it in model management",
                    ctx.getProviderId(), ctx.getModelName(), ctx.getScenario());
        }
        // OpenAIClientImpl 构造时需要一个 credential 对象；OpenAI/兼容的 apiKey 本质是 Bearer token。
        // 同时设 apiKey 与 credential(BearerTokenCredential) 双保险。
        builder.apiKey(apiKey).credential(BearerTokenCredential.create(apiKey));
        ClientOptions options = builder.build();

        // OpenAiChatModel 的流式调用走 async client：必须同时提供 openAiClientAsync，
        // 否则 Builder.build() 会调 OpenAiSetup.setupAsyncClient 自建（需要 spring.ai.* 凭证）→
        // 抛 "At least one credential source must be specified"。同一个 ClientOptions 构造 sync + async。
        OpenAIClient syncClient = new OpenAIClientImpl(options);
        OpenAIClientAsync asyncClient = new OpenAIClientAsyncImpl(options);

        OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient);
        if (chatOptions instanceof OpenAiChatOptions openAiOptions) {
            modelBuilder.options(openAiOptions);
        }
        return modelBuilder.build();
    }

    private void applyOptions(OpenAiChatOptions.Builder builder, com.zify.model.api.dto.chat.ChatOptions opts) {
        if (opts == null) {
            return;
        }
        if (opts.getTemperature() != null) {
            builder.temperature(opts.getTemperature());
        }
        if (opts.getTopP() != null) {
            builder.topP(opts.getTopP());
        }
        if (opts.getMaxTokens() != null) {
            builder.maxTokens(opts.getMaxTokens());
        }
    }

    /**
     * 该 Client 处理的协议类型。
     */
    public static boolean supports(String providerType) {
        return ProviderType.OPENAI.name().equals(providerType)
                || ProviderType.OPENAI_COMPATIBLE.name().equals(providerType);
    }
}
