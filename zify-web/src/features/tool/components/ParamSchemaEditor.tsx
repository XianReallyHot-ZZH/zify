import { useState } from 'react'
import { Button, Input, Select, Space, Switch, Table, Tag } from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ParamMapping, ParamIn } from '../../../types/tool'

type ParamRow = {
  name: string
  in: ParamIn
  type: 'string' | 'number' | 'integer' | 'boolean'
  required: boolean
  description: string
  secret: boolean
}

type Props = {
  params: ParamMapping[]
  /** 当前 inputSchema（JSON 字符串）。源码模式下可手动编辑覆盖。 */
  inputSchema: string
  onChange: (params: ParamMapping[], inputSchema: string) => void
}

const IN_OPTIONS: { label: string; value: ParamIn }[] = [
  { label: 'path', value: 'path' },
  { label: 'query', value: 'query' },
  { label: 'header', value: 'header' },
  { label: 'body', value: 'body' },
]

const TYPE_OPTIONS = [
  { label: 'string', value: 'string' },
  { label: 'integer', value: 'integer' },
  { label: 'number', value: 'number' },
  { label: 'boolean', value: 'boolean' },
]

function toRows(params: ParamMapping[]): ParamRow[] {
  return params.map((p) => ({
    name: p.name,
    in: (p.in as ParamIn) ?? 'query',
    type: (p.type as ParamRow['type']) ?? 'string',
    required: !!p.required,
    description: p.description ?? '',
    secret: !!p.secret,
  }))
}

function rowsToSchema(rows: ParamRow[]): string {
  const properties: Record<string, unknown> = {}
  const required: string[] = []
  for (const r of rows) {
    if (!r.name) continue
    const prop: Record<string, unknown> = { type: r.type }
    if (r.description) prop.description = r.description
    properties[r.name] = prop
    if (r.required) required.push(r.name)
  }
  const schema: Record<string, unknown> = { type: 'object', properties }
  if (required.length > 0) schema.required = required
  return JSON.stringify(schema, null, 2)
}

function rowsToParams(rows: ParamRow[]): ParamMapping[] {
  return rows
    .filter((r) => r.name)
    .map((r) => ({
      name: r.name,
      in: r.in,
      type: r.type,
      required: r.required,
      description: r.description || undefined,
      secret: r.secret || undefined,
    }))
}

/**
 * 可视化参数行表 ↔ JSON Schema 源码切换。
 * 行表驱动 inputSchema 生成；开启源码模式可手动覆盖（切回以行表为准重新生成）。
 */
export default function ParamSchemaEditor({ params, inputSchema, onChange }: Props) {
  const [rows, setRows] = useState<ParamRow[]>(toRows(params))
  const [sourceMode, setSourceMode] = useState(false)
  const [schemaText, setSchemaText] = useState(inputSchema || rowsToSchema(toRows(params)))

  function emit(nextRows: ParamRow[], useOverride: boolean) {
    setRows(nextRows)
    const generated = rowsToSchema(nextRows)
    const schema = useOverride ? schemaText : generated
    if (!useOverride) setSchemaText(generated)
    onChange(rowsToParams(nextRows), schema)
  }

  function updateRow(idx: number, patch: Partial<ParamRow>) {
    const next = rows.map((r, i) => (i === idx ? { ...r, ...patch } : r))
    emit(next, sourceMode)
  }

  function addRow() {
    emit([...rows, { name: '', in: 'query', type: 'string', required: false, description: '', secret: false }], sourceMode)
  }

  function removeRow(idx: number) {
    emit(rows.filter((_, i) => i !== idx), sourceMode)
  }

  return (
    <div>
      <Space style={{ marginBottom: 8 }}>
        <span>源码模式</span>
        <Switch
          checked={sourceMode}
          onChange={(v) => {
            setSourceMode(v)
            if (!v) {
              // 切回行表模式：以行表重新生成 schema
              const generated = rowsToSchema(rows)
              setSchemaText(generated)
              onChange(rowsToParams(rows), generated)
            }
          }}
        />
      </Space>

      {sourceMode ? (
        <Input.TextArea
          value={schemaText}
          onChange={(e) => {
            setSchemaText(e.target.value)
            onChange(rowsToParams(rows), e.target.value)
          }}
          autoSize={{ minRows: 8, maxRows: 20 }}
          style={{ fontFamily: 'monospace' }}
        />
      ) : (
        <Table
          size="small"
          rowKey={(_, i) => String(i)}
          dataSource={rows}
          pagination={false}
          columns={[
            {
              title: '参数名',
              dataIndex: 'name',
              width: 140,
              render: (_, r, i) => (
                <Input value={r.name} size="small" onChange={(e) => updateRow(i, { name: e.target.value })} />
              ),
            },
            {
              title: '位置',
              dataIndex: 'in',
              width: 110,
              render: (_, r, i) => (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  value={r.in}
                  options={IN_OPTIONS}
                  onChange={(v) => updateRow(i, { in: v })}
                />
              ),
            },
            {
              title: '类型',
              dataIndex: 'type',
              width: 110,
              render: (_, r, i) => (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  value={r.type}
                  options={TYPE_OPTIONS}
                  onChange={(v) => updateRow(i, { type: v })}
                />
              ),
            },
            {
              title: '必填',
              dataIndex: 'required',
              width: 60,
              render: (_, r, i) => (
                <Switch size="small" checked={r.required} onChange={(v) => updateRow(i, { required: v })} />
              ),
            },
            {
              title: '描述',
              dataIndex: 'description',
              render: (_, r, i) => (
                <Input
                  value={r.description}
                  size="small"
                  onChange={(e) => updateRow(i, { description: e.target.value })}
                />
              ),
            },
            {
              title: '敏感',
              dataIndex: 'secret',
              width: 60,
              render: (_, r, i) =>
                r.in === 'header' ? (
                  <Switch size="small" checked={r.secret} onChange={(v) => updateRow(i, { secret: v })} />
                ) : (
                  <Tag>—</Tag>
                ),
            },
            {
              title: '',
              width: 50,
              render: (_, __, i) => (
                <Button size="small" type="text" icon={<DeleteOutlined />} onClick={() => removeRow(i)} />
              ),
            },
          ]}
          footer={() => (
            <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addRow}>
              添加参数
            </Button>
          )}
        />
      )}
    </div>
  )
}
