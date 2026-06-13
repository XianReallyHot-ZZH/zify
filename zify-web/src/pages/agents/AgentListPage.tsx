import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Input, message, Pagination, Select, Spin } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { PageHeader } from '../../shared/ui'
import Empty from '../../shared/ui/Empty'
import { ApiError } from '../../api/request'
import { deleteAgent, listAgents, updateAgentStatus } from '../../api/agentApi'
import type { AgentResponse } from '../../types/agent'
import AgentCard from './components/AgentCard'

export default function AgentListPage() {
  const navigate = useNavigate()
  const [agents, setAgents] = useState<AgentResponse[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  // 筛选：name 前缀搜索 + status 等值（agentType P1 恒 REACT，不暴露筛选）
  const [nameInput, setNameInput] = useState('')
  const [name, setName] = useState('')
  const [status, setStatus] = useState<string | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listAgents({ page, pageSize, name: name || undefined, status })
      setAgents(result.records)
      setTotal(result.total)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '加载 Agent 列表失败')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, name, status])

  useEffect(() => {
    load()
  }, [load])

  function handleEdit(agent: AgentResponse) {
    navigate(`/agents/${agent.id}/edit`)
  }

  async function handleToggleStatus(agent: AgentResponse) {
    try {
      const next = agent.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      await updateAgentStatus(agent.id, { status: next })
      message.success(next === 'ACTIVE' ? '已启用' : '已禁用')
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function handleDelete(agent: AgentResponse) {
    try {
      await deleteAgent(agent.id)
      message.success('已删除')
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div className="zify-page">
      <PageHeader
        title="Agent 管理"
        description="创建和管理 AI Agent（P1 仅支持 REACT 类型）"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/agents/create')}>
            新建 Agent
          </Button>
        }
      />

      <div style={{ marginBottom: 16, display: 'flex', gap: 12 }}>
        <Input.Search
          placeholder="按名称搜索"
          allowClear
          style={{ width: 240 }}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onSearch={(v) => {
            setName(v)
            setPage(1)
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 140 }}
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(1)
          }}
          options={[
            { label: '启用', value: 'ACTIVE' },
            { label: '禁用', value: 'INACTIVE' },
          ]}
        />
      </div>

      <Spin spinning={loading}>
        {agents.length === 0 && !loading ? (
          <Empty description="暂无 Agent，点击右上角新建" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {agents.map((agent) => (
              <AgentCard
                key={agent.id}
                agent={agent}
                onEdit={handleEdit}
                onToggleStatus={handleToggleStatus}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
      </Spin>

      {total > pageSize && (
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Pagination
            current={page}
            pageSize={pageSize}
            total={total}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
            size="small"
          />
        </div>
      )}
    </div>
  )
}
