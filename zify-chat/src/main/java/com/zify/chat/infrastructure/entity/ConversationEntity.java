package com.zify.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zify.common.persistence.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 对话会话实体（映射 conversation 表，含历史摘要列）。
 */
@TableName("conversation")
public class ConversationEntity extends BaseEntity {

    private String createdBy;
    private String updatedBy;
    private String title;
    private String agentId;
    private String status;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private String summaryText;
    private String summaryCoveredMessageId;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getSummaryCoveredMessageId() {
        return summaryCoveredMessageId;
    }

    public void setSummaryCoveredMessageId(String summaryCoveredMessageId) {
        this.summaryCoveredMessageId = summaryCoveredMessageId;
    }
}
