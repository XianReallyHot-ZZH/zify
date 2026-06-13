package com.zify.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zify.agent.api.AgentFacade;
import com.zify.agent.api.dto.AgentConfigDTO;
import com.zify.chat.api.dto.ConversationResponse;
import com.zify.chat.api.dto.ConversationSummaryResponse;
import com.zify.chat.api.dto.CreateConversationRequest;
import com.zify.chat.infrastructure.converter.ConversationConverter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话管理 Service（创建 / Keyset 列表 / 详情 / 删除）。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentFacade agentFacade;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                               AgentFacade agentFacade) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.agentFacade = agentFacade;
    }

    @Transactional
    public ConversationResponse create(CreateConversationRequest request) {
        AgentConfigDTO agent = agentFacade.getAgentConfig(request.getAgentId());
        if (!"REACT".equalsIgnoreCase(agent.getAgentType())) {
            throw new BusinessException(ErrorCode.AGENT_TYPE_INVALID);
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(agent.getStatus())) {
            throw new BusinessException(ErrorCode.AGENT_INACTIVE);
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationEntity entity = new ConversationEntity();
        entity.setId(IdGenerator.uuid());
        entity.setTitle(agent.getName());
        entity.setAgentId(agent.getId());
        entity.setStatus(STATUS_ACTIVE);
        entity.setMessageCount(0);
        entity.setLastMessageAt(now);
        conversationMapper.insert(entity);
        log.info("Conversation created: id={}, agentId={}", entity.getId(), agent.getId());

        return ConversationConverter.toResponse(entity, agent.getName());
    }

    /**
     * 会话列表：按 last_message_at DESC, id DESC Keyset 分页（命中 idx_conv_deleted_lastmsg_id）。
     * <p>
     * 注：{@link CursorPageRequest#getCursorCreatedAt()} 在此复用为 last_message_at 游标分量。
     */
    public CursorPageResult<ConversationSummaryResponse> list(String agentId, String titleLike,
                                                               CursorPageRequest request) {
        int limit = request.getLimit();
        int fetchSize = limit + 1;

        LambdaQueryWrapper<ConversationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentId != null && !agentId.isBlank(), ConversationEntity::getAgentId, agentId);
        if (titleLike != null && !titleLike.isBlank()) {
            wrapper.likeRight(ConversationEntity::getTitle, titleLike);
        }
        if (request.hasCursor()) {
            LocalDateTime cursorTs = request.getCursorCreatedAt();
            String cursorId = request.getCursorId();
            wrapper.and(qw -> qw
                    .lt(ConversationEntity::getLastMessageAt, cursorTs)
                    .or(sub -> sub
                            .eq(ConversationEntity::getLastMessageAt, cursorTs)
                            .lt(ConversationEntity::getId, cursorId)));
        }
        wrapper.orderByDesc(ConversationEntity::getLastMessageAt).orderByDesc(ConversationEntity::getId);
        wrapper.last("LIMIT " + fetchSize);

        List<ConversationEntity> all = conversationMapper.selectList(wrapper);
        boolean hasMore = all.size() > limit;
        List<ConversationEntity> page = hasMore ? all.subList(0, limit) : all;

        Map<String, String> agentNameMap = buildAgentNameMap(page);
        List<ConversationSummaryResponse> records = page.stream()
                .map(entity -> ConversationConverter.toSummary(entity, agentNameMap.get(entity.getAgentId())))
                .collect(Collectors.toList());

        String nextCursorId = null;
        LocalDateTime nextCursorTs = null;
        if (hasMore && !page.isEmpty()) {
            ConversationEntity last = page.get(page.size() - 1);
            nextCursorId = last.getId();
            nextCursorTs = last.getLastMessageAt();
        }
        return CursorPageResult.of(records, nextCursorId, nextCursorTs, hasMore);
    }

    public ConversationResponse get(String id) {
        ConversationEntity entity = getConversationOrThrow(id);
        return ConversationConverter.toResponse(entity, safeAgentName(entity.getAgentId()));
    }

    @Transactional
    public void delete(String id) {
        getConversationOrThrow(id);
        // 软删会话 + 其下全部消息（同一事务，纯数据库写）
        conversationMapper.deleteById(id);
        messageMapper.delete(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getConversationId, id));
        log.info("Conversation deleted (soft, cascade messages): id={}", id);
    }

    /**
     * 落库历史摘要（engine 压缩后由 chat 写入 conversation）。
     * <p>
     * 幂等 CAS：仅当 summary_covered_message_id 仍为旧值（或首次为空）时更新，避免并发推进覆盖。
     */
    @Transactional
    public void updateSummary(String conversationId, String summaryText, String coveredMessageId,
                              String previousCoveredId) {
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getId, conversationId)
                .and(w -> w.isNull(ConversationEntity::getSummaryCoveredMessageId)
                        .or().eq(ConversationEntity::getSummaryCoveredMessageId, previousCoveredId))
                .set(ConversationEntity::getSummaryText, summaryText)
                .set(ConversationEntity::getSummaryCoveredMessageId, coveredMessageId));
        if (updated == 0) {
            log.info("Summary CAS skipped (already advanced): conversationId={}", conversationId);
        }
    }

    private ConversationEntity getConversationOrThrow(String id) {
        ConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return entity;
    }

    private Map<String, String> buildAgentNameMap(List<ConversationEntity> conversations) {
        Map<String, String> map = new HashMap<>();
        for (ConversationEntity c : conversations) {
            map.putIfAbsent(c.getAgentId(), safeAgentName(c.getAgentId()));
        }
        return map;
    }

    /**
     * 安全获取 Agent 名称：Agent 已删时 getAgentConfig 抛异常，这里降级为 null（只读会话仍可展示）。
     */
    private String safeAgentName(String agentId) {
        if (agentId == null) {
            return null;
        }
        try {
            AgentConfigDTO agent = agentFacade.getAgentConfig(agentId);
            return agent.getName();
        } catch (BusinessException e) {
            return null;
        }
    }
}
