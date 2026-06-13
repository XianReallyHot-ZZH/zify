package com.zify.chat.adapter.web;

import com.zify.chat.adapter.web.response.CursorPageResponse;
import com.zify.chat.api.dto.ConversationResponse;
import com.zify.chat.api.dto.ConversationSummaryResponse;
import com.zify.chat.api.dto.CreateConversationRequest;
import com.zify.chat.domain.ConversationService;
import com.zify.chat.domain.CursorCodec;
import com.zify.common.web.CursorPageRequest;
import com.zify.common.web.CursorPageResult;
import com.zify.common.web.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话 Controller：在本层做 opaque 游标编解码，CursorPageResult（common）不改。
 */
@RestController
@RequestMapping("/api/chat/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Result<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
        return Result.ok(conversationService.create(request));
    }

    @GetMapping
    public Result<CursorPageResponse<ConversationSummaryResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String title) {

        CursorPageRequest request = toCursorPageRequest(cursor, limit);
        CursorPageResult<ConversationSummaryResponse> result = conversationService.list(agentId, title, request);
        return Result.ok(toCursorPageResponse(result));
    }

    @GetMapping("/{id}")
    public Result<ConversationResponse> get(@PathVariable String id) {
        return Result.ok(conversationService.get(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        conversationService.delete(id);
        return Result.ok();
    }

    static CursorPageRequest toCursorPageRequest(String cursor, Integer limit) {
        CursorPageRequest request = new CursorPageRequest();
        if (limit != null) {
            request.setLimit(limit);
        }
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.DecodedCursor decoded = CursorCodec.decode(cursor);
            if (decoded != null) {
                request.setCursorCreatedAt(decoded.getTimestamp());
                request.setCursorId(decoded.getId());
            }
        }
        return request;
    }

    static <T> CursorPageResponse<T> toCursorPageResponse(CursorPageResult<T> result) {
        String nextCursor = CursorCodec.encode(result.getNextCursorCreatedAt(), result.getNextCursorId());
        return new CursorPageResponse<>(result.getRecords(), nextCursor, result.getHasMore());
    }
}
