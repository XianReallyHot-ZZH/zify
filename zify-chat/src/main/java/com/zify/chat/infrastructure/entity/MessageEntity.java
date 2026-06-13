package com.zify.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zify.common.persistence.entity.BaseEntity;

import java.util.Map;

/**
 * 对话消息实体（映射 message 表）。metadata 仅 ASSISTANT 写入，USER 为 null。
 * <p>
 * message 表无 created_by/updated_by、无 status；ASSISTANT 仅生成完成后 INSERT。
 */
@TableName(value = "message", autoResultMap = true)
public class MessageEntity extends BaseEntity {

    private String conversationId;

    private String role;

    private String content;

    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
