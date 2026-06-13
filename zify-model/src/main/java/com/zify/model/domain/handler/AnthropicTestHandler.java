package com.zify.model.domain.handler;

import com.zify.model.api.dto.model.ModelTestResult;
import com.zify.model.api.dto.provider.ProviderTestResult;
import com.zify.model.domain.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

/**
 * Anthropic 供应商连通性测试 Handler
 */
@Component
public class AnthropicTestHandler implements ProviderTestHandler {

    private static final Logger log = LoggerFactory.getLogger(AnthropicTestHandler.class);

    private final RestClient modelTestRestClient;

    public AnthropicTestHandler(RestClient modelTestRestClient) {
        this.modelTestRestClient = modelTestRestClient;
    }

    @Override
    public Set<ProviderType> supportedTypes() {
        return Set.of(ProviderType.ANTHROPIC);
    }

    @Override
    public ProviderTestResult testConnection(String baseUrl, String apiKey,
                                              Map<String, Object> extraConfig, long startMs) {
        try {
            String apiVersion = resolveApiVersion(extraConfig);

            String body = "{\"model\":\"claude-sonnet-4-20250514\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                    + "\"max_tokens\":1}";

            modelTestRestClient.post()
                    .uri(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", apiVersion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            long latencyMs = System.currentTimeMillis() - startMs;
            ProviderTestResult result = new ProviderTestResult();
            result.setSuccess(true);
            result.setMessage("连接成功");
            result.setLatencyMs(latencyMs);
            result.setAvailableModels(null);
            return result;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.warn("Anthropic connection test failed: {}", e.getMessage());
            ProviderTestResult result = new ProviderTestResult();
            result.setSuccess(false);
            result.setMessage(extractErrorMessage(e));
            result.setLatencyMs(latencyMs);
            return result;
        }
    }

    @Override
    public ModelTestResult testLlmModel(String baseUrl, String apiKey, String modelName,
                                         Map<String, Object> extraConfig, long startMs) {
        String apiVersion = resolveApiVersion(extraConfig);

        String body = "{\"model\":\"" + modelName
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                + "\"max_tokens\":1}";

        modelTestRestClient.post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        long latencyMs = System.currentTimeMillis() - startMs;
        return buildTestResult(true, "模型可用", latencyMs, null);
    }

    @Override
    public ModelTestResult testEmbeddingModel(String baseUrl, String apiKey, String modelName, long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        return buildTestResult(false, "Anthropic 不提供 Embedding 模型", latencyMs,
                "Anthropic has no embedding API");
    }

    private String resolveApiVersion(Map<String, Object> extraConfig) {
        String apiVersion = "2023-06-01";
        if (extraConfig != null && extraConfig.get("apiVersion") != null) {
            apiVersion = extraConfig.get("apiVersion").toString();
        }
        return apiVersion;
    }

    private ModelTestResult buildTestResult(boolean success, String message, long latencyMs, String errorDetail) {
        ModelTestResult result = new ModelTestResult();
        result.setSuccess(success);
        result.setMessage(message);
        result.setLatencyMs(latencyMs);
        result.setErrorDetail(errorDetail);
        return result;
    }

    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
