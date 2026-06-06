import { useState, useCallback, useRef } from 'react'

interface UseRequestState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

interface UseRequestReturn<T> extends UseRequestState<T> {
  run: (...args: unknown[]) => Promise<T | null>
}

/**
 * 通用请求 Hook：管理 loading / error / data
 *
 * const { data, loading, error, run } = useRequest(() => apiGet<User>('/users/1'))
 *
 * // 触发请求
 * useEffect(() => { run() }, [])
 *
 * // 传参触发
 * const search = (keyword: string) => run(keyword)
 */
export function useRequest<T>(
  requestFn: (...args: unknown[]) => Promise<T>,
): UseRequestReturn<T> {
  const [state, setState] = useState<UseRequestState<T>>({
    data: null,
    loading: false,
    error: null,
  })

  // 用 ref 追踪最新请求，避免过期响应覆盖
  const requestIdRef = useRef(0)

  const run = useCallback(
    async (...args: unknown[]): Promise<T | null> => {
      const requestId = ++requestIdRef.current
      setState((prev) => ({ ...prev, loading: true, error: null }))

      try {
        const data = await requestFn(...args)
        if (requestId !== requestIdRef.current) return null
        setState({ data, loading: false, error: null })
        return data
      } catch (err) {
        if (requestId !== requestIdRef.current) return null
        const message = err instanceof Error ? err.message : '请求失败'
        setState((prev) => ({ ...prev, loading: false, error: message }))
        return null
      }
    },
    [requestFn],
  )

  return { ...state, run }
}
