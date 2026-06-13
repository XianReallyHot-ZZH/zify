import { apiGet, apiPost, apiDelete } from './request'
import type { CursorPageResponse } from '../types/api'
import type {
  ChatStreamEvent,
  ConversationListQuery,
  ConversationResponse,
  ConversationSummaryResponse,
  CreateConversationRequest,
  MessageListQuery,
  MessageResponse,
  SendMessageRequest,
  SendMessageResult,
} from '../types/chat'

// ─── Conversation ──────────────────────────────────────────

function createConversation(data: CreateConversationRequest): Promise<ConversationResponse> {
  return apiPost('/chat/conversations', data)
}

function listConversations(
  query?: ConversationListQuery,
): Promise<CursorPageResponse<ConversationSummaryResponse>> {
  return apiGet('/chat/conversations', query as Record<string, unknown>)
}

function getConversation(id: string): Promise<ConversationResponse> {
  return apiGet(`/chat/conversations/${id}`)
}

function deleteConversation(id: string): Promise<void> {
  return apiDelete(`/chat/conversations/${id}`)
}

// ─── Message ───────────────────────────────────────────────

/** 发送用户消息（第一步，POST 落库，不调 LLM）。 */
function sendMessage(conversationId: string, data: SendMessageRequest): Promise<SendMessageResult> {
  return apiPost(`/chat/conversations/${conversationId}/messages`, data)
}

/** 消息历史（Keyset，含 content + metadata）。 */
function listMessages(
  conversationId: string,
  query?: MessageListQuery,
): Promise<CursorPageResponse<MessageResponse>> {
  return apiGet(`/chat/conversations/${conversationId}/messages`, query as Record<string, unknown>)
}

// ─── SSE Stream ────────────────────────────────────────────

type ChatStreamHandlers = {
  onDelta: (e: Extract<ChatStreamEvent, { type: 'message_delta' }>) => void
  onDone: (e: Extract<ChatStreamEvent, { type: 'done' }>) => void
  onError: (e: Extract<ChatStreamEvent, { type: 'run_error' }>) => void
}

/**
 * 建立流式连接（第二步，GET SSE，query 只传 messageId）。
 * 不经 Axios，直接 new EventSource；done/run_error/onerror 均关闭连接。
 */
function openChatStream(messageId: string, handlers: ChatStreamHandlers): EventSource {
  const es = new EventSource(`/api/chat/stream?messageId=${encodeURIComponent(messageId)}`)

  es.addEventListener('message_delta', (ev) => {
    handlers.onDelta(JSON.parse((ev as MessageEvent).data))
  })
  es.addEventListener('done', (ev) => {
    handlers.onDone(JSON.parse((ev as MessageEvent).data))
    es.close()
  })
  es.addEventListener('run_error', (ev) => {
    handlers.onError(JSON.parse((ev as MessageEvent).data))
    es.close()
  })
  es.onerror = () => {
    handlers.onError({ type: 'run_error', message: 'SSE 连接错误' })
    es.close()
  }

  return es
}

export {
  createConversation,
  listConversations,
  getConversation,
  deleteConversation,
  sendMessage,
  listMessages,
  openChatStream,
}
