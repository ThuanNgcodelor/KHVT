import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Dialog } from '../../../components/Dialog'
import { Icon } from '../../../components/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { personnelApi } from '../../personnel/personnelApi'
import { procurementApi } from '../procurementApi'
import { formatMoney } from '../format'
import { saveDownload } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import type { PurchaseOrder } from '../types'

export function ReorderDialog({
  order,
  onClose,
  onSuccess
}: {
  order: PurchaseOrder
  onClose: () => void
  onSuccess: (newOrder: PurchaseOrder) => void
}) {
  const { user } = useAuth()
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())

  const [orderDate, setOrderDate] = useState(today)
  const [poNumber, setPoNumber] = useState('')
  const [preparedBy, setPreparedBy] = useState(user?.displayName || user?.email || order.preparedBy || '')
  const [note, setNote] = useState(order.note || '')
  const [autoIssue, setAutoIssue] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const employees = useQuery({
    queryKey: ['personnel', 'active-employees'],
    queryFn: ({ signal }) => personnelApi.employees({ status: 'ACTIVE', page: 0, size: 200 }, signal)
  })

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const newOrder = await procurementApi.reorder(order.id, {
        orderDate: orderDate || undefined,
        poNumber: poNumber.trim() || undefined,
        preparedBy: preparedBy.trim() || undefined,
        note: note.trim() || undefined,
        autoIssue
      })

      if (autoIssue) {
        try {
          const pdfFile = await procurementApi.pdf(newOrder.id)
          saveDownload(pdfFile)
        } catch (pdfErr) {
          console.warn('PDF auto-download error:', pdfErr)
        }
      }

      onSuccess(newOrder)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog title={`Mua lại theo đơn ${order.poNumber}`} onClose={onClose} busy={busy}>
      <p className="dialog-description">
        Sao chép toàn bộ danh sách {order.items.length} mặt hàng, đơn giá, nhà cung cấp và thuế suất VAT sang một đơn mua hàng mới.
      </p>

      <div className="record-info" style={{ margin: '12px 0 16px', background: 'var(--surface-subtle, #f8fafc)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color, #e2e8f0)' }}>
        <div><strong>Nhà cung cấp:</strong> {order.supplierName}</div>
        <div><strong>Tổng tiền đơn:</strong> {formatMoney(order.grandTotal, order.currency)} {order.currency} ({order.items.length} dòng hàng)</div>
      </div>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <div className="form-grid">
          <div className="form-field">
            <label htmlFor="reorder-date">
              <strong>Ngày mua mới</strong>
            </label>
            <input
              id="reorder-date"
              type="date"
              required
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
            />
          </div>

          <div className="form-field">
            <label htmlFor="reorder-ponumber">
              Số PO mới (tùy chọn)
            </label>
            <input
              id="reorder-ponumber"
              placeholder="Để trống để tự động sinh số mới"
              value={poNumber}
              onChange={(e) => setPoNumber(e.target.value)}
            />
          </div>
        </div>

        <div className="form-field">
          <label htmlFor="reorder-prepared">
            <strong>Người lập đơn</strong>
          </label>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              id="reorder-prepared"
              list="reorder-employee-list"
              placeholder="Chọn hoặc nhập tên người lập…"
              value={preparedBy}
              onChange={(e) => setPreparedBy(e.target.value)}
              style={{ flex: 1 }}
            />
            {employees.data?.content && (
              <select
                aria-label="Chọn người lập từ danh sách nhân viên"
                value=""
                onChange={(e) => {
                  if (e.target.value) setPreparedBy(e.target.value)
                }}
                style={{ width: 'auto' }}
              >
                <option value="">Chọn từ DS…</option>
                {employees.data.content.map((emp) => (
                  <option key={emp.id} value={emp.fullName}>
                    {emp.fullName} {emp.employeeCode ? `(${emp.employeeCode})` : ''}
                  </option>
                ))}
              </select>
            )}
          </div>
          <datalist id="reorder-employee-list">
            {employees.data?.content.map((emp) => (
              <option key={emp.id} value={emp.fullName}>
                {emp.employeeCode} — {emp.departmentName || ''}
              </option>
            ))}
          </datalist>
        </div>

        <div className="form-field">
          <label htmlFor="reorder-note">Ghi chú</label>
          <textarea
            id="reorder-note"
            rows={2}
            placeholder="Ghi chú đơn mua mới (nếu có)…"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>

        <div className="form-field" style={{ marginTop: '4px' }}>
          <label style={{ display: 'flex', alignItems: 'flex-start', gap: '8px', cursor: 'pointer', fontWeight: 500 }}>
            <input
              type="checkbox"
              checked={autoIssue}
              onChange={(e) => setAutoIssue(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <span>
              <strong>Phát hành đơn và tải PDF ngay lập tức</strong>
              <span className="field-help" style={{ display: 'block', marginTop: '2px' }}>
                Áp dụng cho các đơn mua lại y chang số lượng và giá mà không cần qua bản nháp.
              </span>
            </span>
          </label>
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}

        <div className="dialog-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            Hủy
          </button>
          <button type="submit" className="primary-button" disabled={busy}>
            <Icon name="check" size={16} />
            {busy ? 'Đang tạo đơn mua…' : 'Xác nhận mua lại đơn'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
