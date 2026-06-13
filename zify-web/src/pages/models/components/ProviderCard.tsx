import { useState } from 'react'
import { Button, Card, message, Popconfirm, Space, Spin, Switch, Tag } from 'antd'
import { DownOutlined, RightOutlined } from '@ant-design/icons'
import { ApiError } from '../../../api/request'
import type { ProviderResponse, ModelResponse } from '../../../api/modelApi'
import { listProviderModels, updateModelEnabled, deleteModel, testModel, testProvider } from '../../../api/modelApi'

type ProviderCardProps = {
  provider: ProviderResponse
  onEdit: (provider: ProviderResponse) => void
  onDelete: (provider: ProviderResponse) => void
  onToggleStatus: (provider: ProviderResponse) => void
  onAddModel: (providerId: string) => void
  onEditModel: (model: ModelResponse) => void
  onDeleteModel: (model: ModelResponse) => void
  onToggleModel: (model: ModelResponse) => void
}

const PROVIDER_TYPE_LABELS: Record<string, string> = {
  OPENAI: 'OpenAI',
  ANTHROPIC: 'Anthropic',
  OPENAI_COMPATIBLE: 'OpenAI 兼容',
}

const MODEL_TYPE_TAG: Record<string, { label: string; color: string }> = {
  LLM: { label: 'LLM', color: 'blue' },
  EMBEDDING: { label: 'Embedding', color: 'green' },
  RERANK: { label: 'Rerank', color: 'orange' },
}

export default function ProviderCard({
  provider,
  onEdit,
  onDelete,
  onToggleStatus,
  onAddModel,
  onEditModel,
  onDeleteModel,
  onToggleModel,
}: ProviderCardProps) {
  const [expanded, setExpanded] = useState(false)
  const [models, setModels] = useState<ModelResponse[]>([])
  const [modelsLoading, setModelsLoading] = useState(false)
  const [modelsLoaded, setModelsLoaded] = useState(false)
  const [modelTestLoading, setModelTestLoading] = useState<Record<string, boolean>>({})
  const [providerTestLoading, setProviderTestLoading] = useState(false)

  const isActive = provider.status === 'ACTIVE'

  async function handleExpand() {
    const nextExpanded = !expanded
    setExpanded(nextExpanded)

    if (nextExpanded && !modelsLoaded) {
      setModelsLoading(true)
      try {
        const result = await listProviderModels(provider.id)
        setModels(result)
        setModelsLoaded(true)
      } catch (err) {
        message.error(err instanceof ApiError ? err.message : '加载模型列表失败')
      } finally {
        setModelsLoading(false)
      }
    }
  }

  async function handleTestProvider() {
    setProviderTestLoading(true)
    try {
      const result = await testProvider(provider.id)
      if (result.success) {
        message.success(result.message)
      } else {
        message.error(result.message)
      }
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '测试请求失败')
    } finally {
      setProviderTestLoading(false)
    }
  }

  async function handleTestModel(model: ModelResponse) {
    setModelTestLoading(prev => ({ ...prev, [model.id]: true }))
    try {
      const result = await testModel(model.id)
      if (result.success) {
        message.success(result.message)
      } else {
        message.error(result.message)
      }
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '测试请求失败')
    } finally {
      setModelTestLoading(prev => ({ ...prev, [model.id]: false }))
    }
  }

  async function handleToggleModel(model: ModelResponse) {
    try {
      await updateModelEnabled(model.id, !model.enabled)
      message.success(model.enabled ? '模型已禁用' : '模型已启用')
      setModels(prev => prev.map(m => m.id === model.id ? { ...m, enabled: !m.enabled } : m))
      onToggleModel({ ...model, enabled: !model.enabled })
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function handleDeleteModel(model: ModelResponse) {
    try {
      await deleteModel(model.id)
      message.success('模型已删除')
      setModels(prev => prev.filter(m => m.id !== model.id))
      onDeleteModel(model)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <Card
      size="small"
      style={{ borderRadius: 6 }}
      styles={{ body: { padding: 0 } }}
    >
      {/* 头部区域 */}
      <div style={{ padding: '12px 16px', cursor: 'pointer' }} onClick={handleExpand}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{
              display: 'inline-block',
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: isActive ? '#52c41a' : '#d9d9d9',
            }} />
            <span style={{ fontWeight: 500, fontSize: 15 }}>{provider.name}</span>
            {expanded ? <DownOutlined style={{ fontSize: 12, color: '#999' }} /> : <RightOutlined style={{ fontSize: 12, color: '#999' }} />}
          </div>
        </div>
        <div style={{ marginTop: 4, color: 'var(--color-text-secondary)', fontSize: 13 }}>
          {PROVIDER_TYPE_LABELS[provider.providerType] || provider.providerType} · {provider.modelCount} 个模型
        </div>
      </div>

      {/* 操作按钮 */}
      <div style={{ padding: '0 16px 12px', borderTop: '1px solid var(--color-border-secondary)', paddingTop: 8 }}>
        <Space size={4}>
          <Button size="small" onClick={() => onEdit(provider)}>编辑</Button>
          <Button size="small" loading={providerTestLoading} onClick={handleTestProvider}>测试连接</Button>
          <Button size="small" onClick={() => onToggleStatus(provider)}>
            {isActive ? '禁用' : '启用'}
          </Button>
          <Popconfirm
            title={provider.modelCount > 0
              ? `该供应商下有 ${provider.modelCount} 个模型，删除后模型将不可用。确认删除？`
              : '确认删除该供应商？'}
            onConfirm={() => onDelete(provider)}
            okText="确认"
            cancelText="取消"
          >
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      </div>

      {/* 展开区域：模型列表 */}
      {expanded && (
        <div style={{ padding: '0 16px 12px', borderTop: '1px solid var(--color-border-secondary)', paddingTop: 12 }}>
          {modelsLoading ? (
            <Spin size="small" style={{ display: 'block', textAlign: 'center', padding: '12px 0' }} />
          ) : models.length === 0 ? (
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 13, textAlign: 'center', padding: '8px 0' }}>
              暂无模型
            </div>
          ) : (
            models.map(model => {
              const tagInfo = MODEL_TYPE_TAG[model.modelType] || { label: model.modelType, color: 'default' }
              return (
                <div
                  key={model.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 0',
                    borderBottom: '1px solid var(--color-border-secondary)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                    <span style={{ fontWeight: 500, fontSize: 13 }}>{model.modelName}</span>
                    {model.displayName && model.displayName !== model.modelName && (
                      <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>
                        ({model.displayName})
                      </span>
                    )}
                    <Tag color={tagInfo.color} style={{ marginRight: 0 }}>{tagInfo.label}</Tag>
                  </div>
                  <Space size={4}>
                    <Button
                      size="small"
                      loading={modelTestLoading[model.id]}
                      onClick={() => handleTestModel(model)}
                    >
                      测试
                    </Button>
                    <Switch
                      size="small"
                      checked={model.enabled}
                      onChange={() => handleToggleModel(model)}
                    />
                    <Button size="small" onClick={() => onEditModel(model)}>编辑</Button>
                    <Popconfirm
                      title="确认删除该模型？"
                      onConfirm={() => handleDeleteModel(model)}
                      okText="确认"
                      cancelText="取消"
                    >
                      <Button size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                </div>
              )
            })
          )}
          <Button
            size="small"
            type="dashed"
            onClick={() => onAddModel(provider.id)}
            style={{ marginTop: 8, width: '100%' }}
          >
            + 添加模型
          </Button>
        </div>
      )}
    </Card>
  )
}
