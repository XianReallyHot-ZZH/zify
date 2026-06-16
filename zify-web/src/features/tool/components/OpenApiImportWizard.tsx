import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Checkbox, Input, message, Space, Steps, Table, Tag, Typography } from 'antd'
import { ApiError } from '../../../api/request'
import { importOpenApi, parseOpenApi } from '../../../api/toolApi'
import type {
  AuthType,
  ImportSelection,
  OpenApiOperationPreviewResponse,
} from '../../../types/tool'

type Props = {
  onCancel: () => void
}

/**
 * OpenAPI 导入向导：粘贴/上传 spec → 预览 operation → 勾选 + 改名 → 统一鉴权 → 批量导入。
 */
export default function OpenApiImportWizard({ onCancel }: Props) {
  const navigate = useNavigate()
  const [current, setCurrent] = useState(0)
  const [spec, setSpec] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [operations, setOperations] = useState<OpenApiOperationPreviewResponse[]>([])
  const [selections, setSelections] = useState<Record<string, ImportSelection>>({})
  const [authType, setAuthType] = useState<AuthType>('NONE')
  const [credential, setCredential] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleParse() {
    if (!spec.trim()) {
      message.warning('请粘贴或上传 OpenAPI spec')
      return
    }
    setLoading(true)
    try {
      const result = await parseOpenApi(spec)
      setBaseUrl(result.baseUrl ?? '')
      setOperations(result.operations)
      const init: Record<string, ImportSelection> = {}
      for (const op of result.operations) {
        const key = opKey(op)
        init[key] = { operationId: op.operationId ?? undefined, method: op.method, path: op.path, name: op.suggestedName, selected: true }
      }
      setSelections(init)
      setCurrent(1)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '解析失败')
    } finally {
      setLoading(false)
    }
  }

  async function handleImport() {
    const selected = Object.values(selections).filter((s) => s.selected)
    if (selected.length === 0) {
      message.warning('请至少勾选一个 operation')
      return
    }
    setLoading(true)
    try {
      const result = await importOpenApi({
        baseUrl: baseUrl || undefined,
        authType,
        credential: credential || undefined,
        operations: selected,
        spec,
      })
      message.success(`已导入 ${result.created.length} 个工具${result.skipped.length ? `，跳过 ${result.skipped.length}` : ''}`)
      navigate('/tools')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '导入失败')
    } finally {
      setLoading(false)
    }
  }

  function opKey(op: OpenApiOperationPreviewResponse) {
    return `${op.method} ${op.path}`
  }

  return (
    <div>
      <Steps
        current={current}
        items={['上传 spec', '勾选 operation', '统一鉴权', '确认导入'].map((t) => ({ title: t }))}
        style={{ margin: '16px 0 24px' }}
      />

      {current === 0 && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>粘贴 OpenAPI 3.0/3.1 spec（JSON/YAML）</Typography.Text>
          <Input.TextArea
            value={spec}
            onChange={(e) => setSpec(e.target.value)}
            rows={12}
            placeholder='{"openapi":"3.0.0", ...}'
            style={{ fontFamily: 'monospace' }}
          />
          <Space>
            <Button type="primary" onClick={handleParse} loading={loading}>
              解析
            </Button>
            <Button onClick={onCancel}>取消</Button>
          </Space>
        </Space>
      )}

      {current === 1 && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Typography.Text>baseUrl：</Typography.Text>
            <Input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} style={{ width: 320 }} />
          </Space>
          <Table
            size="small"
            rowKey={opKey}
            dataSource={operations}
            pagination={false}
            columns={[
              {
                title: '',
                width: 40,
                render: (_, op) => (
                  <Checkbox
                    checked={!!selections[opKey(op)]?.selected}
                    onChange={(e) =>
                      setSelections((prev) => ({
                        ...prev,
                        [opKey(op)]: { ...prev[opKey(op)], selected: e.target.checked },
                      }))
                    }
                  />
                ),
              },
              { title: 'Method', dataIndex: 'method', width: 80, render: (m: string) => <Tag>{m}</Tag> },
              { title: 'Path', dataIndex: 'path' },
              {
                title: '工具名',
                dataIndex: 'suggestedName',
                render: (_, op) => (
                  <Input
                    value={selections[opKey(op)]?.name ?? op.suggestedName}
                    onChange={(e) =>
                      setSelections((prev) => ({
                        ...prev,
                        [opKey(op)]: { ...prev[opKey(op)], name: e.target.value },
                      }))
                    }
                    style={{ width: 200 }}
                  />
                ),
              },
              { title: '说明', dataIndex: 'summary' },
            ]}
          />
          <Space>
            <Button onClick={() => setCurrent(0)}>上一步</Button>
            <Button type="primary" onClick={() => setCurrent(2)}>
              下一步
            </Button>
          </Space>
        </Space>
      )}

      {current === 2 && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Typography.Text>鉴权方式</Typography.Text>
            <Input value={authType} onChange={(e) => setAuthType(e.target.value as AuthType)} style={{ width: 140 }} />
          </Space>
          {authType !== 'NONE' && (
            <Input.Password
              value={credential}
              onChange={(e) => setCredential(e.target.value)}
              placeholder="凭据（应用到所有勾选工具，加密存储）"
              style={{ width: 360 }}
            />
          )}
          <Space>
            <Button onClick={() => setCurrent(1)}>上一步</Button>
            <Button type="primary" onClick={() => setCurrent(3)}>
              下一步
            </Button>
          </Space>
        </Space>
      )}

      {current === 3 && (
        <Space direction="vertical">
          <Typography.Text>
            将导入 {Object.values(selections).filter((s) => s.selected).length} 个工具，baseUrl={baseUrl || '（spec 默认）'}
          </Typography.Text>
          <Space>
            <Button onClick={() => setCurrent(2)}>上一步</Button>
            <Button type="primary" onClick={handleImport} loading={loading}>
              确认导入
            </Button>
          </Space>
        </Space>
      )}
    </div>
  )
}
