import { useCallback } from 'react'
import { openChatStream, sendMessage } from '../../../api/chatApi'
import { useChatStore } from '../../../stores/chatStore'

/**
 * 封装两步流式发送：POST 落库用户消息 → EventSource 建立流。
 * 处理 message_delta / done / run_error 三类事件写入 chatStore；暴露 send / stop。
 * SSE 创建在 api/chatApi.ts，UI 状态在本 hook + store。
 */
export function useChatStream() {
  const currentConversationId = useChatStore((s) => s.currentConversationId)
  const appendMessage = useChatStore((s) => s.appendMessage)
  const appendDelta = useChatStore((s) => s.appendDelta)
  const appendToolCall = useChatStore((s) => s.appendToolCall)
  const updateToolCall = useChatStore((s) => s.updateToolCall)
  const finishAssistant = useChatStore((s) => s.finishAssistant)
  const setStreaming = useChatStore((s) => s.setStreaming)
  const setEventSource = useChatStore((s) => s.setEventSource)
  const setLastError = useChatStore((s) => s.setLastError)

  const send = useCallback(
    async (content: string) => {
      if (!currentConversationId) return
      const trimmed = content.trim()
      if (!trimmed) return

      // 乐观渲染 USER 气泡
      appendMessage({
        id: `local-${Date.now()}`,
        role: 'USER',
        content: trimmed,
        metadata: null,
        createdAt: new Date().toISOString(),
      })
      setStreaming(true)
      setLastError(null)

      let userMessageId: string
      try {
        const result = await sendMessage(currentConversationId, { content: trimmed })
        userMessageId = result.userMessageId
      } catch (err) {
        setStreaming(false)
        setLastError(err instanceof Error ? err.message : '发送失败')
        return
      }

      const es = openChatStream(userMessageId, {
        onDelta: (e) => appendDelta(e.assistantMessageId, e.delta),
        onToolCallStart: (e) =>
          appendToolCall(e.assistantMessageId, {
            toolCallId: e.toolCallId,
            toolName: e.toolName,
            args: e.args,
            status: null,
            output: null,
            durationMs: null,
            toolCallLogId: null,
          }),
        onToolCallEnd: (e) =>
          updateToolCall(e.assistantMessageId, e.toolCallId, {
            status: e.status,
            output: e.output,
            durationMs: e.durationMs,
            toolCallLogId: e.toolCallLogId,
          }),
        onDone: (e) => {
          finishAssistant(e.assistantMessageId)
          setStreaming(false)
          setEventSource(null)
        },
        onError: (e) => {
          setStreaming(false)
          setEventSource(null)
          setLastError(e.message)
        },
      })
      setEventSource(es)
    },
    [
      currentConversationId,
      appendMessage,
      appendDelta,
      appendToolCall,
      updateToolCall,
      finishAssistant,
      setStreaming,
      setEventSource,
      setLastError,
    ],
  )

  const stop = useCallback(() => {
    // 关闭 EventSource：后端经断连取消上游（glm-docs/06 §11.7）
    const es = useChatStore.getState().eventSource
    es?.close()
    setEventSource(null)
    setStreaming(false)
  }, [setEventSource, setStreaming])

  return { send, stop }
}
