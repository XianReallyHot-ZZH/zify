package com.zify.tool.infrastructure.converter;

import com.zify.tool.api.dto.ToolCallLogDetailResponse;
import com.zify.tool.api.dto.ToolCallLogSummaryResponse;
import com.zify.tool.infrastructure.entity.ToolCallLogEntity;

/**
 * ToolCallLog Entity <-> DTO 转换器（静态工具）。列表不返回 input/output 大字段。
 */
public final class ToolCallLogConverter {

    private ToolCallLogConverter() {
    }

    public static ToolCallLogSummaryResponse toSummary(ToolCallLogEntity entity) {
        ToolCallLogSummaryResponse r = new ToolCallLogSummaryResponse();
        r.setId(entity.getId());
        r.setToolName(entity.getToolName());
        r.setSourceType(entity.getSourceType());
        r.setStatus(entity.getStatus());
        r.setDurationMs(entity.getDurationMs());
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }

    public static ToolCallLogDetailResponse toDetail(ToolCallLogEntity entity) {
        ToolCallLogDetailResponse r = new ToolCallLogDetailResponse();
        r.setId(entity.getId());
        r.setToolId(entity.getToolId());
        r.setToolName(entity.getToolName());
        r.setSourceType(entity.getSourceType());
        r.setAgentId(entity.getAgentId());
        r.setConversationId(entity.getConversationId());
        r.setTurn(entity.getTurn());
        r.setToolCallId(entity.getToolCallId());
        r.setInput(entity.getInput());
        r.setOutput(entity.getOutput());
        r.setStatus(entity.getStatus());
        r.setDurationMs(entity.getDurationMs());
        r.setError(entity.getError());
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }
}
