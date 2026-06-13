package com.zify.common.web;

/**
 * 流式文本回调：每收到一段增量文本（delta）回调一次。
 * <p>
 * 为避免 engine / model / chat 三方重复定义回调，又不把 Spring MVC 的 SseEmitter
 * 泄漏到 domain / api 层，在 common 定义此单方法通用回调。
 * <p>
 * 流向：chat（将 SseEmitter.send 包装为 sink）→ engine.runChatTurn(command, sink)
 * → model.chatStream(command, sink)。token 逐块回调，错误用异常上抛，结束信息用方法返回值。
 */
@FunctionalInterface
public interface TextStreamSink {

    /**
     * 收到一段增量文本。
     *
     * @param delta 增量文本，不为 null（可能为空串）
     */
    void onDelta(String delta);
}
