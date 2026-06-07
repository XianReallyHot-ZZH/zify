/**
 * 后端统一响应体 Result<T>
 */
export interface ApiResponse<T = void> {
  code: number
  message: string
  data: T
}

/**
 * 后端统一错误响应
 */
export type ApiErrorResponse = {
  code: string
  message: string
}

/**
 * Keyset 分页请求参数（大表：message、workflow_run、tool_call_log、trigger_log 等）
 */
export type CursorPageQuery = {
  cursorCreatedAt?: string
  cursorId?: string
  limit?: number
}

/**
 * Keyset 分页响应
 */
export type CursorPageResponse<T> = {
  records: T[]
  nextCursor: string | null
  hasMore: boolean
}

/**
 * OFFSET 分页请求参数（小表：agent、workflow、knowledge、tool、model_provider）
 */
export type OffsetPageQuery = {
  page?: number
  pageSize?: number
}

/**
 * OFFSET 分页响应
 */
export type OffsetPageResponse<T> = {
  records: T[]
  total: number
  page: number
  pageSize: number
}
