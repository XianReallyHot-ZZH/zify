import { create } from 'zustand'
import type { MessageView } from '../types/chat'

interface ChatState {
  /** 当前打开的会话 */
  currentConversationId: string | null
  /** 当前会话的消息流（含流式中的临时 ASSISTANT 气泡） */
  messages: MessageView[]
  /** 是否正在生成 */
  isStreaming: boolean
  /** 当前 EventSource 引用（用于中断） */
  eventSource: EventSource | null
  /** 最近一次流式错误（用于页面提示） */
  lastError: string | null

  setCurrentConversation: (id: string | null, messages?: MessageView[]) => void
  appendMessage: (message: MessageView) => void
  appendDelta: (assistantMessageId: string, delta: string) => void
  finishAssistant: (assistantMessageId: string, error?: string) => void
  setStreaming: (streaming: boolean) => void
  setEventSource: (es: EventSource | null) => void
  setLastError: (message: string | null) => void
  reset: () => void
}

/**
 * chatStore：只存跨组件共享的客户端状态，不发 HTTP（glm-docs/06 §11.8）。
 */
export const useChatStore = create<ChatState>((set) => ({
  currentConversationId: null,
  messages: [],
  isStreaming: false,
  eventSource: null,
  lastError: null,

  setCurrentConversation: (id, messages) =>
    set({ currentConversationId: id, messages: messages ?? [], lastError: null }),

  appendMessage: (message) =>
    set((state) => ({ messages: [...state.messages, message] })),

  // 若该 ASSISTANT 气泡不存在则创建（streaming），存在则追加 delta
  appendDelta: (assistantMessageId, delta) =>
    set((state) => {
      const idx = state.messages.findIndex((m) => m.id === assistantMessageId)
      if (idx >= 0) {
        const next = [...state.messages]
        next[idx] = { ...next[idx], content: next[idx].content + delta }
        return { messages: next }
      }
      return {
        messages: [
          ...state.messages,
          {
            id: assistantMessageId,
            role: 'ASSISTANT',
            content: delta,
            metadata: null,
            createdAt: new Date().toISOString(),
            streaming: true,
          },
        ],
      }
    }),

  finishAssistant: (assistantMessageId, error) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === assistantMessageId ? { ...m, streaming: false, error: Boolean(error) } : m,
      ),
    })),

  setStreaming: (streaming) => set({ isStreaming: streaming }),
  setEventSource: (es) => set({ eventSource: es }),
  setLastError: (message) => set({ lastError: message }),

  reset: () =>
    set({ currentConversationId: null, messages: [], isStreaming: false, eventSource: null, lastError: null }),
}))
