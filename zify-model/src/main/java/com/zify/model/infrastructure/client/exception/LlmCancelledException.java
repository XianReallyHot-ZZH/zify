package com.zify.model.infrastructure.client.exception;

/**
 * 用户断开 / 任务取消（中断虚拟线程）。不可重试。
 */
public class LlmCancelledException extends LlmException {

    public LlmCancelledException(String message, String providerId, String modelName, String scenario) {
        super(message, providerId, modelName, scenario, false);
    }

    public LlmCancelledException(String message, String providerId, String modelName, String scenario, Throwable cause) {
        super(message, providerId, modelName, scenario, false, cause);
    }
}
