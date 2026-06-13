import { useEffect, useRef, useState } from 'react'
import { message } from 'antd'
import AgentSelector from '../../features/agent/components/AgentSelector'
import { createConversation } from '../../api/chatApi'
import { useChatStore } from '../../stores/chatStore'
import type { ConversationResponse, ConversationSummaryResponse } from '../../types/chat'
import type { AgentResponse } from '../../types/agent'
import ConversationSidebar from './components/ConversationSidebar'
import ChatPanel from './components/ChatPanel'

export default function ChatPage() {
  const [active, setActive] = useState<ConversationSummaryResponse | null>(null)
  const [sidebarSignal, setSidebarSignal] = useState(0)
  const [selectorOpen, setSelectorOpen] = useState(false)

  const setCurrentConversation = useChatStore((s) => s.setCurrentConversation)
  const isStreaming = useChatStore((s) => s.isStreaming)
  const prevStreaming = useRef(false)

  // 一轮结束后刷新左栏（按 lastMessageAt 重排）
  useEffect(() => {
    if (prevStreaming.current && !isStreaming) {
      setSidebarSignal((v) => v + 1)
    }
    prevStreaming.current = isStreaming
  }, [isStreaming])

  function handleSelect(conversation: ConversationSummaryResponse) {
    setActive(conversation)
    setCurrentConversation(conversation.id)
  }

  async function handleCreate(agent: AgentResponse) {
    setSelectorOpen(false)
    try {
      const conversation: ConversationResponse = await createConversation({ agentId: agent.id })
      setActive(conversation)
      setCurrentConversation(conversation.id, [])
      setSidebarSignal((v) => v + 1)
    } catch (err) {
      message.error(err instanceof Error ? err.message : '创建会话失败')
    }
  }

  return (
    <div style={{ display: 'flex', height: '100%', background: '#fff' }}>
      <ConversationSidebar
        activeId={active?.id}
        onSelect={handleSelect}
        onNew={() => setSelectorOpen(true)}
        refreshSignal={sidebarSignal}
      />
      <ChatPanel conversation={active} />
      <AgentSelector
        open={selectorOpen}
        onSelect={handleCreate}
        onCancel={() => setSelectorOpen(false)}
      />
    </div>
  )
}
