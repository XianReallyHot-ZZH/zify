import axios from 'axios'
import type { ApiResponse } from '../types/api'

/**
 * 业务错误，携带后端返回的错误码
 */
export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 响应拦截：解包 ApiResponse<T>，业务层直接拿到 data
// 返回值类型由 apiGet/apiPost 等函数的泛型参数控制，此处使用 any 兼容 Axios 拦截器类型
client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === 200) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return body.data as any
    }
    // 后端返回业务错误，使用 ApiError 传递 code
    return Promise.reject(new ApiError(String(body.code), body.message || '请求失败'))
  },
  (error) => {
    // 网络/超时/服务端 5xx 等非业务错误
    if (error.response) {
      const status = error.response.status
      const message = error.response.statusText || '服务器错误'
      return Promise.reject(new ApiError(`HTTP_${status}`, message))
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ERR_CANCELED') {
      return Promise.reject(new ApiError('TIMEOUT', '请求超时'))
    }
    return Promise.reject(new ApiError('NETWORK_ERROR', error.message || '网络错误'))
  },
)

/**
 * GET 请求
 */
export async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await client.get<unknown, T>(url, { params })
  return res
}

/**
 * POST 请求
 */
export async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const res = await client.post<unknown, T>(url, data)
  return res
}

/**
 * PUT 请求
 */
export async function apiPut<T>(url: string, data?: unknown): Promise<T> {
  const res = await client.put<unknown, T>(url, data)
  return res
}

/**
 * DELETE 请求
 */
export async function apiDelete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await client.delete<unknown, T>(url, { params })
  return res
}

export default client
