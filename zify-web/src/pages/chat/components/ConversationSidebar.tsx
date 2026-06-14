import { useCallback, useEffect, useState } from 'react'
import { Button, Input, Popconfirm, Spin, message } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useCursorPagination } from '../../../shared/hooks/useCursorPagination'
import { deleteConversation, listConversations } from '../../../api/chatApi'
import { ApiError } from '../../../api/request'
import type { ConversationSummaryResponse } from '../../../types/chat'

type ConversationSidebarProps = {
  activeId?: string
  onSelect: (conversation: ConversationSummaryResponse) => void
  onNew: () => void
  /** 删除成功后回调（删的是当前会话时父组件据此清空右栏） */
  onDeleted?: (id: string) => void
  refreshSignal: number
}

function formatTime(iso: string): string {
  return iso ? iso.slice(5, 16).replace('T', ' ') : ''
}

/**
 * 左栏：会话列表（按 lastMessageAt 倒序 Keyset），新建对话，搜索，删除会话。
 */
function ConversationSidebar({ activeId, onSelect, onNew, onDeleted, refreshSignal }: ConversationSidebarProps) {
  const [title, setTitle] = useState('')

  const fetchFn = useCallback(
    (query: Record<string, unknown>) => listConversations({ ...query, title: title || undefined }),
    [title],
  )
  const { records, loading, hasMore, loadMore, refresh } = useCursorPagination(fetchFn)

  useEffect(() => {
    refresh()
  }, [refresh, refreshSignal])

  async function handleDelete(c: ConversationSummaryResponse) {
    try {
      await deleteConversation(c.id)
      message.success('会话已删除')
      onDeleted?.(c.id)
      refresh()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div
      style={{
        width: 280,
        flexShrink: 0,
        borderRight: '1px solid #f0f0f0',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={onNew}>
          新建对话
        </Button>
        <Input.Search
          placeholder="搜索会话标题"
          allowClear
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </div>
      <Spin spinning={loading}>
        <div style={{ flex: 1, overflow: 'auto' }}>
          {records.length === 0 && !loading ? (
            <div style={{ padding: 24, textAlign: 'center', color: '#999', fontSize: 13 }}>
              暂无会话
            </div>
          ) : (
            records.map((c) => (
              <div
                key={c.id}
                onClick={() => onSelect(c)}
                style={{
                  padding: '10px 12px',
                  cursor: 'pointer',
                  background: c.id === activeId ? '#e6f4ff' : 'transparent',
                  borderBottom: '1px solid #f5f5f5',
                  display: 'flex',
                  alignItems: 'flex-start',
                  justifyContent: 'space-between',
                  gap: 8,
                }}
              >
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div
                    style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  >
                    {c.title}
                  </div>
                  <div style={{ fontSize: 12, color: '#999' }}>
                    {c.agentName || '—'} · {c.messageCount} 条 · {formatTime(c.lastMessageAt)}
                  </div>
                </div>
                <Popconfirm
                  title="删除该会话？"
                  description="会话及其全部消息将被删除"
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  cancelText="取消"
                  onConfirm={() => handleDelete(c)}
                >
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={(e) => e.stopPropagation()}
                  />
                </Popconfirm>
              </div>
            ))
          )}
          {hasMore && (
            <div style={{ textAlign: 'center', padding: 8 }}>
              <Button size="small" type="link" onClick={loadMore}>
                加载更多
              </Button>
            </div>
          )}
        </div>
      </Spin>
    </div>
  )
}

export default ConversationSidebar
