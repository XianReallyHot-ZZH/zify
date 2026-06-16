package com.zify.tool.adapter.web;

import com.zify.common.web.CursorPageResult;
import com.zify.common.web.Result;
import com.zify.tool.adapter.web.response.CursorPageResponse;
import com.zify.tool.api.dto.ToolCallLogDetailResponse;
import com.zify.tool.api.dto.ToolCallLogQuery;
import com.zify.tool.api.dto.ToolCallLogSummaryResponse;
import com.zify.tool.domain.CursorCodec;
import com.zify.tool.domain.ToolCallLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具调用日志 Controller（glm-docs/13 §6 / P2 §12.3）。在本层做 opaque 游标编解码。
 */
@RestController
@RequestMapping("/api/tool/call-logs")
public class ToolCallLogController {

    private final ToolCallLogService service;

    public ToolCallLogController(ToolCallLogService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Result<ToolCallLogDetailResponse> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    @GetMapping
    public Result<CursorPageResponse<ToolCallLogSummaryResponse>> list(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        ToolCallLogQuery query = new ToolCallLogQuery();
        query.setConversationId(conversationId);
        query.setAgentId(agentId);
        query.setToolId(toolId);
        query.setLimit(limit);
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.DecodedCursor decoded = CursorCodec.decode(cursor);
            if (decoded != null) {
                query.setCursorCreatedAt(decoded.getTimestamp());
                query.setCursorId(decoded.getId());
            }
        }

        CursorPageResult<ToolCallLogSummaryResponse> result = service.list(query);
        String nextCursor = CursorCodec.encode(result.getNextCursorCreatedAt(), result.getNextCursorId());
        return Result.ok(new CursorPageResponse<>(result.getRecords(), nextCursor, result.getHasMore()));
    }
}
