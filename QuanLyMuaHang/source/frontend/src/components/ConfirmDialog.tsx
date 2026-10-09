import { Dialog } from './Dialog'
export function ConfirmDialog({ title, message, confirmLabel, onConfirm, onClose, busy, error, danger = true }: {
  title: string; message: string; confirmLabel: string; onConfirm: () => void; onClose: () => void
  busy: boolean; error?: string; danger?: boolean
}) {
  return <Dialog title={title} busy={busy} onClose={onClose}>
    <p className="dialog-description">{message}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dialog-actions">
      <button type="button" data-autofocus className="secondary-button" disabled={busy} onClick={onClose}>Quay lại</button>
      <button type="button" className={danger ? 'danger-button' : 'primary-button'} disabled={busy} onClick={onConfirm}>{busy ? 'Đang xử lý…' : confirmLabel}</button>
    </div>
  </Dialog>
}
