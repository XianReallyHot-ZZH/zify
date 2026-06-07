import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu } from 'antd'

const { Sider, Content } = Layout

const menuItems = [
  { key: '/', label: '对话' },
  { key: '/agents', label: 'Agents' },
  { key: '/workflows', label: '工作流' },
  { key: '/knowledge', label: '知识库' },
  { key: '/tools', label: '工具' },
  { key: '/models', label: '模型管理' },
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
        <div className="logo">Zify</div>
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
