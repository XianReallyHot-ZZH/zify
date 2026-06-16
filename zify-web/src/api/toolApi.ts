import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type { CursorPageResponse, OffsetPageResponse } from '../types/api'
import type {
  CreateMcpServerRequest,
  CreateToolRequest,
  ImportOpenApiRequest,
  McpServerDetailResponse,
  McpServerListQuery,
  McpServerSummaryResponse,
  McpServerTestResult,
  OpenApiParseResponse,
  ToolCallLogDetailResponse,
  ToolCallLogQuery,
  ToolCallLogSummaryResponse,
  ToolDetailResponse,
  ToolImportResult,
  ToolListQuery,
  ToolSummaryResponse,
  ToolTestRequest,
  ToolTestResult,
  UpdateEnabledRequest,
  UpdateMcpServerRequest,
  UpdateToolRequest,
} from '../types/tool'

// ─── Tool（HTTP 工具 CRUD + OpenAPI 导入 + 测试） ──────────

function createTool(data: CreateToolRequest): Promise<ToolDetailResponse> {
  return apiPost('/tool/tools', data)
}

function listTools(query?: ToolListQuery): Promise<OffsetPageResponse<ToolSummaryResponse>> {
  return apiGet('/tool/tools', query as Record<string, unknown>)
}

function getTool(id: string): Promise<ToolDetailResponse> {
  return apiGet(`/tool/tools/${id}`)
}

function updateTool(id: string, data: UpdateToolRequest): Promise<ToolDetailResponse> {
  return apiPut(`/tool/tools/${id}`, data)
}

function deleteTool(id: string): Promise<void> {
  return apiDelete(`/tool/tools/${id}`)
}

function setToolEnabled(id: string, data: UpdateEnabledRequest): Promise<ToolDetailResponse> {
  return apiPut(`/tool/tools/${id}/enabled`, data)
}

function testTool(id: string, data: ToolTestRequest): Promise<ToolTestResult> {
  return apiPost(`/tool/tools/${id}/test`, data)
}

function parseOpenApi(spec: string): Promise<OpenApiParseResponse> {
  return apiPost('/tool/tools/parse-openapi', { spec })
}

function importOpenApi(data: ImportOpenApiRequest): Promise<ToolImportResult> {
  return apiPost('/tool/tools/import-openapi', data)
}

// ─── MCP Server ───────────────────────────────────────────

function createMcpServer(data: CreateMcpServerRequest): Promise<McpServerDetailResponse> {
  return apiPost('/tool/mcp-servers', data)
}

function listMcpServers(query?: McpServerListQuery): Promise<OffsetPageResponse<McpServerSummaryResponse>> {
  return apiGet('/tool/mcp-servers', query as Record<string, unknown>)
}

function getMcpServer(id: string): Promise<McpServerDetailResponse> {
  return apiGet(`/tool/mcp-servers/${id}`)
}

function updateMcpServer(id: string, data: UpdateMcpServerRequest): Promise<McpServerDetailResponse> {
  return apiPut(`/tool/mcp-servers/${id}`, data)
}

function deleteMcpServer(id: string): Promise<void> {
  return apiDelete(`/tool/mcp-servers/${id}`)
}

function setMcpServerEnabled(id: string, data: UpdateEnabledRequest): Promise<McpServerDetailResponse> {
  return apiPut(`/tool/mcp-servers/${id}/enabled`, data)
}

function testMcpServer(id: string): Promise<McpServerTestResult> {
  return apiPost(`/tool/mcp-servers/${id}/test`)
}

function testMcpServerConfig(data: CreateMcpServerRequest): Promise<McpServerTestResult> {
  return apiPost('/tool/mcp-servers/test', data)
}

function refreshMcpServer(id: string): Promise<McpServerDetailResponse> {
  return apiPost(`/tool/mcp-servers/${id}/refresh`)
}

// ─── 工具调用日志 ──────────────────────────────────────────

function getCallLog(id: string): Promise<ToolCallLogDetailResponse> {
  return apiGet(`/tool/call-logs/${id}`)
}

function listCallLogs(query?: ToolCallLogQuery): Promise<CursorPageResponse<ToolCallLogSummaryResponse>> {
  return apiGet('/tool/call-logs', query as Record<string, unknown>)
}

export {
  createTool,
  listTools,
  getTool,
  updateTool,
  deleteTool,
  setToolEnabled,
  testTool,
  parseOpenApi,
  importOpenApi,
  createMcpServer,
  listMcpServers,
  getMcpServer,
  updateMcpServer,
  deleteMcpServer,
  setMcpServerEnabled,
  testMcpServer,
  testMcpServerConfig,
  refreshMcpServer,
  getCallLog,
  listCallLogs,
}
