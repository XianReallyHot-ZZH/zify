package com.zify.tool.infrastructure.exception;

/**
 * 用户中断 / 任务取消（中断虚拟线程，13 §六）。致命错误，上抛 engine 终止循环。
 */
public class ToolCancelledException extends ToolException {

    public ToolCancelledException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, false);
    }

    public ToolCancelledException(String message, String toolId, String toolName, String scenario, Throwable cause) {
        super(message, toolId, toolName, scenario, false, cause);
    }
}
