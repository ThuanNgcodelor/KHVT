import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Dialog } from '../../../components/Dialog'
import { Icon } from '../../../components/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { personnelApi } from '../../personnel/personnelApi'
import { procurementApi } from '../procurementApi'
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

  // Determine if the user has made any modifications
  const isDirty = useMemo(() => {
    if (orderDate !== today) return true
    if (poNumber.trim() !== '') return true
    if (vat !== (order.vatPercent != null ? String(order.vatPercent) : '0')) return true
    if (preparedBy !== (user?.displayName || user?.email || order.preparedBy || '')) return true
    if (note !== (order.note || '')) return true
    if (!autoIssue) return true
    if (rows.length !== order.items.length) return true
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i]
      const orig = order.items[i]
      if (!orig) return true
      if (r.materialName !== (orig.materialName ?? '')) return true
      if (r.specification !== (orig.specification ?? '')) return true
      if (r.unit !== (orig.unit ?? '')) return true
      if (r.quantity !== (orig.quantity == null ? '' : String(orig.quantity))) return true
      if (r.unitPrice !== (orig.unitPrice == null ? '' : String(orig.unitPrice))) return true
    }
    return false
  }, [orderDate, today, poNumber, vat, order.vatPercent, preparedBy, user, order.preparedBy, note, order.note, autoIssue, rows, order.items])

  function patchRow(key: string, changes: Partial<EditRow>) {
    setRows(prev => prev.map(r => r.key === key ? { ...r, ...changes } : r))
  }

  function addRow() {
    setRows(prev => [
      ...prev,
      {
        key: crypto.randomUUID(),
        materialName: '',
        specification: '',
        unit: '',
        quantity: '',
        unitPrice: ''
      }
    ])
  }

  function removeRow(key: string) {
    if (rows.length <= 1) return
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

    const validRows = rows.filter(r => r.materialName.trim())
    if (!validRows.length) {
      setError('Cần ít nhất 1 dòng hàng có tên vật tư.')
      return
    }
    for (let i = 0; i < validRows.length; i++) {
      const r = validRows[i]
      const q = Number(r.quantity), p = Number(r.unitPrice)
      if (!r.quantity.trim() || isNaN(q) || q <= 0) {
        setError(`Dòng ${i + 1} (${r.materialName}): Số lượng phải là số lớn hơn 0.`)
        return
      }
      if (!r.unitPrice.trim() || isNaN(p) || p <= 0) {
        setError(`Dòng ${i + 1} (${r.materialName}): Đơn giá phải là số lớn hơn 0.`)
        return
      }
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
          console.warn('PDF auto-download warning:', pdfErr)
        }
      }

      onSuccess(newOrder)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog
      title={`Mua lại theo đơn ${order.poNumber}`}
      size="extra-wide"
      disableBackdropClick
      confirmClose={isDirty}
      busy={busy}
      onClose={onClose}
    >
      <p className="dialog-description" style={{ marginBottom: '14px' }}>
        Sao chép toàn bộ danh sách mặt hàng, số lượng, đơn giá từ đơn cũ. Bạn có thể tự do thêm, bớt hoặc điều chỉnh các dòng hàng trước khi tạo đơn mới.
      </p>

      {/* Supplier & Source PO banner */}
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '12px',
        padding: '12px 16px',
        background: 'var(--surface-subtle, #f4f7f4)',
        borderRadius: '6px',
        border: '1px solid var(--border-color, #d2ddd3)',
        marginBottom: '16px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '11px', textTransform: 'uppercase', fontWeight: 600, color: 'var(--muted, #6a806f)' }}>Nhà cung cấp:</span>
          <strong style={{ fontSize: '14px', color: 'var(--brand, #1f513f)' }}>{order.supplierName}</strong>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '12px', color: 'var(--muted, #6a806f)' }}>
          <span>Đơn gốc: <strong>{order.poNumber}</strong> (Rev {order.revision})</span>
          <span>Tiền tệ: <strong style={{ color: '#273d2e' }}>{order.currency}</strong></span>
        </div>
      </div>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Row 1: Date + PO number + VAT + PreparedBy */}
        <div className="form-grid" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: '12px' }}>
          <div className="form-field" style={{ marginBottom: 0 }}>
            <label htmlFor="reorder-date"><strong>Ngày mua mới *</strong></label>
            <input
              id="reorder-date"
              type="date"
              required
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
            />
          </div>
          <div className="form-field" style={{ marginBottom: 0 }}>
            <label htmlFor="reorder-ponumber">Số PO mới (tùy chọn)</label>
            <input
              id="reorder-ponumber"
              placeholder="Để trống tự sinh"
              value={poNumber}
              onChange={(e) => setPoNumber(e.target.value)}
            />
          </div>
          <div className="form-field" style={{ marginBottom: 0 }}>
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
          <div className="form-field" style={{ marginBottom: 0 }}>
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
                    {emp.fullName}{emp.employeeCode ? ` (${emp.employeeCode})` : ''}
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
        </div>

        {/* Row 2: Ghi chú */}
        <div className="form-field" style={{ marginBottom: 0 }}>
          <label htmlFor="reorder-note">Ghi chú đơn mua mới</label>
          <textarea
            id="reorder-note"
            rows={2}
            placeholder="Ghi chú đơn mua mới (nếu có)…"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>

        {/* Items table */}
        <div className="form-field" style={{ marginBottom: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <label style={{ fontWeight: 600, fontSize: '13px', margin: 0 }}>
              Danh sách mặt hàng ({rows.length} dòng)
            </label>
            <button
              type="button"
              className="secondary-button"
              style={{ fontSize: '12px', padding: '5px 12px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
              onClick={addRow}
            >
              <Icon name="plus" size={14} /> Thêm mặt hàng
            </button>
          </div>

          <div className="table-wrap" style={{ maxHeight: '360px', overflowY: 'auto', border: '1px solid var(--border-color, #d2ddd3)', borderRadius: '6px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
              <thead style={{ position: 'sticky', top: 0, background: '#f8fafc', zIndex: 2 }}>
                <tr>
                  <th style={{ width: '38px', textAlign: 'center' }}>#</th>
                  <th style={{ minWidth: '220px' }}>Tên hàng hóa / vật tư *</th>
                  <th style={{ minWidth: '150px' }}>Quy cách</th>
                  <th style={{ width: '80px', textAlign: 'center' }}>ĐVT</th>
                  <th style={{ width: '100px', textAlign: 'right' }}>Số lượng *</th>
                  <th style={{ width: '130px', textAlign: 'right' }}>Đơn giá ({order.currency}) *</th>
                  <th style={{ width: '130px', textAlign: 'right' }}>Thành tiền ({order.currency})</th>
                  <th style={{ width: '40px', textAlign: 'center' }}></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, idx) => {
                  const q = Number(row.quantity), p = Number(row.unitPrice)
                  const lineTotal = q > 0 && p >= 0 && !isNaN(q) && !isNaN(p) ? q * p : null
                  return (
                    <tr key={row.key} style={{ borderBottom: '1px solid #eef2ed' }}>
                      <td style={{ textAlign: 'center', color: 'var(--muted, #6a806f)', fontSize: '12px' }}>
                        {idx + 1}
                      </td>
                      <td>
                        <input
                          aria-label={`Tên vật tư dòng ${idx + 1}`}
                          value={row.materialName}
                          placeholder="Tên hàng hóa…"
                          onChange={e => patchRow(row.key, { materialName: e.target.value })}
                          style={{ width: '100%', minHeight: '34px', padding: '6px 8px', fontSize: '13px' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Quy cách dòng ${idx + 1}`}
                          value={row.specification}
                          placeholder="Quy cách…"
                          onChange={e => patchRow(row.key, { specification: e.target.value })}
                          style={{ width: '100%', minHeight: '34px', padding: '6px 8px', fontSize: '13px' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Đơn vị tính dòng ${idx + 1}`}
                          value={row.unit}
                          placeholder="ĐVT…"
                          onChange={e => patchRow(row.key, { unit: e.target.value })}
                          style={{ width: '100%', minHeight: '34px', padding: '6px 8px', fontSize: '13px', textAlign: 'center' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Số lượng dòng ${idx + 1}`}
                          inputMode="decimal"
                          value={row.quantity}
                          placeholder="0"
                          onChange={e => patchRow(row.key, { quantity: e.target.value })}
                          style={{ width: '100%', minHeight: '34px', padding: '6px 8px', fontSize: '13px', textAlign: 'right' }}
                        />
                      </td>
                      <td>
                        <input
                          aria-label={`Đơn giá dòng ${idx + 1}`}
                          inputMode="decimal"
                          value={row.unitPrice}
                          placeholder="0"
                          onChange={e => patchRow(row.key, { unitPrice: e.target.value })}
                          style={{ width: '100%', minHeight: '34px', padding: '6px 8px', fontSize: '13px', textAlign: 'right' }}
                        />
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600, color: 'var(--brand, #1f513f)' }}>
                        {lineTotal != null
                          ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: order.currency === 'USD' ? 2 : 0 }).format(lineTotal)
                          : '—'}
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        <button
                          type="button"
                          className="text-button"
                          aria-label={`Xóa dòng ${idx + 1}`}
                          title="Xóa dòng này"
                          onClick={() => removeRow(row.key)}
                          disabled={rows.length === 1}
                          style={{
                            color: rows.length === 1 ? '#cbd5e1' : '#dc2626',
                            cursor: rows.length === 1 ? 'not-allowed' : 'pointer',
                            padding: '4px 8px',
                            fontSize: '14px',
                            fontWeight: 'bold'
                          }}
                        >
                          ✕
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Bottom section: Auto-issue card & Financial summary */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(280px, 1.2fr) minmax(260px, 1fr)',
            gap: '16px',
            marginTop: '14px',
            alignItems: 'stretch'
          }}>
            {/* Left: Auto-issue */}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              padding: '14px 16px',
              background: autoIssue ? 'var(--surface-subtle, #f4f7f4)' : '#fafafa',
              border: autoIssue ? '1px solid var(--border-color, #c8d8ca)' : '1px solid #e2e8f0',
              borderRadius: '6px'
            }}>
              <label style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', cursor: 'pointer', margin: 0 }}>
                <input
                  type="checkbox"
                  id="reorder-auto-issue"
                  checked={autoIssue}
                  onChange={(e) => setAutoIssue(e.target.checked)}
                  style={{ marginTop: '2px' }}
                />
                <div>
                  <strong style={{ fontSize: '13px', color: autoIssue ? 'var(--brand, #1f513f)' : '#334155' }}>
                    Phát hành đơn và tải PDF ngay lập tức
                  </strong>
                  <p style={{ margin: '4px 0 0', fontSize: '11px', color: 'var(--muted, #64748b)', lineHeight: 1.5 }}>
                    {autoIssue
                      ? 'Đơn sẽ tự động chuyển sang Đã phát hành (EXPORTED) và tải ngay file PDF về máy tính.'
                      : 'Đơn sẽ được lưu ở trạng thái Bản nháp (DRAFT) để bạn kiểm tra lại trước khi phát hành.'}
                  </p>
                </div>
              </label>
            </div>

            {/* Right: Financial summary */}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
              padding: '12px 16px',
              background: '#fff',
              border: '1px solid var(--border-color, #d2ddd3)',
              borderRadius: '6px',
              fontSize: '13px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--muted, #64748b)' }}>
                <span>Tiền hàng (chưa VAT):</span>
                <strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: order.currency === 'USD' ? 2 : 0 }).format(subtotal)} {order.currency}</strong>
              </div>
              {vatRate > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--muted, #64748b)' }}>
                  <span>Thuế VAT ({vatRate}%):</span>
                  <span>+{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: order.currency === 'USD' ? 2 : 0 }).format(vatAmount)} {order.currency}</span>
                </div>
              )}
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                borderTop: '1px solid var(--line, #e2e8f0)',
                paddingTop: '6px',
                marginTop: '2px',
                fontSize: '14px'
              }}>
                <strong>Tổng thanh toán:</strong>
                <strong style={{ color: 'var(--brand, #1f513f)', fontSize: '16px' }}>
                  {new Intl.NumberFormat('vi-VN', { maximumFractionDigits: order.currency === 'USD' ? 2 : 0 }).format(grandTotal)} {order.currency}
                </strong>
              </div>
            </div>
          </div>
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}

        <div className="dialog-actions" style={{ marginTop: '12px', borderTop: '1px solid var(--line, #e2e8f0)', paddingTop: '16px' }}>
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => {
              if (isDirty) {
                if (!window.confirm('Bạn có thay đổi chưa lưu. Bạn có chắc chắn muốn đóng và hủy bỏ các thay đổi?')) return
              }
              onClose()
            }}
          >
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
