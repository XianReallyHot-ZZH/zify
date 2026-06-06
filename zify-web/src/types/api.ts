/**
 * 后端统一响应体 Result<T>
 */
export interface ApiResponse<T = void> {
  code: number
  message: string
  data: T
}
