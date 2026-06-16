package com.zify.tool.infrastructure.exception;

/**
 * 不可重试的工具调用异常：4xx / 参数错误 / 非幂等工具请求发出后失败 / SSRF 命中（13 §5.2 / §8.1）。
 */
public class ToolNonRetryableException extends ToolException {

    public ToolNonRetryableException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, false);
    }

    public ToolNonRetryableException(String message, String toolId, String toolName, String scenario, Throwable cause) {
        super(message, toolId, toolName, scenario, false, cause);
    }
}
