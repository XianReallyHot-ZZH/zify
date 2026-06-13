package com.zify.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.chat.api.dto.MessageResponse;
import com.zify.chat.api.dto.SendMessageRequest;
import com.zify.chat.api.dto.SendMessageResult;
import com.zify.chat.infrastructure.converter.MessageConverter;
import com.zify.chat.infrastructure.entity.ConversationEntity;
import com.zify.chat.infrastructure.entity.MessageEntity;
import com.zify.chat.infrastructure.mapper.ConversationMapper;
import com.zify.chat.infrastructure.mapper.MessageMapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.common.web.CursorPageRequest;
import com.zify.common.web.CursorPageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息 Service：发送（短事务，不调 LLM）+ 历史 Keyset。
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String ROLE_USER = "USER";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final AgentFacade agentFacade;
    private final ChatContextProperties chatContextProperties;
    private final TokenEstimator tokenEstimator;

    public MessageService(MessageMapper messageMapper, ConversationMapper conversationMapper,
                          AgentFacade agentFacade, ChatContextProperties chatContextProperties,
                          TokenEstimator tokenEstimator) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.agentFacade = agentFacade;
        this.chatContextProperties = chatContextProperties;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 发送用户消息（短事务，不调用 LLM）：校验 → INSERT USER 消息 → 更新会话计数/时间。
     */
    @Transactional
    public SendMessageResult send(String conversationId, SendMessageRequest request) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_ACTIVE);
        }

        AgentConfigDTO agent = agentFacade.getAgentConfig(conversation.getAgentId());
        if (!"REACT".equalsIgnoreCase(agent.getAgentType())) {
            throw new BusinessException(ErrorCode.AGENT_TYPE_INVALID);
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(agent.getStatus())) {
            throw new BusinessException(ErrorCode.AGENT_INACTIVE);
        }
        if (!agentFacade.isModelAvailable(agent.getModelId())) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE);
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_EMPTY);
        }
        if (tokenEstimator.estimate(content) > chatContextProperties.getMaxInputTokens()) {
            throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG);
        }

        MessageEntity message = new MessageEntity();
        message.setId(IdGenerator.uuid());
        message.setConversationId(conversationId);
        message.setRole(ROLE_USER);
        message.setContent(content);
        messageMapper.insert(message);

        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getId, conversationId)
                .set(ConversationEntity::getMessageCount, conversation.getMessageCount() + 1)
                .set(ConversationEntity::getLastMessageAt, message.getCreatedAt()));

        log.info("User message sent: id={}, conversationId={}", message.getId(), conversationId);
        return new SendMessageResult(message.getId(), message.getCreatedAt());
    }

    /**
     * 消息历史：按 created_at DESC, id DESC Keyset（命中 idx_msg_conv_deleted_created_id）。
     */
    public CursorPageResult<MessageResponse> listHistory(String conversationId, CursorPageRequest request) {
        int limit = request.getLimit();
        int fetchSize = limit + 1;

        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getConversationId, conversationId);
        if (request.hasCursor()) {
            LocalDateTime cursorTs = request.getCursorCreatedAt();
            String cursorId = request.getCursorId();
            wrapper.and(qw -> qw
                    .lt(MessageEntity::getCreatedAt, cursorTs)
                    .or(sub -> sub
                            .eq(MessageEntity::getCreatedAt, cursorTs)
                            .lt(MessageEntity::getId, cursorId)));
        }
        wrapper.orderByDesc(MessageEntity::getCreatedAt).orderByDesc(MessageEntity::getId);
        wrapper.last("LIMIT " + fetchSize);

        List<MessageEntity> all = messageMapper.selectList(wrapper);
        boolean hasMore = all.size() > limit;
        List<MessageEntity> page = hasMore ? all.subList(0, limit) : all;

        List<MessageResponse> records = page.stream()
                .map(MessageConverter::toResponse)
                .collect(Collectors.toList());

        String nextCursorId = null;
        LocalDateTime nextCursorTs = null;
        if (hasMore && !page.isEmpty()) {
            MessageEntity last = page.get(page.size() - 1);
            nextCursorId = last.getId();
            nextCursorTs = last.getCreatedAt();
        }
        return CursorPageResult.of(records, nextCursorId, nextCursorTs, hasMore);
    }
}
