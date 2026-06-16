package com.zify.tool.api.dto;

import java.util.Map;

/**
 * 工具执行命令（engine → ToolFacade）。
 */
public class ToolExecutionCommand {

    private String toolId;
    /** LLM 填的入参。 */
    private Map<String, Object> args;
    private ToolExecContext context;

    public ToolExecutionCommand() {
    }

    public ToolExecutionCommand(String toolId, Map<String, Object> args, ToolExecContext context) {
        this.toolId = toolId;
        this.args = args;
        this.context = context;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public ToolExecContext getContext() {
        return context;
    }

    public void setContext(ToolExecContext context) {
        this.context = context;
    }
}
