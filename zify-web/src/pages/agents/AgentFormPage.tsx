import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card, message } from 'antd'
import AgentForm from '../../features/agent/components/AgentForm'
import { PageLoading } from '../../shared/ui'
import { createAgent, getAgent, updateAgent } from '../../api/agentApi'
import type { AgentResponse, CreateAgentRequest, UpdateAgentRequest } from '../../types/agent'

export default function AgentFormPage() {
  const { id } = useParams<{ id?: string }>()
  const navigate = useNavigate()
  const isEdit = Boolean(id)

  const [agent, setAgent] = useState<AgentResponse | undefined>(undefined)
  const [loading, setLoading] = useState(isEdit)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    getAgent(id)
      .then((a) => {
        if (!cancelled) setAgent(a)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  async function handleSubmit(values: CreateAgentRequest | UpdateAgentRequest) {
    try {
      if (id) {
        await updateAgent(id, values as UpdateAgentRequest)
        message.success('Agent 已保存')
      } else {
        await createAgent(values as CreateAgentRequest)
        message.success('Agent 已创建')
      }
      navigate('/agents')
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败')
    }
  }

  if (loading) return <PageLoading />

  return (
    <div className="zify-page">
      <Card title={isEdit ? '编辑 Agent' : '新建 Agent'} style={{ maxWidth: 720 }}>
        <AgentForm initialAgent={agent} onSubmit={handleSubmit} onCancel={() => navigate('/agents')} />
      </Card>
    </div>
  )
}
