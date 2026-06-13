package com.zify.chat.adapter.sse;

import com.zify.chat.domain.ChatStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话流式 SSE Controller（chat 模块持有，glm-docs/06 §11.7 偏离：端点归 chat）。
 * <p>
 * 两步流式第二步：query string 只传 messageId，建立 GET SSE 连接。
 */
@RestController
public class ChatStreamSseController {

    /** SSE 连接兜底超时（非上游 LLM deadline，二者任一触发都取消上游）。 */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final ChatStreamService chatStreamService;

    public ChatStreamSseController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String messageId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        chatStreamService.startChatStream(messageId, emitter);
        return emitter;
    }
}
