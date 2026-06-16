import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Card, Input, message, Select, Space, Tag, Typography } from 'antd'
import { ExperimentOutlined, ReloadOutlined } from '@ant-design/icons'
import { PageHeader } from '../../shared/ui'
import { ApiError } from '../../api/request'
import {
  createMcpServer,
  getMcpServer,
  refreshMcpServer,
  setToolEnabled,
  testMcpServer,
  testMcpServerConfig,
  updateMcpServer,
} from '../../api/toolApi'
import type {
  AuthType,
  McpDiscoveredToolResponse,
  McpServerDetailResponse,
  McpServerTestResult,
  McpToolPreview,
  TransportType,
} from '../../types/tool'
import DiscoveredToolList from '../../features/tool/components/DiscoveredToolList'

const TRANSPORTS: TransportType[] = ['STREAMABLE_HTTP', 'SSE']
const AUTH_TYPES: AuthType[] = ['NONE', 'API_KEY', 'BEARER']

export default function McpServerFormPage() {
  const navigate = useNavigate()
  const { serverId } = useParams()
  const isEdit = Boolean(serverId)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [transportType, setTransportType] = useState<TransportType>('STREAMABLE_HTTP')
  const [authType, setAuthType] = useState<AuthType>('NONE')
  const [authHeaderName, setAuthHeaderName] = useState('X-Api-Key')
  const [credential, setCredential] = useState('')
  const [detail, setDetail] = useState<McpServerDetailResponse | null>(null)
  const [testResult, setTestResult] = useState<McpServerTestResult | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!isEdit || !serverId) return
    setLoading(true)
    getMcpServer(serverId)
      .then((d) => {
        setDetail(d)
        setName(d.name)
        setDescription(d.description ?? '')
        setBaseUrl(d.baseUrl)
        setTransportType(d.transportType)
        setAuthType(d.authType)
      })
      .catch((err) => message.error(err instanceof ApiError ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [serverId, isEdit])

  function buildPayload() {
    return {
      name,
      description: description || undefined,
      baseUrl,
      transportType,
      authType,
      authHeaderName: authType === 'API_KEY' ? authHeaderName : undefined,
      credential: credential || undefined,
    }
  }

  async function handleSave() {
    if (!name || !baseUrl) {
      message.error('name / baseUrl 必填')
      return
    }
    setLoading(true)
    try {
      if (isEdit && serverId) {
        const d = await updateMcpServer(serverId, buildPayload())
        setDetail(d)
        message.success('已更新')
      } else {
        const d = await createMcpServer(buildPayload())
        setDetail(d)
        message.success('已连接')
        navigate(`/tools/mcp/${d.id}/edit`, { replace: true })
      }
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleTest() {
    setLoading(true)
    try {
      const result = isEdit && serverId ? await testMcpServer(serverId) : await testMcpServerConfig(buildPayload())
      setTestResult(result)
      if (!result.success) message.warning(result.message ?? '连接失败')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '测试失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleRefresh() {
    if (!serverId) return
    setLoading(true)
    try {
      const d = await refreshMcpServer(serverId)
      setDetail(d)
      message.success('已刷新')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '刷新失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleToggleDiscovered(tool: McpDiscoveredToolResponse, enabled: boolean) {
    try {
      await setToolEnabled(tool.id, { enabled })
      if (serverId) setDetail(await getMcpServer(serverId))
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  return (
    <div className="zify-page">
      <PageHeader title={isEdit ? '编辑 MCP Server' : '连接 MCP Server'} description="Streamable HTTP / SSE，测试连接并发现工具" />

      <Card title="配置" size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Field label="名称">
            <Input value={name} onChange={(e) => setName(e.target.value)} style={{ width: 280 }} />
          </Field>
          <Field label="描述">
            <Input value={description} onChange={(e) => setDescription(e.target.value)} style={{ width: 400 }} />
          </Field>
          <Field label="连接地址">
            <Input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} style={{ width: 520 }} placeholder="https://mcp.example.com/mcp" />
          </Field>
          <Field label="传输">
            <Select
              value={transportType}
              onChange={setTransportType}
              style={{ width: 200 }}
              options={TRANSPORTS.map((t) => ({ label: t, value: t }))}
            />
          </Field>
          <Field label="认证">
            <Select
              value={authType}
              onChange={setAuthType}
              style={{ width: 160 }}
              options={AUTH_TYPES.map((a) => ({ label: a, value: a }))}
            />
          </Field>
          {authType === 'API_KEY' && (
            <Field label="Header 名">
              <Input value={authHeaderName} onChange={(e) => setAuthHeaderName(e.target.value)} style={{ width: 240 }} />
            </Field>
          )}
          {authType !== 'NONE' && (
            <Field label="凭据">
              <Input.Password
                value={credential}
                onChange={(e) => setCredential(e.target.value)}
                placeholder={isEdit && detail?.hasAuth ? '••••••（留空不改）' : '凭据'}
                style={{ width: 320 }}
              />
            </Field>
          )}
          <Space>
            <Button type="primary" onClick={handleSave} loading={loading}>
              {isEdit ? '保存' : '连接并保存'}
            </Button>
            <Button icon={<ExperimentOutlined />} onClick={handleTest} loading={loading}>
              测试连接
            </Button>
            {isEdit && (
              <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading}>
                刷新发现
              </Button>
            )}
          </Space>
        </Space>
      </Card>

      {testResult && (
        <Card title="测试结果" size="small" style={{ marginBottom: 16 }}>
          <Space>
            <Tag color={testResult.success ? 'green' : 'red'}>{testResult.success ? '成功' : '失败'}</Tag>
            <Typography.Text type="secondary">耗时 {testResult.latencyMs}ms</Typography.Text>
            {testResult.message && <Typography.Text type="secondary">{testResult.message}</Typography.Text>}
          </Space>
          {testResult.discoveredTools.length > 0 && (
            <PreviewTools tools={testResult.discoveredTools} />
          )}
        </Card>
      )}

      {isEdit && detail && (
        <Card title={`已发现工具（${detail.discoveredTools.length}）`} size="small">
          <DiscoveredToolList tools={detail.discoveredTools} onToggle={handleToggleDiscovered} />
        </Card>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <Typography.Text style={{ width: 80, flexShrink: 0 }}>{label}</Typography.Text>
      {children}
    </div>
  )
}

function PreviewTools({ tools }: { tools: McpToolPreview[] }) {
  return (
    <div style={{ marginTop: 8 }}>
      <Typography.Text type="secondary">发现的工具（预览，未持久化）：</Typography.Text>
      <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
        {tools.map((t) => (
          <li key={t.name}>
            <strong>{t.name}</strong> — {t.description}
          </li>
        ))}
      </ul>
    </div>
  )
}
