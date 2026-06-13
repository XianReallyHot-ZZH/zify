package com.zify.model.infrastructure.client.exception;

/**
 * 可重试的 LLM 调用异常：429 / 5xx / 连接失败 / 首 token 超时。
 */
public class LlmRetryableException extends LlmException {

    public LlmRetryableException(String message, String providerId, String modelName, String scenario) {
        super(message, providerId, modelName, scenario, true);
    }

    public LlmRetryableException(String message, String providerId, String modelName, String scenario, Throwable cause) {
        super(message, providerId, modelName, scenario, true, cause);
    }
}
