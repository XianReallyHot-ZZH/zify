package com.zify.tool.infrastructure.exception;

/**
 * 该 tool_id 熔断中（连续可重试失败达阈值），直接拒绝（13 §6.1）。不可重试（需等 OPEN 冷却）。
 */
public class ToolCircuitOpenException extends ToolException {

    public ToolCircuitOpenException(String message, String toolId, String toolName, String scenario) {
        super(message, toolId, toolName, scenario, false);
    }
}
