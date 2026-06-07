import axios from 'axios'
import type { ApiResponse } from '../types/api'

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
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    return Promise.reject(error)
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
