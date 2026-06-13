package com.zify.chat.infrastructure.converter;

import com.zify.chat.api.dto.MessageResponse;
import com.zify.chat.infrastructure.entity.MessageEntity;

/**
 * Message Entity <-> DTO 转换器（静态工具）。
 */
public final class MessageConverter {

    private MessageConverter() {
    }

    public static MessageResponse toResponse(MessageEntity entity) {
        MessageResponse response = new MessageResponse();
        response.setId(entity.getId());
        response.setRole(entity.getRole());
        response.setContent(entity.getContent());
        response.setMetadata(entity.getMetadata());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
