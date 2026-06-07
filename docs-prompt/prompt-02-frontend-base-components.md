# Prompt 02：前端基础组件实施

你是 Zify 项目的 AI 编码助手。现在要在正式开发前端业务页面之前，补齐 `zify-web` 的前端基础组件。

## 一、项目背景

Zify 是模块化单体 AI 应用，前端工程位于 `zify-web/`，使用以下技术栈：

- React 19 + TypeScript
- Vite 8（构建）
- React Router 7（路由）
- Ant Design 6（UI 组件库）
- Zustand 5（状态管理）
- Axios（HTTP 客户端）
- @xyflow/react 12（工作流画布，本次不涉及）

项目前端工程骨架已搭好，现有文件：

```text
zify-web/src/
├── main.tsx                       # 入口：StrictMode + BrowserRouter + Providers + App
├── app/
│   ├── App.tsx                    # 渲染 router
│   ├── router.tsx                 # 6 条一级路由（缺少二级路由）
│   ├── providers.tsx              # Ant Design ConfigProvider + 主题
│   └── layouts/
│       └── MainLayout.tsx         # 左侧 Sider + Menu + Content/Outlet
├── pages/
│   ├── chat/ChatPage.tsx          # 骨架占位
│   ├── agents/AgentListPage.tsx   # 骨架占位
│   ├── workflows/WorkflowListPage.tsx  # 骨架占位
│   ├── knowledge/KnowledgeListPage.tsx # 骨架占位
│   ├── tools/ToolListPage.tsx     # 骨架占位
│   └── models/ModelPage.tsx       # 骨架占位
├── api/
│   ├── request.ts                 # Axios 封装：apiGet/apiPost/apiPut/apiDelete，10s 超时
│   └── healthApi.ts               # GET /api/health
├── types/
│   └── api.ts                     # ApiResponse<T>（只有这一个类型）
├── shared/
│   ├── hooks/
│   │   ├── useRequest.ts          # 通用异步 Hook（loading/error/data + 过期请求保护）
│   │   ├── useConfirm.ts          # 确认弹窗 Hook
│   │   └── index.ts               # barrel export
│   └── ui/
│       ├── Card.tsx               # Card / Card.Header / Card.Body
│       ├── Empty.tsx              # 空状态
│       └── index.ts               # barrel export
├── styles/
│   └── global.css                 # CSS 变量设计系统 + 全局样式
└── vite-env.d.ts
```

你必须先阅读以下文档，理解前端代码组织规范：

```text
CLAUDE.md（§4 前端硬性规则）
glm-docs/06-zify-code-organization.md（十一、前端代码组织规范）
glm-docs/10-zify-database-spec.md（六、分页查询规范）
```

## 二、硬性约束

1. 使用 Vite + React Router，禁止 Next.js。
2. 所有路由统一写在 `src/app/router.tsx`，路由动态参数使用 `:id` 语法。
3. 页面文件命名为 `XxxPage.tsx`。
4. `pages/` 不能被其他页面引用；`features/` 不能引用 `pages/`。
5. `stores/` 不发 HTTP 请求，Store 之间不互相 import。
6. API 文件 `api/*Api.ts` 对齐后端 HTTP request/response，不对齐 Facade DTO。
7. Zustand 只存跨组件状态。表单草稿、弹窗开关、列表搜索条件、单个页面独占的 loading 不进 Store。
8. 禁止在组件中直接 `axios.get()`。
9. 禁止在 `api/*Api.ts` 中写 Toast、Modal、路由跳转、Store 写入。
10. 禁止在 `types/*` 中 import React 组件或运行时代码。
11. 不引入技术栈以外的新依赖。
12. 类型文件中使用 `type` 而非 `interface`（与现有 `types/api.ts` 保持一致）。
13. 组件使用函数式组件 + hooks，不使用 class 组件（ErrorBoundary 除外）。
14. 现有文件已存在的导出内容不要删除或改名，只做增量修改。

## 三、本次任务目标

补齐前端在开发业务页面前必须具备的基础组件，范围包括：

1. 补充 HTTP 类型定义（分页类型、错误类型）
2. 增强 HTTP 请求层（错误处理）
3. 补充共享 UI 组件（Loading、ErrorBoundary）
4. 补充共享工具函数（queryString、format）
5. 补充分页 Hook（Keyset 分页、OFFSET 分页）
6. 创建全局状态 Store（appStore）
7. 补全路由表
8. 修复 MainLayout 菜单选中逻辑

