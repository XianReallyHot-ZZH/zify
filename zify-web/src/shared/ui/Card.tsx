import type { ReactNode, CSSProperties } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
  style?: CSSProperties
}

/**
 * 通用卡片壳
 *
 * <Card>
 *   <Card.Header title="Agent 列表" extra={<Button>新建</Button>} />
 *   <Card.Body>...</Card.Body>
 * </Card>
 */
const Card = ({ children, className, style }: CardProps) => (
  <div className={`zify-card ${className ?? ''}`} style={style}>
    {children}
  </div>
)

const Header = ({
  title,
  description,
  extra,
}: {
  title: string
  description?: string
  extra?: ReactNode
}) => (
  <div className="zify-card-header">
    <div>
      <div className="zify-card-title">{title}</div>
      {description && <div className="zify-card-desc">{description}</div>}
    </div>
    {extra && <div>{extra}</div>}
  </div>
)

const Body = ({ children }: { children: ReactNode }) => (
  <div className="zify-card-body">{children}</div>
)

Card.Header = Header
Card.Body = Body

export default Card
