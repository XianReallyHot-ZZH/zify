import { useEffect, useState } from 'react'
import { List, Modal, Spin, Tag } from 'antd'
import { listAgents } from '../../../api/agentApi'
import type { AgentResponse } from '../../../types/agent'

type AgentSelectorProps = {
  open: boolean
  onSelect: (agent: AgentResponse) => void
  onCancel: () => void
}

/**
 * 新建会话时的 Agent 选择器：弹窗，只列 ACTIVE + REACT Agent。
 */
function AgentSelector({ open, onSelect, onCancel }: AgentSelectorProps) {
  const [agents, setAgents] = useState<AgentResponse[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    listAgents({ status: 'ACTIVE', agentType: 'REACT', pageSize: 100 })
      .then((result) => {
        if (!cancelled) setAgents(result.records)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open])

  return (
    <Modal title="选择 Agent 开始对话" open={open} onCancel={onCancel} footer={null} width={520}>
      <Spin spinning={loading}>
        {agents.length === 0 && !loading ? (
          <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>
            暂无可用 Agent，请先在 Agent 管理创建并启用一个 REACT Agent。
          </div>
        ) : (
          <List
            dataSource={agents}
            renderItem={(agent) => (
              <List.Item
                style={{ cursor: 'pointer' }}
                onClick={() => onSelect(agent)}
              >
                <List.Item.Meta
                  title={
                    <span>
                      {agent.name} <Tag color="blue">REACT</Tag>
                    </span>
                  }
                  description={agent.description || agent.modelName || '未绑定模型'}
                />
              </List.Item>
            )}
          />
        )}
      </Spin>
    </Modal>
  )
}

export default AgentSelector
