import { useEffect, useState } from 'react'
import { Checkbox, Empty, Spin, Tag, Typography } from 'antd'
import { ApiError } from '../../../api/request'
import { listTools } from '../../../api/toolApi'
import type { ToolSummaryResponse } from '../../../types/tool'

type Props = {
  value: string[]
  onChange: (ids: string[]) => void
}

/**
 * Agent 工具绑定多选：按来源分组展示可用工具（HTTP / 按 MCP Server），
 * 勾选状态受控。不可用工具不展示（bindTools 仅接受可用）。
 */
export default function ToolBinder({ value, onChange }: Props) {
  const [tools, setTools] = useState<ToolSummaryResponse[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    listTools({ pageSize: 200, enabled: 1 })
      .then((res) => setTools(res.records.filter((t) => t.status === 'AVAILABLE')))
      .catch((err) => {
        // 静默：仅影响可选工具
        // eslint-disable-next-line no-console
        console.warn('load tools failed', err instanceof ApiError ? err.message : err)
      })
      .finally(() => setLoading(false))
  }, [])

  function toggle(id: string, checked: boolean) {
    if (checked) {
      onChange(Array.from(new Set([...value, id])))
    } else {
      onChange(value.filter((x) => x !== id))
    }
  }

  const httpTools = tools.filter((t) => t.sourceType === 'HTTP')
  const mcpGroups = groupBy(
    tools.filter((t) => t.sourceType === 'MCP'),
    (t) => t.mcpServerName ?? 'MCP',
  )

  return (
    <Spin spinning={loading}>
      {tools.length === 0 && !loading ? (
        <Empty description="暂无可用工具" />
      ) : (
        <div>
          <Group title="HTTP 工具" tools={httpTools} value={value} onToggle={toggle} />
          {Object.entries(mcpGroups).map(([server, list]) => (
            <Group key={server} title={`MCP · ${server}`} tools={list} value={value} onToggle={toggle} />
          ))}
        </div>
      )}
      {value.length > 0 && (
        <Typography.Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
          已选 {value.length} 个工具
        </Typography.Text>
      )}
    </Spin>
  )
}

function Group({
  title,
  tools,
  value,
  onToggle,
}: {
  title: string
  tools: ToolSummaryResponse[]
  value: string[]
  onToggle: (id: string, checked: boolean) => void
}) {
  if (tools.length === 0) return null
  return (
    <div style={{ marginBottom: 12 }}>
      <Typography.Text strong style={{ fontSize: 13 }}>
        {title}
      </Typography.Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
        {tools.map((t) => (
          <Checkbox
            key={t.id}
            checked={value.includes(t.id)}
            onChange={(e) => onToggle(t.id, e.target.checked)}
          >
            {t.name}
            {t.description ? <span style={{ color: '#888' }}> — {t.description}</span> : null}{' '}
            <Tag style={{ fontSize: 11 }}>{t.sourceType}</Tag>
          </Checkbox>
        ))}
      </div>
    </div>
  )
}

function groupBy<T>(arr: T[], keyFn: (t: T) => string): Record<string, T[]> {
  const map: Record<string, T[]> = {}
  for (const item of arr) {
    const k = keyFn(item)
    ;(map[k] ??= []).push(item)
  }
  return map
}
