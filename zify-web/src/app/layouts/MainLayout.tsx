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

const MainLayout = () => {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider width={200} theme="light">
        <div className="logo">Zify</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
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
