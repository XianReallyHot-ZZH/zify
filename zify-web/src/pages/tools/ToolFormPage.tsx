import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Steps,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { PageHeader } from '../../shared/ui'
import { ApiError } from '../../api/request'
import { createTool, getTool, testTool, updateTool } from '../../api/toolApi'
import type {
  AuthType,
  HeaderTemplate,
  ParamMapping,
  ToolConfig,
  ToolDetailResponse,
  ToolTestResult,
} from '../../types/tool'
import ParamSchemaEditor from '../../features/tool/components/ParamSchemaEditor'

const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
const AUTH_TYPES: AuthType[] = ['NONE', 'API_KEY', 'BEARER']

const STEPS = ['基础', '请求', '鉴权', '高级', '测试', '确认']

export default function ToolFormPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const isEdit = Boolean(id)

  const [current, setCurrent] = useState(0)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [method, setMethod] = useState('GET')
  const [endpoint, setEndpoint] = useState('')
  const [params, setParams] = useState<ParamMapping[]>([])
  const [inputSchema, setInputSchema] = useState('{"type":"object","properties":{}}')
  const [headers, setHeaders] = useState<HeaderTemplate[]>([])
  const [bodyTemplate, setBodyTemplate] = useState('')
  const [authType, setAuthType] = useState<AuthType>('NONE')
  const [authHeaderName, setAuthHeaderName] = useState('X-Api-Key')
  const [credential, setCredential] = useState('')
  const [hasAuth, setHasAuth] = useState(false)
  const [timeoutSeconds, setTimeoutSeconds] = useState<number | null>(null)
  const [idempotent, setIdempotent] = useState<boolean>(true)
  const [loading, setLoading] = useState(false)

  const [testArgs, setTestArgs] = useState('{}')
  const [testResult, setTestResult] = useState<ToolTestResult | null>(null)

  useEffect(() => {
    if (!isEdit || !id) return
    setLoading(true)
    getTool(id)
      .then((tool) => fillFromTool(tool))
      .catch((err) => message.error(err instanceof ApiError ? err.message : '加载工具失败'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isEdit])

  function fillFromTool(tool: ToolDetailResponse) {
    setName(tool.name)
    setDescription(tool.description ?? '')
    setMethod(tool.method ?? 'GET')
    setEndpoint(tool.endpoint ?? '')
    setParams(tool.configJson?.paramsMapping ?? [])
    setInputSchema(tool.inputSchema ?? '{}')
    setHeaders(tool.configJson?.headersTemplate ?? [])
    setBodyTemplate(tool.configJson?.bodyTemplate ?? '')
    setAuthType((tool.authType as AuthType) ?? 'NONE')
    setHasAuth(tool.hasAuth)
    setTimeoutSeconds(tool.timeoutSeconds)
    setIdempotent(tool.idempotent === 1)
  }

  const configJson = useMemo<ToolConfig>(
    () => ({ paramsMapping: params, headersTemplate: headers, bodyTemplate: bodyTemplate || null }),
    [params, headers, bodyTemplate],
  )

  function buildPayload() {
    return {
      name,
      description: description || undefined,
      method,
      endpoint,
      inputSchema,
      configJson,
      authType,
      authHeaderName: authType === 'API_KEY' ? authHeaderName : undefined,
      credential: credential || undefined,
      timeoutSeconds,
      idempotent: idempotent ? 1 : 0,
    }
  }

  async function handleSubmit() {
    if (!name || !method || !endpoint) {
      message.error('name / method / endpoint 必填')
      return
    }
    setLoading(true)
    try {
      if (isEdit && id) {
        await updateTool(id, buildPayload())
        message.success('已更新')
      } else {
        await createTool(buildPayload())
        message.success('已创建')
      }
      navigate('/tools')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleTest() {
    if (!isEdit || !id) {
      message.warning('请先保存工具再测试')
      return
    }
    let args: Record<string, unknown> = {}
    try {
      args = testArgs.trim() ? JSON.parse(testArgs) : {}
    } catch {
      message.error('args 不是合法 JSON')
      return
    }
    setLoading(true)
    try {
      const result = await testTool(id, { args })
      setTestResult(result)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '测试失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="zify-page">
      <PageHeader
        title={isEdit ? '编辑 HTTP 工具' : '新建 HTTP 工具'}
        description="手动配置 endpoint / 参数映射 / 鉴权，可视化参数表生成 inputSchema"
      />

      <Steps current={current} items={STEPS.map((t) => ({ title: t }))} style={{ margin: '16px 0 24px' }} />

      {current === 0 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong>工具名（LLM 可见，唯一）</Typography.Text>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="get_user_info" />
          </div>
          <div>
            <Typography.Text strong>描述（喂给 LLM）</Typography.Text>
            <Input.TextArea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
          </div>
        </Space>
      )}

      {current === 1 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space>
            <Select
              value={method}
              onChange={setMethod}
              style={{ width: 120 }}
              options={METHODS.map((m) => ({ label: m, value: m }))}
            />
            <Input
              value={endpoint}
              onChange={(e) => setEndpoint(e.target.value)}
              placeholder="https://api.example.com/users/{userId}"
              style={{ width: 520 }}
            />
          </Space>
          <div>
            <Typography.Text strong>参数表（生成 inputSchema + paramsMapping）</Typography.Text>
            <ParamSchemaEditor
              params={params}
              inputSchema={inputSchema}
              onChange={(p, schema) => {
                setParams(p)
                setInputSchema(schema)
              }}
            />
          </div>
          <HeadersEditor value={headers} onChange={setHeaders} />
          <div>
            <Typography.Text strong>Body 模板（支持 {'{{args.x}}'} / {'{{auth.x}}'}）</Typography.Text>
            <Input.TextArea value={bodyTemplate} onChange={(e) => setBodyTemplate(e.target.value)} rows={3} />
          </div>
        </Space>
      )}

      {current === 2 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Select
            value={authType}
            onChange={setAuthType}
            style={{ width: 200 }}
            options={AUTH_TYPES.map((a) => ({ label: a, value: a }))}
          />
          {isEdit && (
            <Typography.Text type="secondary">
              {hasAuth ? '已配置凭据（留空不修改）' : '未配置凭据'}
            </Typography.Text>
          )}
          {authType === 'API_KEY' && (
            <Input
              value={authHeaderName}
              onChange={(e) => setAuthHeaderName(e.target.value)}
              placeholder="Header 名，如 X-Api-Key"
              style={{ width: 240 }}
            />
          )}
          {authType !== 'NONE' && (
            <Input.Password
              value={credential}
              onChange={(e) => setCredential(e.target.value)}
              placeholder={isEdit && hasAuth ? '••••••（留空不改）' : '凭据'}
              style={{ width: 320 }}
            />
          )}
        </Space>
      )}

      {current === 3 && (
        <Space direction="vertical" size="middle">
          <Space>
            <Typography.Text>单次请求超时（秒，空=全局 30s）</Typography.Text>
            <InputNumber
              value={timeoutSeconds ?? undefined}
              onChange={(v) => setTimeoutSeconds((v as number | null) ?? null)}
              min={1}
              max={300}
            />
          </Space>
          <Space>
            <Typography.Text>幂等（请求发出后失败是否重试）</Typography.Text>
            <Switch checked={idempotent} onChange={setIdempotent} />
            {idempotent ? <Tag color="green">可重试</Tag> : <Tag>不重试</Tag>}
          </Space>
        </Space>
      )}

      {current === 4 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text strong>示例 args（JSON）</Typography.Text>
          <Input.TextArea
            value={testArgs}
            onChange={(e) => setTestArgs(e.target.value)}
            rows={3}
            style={{ fontFamily: 'monospace' }}
          />
          <Button onClick={handleTest} loading={loading}>
            执行测试（真实调用，写一条 tool_call_log）
          </Button>
          {testResult && (
            <div>
              <Tag color={testResult.success ? 'green' : 'red'}>{testResult.status}</Tag>
              <Typography.Text type="secondary">耗时 {testResult.durationMs}ms</Typography.Text>
              <Input.TextArea
                value={testResult.output}
                readOnly
                autoSize={{ minRows: 3, maxRows: 16 }}
                style={{ marginTop: 8, fontFamily: 'monospace' }}
              />
              {testResult.error && <Typography.Text type="danger">{testResult.error}</Typography.Text>}
            </div>
          )}
        </Space>
      )}

      {current === 5 && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>
            名称：{name} · {method} {endpoint}
          </Typography.Text>
          <Typography.Text type="secondary">鉴权：{authType}</Typography.Text>
          <Typography.Text type="secondary">幂等：{idempotent ? '是' : '否'}</Typography.Text>
        </Space>
      )}

      <div style={{ marginTop: 24, display: 'flex', justifyContent: 'space-between' }}>
        <Button disabled={current === 0} onClick={() => setCurrent((c) => c - 1)}>
          上一步
        </Button>
        {current < STEPS.length - 1 ? (
          <Button type="primary" onClick={() => setCurrent((c) => c + 1)}>
            下一步
          </Button>
        ) : (
          <Button type="primary" onClick={handleSubmit} loading={loading}>
            保存
          </Button>
        )}
      </div>
    </div>
  )
}

