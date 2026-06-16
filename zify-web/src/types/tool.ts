/**
 * Tool 模块 HTTP 契约类型（对齐后端 /api/tool/** 的 request/response）。
 */

export type ToolSourceType = 'HTTP' | 'MCP' | 'WORKFLOW'
export type TransportType = 'STREAMABLE_HTTP' | 'SSE'
export type AuthType = 'NONE' | 'API_KEY' | 'BEARER'
export type ToolCallLogStatus = 'SUCCESS' | 'ERROR' | 'TIMEOUT' | 'CIRCUIT_OPEN' | 'CANCELLED'

// ─── HTTP 工具配置（config_json） ──────────────────────────

export type ParamIn = 'path' | 'query' | 'header' | 'body'

export type ParamMapping = {
  name: string
  in: ParamIn
  required?: boolean
  /** 参数类型（生成 inputSchema 用）。 */
  type?: 'string' | 'number' | 'integer' | 'boolean'
  description?: string
  /** header 参数：值运行时从 auth_config 解密注入。 */
  secret?: boolean
}

export type HeaderTemplate = {
  name: string
  /** 支持 {{auth.xxx}} / {{args.xxx}} 占位。 */
  value: string
  secret?: boolean
}

export type ToolConfig = {
  paramsMapping: ParamMapping[]
  headersTemplate: HeaderTemplate[]
  bodyTemplate: string | null
}

// ─── Tool ─────────────────────────────────────────────────

export type CreateToolRequest = {
  name: string
  description?: string
  method: string
  endpoint: string
  inputSchema: string
  configJson: ToolConfig
  authType?: AuthType
  authHeaderName?: string
  /** 明文凭据（加密存储；留空=NONE）。 */
  credential?: string
  timeoutSeconds?: number | null
  /** null → 按 method 推断。 */
  idempotent?: number | null
}

export type UpdateToolRequest = Partial<CreateToolRequest> & {
  enabled?: number
}

export type ToolListQuery = {
  page?: number
  pageSize?: number
  sourceType?: ToolSourceType
  mcpServerId?: string
  enabled?: number
}

export type ToolSummaryResponse = {
  id: string
  name: string
  description: string | null
  sourceType: ToolSourceType
  mcpServerId: string | null
  mcpServerName: string | null
  enabled: number
  method: string | null
  endpoint: string | null
  /** AVAILABLE / UNAVAILABLE */
  status: string
  createdAt: string
}

export type ToolDetailResponse = ToolSummaryResponse & {
  inputSchema: string | null
  configJson: ToolConfig | null
  authType: string
  hasAuth: boolean
  timeoutSeconds: number | null
  idempotent: number
}

export type ToolTestRequest = {
  args: Record<string, unknown>
}

export type ToolTestResult = {
  success: boolean
  status: string
  output: string
  durationMs: number
  error: string | null
  toolCallLogId: string | null
}

// ─── MCP Server ───────────────────────────────────────────

export type CreateMcpServerRequest = {
  name: string
  description?: string
  baseUrl: string
  transportType?: TransportType
  authType?: AuthType
  authHeaderName?: string
  credential?: string
}

export type UpdateMcpServerRequest = Partial<CreateMcpServerRequest> & {
  enabled?: number
}

export type McpServerListQuery = {
  page?: number
  pageSize?: number
  enabled?: number
  status?: string
}

export type McpDiscoveredToolResponse = {
  id: string
  name: string
  description: string | null
  sourceType: ToolSourceType
  enabled: number
}

export type McpServerSummaryResponse = {
  id: string
  name: string
  description: string | null
  baseUrl: string
  transportType: TransportType
  authType: AuthType
  hasAuth: boolean
  enabled: number
  status: string
  toolsCount: number
  createdAt: string
}

export type McpServerDetailResponse = McpServerSummaryResponse & {
  statusMessage: string | null
  lastConnectedAt: string | null
  discoveredTools: McpDiscoveredToolResponse[]
}

export type McpToolPreview = {
  name: string
  description: string | null
  inputSchema: string | null
}

export type McpServerTestResult = {
  success: boolean
  message: string | null
  latencyMs: number
  discoveredTools: McpToolPreview[]
}

export type UpdateEnabledRequest = {
  enabled: boolean
}

// ─── OpenAPI 导入 ─────────────────────────────────────────

export type ImportSelection = {
  operationId?: string
  method?: string
  path?: string
  name?: string
  selected?: boolean
}

export type OpenApiOperationPreviewResponse = {
  operationId: string | null
  method: string
  path: string
  summary: string | null
  suggestedName: string
  hasRequestBody: boolean
}

export type OpenApiParseResponse = {
  baseUrl: string | null
  operations: OpenApiOperationPreviewResponse[]
}

export type ImportOpenApiRequest = {
  baseUrl?: string
  authType?: AuthType
  authHeaderName?: string
  credential?: string
  operations: ImportSelection[]
  spec: string
}

export type ToolImportResult = {
  created: ToolDetailResponse[]
  skipped: string[]
}

// ─── 工具调用日志 ──────────────────────────────────────────

export type ToolCallLogQuery = {
  cursor?: string
  limit?: number
  conversationId?: string
  agentId?: string
  toolId?: string
}

export type ToolCallLogSummaryResponse = {
  id: string
  toolName: string
  sourceType: ToolSourceType
  status: ToolCallLogStatus
  durationMs: number
  createdAt: string
}

export type ToolCallLogDetailResponse = ToolCallLogSummaryResponse & {
  toolId: string
  agentId: string | null
  conversationId: string | null
  turn: number | null
  toolCallId: string | null
  input: Record<string, unknown> | null
  output: string | null
  error: string | null
}

// ─── 对话工具卡片视图（MessageView.toolCalls 用） ──────────

export type ToolCallView = {
  toolCallId: string
  toolName: string
  args: string | null
  status: string | null
  output: string | null
  durationMs: number | null
  toolCallLogId: string | null
}

// ─── Agent 工具绑定视图（复用） ────────────────────────────

export type BoundTool = {
  id: string
  name: string
  description: string | null
  sourceType: ToolSourceType
  enabled: number
  available: boolean
}

export type AgentToolsResponse = {
  toolIds: string[]
  tools: BoundTool[]
}
