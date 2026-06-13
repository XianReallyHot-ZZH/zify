/**
 * Agent 模块 HTTP 契约类型（对齐后端 /api/agents 的 request/response）。
 */

export type AgentType = 'REACT' | 'WORKFLOW'
export type AgentStatus = 'ACTIVE' | 'INACTIVE'

/** Agent 详情响应（列表也复用此类型）。 */
export type AgentResponse = {
  id: string
  name: string
  description: string | null
  agentType: string
  status: string
  systemPrompt: string | null
  modelId: string | null
  modelName: string | null
  createdAt: string
  updatedAt: string
}

export type CreateAgentRequest = {
  name: string
  description?: string
  /** P1 仅 REACT */
  agentType: string
  systemPrompt?: string
  modelId: string
}

/** 更新 Agent（不含 agentType，创建后不可修改）。 */
export type UpdateAgentRequest = {
  name?: string
  description?: string
  systemPrompt?: string
  modelId?: string
  status?: string
}

export type UpdateAgentStatusRequest = {
  status: string
}

/** Agent 列表查询（小表 OFFSET 分页）。 */
export type AgentListQuery = {
  page?: number
  pageSize?: number
  name?: string
  agentType?: string
  status?: string
}
