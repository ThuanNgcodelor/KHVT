import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

export type DialogSize = 'normal' | 'wide' | 'extra-wide' | 'full'

export function Dialog({
  title,
  children,
  onClose,
  busy = false,
  size = 'normal',
  confirmClose = false,
  disableBackdropClick = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
  size?: DialogSize
  confirmClose?: boolean | (() => boolean)
  disableBackdropClick?: boolean
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const id = useId()

  useEffect(() => {
    const element = dialog.current!
    const previous = document.activeElement as HTMLElement | null
    element.showModal()
    element.querySelector<HTMLElement>('input:not(:disabled), select:not(:disabled), [data-autofocus]')?.focus()
    return () => { element.close(); if (previous?.isConnected) previous.focus() }
  }, [])

  function requestClose() {
    if (busy) return
    const shouldConfirm = typeof confirmClose === 'function' ? confirmClose() : confirmClose
    if (shouldConfirm) {
      const ok = window.confirm('Bạn có chắc chắn muốn đóng? Dữ liệu đang nhập hoặc chỉnh sửa sẽ không được lưu.')
      if (!ok) return
    }
    onClose()
  }

  const sizeClass = size === 'wide'
    ? 'dialog-wide'
    : size === 'extra-wide'
    ? 'dialog-extra-wide'
    : size === 'full'
    ? 'dialog-full'
    : ''

  return (
    <dialog
      className={`dialog ${sizeClass}`.trim()}
      ref={dialog}
      aria-labelledby={id}
      onCancel={(event) => {
        event.preventDefault()
        requestClose()
      }}
      onClick={(event) => {
        if (event.target !== event.currentTarget || busy) return
        if (disableBackdropClick) return
        const box = event.currentTarget.getBoundingClientRect()
        if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) {
          requestClose()
        }
      }}
    >
      <div className="dialog-heading">
        <h2 id={id}>{title}</h2>
        <button
          className="dialog-close"
          aria-label="Đóng hộp thoại"
          type="button"
          disabled={busy}
          onClick={requestClose}
        >
          <Icon name="close" />
        </button>
      </div>
      <div className="dialog-body">{children}</div>
    </dialog>
  )
}
