import { useState, useCallback } from 'react'

interface ConfirmOptions {
  title: string
  content: string
  okText?: string
  cancelText?: string
}

interface ConfirmState extends ConfirmOptions {
  open: boolean
  onConfirm: () => void
}

/**
 * 通用确认弹窗 Hook
 *
 * const { confirm, confirmProps } = useConfirm()
 *
 * // 触发确认
 * confirm({ title: '删除确认', content: '确定删除？' }, () => doDelete(id))
 *
 * // JSX 中挂载
 * <Modal {...confirmProps} />
 */
export function useConfirm() {
  const [state, setState] = useState<ConfirmState>({
    open: false,
    title: '',
    content: '',
    onConfirm: () => {},
  })

  const confirm = useCallback((options: ConfirmOptions, onConfirm: () => void) => {
    setState({ ...options, open: true, onConfirm })
  }, [])

  const handleOk = useCallback(() => {
    state.onConfirm()
    setState((prev) => ({ ...prev, open: false }))
  }, [state])

  const handleCancel = useCallback(() => {
    setState((prev) => ({ ...prev, open: false }))
  }, [])

  const confirmProps = {
    open: state.open,
    title: state.title,
    content: state.content,
    okText: state.okText ?? '确定',
    cancelText: state.cancelText ?? '取消',
    onOk: handleOk,
    onCancel: handleCancel,
    okButtonProps: { danger: true },
  }

  return { confirm, confirmProps }
}
