package com.zify.tool.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.web.CursorPageResult;
import com.zify.tool.api.dto.ToolCallLogDetailResponse;
import com.zify.tool.api.dto.ToolCallLogQuery;
import com.zify.tool.api.dto.ToolCallLogSummaryResponse;
import com.zify.tool.infrastructure.converter.ToolCallLogConverter;
import com.zify.tool.infrastructure.entity.ToolCallLogEntity;
import com.zify.tool.infrastructure.mapper.ToolCallLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具调用日志查询（glm-docs/13 §6 / P2 §12.3）。
 * <p>
 * 大表 Keyset（created_at DESC, id DESC）；至少一个过滤维度，禁止全表扫；
 * 列表只返回轻量字段（不返回 input/output），详情按主键返回。
 */
@Service
public class ToolCallLogService {

    private final ToolCallLogMapper mapper;

    public ToolCallLogService(ToolCallLogMapper mapper) {
        this.mapper = mapper;
    }

    public ToolCallLogDetailResponse get(String id) {
        ToolCallLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return ToolCallLogConverter.toDetail(entity);
    }

    public CursorPageResult<ToolCallLogSummaryResponse> list(ToolCallLogQuery query) {
        if (isBlank(query.getConversationId()) && isBlank(query.getAgentId()) && isBlank(query.getToolId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少传一个过滤维度（conversationId/agentId/toolId）");
        }
        int limit = query.getLimit();
        int fetchSize = limit + 1;

        LambdaQueryWrapper<ToolCallLogEntity> wrapper = new LambdaQueryWrapper<>();
        // 显式轻量列（不取 input/output 大字段）
        wrapper.select(ToolCallLogEntity::getId, ToolCallLogEntity::getToolName,
                ToolCallLogEntity::getSourceType, ToolCallLogEntity::getStatus,
                ToolCallLogEntity::getDurationMs, ToolCallLogEntity::getCreatedAt);
        if (!isBlank(query.getConversationId())) {
            wrapper.eq(ToolCallLogEntity::getConversationId, query.getConversationId());
        }
        if (!isBlank(query.getAgentId())) {
            wrapper.eq(ToolCallLogEntity::getAgentId, query.getAgentId());
        }
        if (!isBlank(query.getToolId())) {
            wrapper.eq(ToolCallLogEntity::getToolId, query.getToolId());
        }
        if (query.hasCursor()) {
            LocalDateTime cursorTs = query.getCursorCreatedAt();
            String cursorId = query.getCursorId();
            wrapper.and(qw -> qw
                    .lt(ToolCallLogEntity::getCreatedAt, cursorTs)
                    .or(sub -> sub
                            .eq(ToolCallLogEntity::getCreatedAt, cursorTs)
                            .lt(ToolCallLogEntity::getId, cursorId)));
        }
        wrapper.orderByDesc(ToolCallLogEntity::getCreatedAt).orderByDesc(ToolCallLogEntity::getId);
        wrapper.last("LIMIT " + fetchSize);

        List<ToolCallLogEntity> all = mapper.selectList(wrapper);
        boolean hasMore = all.size() > limit;
        List<ToolCallLogEntity> page = hasMore ? all.subList(0, limit) : all;
        List<ToolCallLogSummaryResponse> records = page.stream()
                .map(ToolCallLogConverter::toSummary)
                .collect(Collectors.toList());

        String nextCursorId = null;
        LocalDateTime nextCursorTs = null;
        if (hasMore && !page.isEmpty()) {
            ToolCallLogEntity last = page.get(page.size() - 1);
            nextCursorId = last.getId();
            nextCursorTs = last.getCreatedAt();
        }
        return CursorPageResult.of(records, nextCursorId, nextCursorTs, hasMore);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
