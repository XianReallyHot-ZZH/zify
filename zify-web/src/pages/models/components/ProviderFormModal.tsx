import { useEffect, useState } from 'react'
import { Button, Form, Input, Modal, Select } from 'antd'
import { EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { ApiError } from '../../../api/request'
import { getProviderApiKey } from '../../../api/modelApi'
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

const MASKED_PLACEHOLDER = '*******'

export default function ProviderFormModal({ open, provider, onSubmit, onCancel }: ProviderFormModalProps) {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const isEdit = !!provider

  // API Key 遮罩/解密状态
  const [apiKeyRevealed, setApiKeyRevealed] = useState(false)
  const [apiKeyLoading, setApiKeyLoading] = useState(false)

  // 监听供应商类型变化，自动预填 Base URL
  const providerType = Form.useWatch('providerType', form)

  useEffect(() => {
    if (providerType && !isEdit) {
      const currentBaseUrl = form.getFieldValue('baseUrl') || ''
      const prevDefault = Object.values(DEFAULT_BASE_URLS).find(v => v === currentBaseUrl)
      if (!currentBaseUrl || prevDefault !== undefined) {
        form.setFieldValue('baseUrl', DEFAULT_BASE_URLS[providerType] || '')
      }
    }
  }, [providerType, form, isEdit])

  // 弹窗打开时初始化表单
  useEffect(() => {
    if (open) {
      setApiKeyRevealed(false)
      if (provider) {
        const apiVersion = provider.extraConfig?.apiVersion as string | undefined
        form.setFieldsValue({
          name: provider.name,
          providerType: provider.providerType,
          apiKey: provider.hasApiKey ? MASKED_PLACEHOLDER : undefined,
          baseUrl: provider.baseUrl,
          apiVersion: apiVersion || '',
        })
      } else {
        form.resetFields()
      }
    }
  }, [open, provider, form])

  async function handleToggleApiKeyReveal() {
    if (!provider) return

    if (apiKeyRevealed) {
      // 切回遮罩
      form.setFieldValue('apiKey', MASKED_PLACEHOLDER)
      setApiKeyRevealed(false)
      return
    }

    // 请求解密
    setApiKeyLoading(true)
    try {
      const result = await getProviderApiKey(provider.id, true)
      if (result.decryptedApiKey) {
        form.setFieldValue('apiKey', result.decryptedApiKey)
        setApiKeyRevealed(true)
      }
    } catch {
      // 解密失败，保持遮罩
    } finally {
      setApiKeyLoading(false)
    }
  }

  async function handleOk() {
    try {
      const values = await form.validateFields()
      setSubmitting(true)

      if (isEdit) {
        const updateData: UpdateProviderRequest = {
          name: values.name,
          baseUrl: values.baseUrl,
        }
        // 遮罩占位符不算有效输入，只有用户实际修改了才提交
        if (values.apiKey && values.apiKey !== MASKED_PLACEHOLDER) {
          updateData.apiKey = values.apiKey
        }
        if (provider?.providerType === 'ANTHROPIC') {
          updateData.extraConfig = values.apiVersion
            ? { apiVersion: values.apiVersion }
            : undefined
        }
        await onSubmit(updateData)
      } else {
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
    } catch (err) {
      if (err instanceof ApiError) {
        throw err
      }
    } finally {
      setSubmitting(false)
    }
  }

  const currentType = isEdit ? provider.providerType : providerType
  const showApiVersion = currentType === 'ANTHROPIC'
  const hasApiKey = isEdit && provider.hasApiKey

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
          {hasApiKey ? (
            <Input
              type={apiKeyRevealed ? 'text' : 'password'}
              suffix={
                <Button
                  type="text"
                  size="small"
                  loading={apiKeyLoading}
                  onClick={handleToggleApiKeyReveal}
                  icon={apiKeyRevealed ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                  style={{ marginRight: -4 }}
                />
              }
            />
          ) : (
            <Input.Password placeholder="可选，Ollama 等本地服务可留空" />
          )}
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
