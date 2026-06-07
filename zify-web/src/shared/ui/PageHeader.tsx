import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  description?: string
  extra?: ReactNode
}

/**
 * 页面标题区域：标题 + 描述 + 右侧操作区
 *
 * <PageHeader title="Agents" description="管理你的 AI Agent" />
 * <PageHeader title="Agents" description="..." extra={<Button>新建</Button>} />
 */
export function PageHeader({ title, description, extra }: PageHeaderProps) {
  return (
    <div className="zify-page-header">
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <h1 className="zify-page-title">{title}</h1>
          {description && <p className="zify-page-desc">{description}</p>}
        </div>
        {extra && <div>{extra}</div>}
      </div>
    </div>
  )
}