本次只实现"前端基础组件"，不实现具体业务页面功能。

不要创建 Agent、Chat、Workflow、Knowledge、Tool、Model 等业务页面内容。

---

## 四、实施任务清单

### 任务 1：补充 HTTP 类型定义

#### 目标

补齐分页和错误相关的类型定义，为后续所有业务 API 提供类型基础。

#### 需要做

修改 `zify-web/src/types/api.ts`，在现有 `ApiResponse` 下方追加以下类型，不要删除或修改已有的 `ApiResponse`：

```typescript
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
```

#### 规则

- 不删除已有的 `ApiResponse`。
- 所有类型使用 `type`，不使用 `interface`，与现有代码一致。
- 不 import 任何 React 或运行时代码。

#### 验收标准

- `npm run build` 通过。
- 已有代码无编译错误。

---

### 任务 2：增强 HTTP 请求层错误处理

#### 目标

当前 `api/request.ts` 的错误处理只传递 message 字符串，业务页面拿不到结构化错误码（后端使用 `ErrorCode` 枚举返回 `code` 字段）。需要让调用方能拿到错误码做对应处理。

#### 需要做

1. 在 `zify-web/src/api/request.ts` 中新增 `ApiError` 类：

```typescript
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
```

2. 修改响应拦截器，使用 `ApiError` 替代普通 `Error`：

将现有拦截器改为：

```typescript
client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === 200) {
      return body.data
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
```

3. 在文件末尾 export `ApiError`：

```typescript
export { ApiError }
```

同时确保现有的 `apiGet`、`apiPost`、`apiPut`、`apiDelete`、`client` 都继续正常 export，不破坏已有引用。

#### 验收标准

- `ApiError` 有 `code` 和 `message` 两个属性。
- `apiGet`/`apiPost`/`apiPut`/`apiDelete` 签名和返回值不变。
- `healthApi.ts` 不需要改动，仍然正常工作。
- `npm run build` 通过。

---

### 任务 3：创建 Loading 组件

#### 目标

每个页面和列表都需要 loading 态。

#### 需要做

创建 `zify-web/src/shared/ui/Loading.tsx`：

```typescript
import { Spin } from 'antd'

interface LoadingProps {
  tip?: string
}

/**
 * 局部 Loading，用于卡片、列表等区域
 */
export function Loading({ tip = '加载中...' }: LoadingProps) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
      <Spin tip={tip} />
    </div>
  )
}

/**
 * 全页 Loading，用于页面级加载
 */
export function PageLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
      <Spin size="large" tip="加载中..." />
    </div>
  )
}
```

同时更新 `zify-web/src/shared/ui/index.ts`，在现有 export 基础上追加：

```typescript
export { Loading, PageLoading } from './Loading'
```

不要删除现有的 `Card` 和 `Empty` 导出。

#### 验收标准

- `Loading` 和 `PageLoading` 都能被 import。
- `npm run build` 通过。

---

### 任务 4：创建 ErrorBoundary 组件

#### 目标

React 渲染异常兜底，防止子组件崩溃导致整页白屏。

#### 需要做

创建 `zify-web/src/shared/ui/ErrorBoundary.tsx`：

```typescript
import { Component, type ReactNode } from 'react'
import { Button, Result } from 'antd'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

/**
 * 错误边界：捕获子组件渲染异常，展示错误提示
 */
export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }
      return (
        <Result
          status="error"
          title="页面出错了"
          subTitle={this.state.error?.message || '发生了未知错误'}
          extra={
            <Button type="primary" onClick={this.handleReset}>
              重试
            </Button>
          }
        />
      )
    }
    return this.props.children
  }
}
```

同时更新 `zify-web/src/shared/ui/index.ts`，追加：

```typescript
export { ErrorBoundary } from './ErrorBoundary'
```

#### 验收标准

- `ErrorBoundary` 可被 import。
- `npm run build` 通过。

---

### 任务 5：创建 queryString 工具函数

#### 目标

SSE URL 构建、分页游标传递、列表筛选参数都需要序列化查询参数。`glm-docs/06` 中 `engineApi.ts` 明确需要 `toQueryString`。

#### 需要做

创建 `zify-web/src/shared/utils/queryString.ts`：

```typescript
/**
 * 将对象序列化为 URL 查询字符串。
 * 自动过滤 undefined 和 null 值。
 * 数组值会重复键名（如 a=1&a=2）。
 */
export function toQueryString(
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const entries: string[] = []

  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue
    entries.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  }

  return entries.join('&')
}
```

