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
