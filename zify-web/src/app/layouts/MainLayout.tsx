import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Breadcrumb, Avatar } from 'antd'
import {
  MessageOutlined,
  RobotOutlined,
  ApartmentOutlined,
  BookOutlined,
  ToolOutlined,
  CloudServerOutlined,
  UserOutlined,
} from '@ant-design/icons'

const { Sider, Content, Header } = Layout

const menuItems = [
  { key: '/', label: '对话', icon: <MessageOutlined /> },
  { key: '/agents', label: 'Agents', icon: <RobotOutlined /> },
  { key: '/workflows', label: '工作流', icon: <ApartmentOutlined /> },
  { key: '/knowledge', label: '知识库', icon: <BookOutlined /> },
  { key: '/tools', label: '工具', icon: <ToolOutlined /> },
  { key: '/models', label: '模型管理', icon: <CloudServerOutlined /> },
]

// 一级路径 -> 面包屑显示名
const breadcrumbNameMap: Record<string, string> = {
  '/': '对话',
  '/agents': 'Agents',
  '/workflows': '工作流',
  '/knowledge': '知识库',
  '/tools': '工具',
  '/models': '模型管理',
}

// 从 pathname 提取一级路径用于菜单高亮
function getActiveMenuKey(pathname: string): string {
  if (pathname === '/') return '/'
  const segments = pathname.split('/').filter(Boolean)
  return segments.length > 0 ? `/${segments[0]}` : ''
}

const MainLayout = () => {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = getActiveMenuKey(location.pathname)

  // 面包屑：根据当前路径生成
  const breadcrumbItems = (() => {
    const homeItem = { title: <span style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>首页</span> }

    if (location.pathname === '/') return [{ title: <span>对话</span> }]

    const segments = location.pathname.split('/').filter(Boolean)
    const items: { title: React.ReactNode }[] = [homeItem]

    for (let i = 0; i < segments.length; i++) {
      const isLast = i === segments.length - 1
      const isFirst = i === 0
      const fullPath = '/' + segments.slice(0, i + 1).join('/')

      if (isFirst) {
        items.push({
          title: <span style={{ cursor: 'pointer' }} onClick={() => navigate(fullPath)}>{breadcrumbNameMap[fullPath] || segments[i]}</span>,
        })
      } else if (isLast) {
        const label = segments[i] === 'create' ? '新建' : segments[i] === 'edit' ? '编辑' : '详情'
        items.push({ title: <span>{label}</span> })
      }
    }

    return items
  })()

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider width={200} theme="light">
        <div className="logo">
          <img src="/favicon.svg" alt="Zify" className="logo-icon" />
          <div className="logo-text">
            <span className="logo-brand">Zify</span>
            <span className="logo-subtitle">AI Agent Platform</span>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="main-header">
          <Breadcrumb items={breadcrumbItems} />
          <div className="header-user">
            <Avatar size={28} icon={<UserOutlined />} />
            <span className="header-username">Admin</span>
          </div>
        </Header>
        <Content className="main-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