创建 `zify-web/src/shared/utils/index.ts`（barrel export）：

```typescript
export { toQueryString } from './queryString'
```

#### 验收标准

- `toQueryString({ a: 1, b: undefined, c: 'hello' })` 返回 `"a=1&c=hello"`。
- `toQueryString({})` 返回 `""`。
- `npm run build` 通过。

---

### 任务 6：创建 format 工具函数

#### 目标

几乎所有列表页面都需要展示时间。需要相对时间（"3 分钟前"）和绝对时间格式化。

#### 需要做

创建 `zify-web/src/shared/utils/format.ts`：

```typescript
/**
 * 格式化为绝对日期时间：2026-06-07 14:30:00
 *
 * 输入支持 ISO 8601 字符串或 Date 对象。
 */
export function formatDateTime(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 格式化为绝对日期：2026-06-07
 */
export function formatDate(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 格式化为相对时间：
 * - 刚刚（<60s）
 * - N 分钟前（<60min）
 * - N 小时前（<24h）
 * - 昨天 HH:mm
 * - N 天前（<30d）
 * - MM-DD（<365d）
 * - YYYY-MM-DD（>=365d）
 */
export function formatRelativeTime(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date
  const now = Date.now()
  const diff = now - d.getTime()

  if (diff < 0) return '刚刚'

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days === 1) return `昨天 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  if (days < 30) return `${days} 天前`
  if (days < 365) {
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  }
  return formatDate(d)
}
```

更新 `zify-web/src/shared/utils/index.ts`，追加：

```typescript
export { formatDateTime, formatDate, formatRelativeTime } from './format'
```

#### 规则

- 不引入 dayjs、date-fns 等第三方日期库。使用原生 `Date` API 即可，Zify 一期面向中文用户，格式简单。
- 不处理时区转换。后端返回 UTC 时间字符串，前端按本地时区展示即可。

#### 验收标准

- `formatDateTime('2026-06-07T14:30:00Z')` 能返回一个合理格式的时间字符串。
- `formatRelativeTime` 能返回 "刚刚"、"N 分钟前" 等中文文本。
- `npm run build` 通过。

---

### 任务 7：创建 Keyset 分页 Hook

#### 目标

大表（message、tool_call_log、workflow_run、trigger_log）使用 Keyset 分页。封装为通用 Hook 避免每个页面重复写游标逻辑。

#### 需要做

创建 `zify-web/src/shared/hooks/useCursorPagination.ts`：

```typescript
import { useState, useCallback, useRef } from 'react'
import type { CursorPageResponse, CursorPageQuery } from '@/types/api'

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
```

注意：上面的实现中 `CursorPageQuery` 使用了 `cursor` 字段。请根据任务 1 中实际定义的 `CursorPageQuery` 类型调整字段名。如果任务 1 定义的是 `cursorCreatedAt` + `cursorId` 两个字段，则此处需要对应调整。

**核心思路是**：后端返回的 `nextCursor` 是一个 opaque token（比如 base64 编码的 `createdAt#id`），前端不需要解码，直接把 `nextCursor` 传给后端即可。如果后端的设计是前端分别传 `cursorCreatedAt` 和 `cursorId`，则 `CursorPageQuery` 和本 Hook 都需要对应调整。请根据 `glm-docs/10-zify-database-spec.md` 的游标分页规范来决定。

#### 验收标准

- Hook 导出 `records`、`loading`、`hasMore`、`error`、`loadMore`、`refresh`。
- `refresh()` 清空已有记录，从头加载。
- `loadMore()` 追加到已有记录。
- 使用 `versionRef` 防止过期响应覆盖。
- `npm run build` 通过。

---

### 任务 8：创建 OFFSET 分页 Hook

#### 目标

小表（agent、workflow、knowledge、tool、model_provider）使用 OFFSET 分页 + Ant Design Table。

#### 需要做

创建 `zify-web/src/shared/hooks/useOffsetPagination.ts`：

```typescript
import { useState, useCallback, useRef } from 'react'
import type { OffsetPageResponse, OffsetPageQuery } from '@/types/api'

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
```

更新 `zify-web/src/shared/hooks/index.ts`，追加：

```typescript
export { useCursorPagination } from './useCursorPagination'
export { useOffsetPagination } from './useOffsetPagination'
```

不要删除已有的 `useRequest` 和 `useConfirm` 导出。