/** 简易 Header 键值表（含 secret 标记）。 */
function HeadersEditor({
  value,
  onChange,
}: {
  value: HeaderTemplate[]
  onChange: (v: HeaderTemplate[]) => void
}) {
  return (
    <div>
      <Typography.Text strong>固定/模板 Header</Typography.Text>
      <Space direction="vertical" size="small" style={{ width: '100%', marginTop: 8 }}>
        {value.map((h, i) => (
          <Space key={i}>
            <Input
              value={h.name}
              placeholder="Header 名"
              style={{ width: 160 }}
              onChange={(e) => onChange(value.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))}
            />
            <Input
              value={h.value}
              placeholder="值（支持 {{auth.x}}）"
              style={{ width: 280 }}
              onChange={(e) => onChange(value.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
            />
            <Switch
              checked={!!h.secret}
              checkedChildren="secret"
              unCheckedChildren="plain"
              onChange={(v) => onChange(value.map((x, j) => (j === i ? { ...x, secret: v } : x)))}
            />
            <Button type="text" onClick={() => onChange(value.filter((_, j) => j !== i))}>
              删除
            </Button>
          </Space>
        ))}
        <Button size="small" type="dashed" onClick={() => onChange([...value, { name: '', value: '', secret: false }])}>
          添加 Header
        </Button>
      </Space>
    </div>
  )
}
