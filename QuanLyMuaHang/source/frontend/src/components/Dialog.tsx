import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'
export function Dialog({ title, children, onClose, busy = false }: {
  title: string; children: ReactNode; onClose: () => void; busy?: boolean
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
  return <dialog className="dialog" ref={dialog} aria-labelledby={id}
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}
    onClick={(event) => {
      if (event.target !== event.currentTarget || busy) return
      const box = event.currentTarget.getBoundingClientRect()
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose()
    }}>
    <div className="dialog-heading"><h2 id={id}>{title}</h2>
      <button className="dialog-close" aria-label="Đóng hộp thoại" type="button" disabled={busy} onClick={onClose}><Icon name="close" /></button>
    </div>
    <div className="dialog-body">{children}</div>
  </dialog>
}
