package com.zify.model.infrastructure.client.exception;

/**
 * LLM 调用异常基类（对齐 glm-docs/07 §七）。
 * <p>
 * 携带 providerId / modelName / scenario / retryable，用于结构化日志与重试决策。
 * <b>禁止包含 API Key。</b>
 */
public class LlmException extends RuntimeException {

    private final String providerId;
    private final String modelName;
    private final String scenario;
    private final boolean retryable;

    public LlmException(String message, String providerId, String modelName, String scenario, boolean retryable) {
        super(message);
        this.providerId = providerId;
        this.modelName = modelName;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public LlmException(String message, String providerId, String modelName, String scenario,
                        boolean retryable, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.modelName = modelName;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
