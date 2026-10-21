import { useState } from 'react'
import { Dialog } from '../../../components/Dialog'

export function UnlockOrderDialog({
  orderNumber,
  onClose,
  onConfirm,
  busy,
  error
}: {
  orderNumber: string
  onClose: () => void
  onConfirm: (reason: string) => void
  busy: boolean
  error?: string
}) {
  const [reason, setReason] = useState('')

  return (
    <Dialog title={`Mở khóa đơn mua ${orderNumber}`} onClose={onClose} busy={busy}>
      <p className="dialog-description">
        Đơn mua đã được phát hành gửi nhà cung cấp. Việc mở khóa sẽ đưa đơn về trạng thái dự thảo để lập phiên bản sửa đổi mới (Revision) và ghi nhận lý do vào nhật ký kiểm toán hệ thống.
      </p>

      <div className="form-field" style={{ marginTop: '14px' }}>
        <label htmlFor="unlock-reason">
          <strong>Lý do sửa đổi (Bắt buộc)</strong>
        </label>
        <textarea
          id="unlock-reason"
          rows={3}
          style={{ width: '100%', resize: 'vertical' }}
          placeholder="Nhập lý do cần sửa đơn (VD: Điều chỉnh quy cách theo yêu cầu NCC, cập nhật số lượng thực tế...)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>

      {error && <p className="form-error" role="alert" style={{ marginTop: '10px' }}>{error}</p>}

      <div className="dialog-actions">
        <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
          Hủy
        </button>
        <button
          type="button"
          className="primary-button"
          disabled={busy || !reason.trim()}
          onClick={() => onConfirm(reason.trim())}
        >
          {busy ? 'Đang mở khóa…' : 'Xác nhận mở khóa'}
        </button>
      </div>
    </Dialog>
  )
}
