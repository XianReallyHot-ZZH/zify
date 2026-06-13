package com.zify.model.infrastructure.client;

import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatCompletionResult;

/**
 * 单个 Provider 协议的流式 Chat 客户端。
 * <p>
 * 实现按 providerType 选择：OpenAI / OPENAI_COMPATIBLE 共用 {@link OpenAiChatClient}，
 * ANTHROPIC 用 {@link AnthropicChatClient}。
 */
public interface LlmChatClient {

    /**
     * 流式调用：token 经 sink 回调，返回最终结果。阻塞当前（虚拟）线程直到流结束。
     * <p>
     * 中断（Thread.interrupted）/ 超过 deadline → 取消上游订阅（dispose）并抛
     * {@code LlmCancelledException} / {@code LlmTimeoutException}。
     */
    ChatCompletionResult streamChat(ChatCallContext ctx, TextStreamSink sink);
}
