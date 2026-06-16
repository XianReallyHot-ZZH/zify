package com.zify.tool.infrastructure.exception;

/**
 * 工具调用总 deadline 超时（13 §4）。不可重试（已耗尽时间预算）。
 */
public class ToolTimeoutException extends ToolException {

    public ToolTimeoutException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, false);
    }

    public ToolTimeoutException(String message, String toolId, String toolName, String scenario, Throwable cause) {
        super(message, toolId, toolName, scenario, false, cause);
    }
}
