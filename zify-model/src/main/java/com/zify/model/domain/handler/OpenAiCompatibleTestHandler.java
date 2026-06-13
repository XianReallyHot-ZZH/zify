package com.zify.model.domain.handler;

import com.zify.model.api.dto.model.ModelTestResult;
import com.zify.model.api.dto.provider.ProviderTestResult;
import com.zify.model.domain.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * OpenAI 兼容供应商连通性测试 Handler
 * <p>
 * 同时支持 OPENAI 和 OPENAI_COMPATIBLE 类型（两者测试逻辑相同）。
 */
@Component
public class OpenAiCompatibleTestHandler implements ProviderTestHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleTestHandler.class);

    private final RestClient modelTestRestClient;

    public OpenAiCompatibleTestHandler(RestClient modelTestRestClient) {
        this.modelTestRestClient = modelTestRestClient;
    }

    @Override
    public Set<ProviderType> supportedTypes() {
        return Set.of(ProviderType.OPENAI, ProviderType.OPENAI_COMPATIBLE);
    }

    @Override
    public ProviderTestResult testConnection(String baseUrl, String apiKey,
                                              java.util.Map<String, Object> extraConfig, long startMs) {
        try {
            var requestSpec = modelTestRestClient.get()
                    .uri(baseUrl + "/v1/models")
                    .accept(MediaType.APPLICATION_JSON);

            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            requestSpec.retrieve().body(String.class);
            long latencyMs = System.currentTimeMillis() - startMs;

            ProviderTestResult result = new ProviderTestResult();
            result.setSuccess(true);
            result.setMessage("连接成功");
            result.setLatencyMs(latencyMs);
            result.setAvailableModels(null);
            return result;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.warn("OpenAI-compatible connection test failed: {}", e.getMessage());
            ProviderTestResult result = new ProviderTestResult();
            result.setSuccess(false);
            result.setMessage(extractErrorMessage(e));
            result.setLatencyMs(latencyMs);
            return result;
        }
    }

    @Override
    public ModelTestResult testLlmModel(String baseUrl, String apiKey, String modelName,
                                         java.util.Map<String, Object> extraConfig, long startMs) {
        String body = "{\"model\":\"" + modelName
                + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                + "\"max_tokens\":1}";

        var requestSpec = modelTestRestClient.post()
                .uri(baseUrl + "/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec.header("Authorization", "Bearer " + apiKey);
        }
        requestSpec.retrieve().body(String.class);

        long latencyMs = System.currentTimeMillis() - startMs;
        return buildTestResult(true, "模型可用", latencyMs, null);
    }

    @Override
    public ModelTestResult testEmbeddingModel(String baseUrl, String apiKey, String modelName, long startMs) {
        String body = "{\"model\":\"" + modelName + "\",\"input\":[\"test\"]}";

        var requestSpec = modelTestRestClient.post()
                .uri(baseUrl + "/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec.header("Authorization", "Bearer " + apiKey);
        }
        requestSpec.retrieve().body(String.class);

        long latencyMs = System.currentTimeMillis() - startMs;
        return buildTestResult(true, "模型可用", latencyMs, null);
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
