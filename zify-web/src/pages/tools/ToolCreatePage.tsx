import { useSearchParams } from 'react-router-dom'
import ToolFormPage from './ToolFormPage'
import McpServerFormPage from './McpServerFormPage'

/**
 * 工具创建入口：按 ?type 切换 HTTP 工具（ToolFormPage，可再按 ?mode=openapi 进入导入向导）
 * 或 MCP Server（McpServerFormPage）。
 */
export default function ToolCreatePage() {
  const [searchParams] = useSearchParams()
  if (searchParams.get('type') === 'mcp') {
    return <McpServerFormPage />
  }
  return <ToolFormPage />
}
