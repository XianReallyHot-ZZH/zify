import { useState } from 'react'
import { Button, Descriptions, Form, Input, Steps, Space, message } from 'antd'
import AgentTypeSelector from './AgentTypeSelector'
import PromptEditor from './PromptEditor'
import ModelSelector from '../../model/components/ModelSelector'
import type { AgentResponse, CreateAgentRequest, UpdateAgentRequest } from '../../../types/agent'

type AgentFormProps = {
  initialAgent?: AgentResponse
  onSubmit: (values: CreateAgentRequest | UpdateAgentRequest) => Promise<void>
  onCancel: () => void
}

const STEPS = [
  { title: '基础信息', fields: ['name', 'description', 'agentType'] as const },
  { title: '人设', fields: ['systemPrompt'] as const },
  { title: '能力', fields: ['modelId'] as const },
  { title: '确认', fields: [] as const },
]

/**
 * Agent 分步表单（创建/编辑复用）。Steps：① 基础信息 ② 人设 ③ 能力 ④ 确认。
 */
function AgentForm({ initialAgent, onSubmit, onCancel }: AgentFormProps) {
  const [form] = Form.useForm()
  const [current, setCurrent] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const isEdit = Boolean(initialAgent)

  async function goNext() {
    try {
      await form.validateFields([...STEPS[current].fields])
      setCurrent((c) => Math.min(c + 1, STEPS.length - 1))
    } catch {
      // 校验失败：Ant Form 已显示字段错误
    }
  }

  async function finish() {
    setSubmitting(true)
    try {
      const values = await form.validateFields()
      const payload: CreateAgentRequest | UpdateAgentRequest = {
        name: values.name,
        description: values.description,
        agentType: values.agentType || 'REACT',
        systemPrompt: values.systemPrompt,
        modelId: values.modelId,
        ...(isEdit ? { status: initialAgent?.status } : {}),
      }
      await onSubmit(payload)
    } catch (err) {
      if (err instanceof Error && err.message) {
        message.error(err.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={{
        agentType: 'REACT',
        ...(initialAgent
          ? {
              name: initialAgent.name,
              description: initialAgent.description ?? undefined,
              agentType: initialAgent.agentType,
              systemPrompt: initialAgent.systemPrompt ?? undefined,
              modelId: initialAgent.modelId ?? undefined,
            }
          : {}),
      }}
    >
      <Steps current={current} size="small" style={{ marginBottom: 24 }} items={STEPS.map((s) => ({ title: s.title }))} />

      {/* Step 1 基础信息 */}
      {current === 0 && (
        <>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入 Agent 名称' }]}>
            <Input maxLength={128} placeholder="如：客服助手" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input maxLength={512} placeholder="Agent 用途说明" />
          </Form.Item>
          <Form.Item label="类型" name="agentType" rules={[{ required: true, message: '请选择类型' }]}>
            <AgentTypeSelector disabled={isEdit} />
          </Form.Item>
        </>
      )}

      {/* Step 2 人设 */}
      {current === 1 && (
        <Form.Item label="System Prompt" name="systemPrompt">
          <PromptEditor />
        </Form.Item>
      )}

      {/* Step 3 能力 */}
      {current === 2 && (
        <Form.Item label="绑定模型" name="modelId" rules={[{ required: true, message: '请选择可用 LLM 模型' }]}>
          <ModelSelector />
        </Form.Item>
      )}

      {/* Step 4 确认 */}
      {current === 3 && (
        <Form.Item shouldUpdate>
          {() => {
            const v = form.getFieldsValue(true)
            return (
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="名称">{v.name}</Descriptions.Item>
                <Descriptions.Item label="描述">{v.description || '—'}</Descriptions.Item>
                <Descriptions.Item label="类型">{v.agentType}</Descriptions.Item>
                <Descriptions.Item label="System Prompt">
                  <span style={{ whiteSpace: 'pre-wrap' }}>{v.systemPrompt || '—'}</span>
                </Descriptions.Item>
                <Descriptions.Item label="模型">{v.modelId || '—'}</Descriptions.Item>
              </Descriptions>
            )
          }}
        </Form.Item>
      )}

      {/* 操作按钮 */}
      <div style={{ marginTop: 24, display: 'flex', justifyContent: 'space-between' }}>
        <Button onClick={onCancel}>取消</Button>
        <Space>
          {current > 0 && <Button onClick={() => setCurrent((c) => c - 1)}>上一步</Button>}
          {current < STEPS.length - 1 ? (
            <Button type="primary" onClick={goNext}>
              下一步
            </Button>
          ) : (
            <Button type="primary" loading={submitting} onClick={finish}>
              {isEdit ? '保存' : '创建'}
            </Button>
          )}
        </Space>
      </div>
    </Form>
  )
}

export default AgentForm
