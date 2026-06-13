import { useCallback, useEffect, useState } from 'react'
import { Button, message, Pagination, Select, Spin } from 'antd'
import { ApiError } from '../../api/request'
import { PageHeader } from '../../shared/ui'
import Empty from '../../shared/ui/Empty'
import {
  createProvider,
  listProviders,
  updateProvider,
  deleteProvider,
  updateProviderStatus,
  createModel,
  updateModel,
} from '../../api/modelApi'
import type {
  ProviderResponse,
  ModelResponse,
  CreateProviderRequest,
  UpdateProviderRequest,
  CreateModelRequest,
  UpdateModelRequest,
} from '../../api/modelApi'
import ProviderFormModal from './components/ProviderFormModal'
import ModelFormModal from './components/ModelFormModal'
import ProviderCard from './components/ProviderCard'

export default function ModelPage() {
  const [providers, setProviders] = useState<ProviderResponse[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [filterType, setFilterType] = useState<string | undefined>()
  const [filterStatus, setFilterStatus] = useState<string | undefined>()

  const [providerFormOpen, setProviderFormOpen] = useState(false)
  const [editingProvider, setEditingProvider] = useState<ProviderResponse | undefined>()

  const [modelFormOpen, setModelFormOpen] = useState(false)
  const [currentProviderId, setCurrentProviderId] = useState('')
  const [editingModel, setEditingModel] = useState<ModelResponse | undefined>()

  const loadProviders = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listProviders({
        page,
        pageSize,
        providerType: filterType || undefined,
        status: filterStatus || undefined,
      })
      setProviders(result.records)
      setTotal(result.total)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '加载供应商列表失败')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, filterType, filterStatus])

  useEffect(() => {
    loadProviders()
  }, [loadProviders])

  // ─── Provider 事件处理 ─────────────────────────────────────

  function handleCreateProvider() {
    setEditingProvider(undefined)
    setProviderFormOpen(true)
  }

  function handleEditProvider(provider: ProviderResponse) {
    setEditingProvider(provider)
    setProviderFormOpen(true)
  }

  async function handleProviderSubmit(values: CreateProviderRequest | UpdateProviderRequest) {
    try {
      if (editingProvider) {
        await updateProvider(editingProvider.id, values as UpdateProviderRequest)
        message.success('供应商更新成功')
      } else {
        await createProvider(values as CreateProviderRequest)
        message.success('供应商创建成功')
      }
      setProviderFormOpen(false)
      loadProviders()
    } catch (err) {
      if (err instanceof ApiError) {
        message.error(err.message)
      } else {
        message.error('操作失败')
      }
    }
  }

  async function handleDeleteProvider(provider: ProviderResponse) {
    try {
      await deleteProvider(provider.id)
      message.success('供应商已删除')
      loadProviders()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  async function handleToggleProviderStatus(provider: ProviderResponse) {
    try {
      const newStatus = provider.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      await updateProviderStatus(provider.id, newStatus)
      message.success(newStatus === 'ACTIVE' ? '供应商已启用' : '供应商已禁用')
      loadProviders()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  // ─── Model 事件处理 ────────────────────────────────────────

  function handleAddModel(providerId: string) {
    setCurrentProviderId(providerId)
    setEditingModel(undefined)
    setModelFormOpen(true)
  }

  function handleEditModel(model: ModelResponse) {
    setCurrentProviderId(model.providerId)
    setEditingModel(model)
    setModelFormOpen(true)
  }

  async function handleModelSubmit(values: CreateModelRequest | UpdateModelRequest) {
    try {
      if (editingModel) {
        await updateModel(editingModel.id, values as UpdateModelRequest)
        message.success('模型更新成功')
      } else {
        await createModel(currentProviderId, values as CreateModelRequest)
        message.success('模型添加成功')
      }
      setModelFormOpen(false)
      loadProviders()
    } catch (err) {
      if (err instanceof ApiError) {
        message.error(err.message)
      } else {
        message.error('操作失败')
      }
    }
  }

  // 模型删除/禁用后需要刷新供应商（modelCount 变化）
  function handleDeleteModel(_model: ModelResponse) {
    loadProviders()
  }

  function handleToggleModel(_model: ModelResponse) {
    // 模型切换由 ProviderCard 内部处理本地状态，无需刷新
  }

  return (
    <div className="zify-page">
      <PageHeader
        title="模型管理"
        description="配置 LLM 模型供应商和连接"
        extra={
          <Button type="primary" onClick={handleCreateProvider}>
            添加供应商
          </Button>
        }
      />

      {/* 筛选栏 */}
      <div style={{ marginBottom: 16, display: 'flex', gap: 12 }}>
        <Select
          placeholder="供应商类型"
          allowClear
          style={{ width: 160 }}
          value={filterType}
          onChange={(val) => { setFilterType(val); setPage(1) }}
          options={[
            { label: 'OpenAI', value: 'OPENAI' },
            { label: 'Anthropic', value: 'ANTHROPIC' },
            { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
          ]}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 120 }}
          value={filterStatus}
          onChange={(val) => { setFilterStatus(val); setPage(1) }}
          options={[
            { label: '已启用', value: 'ACTIVE' },
            { label: '已禁用', value: 'INACTIVE' },
          ]}
        />
      </div>

      {/* 供应商卡片列表 */}
      <Spin spinning={loading}>
        {providers.length === 0 && !loading ? (
          <Empty description="暂无供应商，点击上方按钮添加" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {providers.map(provider => (
              <ProviderCard
                key={provider.id}
                provider={provider}
                onEdit={handleEditProvider}
                onDelete={handleDeleteProvider}
                onToggleStatus={handleToggleProviderStatus}
                onAddModel={handleAddModel}
                onEditModel={handleEditModel}
                onDeleteModel={handleDeleteModel}
                onToggleModel={handleToggleModel}
              />
            ))}
          </div>
        )}
      </Spin>

      {/* 分页 */}
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

      {/* 弹窗 */}
      <ProviderFormModal
        open={providerFormOpen}
        provider={editingProvider}
        onSubmit={handleProviderSubmit}
        onCancel={() => setProviderFormOpen(false)}
      />
      <ModelFormModal
        open={modelFormOpen}
        providerId={currentProviderId}
        model={editingModel}
        onSubmit={handleModelSubmit}
        onCancel={() => setModelFormOpen(false)}
      />
    </div>
  )
}