#### 验收标准

- Hook 导出 `records`、`total`、`page`、`pageSize`、`loading`、`error`、`onChange`、`refresh`。
- `onChange` 更新页码并触发请求。
- `refresh` 用当前页码和 pageSize 重新请求。
- 使用 `versionRef` 防止过期响应覆盖。
- `npm run build` 通过。

---

### 任务 9：创建 appStore

#### 目标

管理侧边栏收起状态等全局应用状态。工作流编辑页需要收起侧边栏。

#### 需要做

创建 `zify-web/src/stores/appStore.ts`：

```typescript
import { create } from 'zustand'

interface AppState {
  sidebarCollapsed: boolean
  toggleSidebar: () => void
  setSidebarCollapsed: (collapsed: boolean) => void
}

export const useAppStore = create<AppState>((set) => ({
  sidebarCollapsed: false,
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
  setSidebarCollapsed: (collapsed: boolean) => set({ sidebarCollapsed: collapsed }),
}))
```

#### 规则

- Store 只存状态和 action，不发 HTTP 请求。
- Store 不 import 其他 Store。
- 组件读取 Store 使用 selector：`useAppStore((state) => state.sidebarCollapsed)`。

#### 验收标准

- `useAppStore` 可被 import。
- `npm run build` 通过。

---

### 任务 10：补全路由表

#### 目标

当前只有 6 条一级路由。`glm-docs/06` 定义了 12 条路由，需要补全二级路由并创建对应页面骨架。

#### 需要做

1. 创建缺失的页面骨架文件，每个页面导出默认组件，内容为最简单的占位：

```typescript
export default function XxxPage() {
  return <div>XxxPage</div>
}
```

需要创建的页面文件：

```text
zify-web/src/pages/agents/AgentFormPage.tsx
zify-web/src/pages/workflows/WorkflowEditorPage.tsx
zify-web/src/pages/knowledge/KnowledgeDetailPage.tsx
zify-web/src/pages/tools/ToolFormPage.tsx
```

每个页面的占位内容：

```typescript
// AgentFormPage.tsx
export default function AgentFormPage() {
  return <div>AgentFormPage</div>
}

// WorkflowEditorPage.tsx
export default function WorkflowEditorPage() {
  return <div>WorkflowEditorPage</div>
}

// KnowledgeDetailPage.tsx
export default function KnowledgeDetailPage() {
  return <div>KnowledgeDetailPage</div>
}

// ToolFormPage.tsx
export default function ToolFormPage() {
  return <div>ToolFormPage</div>
}
```

2. 修改 `zify-web/src/app/router.tsx`，补全路由。修改后的完整路由表：

```typescript
import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import ChatPage from '../pages/chat/ChatPage'
import AgentListPage from '../pages/agents/AgentListPage'
import AgentFormPage from '../pages/agents/AgentFormPage'
import WorkflowListPage from '../pages/workflows/WorkflowListPage'
import WorkflowEditorPage from '../pages/workflows/WorkflowEditorPage'
import KnowledgeListPage from '../pages/knowledge/KnowledgeListPage'
import KnowledgeDetailPage from '../pages/knowledge/KnowledgeDetailPage'
import ToolListPage from '../pages/tools/ToolListPage'
import ToolFormPage from '../pages/tools/ToolFormPage'
import ModelPage from '../pages/models/ModelPage'

const router = (
  <Routes>
    <Route path="/" element={<MainLayout />}>
      <Route index element={<ChatPage />} />
      <Route path="agents" element={<AgentListPage />} />
      <Route path="agents/create" element={<AgentFormPage />} />
      <Route path="agents/:id/edit" element={<AgentFormPage />} />
      <Route path="workflows" element={<WorkflowListPage />} />
      <Route path="workflows/:id" element={<WorkflowEditorPage />} />
      <Route path="knowledge" element={<KnowledgeListPage />} />
      <Route path="knowledge/:id" element={<KnowledgeDetailPage />} />
      <Route path="tools" element={<ToolListPage />} />
      <Route path="tools/create" element={<ToolFormPage />} />
      <Route path="tools/:id/edit" element={<ToolFormPage />} />
      <Route path="models" element={<ModelPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes>
)

export default router
```

#### 规则

- 所有业务页面都在 `MainLayout` 下。
- 新建页面只做最简占位，不实现业务逻辑。
- 不需要创建 `features/` 下的业务组件。

#### 验收标准

