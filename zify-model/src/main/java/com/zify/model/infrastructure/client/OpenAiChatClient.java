package com.zify.model.infrastructure.client;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.zify.model.domain.ProviderType;
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
        if (ctx.getApiKey() != null && !ctx.getApiKey().isBlank()) {
            builder.apiKey(ctx.getApiKey());
        }
        OpenAIClient client = new OpenAIClientImpl(builder.build());

        OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder().openAiClient(client);
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
