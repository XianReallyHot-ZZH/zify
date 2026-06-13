import { Input } from 'antd'

const { TextArea } = Input

type PromptEditorProps = {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
}

/**
 * System Prompt 多行编辑器。
 */
function PromptEditor({ value, onChange, placeholder }: PromptEditorProps) {
  return (
    <TextArea
      value={value}
      onChange={(e) => onChange?.(e.target.value)}
      placeholder={placeholder ?? '定义 Agent 的角色、行为与回答风格（可空，按原文透传给 LLM）'}
      autoSize={{ minRows: 8, maxRows: 20 }}
      maxLength={5000}
      showCount
    />
  )
}

export default PromptEditor
