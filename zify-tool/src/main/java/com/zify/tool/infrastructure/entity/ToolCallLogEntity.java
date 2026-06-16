package com.zify.tool.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zify.common.persistence.entity.BaseEntity;

import java.util.Map;

/**
 * 工具调用日志实体（映射 tool_call_log 表，大表）。
 * <p>
 * input 为 args JSON（Map，JacksonTypeHandler），故 autoResultMap=true；output 为截断后文本。
 * 大表无 created_by/updated_by（§3.3）。workflow_run_id/workflow_node_run_id P2 恒 null（列预建）。
 */
@TableName(value = "tool_call_log", autoResultMap = true)
public class ToolCallLogEntity extends BaseEntity {

    private String toolId;
    private String toolName;
    private String sourceType;
    private String mcpServerId;
    private String agentId;
    private String conversationId;
    private String workflowRunId;
    private String workflowNodeRunId;
    private Integer turn;
    private String toolCallId;

    /** 调用入参（args JSON，截断后）。 */
    @TableField(value = "input", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> input;
    /** 调用结果 output（截断后）。 */
    private String output;
    /** 执行状态：SUCCESS / ERROR / TIMEOUT / CIRCUIT_OPEN / CANCELLED。 */
    private String status;
    private Integer durationMs;
    /** 失败时的精简错误信息（成功为 null；不含鉴权凭据）。 */
    private String error;

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getMcpServerId() {
        return mcpServerId;
    }

    public void setMcpServerId(String mcpServerId) {
        this.mcpServerId = mcpServerId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(String workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public String getWorkflowNodeRunId() {
        return workflowNodeRunId;
    }

    public void setWorkflowNodeRunId(String workflowNodeRunId) {
        this.workflowNodeRunId = workflowNodeRunId;
    }

    public Integer getTurn() {
        return turn;
    }

    public void setTurn(Integer turn) {
        this.turn = turn;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
