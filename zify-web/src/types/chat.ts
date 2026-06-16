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

/** ASSISTANT 消息 metadata.toolCalls 的单项（历史回放用）。 */
export type ToolCallMeta = {
  id: string
  name: string
  args: string | null
}

export type MessageMetadata = {
  modelId?: string
  modelName?: string
  providerType?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  finishReason?: string
  durationMs?: number
  /** ASSISTANT（中间轮，带 toolCall）。 */
  toolCalls?: ToolCallMeta[]
  /** TOOL 消息：配对的 toolCall id + 工具名 + 日志 id。 */
  toolCallId?: string
  toolName?: string
  toolCallLogId?: string
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
 * 前端视图消息（chatStore 中维护，含流式临时态）。
 * P2：toolCalls 承载实时流 + 历史回放的工具卡片。
 */
import type { ToolCallView } from './tool'

export type MessageView = {
  id: string
  role: string
  content: string
  metadata: MessageMetadata
  createdAt: string
  /** 流式生成中（临时 ASSISTANT 气泡），完成/出错后置 false */
  streaming?: boolean
  /** 生成出错时的提示（仅 ASSISTANT 临时态） */
  error?: boolean
  /** P2：本轮工具调用卡片（按 toolCallId 配对）。 */
  toolCalls?: ToolCallView[]
}

/**
 * SSE 流式事件（对齐后端 message_delta / tool_call_start / tool_call_end / done / run_error）。
 */
export type ChatStreamEvent =
  | { type: 'message_delta'; conversationId: string; assistantMessageId: string; delta: string }
  | {
      type: 'tool_call_start'
      conversationId: string
      assistantMessageId: string
      toolCallId: string
      toolName: string
      args: string
    }
  | {
      type: 'tool_call_end'
      conversationId: string
      assistantMessageId: string
      toolCallId: string
      toolName: string
      status: string
      output: string
      durationMs: number
      toolCallLogId: string
    }
  | { type: 'done'; conversationId: string; assistantMessageId: string }
  | { type: 'run_error'; message: string; retryable?: boolean }
