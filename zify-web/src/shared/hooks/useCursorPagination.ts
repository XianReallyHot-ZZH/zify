import { useState, useCallback, useRef } from 'react'
import type { CursorPageResponse, CursorPageQuery } from '../../types/api'

interface UseCursorPaginationOptions {
  defaultLimit?: number
}

interface UseCursorPaginationReturn<T> {
  records: T[]
  loading: boolean
  hasMore: boolean
  error: string | null
  loadMore: () => Promise<void>
  refresh: () => Promise<void>
}

/**
 * Keyset 分页 Hook
 *
 * 用法：
 * const { records, loading, hasMore, loadMore, refresh } = useCursorPagination(
 *   (query) => messageApi.listMessages(conversationId, query),
 *   { defaultLimit: 20 }
 * )
 */
export function useCursorPagination<T, Q extends Record<string, unknown>>(
  fetchFn: (query: Q & CursorPageQuery) => Promise<CursorPageResponse<T>>,
  options?: UseCursorPaginationOptions,
): UseCursorPaginationReturn<T> {
  const limit = options?.defaultLimit ?? 20

  const [records, setRecords] = useState<T[]>([])
  const [loading, setLoading] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // 当前游标，用 ref 保存避免闭包问题
  const nextCursorRef = useRef<string | null>(null)
  // 请求版本号，防止过期响应覆盖
  const versionRef = useRef(0)

  // 首次加载或重新加载
  const refresh = useCallback(async () => {
    const version = ++versionRef.current
    nextCursorRef.current = null
    setLoading(true)
    setError(null)

    try {
      const result = await fetchFn({ limit } as Q & CursorPageQuery)
      if (version !== versionRef.current) return

      setRecords(result.records)
      nextCursorRef.current = result.nextCursor
      setHasMore(result.hasMore)
    } catch (err) {
      if (version !== versionRef.current) return
      setError(err instanceof Error ? err.message : '加载失败')
    } finally {
      if (version === versionRef.current) {
        setLoading(false)
      }
    }
  }, [fetchFn, limit])

  // 加载下一页，追加到已有记录
  const loadMore = useCallback(async () => {
    if (!hasMore || loading) return

    const version = ++versionRef.current
    setLoading(true)
    setError(null)

    try {
      const query: CursorPageQuery = { limit }
      if (nextCursorRef.current) {
        query.cursor = nextCursorRef.current
      }

      const result = await fetchFn(query as Q & CursorPageQuery)
      if (version !== versionRef.current) return

      setRecords((prev) => [...prev, ...result.records])
      nextCursorRef.current = result.nextCursor
      setHasMore(result.hasMore)
    } catch (err) {
      if (version !== versionRef.current) return
      setError(err instanceof Error ? err.message : '加载失败')
    } finally {
      if (version === versionRef.current) {
        setLoading(false)
      }
    }
  }, [fetchFn, limit, hasMore, loading])

  return { records, loading, hasMore, error, loadMore, refresh }
}
