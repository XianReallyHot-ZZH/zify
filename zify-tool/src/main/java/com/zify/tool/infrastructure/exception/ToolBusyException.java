package com.zify.tool.infrastructure.exception;

/**
 * 全局并发已满（Semaphore 未在超时内拿到许可），可稍后重试（13 §3.2）。
 */
public class ToolBusyException extends ToolException {

    public ToolBusyException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, true);
    }
}
