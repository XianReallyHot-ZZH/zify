import { useEffect, useState } from 'react'
import { Alert, Tag } from 'antd'
import { listMessages } from '../../../api/chatApi'
import { useChatStore } from '../../../stores/chatStore'
import { useChatStream } from '../../../features/chat/hooks/useChatStream'
import type { ConversationSummaryResponse, MessageResponse, MessageView } from '../../../types/chat'
import type { ToolCallView } from '../../../types/tool'
import MessageList from './MessageList'
import MessageInput from './MessageInput'

type ChatPanelProps = {
  conversation: ConversationSummaryResponse | null
}

function toView(m: MessageResponse): MessageView {
  return { id: m.id, role: m.role, content: m.content, metadata: m.metadata, createdAt: m.createdAt }
}

/**
 * 历史回放重建：把 ASSISTANT.metadata.toolCalls 展开为卡片，并合并其后 TOOL 消息的结果
 * （按 toolCallId 配对），TOOL 消息本身从视图移除（已并入卡片）。与实时流同渲染。
 */
function reconstructToolCards(views: MessageView[]): MessageView[] {
  const result: MessageView[] = []
  for (const m of views) {
    if (m.role === 'TOOL') {
      // 合并到最近一个 ASSISTANT 的同 toolCallId 卡片
      const target = [...result].reverse().find((r) => r.role === 'ASSISTANT' && r.toolCalls?.length)
      const tcId = m.metadata?.toolCallId
      if (target && tcId) {
        const card = target.toolCalls!.find((c) => c.toolCallId === tcId)
        if (card) {
          card.output = m.content
          card.toolCallLogId = m.metadata?.toolCallLogId ?? null
          card.toolName = m.metadata?.toolName ?? card.toolName
          card.status = 'SUCCESS'
        }
      }
      continue // TOOL 消息并入卡片，不单独渲染
    }
    if (m.role === 'ASSISTANT' && m.metadata?.toolCalls && m.metadata.toolCalls.length > 0) {
      const cards: ToolCallView[] = m.metadata.toolCalls.map((tc) => ({
        toolCallId: tc.id,
        toolName: tc.name,
        args: tc.args ?? null,
        status: null,
        output: null,
        durationMs: null,
        toolCallLogId: null,
      }))
      result.push({ ...m, toolCalls: cards })
      continue
    }
    result.push(m)
  }
  return result
}

/**
 * 右栏：Agent 头部 + 消息流 + 输入框。加载会话历史并在切换会话时重置。
 */
function ChatPanel({ conversation }: ChatPanelProps) {
  const conversationId = useChatStore((s) => s.currentConversationId)
  const messages = useChatStore((s) => s.messages)
  const isStreaming = useChatStore((s) => s.isStreaming)
  const lastError = useChatStore((s) => s.lastError)
  const setCurrentConversation = useChatStore((s) => s.setCurrentConversation)
  const prependMessages = useChatStore((s) => s.prependMessages)
  const { send, stop } = useChatStream()

  const [historyCursor, setHistoryCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  // 切换会话：加载最新一页历史（反转为时间正序）
  useEffect(() => {
    if (!conversationId) {
      setCurrentConversation(null, [])
      setHistoryCursor(null)
      setHasMore(false)
      return
    }
    let cancelled = false
    listMessages(conversationId, { limit: 20 })
      .then((res) => {
        if (cancelled) return
        const view = reconstructToolCards(res.records.slice().reverse().map(toView))
        setCurrentConversation(conversationId, view)
        setHistoryCursor(res.nextCursor)
        setHasMore(res.hasMore)
      })
      .catch(() => {
        if (!cancelled) setCurrentConversation(conversationId, [])
      })
    return () => {
      cancelled = true
    }
  }, [conversationId, setCurrentConversation])

  function handleLoadMore() {
    if (!conversationId || !historyCursor) return
    setLoadingMore(true)
    listMessages(conversationId, { limit: 20, cursor: historyCursor })
      .then((res) => {
        const older = res.records.slice().reverse().map(toView)
        prependMessages(older)
        setHistoryCursor(res.nextCursor)
        setHasMore(res.hasMore)
      })
      .finally(() => setLoadingMore(false))
  }

  // 空状态
  if (!conversationId || !conversation) {
    return (
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#999',
        }}
      >
        选择一个 Agent 开始对话
      </div>
    )
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span style={{ fontWeight: 600 }}>{conversation.title}</span>
        <Tag>{conversation.agentName || 'Agent'}</Tag>
        <span style={{ color: '#999', fontSize: 12 }}>{conversation.messageCount} 条消息</span>
      </div>

      {lastError && (
        <Alert
          style={{ margin: '8px 16px 0' }}
          type="error"
          message={lastError}
          showIcon
          closable
        />
      )}

      <MessageList
        messages={messages}
        hasMore={hasMore}
        loadingMore={loadingMore}
        onLoadMore={handleLoadMore}
      />
      <MessageInput onSend={send} onStop={stop} isStreaming={isStreaming} />
    </div>
  )
}

export default ChatPanel
