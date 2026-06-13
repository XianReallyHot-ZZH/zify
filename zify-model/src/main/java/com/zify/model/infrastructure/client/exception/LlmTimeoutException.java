package com.zify.model.infrastructure.client.exception;

/**
 * LLM 调用超时。
 * <p>
 * 首 token 超时构造时传 retryable=true（可重试）；总 deadline 超时 / idle 超时构造时传 retryable=false。
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message, String providerId, String modelName, String scenario, boolean retryable) {
        super(message, providerId, modelName, scenario, retryable);
    }

    public LlmTimeoutException(String message, String providerId, String modelName, String scenario,
                               boolean retryable, Throwable cause) {
        super(message, providerId, modelName, scenario, retryable, cause);
    }
}
