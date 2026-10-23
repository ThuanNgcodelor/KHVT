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

type EditRow = {
  key: string
  materialName: string
  specification: string
  unit: string
  quantity: string
  unitPrice: string
}

function rowFromItem(item: PurchaseOrder['items'][number]): EditRow {
  return {
    key: crypto.randomUUID(),
    materialName: item.materialName ?? '',
    specification: item.specification ?? '',
    unit: item.unit ?? '',
    quantity: item.quantity == null ? '' : String(item.quantity),
    unitPrice: item.unitPrice == null ? '' : String(item.unitPrice),
  }
}

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
  const [vat, setVat] = useState<string>(order.vatPercent != null ? String(order.vatPercent) : '0')
  const [preparedBy, setPreparedBy] = useState(user?.displayName || user?.email || order.preparedBy || '')
  const [note, setNote] = useState(order.note || '')
  const [autoIssue, setAutoIssue] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [rows, setRows] = useState<EditRow[]>(() => order.items.map(rowFromItem))

  const employees = useQuery({
    queryKey: ['personnel', 'active-employees'],
    queryFn: ({ signal }) => personnelApi.employees({ status: 'ACTIVE', page: 0, size: 200 }, signal)
  })

  function patchRow(key: string, changes: Partial<EditRow>) {
    setRows(prev => prev.map(r => r.key === key ? { ...r, ...changes } : r))
  }

  function addRow() {
    setRows(prev => [...prev, { key: crypto.randomUUID(), materialName: '', specification: '', unit: '', quantity: '', unitPrice: '' }])
  }

  function removeRow(key: string) {
    setRows(prev => prev.filter(r => r.key !== key))
  }

  const subtotal = rows.reduce((sum, r) => {
    const q = Number(r.quantity), p = Number(r.unitPrice)
    return sum + (q > 0 && p >= 0 && !isNaN(q) && !isNaN(p) ? q * p : 0)
  }, 0)
  const vatRate = Number(vat) || 0
  const vatAmount = subtotal * (vatRate / 100)
  const grandTotal = subtotal + vatAmount

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    // Validate items
    const validRows = rows.filter(r => r.materialName.trim())
    if (!validRows.length) { setError('Cần ít nhất 1 dòng hàng có tên vật tư.'); return }
    for (let i = 0; i < validRows.length; i++) {
      const r = validRows[i]
      const q = Number(r.quantity), p = Number(r.unitPrice)
      if (!r.quantity.trim() || isNaN(q) || q <= 0) { setError(`Dòng ${i + 1} (${r.materialName}): Số lượng cần là số dương.`); return }
      if (!r.unitPrice.trim() || isNaN(p) || p <= 0) { setError(`Dòng ${i + 1} (${r.materialName}): Đơn giá cần là số dương.`); return }
    }

    setBusy(true)
    try {
      const items = validRows.map(r => ({
        materialName: r.materialName.trim(),
        specification: r.specification.trim() || undefined,
        unit: r.unit.trim() || undefined,
        quantity: Number(r.quantity),
        unitPrice: Number(r.unitPrice),
      }))

      const newOrder = await procurementApi.reorder(order.id, {
        orderDate: orderDate || undefined,
        poNumber: poNumber.trim() || undefined,
        preparedBy: preparedBy.trim() || undefined,
        note: note.trim() || undefined,
        vatPercent: Number(vat),
        autoIssue,
        items,
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
        Sao chép và tùy chỉnh danh sách mặt hàng, số lượng, đơn giá, VAT sang một đơn mua hàng mới.
      </p>

      <div className="record-info" style={{ margin: '0 0 16px', background: 'var(--surface-subtle, #f8fafc)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color, #e2e8f0)' }}>
        <div><strong>Nhà cung cấp:</strong> {order.supplierName}</div>
      </div>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Row 1: Date + PO number + VAT */}
        <div className="form-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
          <div className="form-field">
            <label htmlFor="reorder-date"><strong>Ngày mua mới</strong></label>
            <input
              id="reorder-date"
              type="date"
              required
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="reorder-ponumber">Số PO mới (tùy chọn)</label>
            <input
              id="reorder-ponumber"
              placeholder="Để trống để tự động sinh số mới"
              value={poNumber}
              onChange={(e) => setPoNumber(e.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="reorder-vat"><strong>VAT (%)</strong></label>
            <select
              id="reorder-vat"
              value={vat}
              onChange={(e) => setVat(e.target.value)}
            >
              <option value="0">0% (Không thuế)</option>
              <option value="5">5%</option>
              <option value="8">8%</option>
              <option value="10">10%</option>
            </select>
          </div>
        </div>

        {/* Row 2: Người lập - combobox tích hợp */}
        <div className="form-field">
          <label htmlFor="reorder-prepared"><strong>Người lập đơn</strong></label>
          {employees.data?.content && employees.data.content.length > 0 ? (
            <select
              id="reorder-prepared"
              value={preparedBy}
              onChange={(e) => setPreparedBy(e.target.value)}
            >
              <option value="">-- Chọn người lập --</option>
              {employees.data.content.map((emp) => (
                <option key={emp.id} value={emp.fullName}>
                  {emp.fullName}{emp.employeeCode ? ` (${emp.employeeCode})` : ''}{emp.departmentName ? ` — ${emp.departmentName}` : ''}
                </option>
              ))}
            </select>
          ) : (
            <input
              id="reorder-prepared"
              placeholder="Nhập tên người lập…"
              value={preparedBy}
              onChange={(e) => setPreparedBy(e.target.value)}
            />
          )}
        </div>

        {/* Row 3: Ghi chú */}
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

        {/* Items table */}
        <div className="form-field">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <label style={{ fontWeight: 600, marginBottom: 0 }}>Danh sách mặt hàng ({rows.length} dòng)</label>
            <button type="button" className="secondary-button" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={addRow}>
              <Icon name="plus" size={13} /> Thêm dòng
            </button>
          </div>
          <div className="table-wrap" style={{ maxHeight: '320px', overflowY: 'auto' }}>
            <table className="po-cart" style={{ fontSize: '13px' }}>
              <thead>
                <tr>
                  <th style={{ minWidth: '160px' }}>Tên vật tư</th>
                  <th style={{ minWidth: '110px' }}>Quy cách / ĐVT</th>
                  <th style={{ minWidth: '80px' }}>Số lượng</th>
                  <th style={{ minWidth: '100px' }}>Đơn giá</th>
                  <th style={{ minWidth: '90px', textAlign: 'right' }}>Thành tiền</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, idx) => {
                  const q = Number(row.quantity), p = Number(row.unitPrice)
                  const lineTotal = q > 0 && p >= 0 && !isNaN(q) && !isNaN(p) ? q * p : null
                  return (
                    <tr key={row.key}>
                      <td>
                        <input
                          aria-label={`Tên vật tư dòng ${idx + 1}`}
                          value={row.materialName}
                          placeholder="Tên hàng hóa…"
                          onChange={e => patchRow(row.key, { materialName: e.target.value })}
                          style={{ width: '100%' }}
                        />
                        <input
                          aria-label={`Quy cách dòng ${idx + 1}`}
                          value={row.specification}
                          placeholder="Quy cách…"
                          onChange={e => patchRow(row.key, { specification: e.target.value })}
                          style={{ width: '100%', marginTop: '2px', fontSize: '11px' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Đơn vị dòng ${idx + 1}`}
                          value={row.unit}
                          placeholder="ĐVT…"
                          onChange={e => patchRow(row.key, { unit: e.target.value })}
                          style={{ width: '100%' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Số lượng dòng ${idx + 1}`}
                          inputMode="decimal"
                          value={row.quantity}
                          placeholder="0"
                          onChange={e => patchRow(row.key, { quantity: e.target.value })}
                          style={{ width: '100%', textAlign: 'right' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Đơn giá dòng ${idx + 1}`}
                          inputMode="decimal"
                          value={row.unitPrice}
                          placeholder="0"
                          onChange={e => patchRow(row.key, { unitPrice: e.target.value })}
                          style={{ width: '100%', textAlign: 'right' }}
                        />
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        {lineTotal != null
                          ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(lineTotal)
                          : '—'}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="text-button"
                          aria-label={`Bỏ dòng ${idx + 1}`}
                          onClick={() => removeRow(row.key)}
                          disabled={rows.length === 1}
                          style={{ color: 'var(--danger, #e53e3e)', padding: '0 4px' }}
                        >✕</button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {/* Financial summary */}
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-end',
            gap: '4px',
            padding: '10px 12px',
            marginTop: '8px',
            background: 'var(--surface-subtle, #f8fafc)',
            borderRadius: '6px',
            border: '1px solid var(--border-color, #e2e8f0)',
            fontSize: '13px'
          }}>
            <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted, #64748b)' }}>
              <span>Tiền hàng (chưa VAT):</span>
              <strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(subtotal)} {order.currency}</strong>
            </div>
            {vatRate > 0 && (
              <div style={{ display: 'flex', gap: '16px', color: 'var(--text-muted, #64748b)' }}>
                <span>Thuế VAT ({vatRate}%):</span>
                <span>+{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(vatAmount)} {order.currency}</span>
              </div>
            )}
            <div style={{ display: 'flex', gap: '16px', borderTop: '1px solid var(--border-color, #e2e8f0)', paddingTop: '4px', marginTop: '2px', fontSize: '14px' }}>
              <strong>Tổng thanh toán:</strong>
              <strong style={{ color: 'var(--primary, #2563eb)', fontSize: '15px' }}>
                {new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(grandTotal)} {order.currency}
              </strong>
            </div>
          </div>
        </div>

        {/* Auto issue */}
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