- 12 条路由全部定义。
- `npm run build` 通过。
- 每个路由对应的页面组件能正确渲染。

---

### 任务 11：修复 MainLayout 菜单选中逻辑

#### 目标

当前 `MainLayout` 使用 `selectedKeys={[location.pathname]}`，访问 `/agents/create` 或 `/agents/abc/edit` 时，侧边栏菜单不会高亮 "Agents"。需要从 `location.pathname` 提取一级路径做匹配。

#### 需要做

修改 `zify-web/src/app/layouts/MainLayout.tsx`。

将菜单选中逻辑改为从 pathname 提取一级路径：

```typescript
// 从 pathname 提取一级路径用于菜单高亮
// "/" -> "/"
// "/agents" -> "/agents"
// "/agents/create" -> "/agents"
// "/agents/abc/edit" -> "/agents"
// "/workflows/123" -> "/workflows"
function getActiveMenuKey(pathname: string): string {
  if (pathname === '/') return '/'
  const segments = pathname.split('/').filter(Boolean)
  return segments.length > 0 ? `/${segments[0]}` : '/'
}
```

在组件中使用：

```typescript
const selectedKey = getActiveMenuKey(location.pathname)
// ...
<Menu selectedKeys={[selectedKey]} ... />
```

不要改动其他已有的布局结构（Sider 宽度、菜单项、Layout 结构）。

#### 验收标准

- 访问 `/agents` 时 "Agents" 菜单高亮。
- 访问 `/agents/create` 时 "Agents" 菜单高亮。
- 访问 `/agents/xxx/edit` 时 "Agents" 菜单高亮。
- 访问 `/` 时 "对话" 菜单高亮。
- 访问 `/workflows/xxx` 时 "工作流" 菜单高亮。
- `npm run build` 通过。

---

## 五、禁止实现的内容

本次不要做：

1. 不要实现 Agent 列表、创建、编辑页面的业务内容。
2. 不要实现 Chat 对话页面的业务内容。
3. 不要实现 Workflow 编辑页面的画布。
4. 不要实现 Knowledge 文档上传、解析。
5. 不要实现 Tool 配置页面。
6. 不要实现 Model Provider 管理页面。
7. 不要创建 `api/agentApi.ts`、`api/chatApi.ts` 等业务 API 文件。
8. 不要创建 `types/agent.ts`、`types/chat.ts` 等业务类型文件。
9. 不要创建 `features/` 下的业务组件。
10. 不要修改 `api/healthApi.ts`。
11. 不要修改 `styles/global.css`。
12. 不要修改 `vite.config.ts`。
13. 不要修改 `providers.tsx`。
14. 不要安装新依赖。

---

## 六、实施顺序

严格按以下顺序执行：

1. 先阅读项目现有前端代码，确认当前文件状态。
2. 补充 `types/api.ts` 分页类型（任务 1）。
3. 增强 `api/request.ts` 错误处理（任务 2）。
4. 创建 `shared/ui/Loading.tsx`（任务 3）。
5. 创建 `shared/ui/ErrorBoundary.tsx`（任务 4）。
6. 创建 `shared/utils/queryString.ts`（任务 5）。
7. 创建 `shared/utils/format.ts`（任务 6）。
8. 创建 `shared/hooks/useCursorPagination.ts`（任务 7）。
9. 创建 `shared/hooks/useOffsetPagination.ts`（任务 8）。
10. 创建 `stores/appStore.ts`（任务 9）。
11. 创建页面骨架文件（任务 10 的第 1 步）。
12. 修改 `app/router.tsx` 补全路由（任务 10 的第 2 步）。
13. 修复 `MainLayout.tsx` 菜单选中逻辑（任务 11）。
14. 运行构建验证。
15. 汇总修改内容。

---

## 七、验证命令

完成后必须运行：

```bash
cd zify-web
npm run build
```

确保 TypeScript 编译和 Vite 构建都通过，无类型错误、无构建错误。

如发现 lint 错误（`npm run lint`），也需要修复。

---

## 八、输出要求

完成后输出：

1. 修改了哪些文件（逐一列出）。
2. 新增了哪些文件（逐一列出）。
3. 每个基础组件解决什么问题（一句话）。
4. 是否引入了新依赖（应该没有）。
5. `npm run build` 结果。
6. `npm run lint` 结果（如有问题）。
7. 是否有未完成事项。
8. 后续开发业务页面时的注意事项。

如果构建失败，不要隐瞒，必须贴出完整错误信息并说明原因和修复方案。
