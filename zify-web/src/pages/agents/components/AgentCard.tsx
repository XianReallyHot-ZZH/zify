import { Button, Card, Popconfirm, Space, Tag } from 'antd'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons'
import type { AgentResponse } from '../../../types/agent'

type AgentCardProps = {
  agent: AgentResponse
  onEdit: (agent: AgentResponse) => void
  onToggleStatus: (agent: AgentResponse) => void
  onDelete: (agent: AgentResponse) => void
}

function AgentCard({ agent, onEdit, onToggleStatus, onDelete }: AgentCardProps) {
  const active = agent.status === 'ACTIVE'
  return (
    <Card
      title={
        <Space>
          <span>{agent.name}</span>
          <Tag color="blue">REACT</Tag>
          <Tag color={active ? 'green' : 'default'}>{active ? '启用' : '禁用'}</Tag>
        </Space>
      }
      extra={
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(agent)}>
            编辑
          </Button>
          <Button size="small" onClick={() => onToggleStatus(agent)}>
            {active ? '禁用' : '启用'}
          </Button>
          <Popconfirm
            title="确认删除该 Agent？"
            description="其下会话将变为只读（保留历史）"
            onConfirm={() => onDelete(agent)}
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      }
    >
      <div style={{ color: '#666', minHeight: 22 }}>{agent.description || '暂无描述'}</div>
      <div style={{ marginTop: 8, color: '#999', fontSize: 12 }}>
        模型：{agent.modelName || '—'} · 创建于 {agent.createdAt.slice(0, 10)}
      </div>
    </Card>
  )
}

export default AgentCard
