import { useEffect, useState } from 'react'
import { Form, Input, Modal, Select } from 'antd'
import type {
  ProviderResponse,
  CreateProviderRequest,
  UpdateProviderRequest,
} from '../../../api/modelApi'

type ProviderFormModalProps = {
  open: boolean
  provider?: ProviderResponse
  onSubmit: (values: CreateProviderRequest | UpdateProviderRequest) => Promise<void>
  onCancel: () => void
}

const PROVIDER_TYPE_OPTIONS = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Anthropic', value: 'ANTHROPIC' },
  { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
]

const DEFAULT_BASE_URLS: Record<string, string> = {
  OPENAI: 'https://api.openai.com',
  ANTHROPIC: 'https://api.anthropic.com',
  OPENAI_COMPATIBLE: '',
}

export default function ProviderFormModal({ open, provider, onSubmit, onCancel }: ProviderFormModalProps) {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const isEdit = !!provider

  // 监听供应商类型变化，自动预填 Base URL
  const providerType = Form.useWatch('providerType', form)

  useEffect(() => {
    if (providerType && !isEdit) {
      const currentBaseUrl = form.getFieldValue('baseUrl') || ''
      // 只在 Base URL 为空或等于之前类型的默认值时自动填充
      const prevDefault = Object.values(DEFAULT_BASE_URLS).find(v => v === currentBaseUrl)
      if (!currentBaseUrl || prevDefault !== undefined) {
        form.setFieldValue('baseUrl', DEFAULT_BASE_URLS[providerType] || '')
      }
    }
  }, [providerType, form, isEdit])

  // 弹窗打开时初始化表单
  useEffect(() => {
    if (open) {
      if (provider) {
        // 编辑模式：预填数据
        const apiVersion = provider.extraConfig?.apiVersion as string | undefined
        form.setFieldsValue({
          name: provider.name,
          providerType: provider.providerType,
          apiKey: undefined,
          baseUrl: provider.baseUrl,
          apiVersion: apiVersion || '',
        })
      } else {
        // 创建模式：重置
        form.resetFields()
      }
    }
  }, [open, provider, form])

  async function handleOk() {
    try {
      const values = await form.validateFields()
      setSubmitting(true)

      if (isEdit) {
        // 编辑模式：不提交 providerType，apiKey 留空时不提交
        const updateData: UpdateProviderRequest = {
          name: values.name,
          baseUrl: values.baseUrl,
        }
        if (values.apiKey) {
          updateData.apiKey = values.apiKey
        }
        // Anthropic 时提交 apiVersion 作为 extraConfig
        if (provider?.providerType === 'ANTHROPIC') {
          updateData.extraConfig = values.apiVersion
            ? { apiVersion: values.apiVersion }
            : undefined
        }
        await onSubmit(updateData)
      } else {
        // 创建模式
        const createData: CreateProviderRequest = {
          name: values.name,
          providerType: values.providerType,
          baseUrl: values.baseUrl,
        }
        if (values.apiKey) {
          createData.apiKey = values.apiKey
        }
        if (values.providerType === 'ANTHROPIC' && values.apiVersion) {
          createData.extraConfig = { apiVersion: values.apiVersion }
        }
        await onSubmit(createData)
      }
    } catch {
      // 表单校验失败，不做处理
    } finally {
      setSubmitting(false)
    }
  }

  const currentType = isEdit ? provider.providerType : providerType
  const showApiVersion = currentType === 'ANTHROPIC'

  return (
    <Modal
      title={isEdit ? '编辑供应商' : '添加供应商'}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={submitting}
      destroyOnClose
      width={520}
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <Form.Item name="name" label="供应商名称" rules={[{ required: true, message: '请输入供应商名称' }]}>
          <Input maxLength={128} placeholder="如：我的 DeepSeek" />
        </Form.Item>

        <Form.Item name="providerType" label="供应商类型" rules={[{ required: true, message: '请选择供应商类型' }]}>
          <Select options={PROVIDER_TYPE_OPTIONS} disabled={isEdit} placeholder="选择供应商类型" />
        </Form.Item>

        <Form.Item name="apiKey" label="API Key">
          <Input.Password
            placeholder={isEdit ? '留空则不修改' : '可选，Ollama 等本地服务可留空'}
          />
        </Form.Item>

        <Form.Item name="baseUrl" label="Base URL" rules={[{ required: true, message: '请输入 Base URL' }]}>
          <Input placeholder="如：https://api.openai.com" />
        </Form.Item>

        {showApiVersion && (
          <Form.Item name="apiVersion" label="API Version">
            <Input placeholder="2023-06-01" />
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
