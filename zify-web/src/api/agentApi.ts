import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type { OffsetPageResponse } from '../types/api'
import type {
  AgentListQuery,
  AgentResponse,
  CreateAgentRequest,
  UpdateAgentRequest,
  UpdateAgentStatusRequest,
} from '../types/agent'

/** 创建 Agent。 */
function createAgent(data: CreateAgentRequest): Promise<AgentResponse> {
  return apiPost('/agents', data)
}

/** Agent 列表（小表 OFFSET 分页）。 */
function listAgents(query?: AgentListQuery): Promise<OffsetPageResponse<AgentResponse>> {
  return apiGet('/agents', query as Record<string, unknown>)
}

/** Agent 详情。 */
function getAgent(id: string): Promise<AgentResponse> {
  return apiGet(`/agents/${id}`)
}

/** 更新 Agent。 */
function updateAgent(id: string, data: UpdateAgentRequest): Promise<AgentResponse> {
  return apiPut(`/agents/${id}`, data)
}

/** 软删 Agent。 */
function deleteAgent(id: string): Promise<void> {
  return apiDelete(`/agents/${id}`)
}

/** 启用/禁用 Agent。 */
function updateAgentStatus(id: string, data: UpdateAgentStatusRequest): Promise<void> {
  return apiPut(`/agents/${id}/status`, data)
}

export { createAgent, listAgents, getAgent, updateAgent, deleteAgent, updateAgentStatus }
