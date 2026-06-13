package com.zify.chat.adapter.web;

import com.zify.chat.adapter.web.response.CursorPageResponse;
import com.zify.chat.api.dto.MessageResponse;
import com.zify.chat.api.dto.SendMessageRequest;
import com.zify.chat.api.dto.SendMessageResult;
import com.zify.chat.domain.CursorCodec;
import com.zify.chat.domain.MessageService;
import com.zify.common.web.CursorPageRequest;
import com.zify.common.web.CursorPageResult;
import com.zify.common.web.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息 Controller（归属会话路径下）。在本层做 opaque 游标编解码。
 */
@RestController
@RequestMapping("/api/chat/conversations")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/{id}/messages")
    public Result<SendMessageResult> send(@PathVariable String id,
                                          @Valid @RequestBody SendMessageRequest request) {
        return Result.ok(messageService.send(id, request));
    }

    @GetMapping("/{id}/messages")
    public Result<CursorPageResponse<MessageResponse>> listHistory(
            @PathVariable String id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        CursorPageRequest pageRequest = ConversationController.toCursorPageRequest(cursor, limit);
        CursorPageResult<MessageResponse> result = messageService.listHistory(id, pageRequest);
        String nextCursor = CursorCodec.encode(result.getNextCursorCreatedAt(), result.getNextCursorId());
        return Result.ok(new CursorPageResponse<>(result.getRecords(), nextCursor, result.getHasMore()));
    }
}
