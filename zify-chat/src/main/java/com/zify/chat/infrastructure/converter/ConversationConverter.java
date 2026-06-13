package com.zify.chat.infrastructure.converter;

import com.zify.chat.api.dto.ConversationResponse;
import com.zify.chat.api.dto.ConversationSummaryResponse;
import com.zify.chat.infrastructure.entity.ConversationEntity;

/**
 * Conversation Entity <-> DTO 转换器（静态工具）。agentName 由 Service 补。
 */
public final class ConversationConverter {

    public static final String DEFAULT_STATUS = "ACTIVE";

    private ConversationConverter() {
    }

    public static ConversationResponse toResponse(ConversationEntity entity, String agentName) {
        ConversationResponse response = new ConversationResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setAgentId(entity.getAgentId());
        response.setAgentName(agentName);
        response.setStatus(entity.getStatus());
        response.setMessageCount(entity.getMessageCount());
        response.setLastMessageAt(entity.getLastMessageAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public static ConversationSummaryResponse toSummary(ConversationEntity entity, String agentName) {
        ConversationSummaryResponse summary = new ConversationSummaryResponse();
        summary.setId(entity.getId());
        summary.setTitle(entity.getTitle());
        summary.setAgentName(agentName);
        summary.setMessageCount(entity.getMessageCount());
        summary.setLastMessageAt(entity.getLastMessageAt());
        return summary;
    }
}
