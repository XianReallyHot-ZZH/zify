import { useState } from 'react'
import { Button, Input } from 'antd'
import { SendOutlined, StopOutlined } from '@ant-design/icons'

type MessageInputProps = {
  onSend: (content: string) => void
  onStop: () => void
  isStreaming: boolean
  disabled?: boolean
}

/**
 * 消息输入框：Enter 发送，Shift+Enter 换行；流式中显示「停止」。
 */
function MessageInput({ onSend, onStop, isStreaming, disabled }: MessageInputProps) {
  const [value, setValue] = useState('')

  function submit() {
    const trimmed = value.trim()
    if (!trimmed || disabled || isStreaming) return
    onSend(trimmed)
    setValue('')
  }

  return (
    <div style={{ display: 'flex', gap: 8, padding: '12px 16px', borderTop: '1px solid #f0f0f0' }}>
      <Input.TextArea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={disabled ? '请先选择 Agent 开始对话' : '输入消息，Enter 发送，Shift+Enter 换行'}
        autoSize={{ minRows: 1, maxRows: 6 }}
        onPressEnter={(e) => {
          if (!e.shiftKey) {
            e.preventDefault()
            submit()
          }
        }}
        disabled={disabled}
        style={{ flex: 1 }}
      />
      {isStreaming ? (
        <Button danger icon={<StopOutlined />} onClick={onStop}>
          停止
        </Button>
      ) : (
        <Button type="primary" icon={<SendOutlined />} onClick={submit} disabled={disabled || !value.trim()}>
          发送
        </Button>
      )}
    </div>
  )
}

export default MessageInput
