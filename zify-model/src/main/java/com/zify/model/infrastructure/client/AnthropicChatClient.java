package com.zify.model.infrastructure.client;

import com.anthropic.client.AnthropicClient;
import com.zify.model.domain.ProviderType;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Anthropic 流式 Chat 客户端（Spring AI 2.0）。
 * <p>
 * 通过 {@link AnthropicSetup#setupSyncClient} 程序化构造 AnthropicClient（baseUrl + 解密 apiKey
 * + anthropic-version header），包装为 {@link AnthropicChatModel}；流式桥接与异常分类由
 * {@link AbstractSpringAiChatClient} 统一处理。
 */
@Component
public class AnthropicChatClient extends AbstractSpringAiChatClient {

    /** Anthropic 强制要求 max_tokens；调用方未提供时的兜底值。 */
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    @Override
    protected ChatOptions buildOptions(ChatCallContext ctx) {
        Integer maxTokens = ctx.getOptions() != null && ctx.getOptions().getMaxTokens() != null
                ? ctx.getOptions().getMaxTokens()
                : DEFAULT_MAX_TOKENS;

        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(ctx.getModelName())
                .maxTokens(maxTokens);

        com.zify.model.api.dto.chat.ChatOptions opts = ctx.getOptions();
        if (opts != null) {
            if (opts.getTemperature() != null) {
                builder.temperature(opts.getTemperature());
            }
            if (opts.getTopP() != null) {
                builder.topP(opts.getTopP());
            }
        }
        return builder.build();
    }

    @Override
    protected ChatModel buildChatModel(ChatCallContext ctx, ChatOptions chatOptions) {
        Duration total = Duration.between(Instant.now(), ctx.getDeadline());
        Map<String, String> headers = new HashMap<>();
        headers.put("anthropic-version", resolveApiVersion(ctx));

        AnthropicClient client = AnthropicSetup.setupSyncClient(
                ctx.getBaseUrl(),
                ctx.getApiKey(),
                total,
                0,
                null,
                headers
        );

        AnthropicChatModel.Builder modelBuilder = AnthropicChatModel.builder().anthropicClient(client);
        if (chatOptions instanceof AnthropicChatOptions anthropicOptions) {
            modelBuilder.options(anthropicOptions);
        }
        return modelBuilder.build();
    }

    private String resolveApiVersion(ChatCallContext ctx) {
        Map<String, Object> extra = ctx.getExtraConfig();
        if (extra != null && extra.get("apiVersion") != null) {
            return extra.get("apiVersion").toString();
        }
        return DEFAULT_ANTHROPIC_VERSION;
    }

    /**
     * 该 Client 处理的协议类型。
     */
    public static boolean supports(String providerType) {
        return ProviderType.ANTHROPIC.name().equals(providerType);
    }
}
