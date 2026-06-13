import { useEffect, useState } from 'react'
import { Select } from 'antd'
import { listModels } from '../../../api/modelApi'
import type { ModelResponse } from '../../../api/modelApi'
import { ApiError } from '../../../api/request'

type ModelSelectorProps = {
  value?: string
  onChange?: (modelId: string) => void
  placeholder?: string
  disabled?: boolean
}

/**
 * 可用 LLM 模型下拉选择器（Agent 表单 / 工作流 LLM 节点复用）。
 * 数据来自 listModels({ modelType: 'LLM', enabled: true })，前端再过滤 provider active。
 */
function ModelSelector({ value, onChange, placeholder, disabled }: ModelSelectorProps) {
  const [models, setModels] = useState<ModelResponse[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listModels({ modelType: 'LLM', enabled: true })
      .then((result) => {
        if (cancelled) return
        setModels(result.records.filter((m) => m.providerStatus === 'ACTIVE'))
      })
      .catch((err) => {
        if (cancelled) return
        // 静默失败：仅记录，不打断表单
        // eslint-disable-next-line no-console
        console.warn('load available LLM models failed', err instanceof ApiError ? err.message : err)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <Select
      value={value}
      onChange={onChange}
      placeholder={placeholder ?? '选择可用 LLM 模型'}
      loading={loading}
      disabled={disabled}
      allowClear
      showSearch
      optionFilterProp="label"
      options={models.map((m) => ({
        value: m.id,
        label: m.displayName || m.modelName,
      }))}
      style={{ width: '100%' }}
      notFoundContent={loading ? '加载中...' : '暂无可用模型'}
    />
  )
}

export default ModelSelector
