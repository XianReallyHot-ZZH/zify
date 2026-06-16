import { List, Switch, Tag } from 'antd'
import type { McpDiscoveredToolResponse } from '../../../types/tool'

type Props = {
  tools: McpDiscoveredToolResponse[]
  onToggle: (tool: McpDiscoveredToolResponse, enabled: boolean) => void
}

/** MCP Server 已发现的工具列表（逐个启用/禁用开关）。 */
export default function DiscoveredToolList({ tools, onToggle }: Props) {
  if (tools.length === 0) {
    return <div style={{ color: '#888', padding: 8 }}>暂无已发现工具</div>
  }
  return (
    <List
      size="small"
      bordered
      dataSource={tools}
      renderItem={(tool) => (
        <List.Item
          actions={[
            <Switch
              key="sw"
              size="small"
              checked={tool.enabled === 1}
              onChange={(v) => onToggle(tool, v)}
            />,
          ]}
        >
          <List.Item.Meta
            title={
              <span>
                {tool.name} <Tag>MCP</Tag>
              </span>
            }
            description={tool.description}
          />
        </List.Item>
      )}
    />
  )
}
