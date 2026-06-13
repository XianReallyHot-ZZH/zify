import { useEffect, useState } from 'react'
import { Form, Input, InputNumber, Modal, Select, Switch } from 'antd'
import { ApiError } from '../../../api/request'
import type {
  ModelResponse,
  CreateModelRequest,
  UpdateModelRequest,
} from '../../../api/modelApi'

type ModelFormModalProps = {
  open: boolean
  providerId: string
  model?: ModelResponse
  onSubmit: (values: CreateModelRequest | UpdateModelRequest) => Promise<void>
  onCancel: () => void
}

const MODEL_TYPE_OPTIONS = [
  { label: 'LLM', value: 'LLM' },
  { label: 'Embedding', value: 'EMBEDDING' },
]

export default function ModelFormModal({ open, model, onSubmit, onCancel }: ModelFormModalProps) {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const isEdit = !!model

  useEffect(() => {
    if (open) {
      if (model) {
        form.setFieldsValue({
          modelName: model.modelName,
          displayName: model.displayName || '',
          modelType: model.modelType,
          enabled: model.enabled,
          temperature: model.defaultParams?.temperature as number | undefined,
          maxTokens: model.defaultParams?.maxTokens as number | undefined,
          topP: model.defaultParams?.topP as number | undefined,
        })
      } else {
        form.resetFields()
      }
    }
  }, [open, model, form])

  async function handleOk() {
    try {
      const values = await form.validateFields()
      setSubmitting(true)

      if (isEdit) {
        const updateData: UpdateModelRequest = {
          displayName: values.displayName || undefined,
          modelType: values.modelType,
          enabled: values.enabled,
        }
        // 构建默认参数（只包含有值的项）
        const params: Record<string, unknown> = {}
        if (values.temperature != null) params.temperature = values.temperature
        if (values.maxTokens != null) params.maxTokens = values.maxTokens
        if (values.topP != null) params.topP = values.topP
        updateData.defaultParams = Object.keys(params).length > 0 ? params : undefined
        await onSubmit(updateData)
      } else {
        const createData: CreateModelRequest = {
          modelName: values.modelName,
          displayName: values.displayName || undefined,
          modelType: values.modelType,
          enabled: values.enabled ?? true,
        }
        await onSubmit(createData)
      }
    } catch (err) {
      // 表单校验失败不处理；API 错误继续向上抛，让页面层展示错误消息
      if (err instanceof ApiError) {
        throw err
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={isEdit ? '编辑模型' : '添加模型'}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={submitting}
      destroyOnClose
      width={480}
    >
      <Form form={form} layout="vertical" autoComplete="off" initialValues={{ enabled: true, modelType: 'LLM' }}>
        <Form.Item name="modelName" label="模型标识" rules={[{ required: true, message: '请输入模型标识' }]}>
          <Input
            disabled={isEdit}
            placeholder="如 gpt-4o、deepseek-chat"
          />
        </Form.Item>

        <Form.Item name="displayName" label="显示名称">
          <Input placeholder="如 GPT-4o，为空则使用模型标识" />
        </Form.Item>

        <Form.Item name="modelType" label="模型类型" rules={[{ required: true, message: '请选择模型类型' }]}>
          <Select options={MODEL_TYPE_OPTIONS} placeholder="选择模型类型" />
        </Form.Item>

        <Form.Item name="enabled" label="启用" valuePropName="checked">
          <Switch />
        </Form.Item>

        {isEdit && (
          <>
            <div style={{ marginBottom: 8, fontWeight: 500, color: 'var(--color-text-secondary)' }}>
              高级设置
            </div>
            <Form.Item name="temperature" label="Temperature">
              <InputNumber min={0} max={1} step={0.1} style={{ width: '100%' }} placeholder="0 ~ 1" />
            </Form.Item>
            <Form.Item name="maxTokens" label="Max Tokens">
              <InputNumber min={1} style={{ width: '100%' }} placeholder="最大输出长度" />
            </Form.Item>
            <Form.Item name="topP" label="Top P">
              <InputNumber min={0} max={1} step={0.1} style={{ width: '100%' }} placeholder="0 ~ 1" />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  )
}
