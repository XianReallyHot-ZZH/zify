import type { ReactNode } from 'react'
import { ConfigProvider } from 'antd'

const antTheme = {
  token: {
    colorPrimary: '#5B5FEF',
    colorBgBase: '#FFFFFF',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    colorBgLayout: '#F9FAFB',
    colorBorder: '#E5E7EB',
    colorBorderSecondary: '#F3F4F6',
    colorText: '#111827',
    colorTextSecondary: '#4B5563',
    colorTextTertiary: '#9CA3AF',
    colorTextDisabled: '#D1D5DB',
    borderRadius: 6,
    borderRadiusSM: 4,
    borderRadiusLG: 8,
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    fontSize: 14,
    controlHeight: 32,
  },
  components: {
    Menu: {
      itemBorderRadius: 6,
      itemMarginInline: 8,
      itemHeight: 36,
    },
    Layout: {
      siderBg: '#FFFFFF',
    },
  },
}

const Providers = ({ children }: { children: ReactNode }) => (
  <ConfigProvider theme={antTheme}>{children}</ConfigProvider>
)

export default Providers
