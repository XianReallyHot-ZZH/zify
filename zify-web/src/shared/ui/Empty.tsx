import { Empty as AntEmpty } from 'antd'

interface EmptyProps {
  description?: string
}

/**
 * 通用空状态
 *
 * <Empty description="暂无 Agent" />
 */
const Empty = ({ description = '暂无数据' }: EmptyProps) => (
  <div className="zify-empty">
    <AntEmpty description={description} />
  </div>
)

export default Empty
