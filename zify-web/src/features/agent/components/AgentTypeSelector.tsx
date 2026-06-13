import { Radio } from 'antd'

type AgentTypeSelectorProps = {
  value?: string
  onChange?: (type: string) => void
  disabled?: boolean
}

/**
 * Agent 类型选择器：P1 仅 REACT 可选，WORKFLOW 禁用并标注 P4。
 */
function AgentTypeSelector({ value, onChange, disabled }: AgentTypeSelectorProps) {
  return (
    <Radio.Group value={value} onChange={(e) => onChange?.(e.target.value)} disabled={disabled}>
      <Radio value="REACT">REACT（智能体）</Radio>
      <Radio value="WORKFLOW" disabled>
        WORKFLOW（P4 上线）
      </Radio>
    </Radio.Group>
  )
}

export default AgentTypeSelector
