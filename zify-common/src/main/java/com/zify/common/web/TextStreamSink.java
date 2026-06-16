package com.zify.common.web;

/**
 * 流式回调（P2 升级）：文本 delta + ReAct 循环的工具事件。
 * <p>
 * 保留 {@code onDelta} 为唯一抽象方法（@FunctionalInterface 不变，P1 的单方法 lambda 调用方不受影响）；
 * 工具事件用 default 空实现，engine 在 ReAct 循环里调用。用原生类型（String/long），不在 common 引入业务 DTO；
 * {@code conversationId}/{@code assistantMessageId} 由 chat 层（持有会话上下文）在转 SSE 时补入。
 * <p>
 * 流向：chat（SseEmitter.send 包装为 sink）→ engine.runChatTurn(command, sink)
 * → model.chatStream(command, sink)。token 经 onDelta 回调；工具事件经 onToolCallStart/End；
 * 错误用异常上抛。
 */
public interface TextStreamSink {

    /**
     * 收到一段增量文本（model 调）。
     *
     * @param delta 增量文本，不为 null（可能为空串）
     */
    void onDelta(String delta);

    /** engine 每轮文本段开始（ReAct 多轮每轮独立 assistantMessageId）。 */
    default void onAssistantSegment(String assistantMessageId) {
    }

    /** 模型决定调工具、engine 即将执行。 */
    default void onToolCallStart(String assistantMessageId, String toolCallId, String toolName, String argsJson) {
    }

    /** 工具执行完（成功/失败/熔断）。 */
    default void onToolCallEnd(String assistantMessageId, String toolCallId, String toolName,
                               String status, String output, long durationMs, String toolCallLogId) {
    }
}
