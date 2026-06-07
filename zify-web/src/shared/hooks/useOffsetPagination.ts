import { useState, useCallback, useRef } from 'react'
import type { OffsetPageResponse, OffsetPageQuery } from '../../types/api'

interface UseOffsetPaginationReturn<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  error: string | null
  onChange: (page: number, pageSize: number) => void
  refresh: () => Promise<void>
}

/**
 * OFFSET 分页 Hook
 *
 * 用法：
 * const { records, total, page, pageSize, loading, onChange, refresh } = useOffsetPagination(
 *   (query) => agentApi.listAgents(query),
 * )
 */
export function useOffsetPagination<T, Q extends Record<string, unknown>>(
  fetchFn: (query: Q & OffsetPageQuery) => Promise<OffsetPageResponse<T>>,
  defaultPageSize = 20,
): UseOffsetPaginationReturn<T> {
  const [records, setRecords] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(defaultPageSize)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const versionRef = useRef(0)

  const doFetch = useCallback(
    async (p: number, ps: number) => {
      const version = ++versionRef.current
      setLoading(true)
      setError(null)

      try {
        const result = await fetchFn({ page: p, pageSize: ps } as Q & OffsetPageQuery)
        if (version !== versionRef.current) return

        setRecords(result.records)
        setTotal(result.total)
        setPage(p)
        setPageSize(ps)
      } catch (err) {
        if (version !== versionRef.current) return
        setError(err instanceof Error ? err.message : '加载失败')
      } finally {
        if (version === versionRef.current) {
          setLoading(false)
        }
      }
    },
    [fetchFn],
  )

  const onChange = useCallback(
    (newPage: number, newPageSize: number) => {
      doFetch(newPage, newPageSize)
    },
    [doFetch],
  )

  const refresh = useCallback(() => {
    return doFetch(page, pageSize)
  }, [doFetch, page, pageSize])

  return { records, total, page, pageSize, loading, error, onChange, refresh }
}
