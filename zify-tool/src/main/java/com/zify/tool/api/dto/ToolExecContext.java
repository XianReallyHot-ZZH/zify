package com.zify.tool.api.dto;

/**
 * 工具执行审计上下文（纯审计，不发给模型）。对话场景非空，手动测试/工作流为 null。
 */
public class ToolExecContext {

    private String conversationId;
    private String agentId;
    private Integer turn;

    public ToolExecContext() {
    }

    public ToolExecContext(String conversationId, String agentId, Integer turn) {
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.turn = turn;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Integer getTurn() {
        return turn;
    }

    public void setTurn(Integer turn) {
        this.turn = turn;
    }
}
