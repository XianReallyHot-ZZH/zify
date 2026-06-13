package com.zify.model.infrastructure.client.exception;

/**
 * 不可重试的 LLM 调用异常：400 / 401 / 403 / 参数错误 / 上下文过长 / 模型不存在。
 */
public class LlmNonRetryableException extends LlmException {

    public LlmNonRetryableException(String message, String providerId, String modelName, String scenario) {
        super(message, providerId, modelName, scenario, false);
    }

    public LlmNonRetryableException(String message, String providerId, String modelName, String scenario, Throwable cause) {
        super(message, providerId, modelName, scenario, false, cause);
    }
}
