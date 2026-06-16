package com.zify.engine.api.dto;

import java.util.List;

/**
 * 单轮对话编排命令（chat → engine）。
 * <p>
 * history 含历史消息 + 本轮 user 输入（已由 chat 落库并加载）。
 * summary / summaryCoveredMessageId 为当前会话 running summary，可空（本任务恒空传入）。
 */
public class ChatTurnCommand {

    private String agentId;
    /** 触发会话（工具日志审计用，可空）。 */
    private String conversationId;
    private List<ChatMessage> history;
    private String assistantMessageId;
    private String summary;
    private String summaryCoveredMessageId;

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

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryCoveredMessageId() {
        return summaryCoveredMessageId;
    }

    public void setSummaryCoveredMessageId(String summaryCoveredMessageId) {
        this.summaryCoveredMessageId = summaryCoveredMessageId;
    }
}
