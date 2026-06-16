import { Button } from 'antd'
import type { MessageView } from '../../../types/chat'
import ToolCallTrace from './ToolCallTrace'

type MessageListProps = {
  messages: MessageView[]
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: () => void
}

/**
 * 消息流：USER 靠右（蓝），ASSISTANT 靠左（灰），流式中 ASSISTANT 末尾显示光标。
 * ASSISTANT 带 toolCalls 时在其文本下渲染工具卡片。顶部「加载更多历史」向上翻。
 */
function MessageList({ messages, hasMore, loadingMore, onLoadMore }: MessageListProps) {
  return (
    <div style={{ flex: 1, overflow: 'auto', padding: '16px' }}>
      {hasMore && (
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <Button size="small" onClick={onLoadMore} loading={loadingMore}>
            加载更多历史
          </Button>
        </div>
      )}
      {messages.length === 0 && !hasMore && (
        <div style={{ textAlign: 'center', color: '#999', marginTop: 48 }}>发送一条消息开始对话</div>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {messages.map((m) => {
          const isUser = m.role === 'USER'
          return (
            <div
              key={m.id}
              style={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start' }}
            >
              <div style={{ maxWidth: '78%' }}>
                <div
                  style={{
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: isUser ? '#e6f4ff' : '#f5f5f5',
                    color: m.error ? '#cf1322' : undefined,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {m.content}
                  {!isUser && m.streaming && <span style={{ opacity: 0.5 }}>▍</span>}
                </div>
                {!isUser && m.toolCalls && m.toolCalls.length > 0 && <ToolCallTrace toolCalls={m.toolCalls} />}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default MessageList
