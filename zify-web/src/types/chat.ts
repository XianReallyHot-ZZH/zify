/**
 * Chat 模块 HTTP 契约类型 + SSE 流式事件类型（对齐后端 /api/chat/**）。
 */

export type ConversationStatus = 'ACTIVE' | 'ARCHIVED'

export type ConversationResponse = {
  id: string
  title: string
  agentId: string
  agentName: string | null
  status: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
  updatedAt: string
}

/** 会话列表摘要（轻量，无 content）。 */
export type ConversationSummaryResponse = {
  id: string
  title: string
  agentName: string | null
  messageCount: number
  lastMessageAt: string
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'

export type MessageMetadata = {
  modelId?: string
  modelName?: string
  providerType?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  finishReason?: string
  durationMs?: number
} | null

export type MessageResponse = {
  id: string
  role: string
  content: string
  metadata: MessageMetadata
  createdAt: string
}

export type CreateConversationRequest = {
  agentId: string
}

export type SendMessageRequest = {
  content: string
}

export type SendMessageResult = {
  userMessageId: string
  createdAt: string
}

export type ConversationListQuery = {
  cursor?: string
  limit?: number
  agentId?: string
  title?: string
}

export type MessageListQuery = {
  cursor?: string
  limit?: number
}

/**
 * SSE 流式事件（对齐后端 message_delta / done / run_error）。
 * P1 不收 tool_call（P2 才有工具），此处不定义。
 */
export type ChatStreamEvent =
  | { type: 'message_delta'; conversationId: string; assistantMessageId: string; delta: string }
  | { type: 'done'; conversationId: string; assistantMessageId: string }
  | { type: 'run_error'; message: string; retryable?: boolean }
