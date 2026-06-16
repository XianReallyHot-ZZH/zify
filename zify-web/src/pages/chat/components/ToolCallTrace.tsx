import { useState } from 'react'
import { Button, Collapse, Descriptions, Drawer, Tag, Typography } from 'antd'
import { ToolOutlined } from '@ant-design/icons'
import { getCallLog } from '../../../api/toolApi'
import type { ToolCallLogDetailResponse, ToolCallView } from '../../../types/tool'

type Props = {
  toolCalls: ToolCallView[]
}

const STATUS_COLOR: Record<string, string> = {
  SUCCESS: 'green',
  ERROR: 'red',
  pending: 'blue',
}

/**
 * 工具调用卡片（实时流 + 历史回放同渲染）：tool_call_start 显示「调用中」，
 * tool_call_end 更新状态/耗时/可折叠 output；点 toolCallLogId 下钻完整日志。
 */
export default function ToolCallTrace({ toolCalls }: Props) {
  const [drawerId, setDrawerId] = useState<string | null>(null)
  const [drawerData, setDrawerData] = useState<ToolCallLogDetailResponse | null>(null)
  const [drawerLoading, setDrawerLoading] = useState(false)

  async function openDrawer(toolCallLogId: string | null) {
    if (!toolCallLogId) return
    setDrawerId(toolCallLogId)
    setDrawerData(null)
    setDrawerLoading(true)
    try {
      setDrawerData(await getCallLog(toolCallLogId))
    } catch (err) {
      // 忽略：关闭抽屉
      setDrawerId(null)
    } finally {
      setDrawerLoading(false)
    }
  }

  if (!toolCalls || toolCalls.length === 0) return null

  return (
    <div style={{ margin: '6px 0' }}>
      <Collapse
        size="small"
        items={toolCalls.map((tc) => ({
          key: tc.toolCallId,
          label: (
            <span>
              <ToolOutlined /> {tc.toolName}{' '}
              <Tag color={STATUS_COLOR[tc.status ?? 'pending'] ?? 'default'}>
                {tc.status ?? '调用中…'}
              </Tag>
              {tc.durationMs != null && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {tc.durationMs}ms
                </Typography.Text>
              )}
            </span>
          ),
          children: (
            <div>
              {tc.args && (
                <div>
                  <Typography.Text type="secondary">入参：</Typography.Text>
                  <pre style={{ margin: '4px 0', fontSize: 12, maxHeight: 160, overflow: 'auto' }}>{tc.args}</pre>
                </div>
              )}
              {tc.output != null && (
                <div>
                  <Typography.Text type="secondary">输出：</Typography.Text>
                  <pre style={{ margin: '4px 0', fontSize: 12, maxHeight: 220, overflow: 'auto' }}>{tc.output}</pre>
                </div>
              )}
              {tc.toolCallLogId && (
                <Button type="link" size="small" onClick={() => openDrawer(tc.toolCallLogId)} style={{ padding: 0 }}>
                  查看完整日志
                </Button>
              )}
            </div>
          ),
        }))}
      />

      <Drawer
        title="工具调用日志"
        open={Boolean(drawerId)}
        onClose={() => setDrawerId(null)}
        width={520}
        loading={drawerLoading}
      >
        {drawerData && (
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="状态">
              <Tag color={STATUS_COLOR[drawerData.status] ?? 'default'}>{drawerData.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="工具">{drawerData.toolName}</Descriptions.Item>
            <Descriptions.Item label="耗时">{drawerData.durationMs}ms</Descriptions.Item>
            <Descriptions.Item label="轮次">{drawerData.turn ?? '—'}</Descriptions.Item>
            {drawerData.error && <Descriptions.Item label="错误">{drawerData.error}</Descriptions.Item>}
            <Descriptions.Item label="入参">
              <pre style={{ margin: 0, fontSize: 12, maxHeight: 200, overflow: 'auto' }}>
                {JSON.stringify(drawerData.input, null, 2)}
              </pre>
            </Descriptions.Item>
            <Descriptions.Item label="输出">
              <pre style={{ margin: 0, fontSize: 12, maxHeight: 320, overflow: 'auto' }}>{drawerData.output}</pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}
