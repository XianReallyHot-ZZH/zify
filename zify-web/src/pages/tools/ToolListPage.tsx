import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Dropdown, message, Pagination, Popconfirm, Space, Spin, Switch, Tag } from 'antd'
import { DownOutlined, EditOutlined, DeleteOutlined, ExperimentOutlined, PlusOutlined } from '@ant-design/icons'
import { PageHeader } from '../../shared/ui'
import Empty from '../../shared/ui/Empty'
import { ApiError } from '../../api/request'
import {
  deleteMcpServer,
  deleteTool,
  listMcpServers,
  listTools,
  setMcpServerEnabled,
  setToolEnabled,
  testTool,
} from '../../api/toolApi'
import type { McpServerSummaryResponse, ToolSummaryResponse } from '../../types/tool'

const STATUS_COLOR: Record<string, string> = {
  ONLINE: 'green',
  OFFLINE: 'default',
  ERROR: 'red',
  AVAILABLE: 'green',
  UNAVAILABLE: 'default',
}

export default function ToolListPage() {
  const navigate = useNavigate()
  const [tools, setTools] = useState<ToolSummaryResponse[]>([])
  const [toolTotal, setToolTotal] = useState(0)
  const [toolPage, setToolPage] = useState(1)
  const [servers, setServers] = useState<McpServerSummaryResponse[]>([])
  const [loading, setLoading] = useState(false)
  const pageSize = 20

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [toolPageRes, serverRes] = await Promise.all([
        listTools({ page: toolPage, pageSize, sourceType: 'HTTP' }),
        listMcpServers({ page: 1, pageSize: 100 }),
      ])
      setTools(toolPageRes.records)
      setToolTotal(toolPageRes.total)
      setServers(serverRes.records)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '加载工具失败')
    } finally {
      setLoading(false)
    }
  }, [toolPage])

  useEffect(() => {
    load()
  }, [load])

  async function handleToggleTool(tool: ToolSummaryResponse, enabled: boolean) {
    try {
      await setToolEnabled(tool.id, { enabled })
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function handleToggleServer(server: McpServerSummaryResponse, enabled: boolean) {
    try {
      await setMcpServerEnabled(server.id, { enabled })
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function handleDeleteTool(tool: ToolSummaryResponse) {
    try {
      await deleteTool(tool.id)
      message.success('已删除')
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  async function handleDeleteServer(server: McpServerSummaryResponse) {
    try {
      await deleteMcpServer(server.id)
      message.success('已删除')
      load()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  async function handleTestTool(tool: ToolSummaryResponse) {
    try {
      const result = await testTool(tool.id, { args: {} })
      message[result.success ? 'success' : 'warning'](
        `${result.status} · ${result.durationMs}ms`,
      )
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : '测试失败')
    }
  }

  const createMenu = {
    items: [
      { key: 'http', label: 'HTTP 工具（手动）', onClick: () => navigate('/tools/create?type=http') },
      { key: 'openapi', label: '从 OpenAPI 导入', onClick: () => navigate('/tools/create?type=http&mode=openapi') },
      { key: 'mcp', label: '连接 MCP Server', onClick: () => navigate('/tools/create?type=mcp') },
      { key: 'workflow', label: '工作流工具（P4 上线）', disabled: true },
    ],
  }

  return (
    <div className="zify-page">
      <PageHeader
        title="工具管理"
        description="HTTP 工具（手动/OpenAPI 导入）+ MCP Server 发现工具"
        extra={
          <Dropdown menu={createMenu}>
            <Button type="primary" icon={<PlusOutlined />}>
              新建工具 <DownOutlined />
            </Button>
          </Dropdown>
        }
      />

      <Spin spinning={loading}>
        <SectionTitle>HTTP 工具</SectionTitle>
        {tools.length === 0 && !loading ? (
          <Empty description="暂无 HTTP 工具" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {tools.map((tool) => (
              <Card key={tool.id} size="small">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Space>
                      <strong>{tool.name}</strong>
                      <Tag color="blue">HTTP</Tag>
                      <Tag color={STATUS_COLOR[tool.status] ?? 'default'}>{tool.status}</Tag>
                    </Space>
                    <div style={{ color: '#888', fontSize: 13 }}>{tool.description}</div>
                    <div style={{ color: '#555', fontSize: 13, fontFamily: 'monospace' }}>
                      {tool.method} {tool.endpoint}
                    </div>
                  </div>
                  <Space>
                    <Switch
                      checked={tool.enabled === 1}
                      onChange={(v) => handleToggleTool(tool, v)}
                      size="small"
                    />
                    <Button size="small" icon={<ExperimentOutlined />} onClick={() => handleTestTool(tool)}>
                      测试
                    </Button>
                    <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/tools/${tool.id}/edit`)}>
                      编辑
                    </Button>
                    <Popconfirm title="删除该工具？" onConfirm={() => handleDeleteTool(tool)}>
                      <Button size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </Space>
                </div>
              </Card>
            ))}
          </div>
        )}

        {toolTotal > pageSize && (
          <div style={{ marginTop: 12, textAlign: 'right' }}>
            <Pagination
              current={toolPage}
              pageSize={pageSize}
              total={toolTotal}
              onChange={setToolPage}
              showSizeChanger={false}
              size="small"
            />
          </div>
        )}

        <SectionTitle style={{ marginTop: 24 }}>MCP 工具</SectionTitle>
        {servers.length === 0 ? (
          <Empty description="暂无 MCP Server" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {servers.map((server) => (
              <Card key={server.id} size="small">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Space>
                      <strong>{server.name}</strong>
                      <Tag color={STATUS_COLOR[server.status] ?? 'default'}>{server.status}</Tag>
                      <Tag>{server.transportType}</Tag>
                      <span style={{ color: '#888' }}>{server.toolsCount} 个工具</span>
                    </Space>
                    <div style={{ color: '#555', fontSize: 13, fontFamily: 'monospace' }}>{server.baseUrl}</div>
                  </div>
                  <Space>
                    <Switch
                      checked={server.enabled === 1}
                      onChange={(v) => handleToggleServer(server, v)}
                      size="small"
                    />
                    <Button
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => navigate(`/tools/mcp/${server.id}/edit`)}
                    >
                      管理
                    </Button>
                    <Popconfirm title="删除该 MCP Server（其下工具一并软删）？" onConfirm={() => handleDeleteServer(server)}>
                      <Button size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </Space>
                </div>
              </Card>
            ))}
          </div>
        )}

        <SectionTitle style={{ marginTop: 24 }}>工作流工具（P4 上线）</SectionTitle>
        <Empty description="工作流发布为工具后自动注册（P4）" />
      </Spin>
    </div>
  )
}

function SectionTitle({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 8, marginTop: 4, ...style }}>{children}</div>
  )
}
