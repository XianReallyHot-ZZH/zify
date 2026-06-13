package com.zify.model.infrastructure.client.exception;

/**
 * Provider 并发已满（bulkhead 未在 acquire-timeout 内拿到许可），可稍后重试。
 */
public class LlmBusyException extends LlmException {

    public LlmBusyException(String message, String providerId, String modelName, String scenario) {
        super(message, providerId, modelName, scenario, true);
    }
}
