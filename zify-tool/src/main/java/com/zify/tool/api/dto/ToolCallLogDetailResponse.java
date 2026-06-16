package com.zify.tool.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工具调用日志详情（含 input/output/error，按主键取）。
 */
public class ToolCallLogDetailResponse {

    private String id;
    private String toolId;
    private String toolName;
    private String sourceType;
    private String agentId;
    private String conversationId;
    private Integer turn;
    private String toolCallId;
    private Map<String, Object> input;
    private String output;
    private String status;
    private Integer durationMs;
    private String error;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Integer getTurn() { return turn; }
    public void setTurn(Integer turn) { this.turn = turn; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
