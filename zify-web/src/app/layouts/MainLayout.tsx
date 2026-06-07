import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  MessageOutlined,
  RobotOutlined,
  ApartmentOutlined,
  BookOutlined,
  ToolOutlined,
  CloudServerOutlined,
} from '@ant-design/icons'

const { Sider, Content } = Layout

const menuItems = [
  { key: '/', label: '对话', icon: <MessageOutlined /> },
  { key: '/agents', label: 'Agents', icon: <RobotOutlined /> },
  { key: '/workflows', label: '工作流', icon: <ApartmentOutlined /> },
  { key: '/knowledge', label: '知识库', icon: <BookOutlined /> },
  { key: '/tools', label: '工具', icon: <ToolOutlined /> },
  { key: '/models', label: '模型管理', icon: <CloudServerOutlined /> },
]

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

const MainLayout = () => {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = getActiveMenuKey(location.pathname)

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
      <Content>
        <Outlet />
      </Content>
    </Layout>
  )
}

export default MainLayout
