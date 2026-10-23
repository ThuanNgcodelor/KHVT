import { useState, useMemo } from 'react'
import { Dialog } from '../../../components/Dialog'
import { Icon } from '../../../components/Icon'

export type ParsedRow = {
  materialName: string
  materialCode?: string
  specification?: string
  unit?: string
  quantity: string
  unitPrice: string
}

function cleanNumber(str: string): string {
  if (!str) return ''
  let s = str.trim().replace(/[^\d.,-]/g, '')
  if (s.includes('.') && s.includes(',')) {
    if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
      s = s.replace(/\./g, '').replace(',', '.')
    } else {
      s = s.replace(/,/g, '')
    }
  } else if (s.includes(',')) {
    if (/,\d{3}$/.test(s)) {
      s = s.replace(/,/g, '')
    } else {
      s = s.replace(',', '.')
    }
  } else if (s.includes('.')) {
    if (/\.\d{3}$/.test(s) && !/\.\d{3}\./.test(s)) {
      if (s.length > 5) s = s.replace(/\./g, '')
    }
  }
  return s
}

export function parseExcelClipboard(text: string): ParsedRow[] {
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
  if (!lines.length) return []

  const result: ParsedRow[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const delimiter = line.includes('\t') ? '\t' : line.includes(';') ? ';' : ','
    const parts = line.split(delimiter).map(p => p.replace(/^"|"$/g, '').trim())

    const lower = parts.join(' ').toLowerCase()
    if (i === 0 && (lower.includes('tên vật tư') || lower.includes('tên hàng') || lower.includes('stt') || lower.includes('quy cách') || lower.includes('đơn vị') || lower.includes('đơn giá') || lower.includes('số lượng'))) {
      continue
    }

    let name = '', code = '', spec = '', unit = '', qty = '', price = ''

    if (parts.length >= 7) {
      if (/^\d+$/.test(parts[0])) {
        code = parts[1]
        name = parts[2]
        spec = parts[3]
        unit = parts[4]
        qty = parts[5]
        price = parts[6]
      } else {
        name = parts[0]
        code = parts[1]
        spec = parts[2]
        unit = parts[3]
        qty = parts[4]
        price = parts[5]
      }
    } else if (parts.length === 6) {
      if (/^\d+$/.test(parts[0])) {
        name = parts[1]
        spec = parts[2]
        unit = parts[3]
        qty = parts[4]
        price = parts[5]
      } else {
        code = parts[0]
        name = parts[1]
        spec = parts[2]
        unit = parts[3]
        qty = parts[4]
        price = parts[5]
      }
    } else if (parts.length === 5) {
      name = parts[0]
      spec = parts[1]
      unit = parts[2]
      qty = parts[3]
      price = parts[4]
    } else if (parts.length === 4) {
      name = parts[0]
      unit = parts[1]
      qty = parts[2]
      price = parts[3]
    } else if (parts.length === 3) {
      name = parts[0]
      qty = parts[1]
      price = parts[2]
    } else if (parts.length === 2) {
      name = parts[0]
      qty = parts[1]
    } else if (parts.length === 1 && parts[0]) {
      name = parts[0]
    }

    if (name) {
      result.push({
        materialName: name,
        materialCode: code || undefined,
        specification: spec || undefined,
        unit: unit || undefined,
        quantity: cleanNumber(qty),
        unitPrice: cleanNumber(price)
      })
    }
  }

  return result
}

export function PasteExcelModal({
  onClose,
  onImport
}: {
  onClose: () => void
  onImport: (rows: ParsedRow[], mode: 'replace' | 'append') => void
}) {
  const [text, setText] = useState('')
  const [error, setError] = useState('')

  const parsedRows = useMemo(() => parseExcelClipboard(text), [text])

  function handleApply(mode: 'replace' | 'append') {
    if (!parsedRows.length) {
      setError('Chưa nhận diện được dòng hàng nào hợp lệ. Vui lòng dán dữ liệu từ bảng tính Excel.')
      return
    }
    onImport(parsedRows, mode)
    onClose()
  }

  return (
    <Dialog title="Dán dữ liệu trực tiếp từ Excel" onClose={onClose}>
      <p className="dialog-description">
        Sao chép các dòng trong Excel (bôi đen các ô rồi nhấn Ctrl+C) và dán vào ô bên dưới. Hệ thống sẽ tự động nhận diện các cột: Tên vật tư, Quy cách, ĐVT, Số lượng, Đơn giá.
      </p>

      {error && <p className="form-error" role="alert">{error}</p>}

      <div className="form-field" style={{ marginBottom: '14px' }}>
        <label htmlFor="excel-paste-area"><strong>Dán nội dung từ Excel tại đây (Ctrl+V)</strong></label>
        <textarea
          id="excel-paste-area"
          rows={6}
          placeholder={'Tên vật tư\tQuy cách\tĐVT\tSố lượng\tĐơn giá\nThép hộp 50x50x2mm\t6m/cây\tCây\t20\t185000\nQue hàn KT-421\tTiêu chuẩn\tHộp\t10\t95000'}
          value={text}
          onChange={(e) => { setText(e.target.value); setError('') }}
          style={{ width: '100%', fontFamily: 'monospace', fontSize: '13px' }}
          autoFocus
        />
      </div>

      {parsedRows.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', fontSize: '13px' }}>
            <span>Đã nhận diện: <strong style={{ color: 'var(--primary, #2563eb)' }}>{parsedRows.length}</strong> dòng hàng</span>
            <span style={{ fontSize: '12px', color: 'var(--text-muted, #64748b)' }}>Xem trước dữ liệu trích xuất</span>
          </div>

          <div className="table-wrap" style={{ maxHeight: '220px', overflowY: 'auto', border: '1px solid var(--border-color, #e2e8f0)', borderRadius: '6px' }}>
            <table className="po-cart" style={{ fontSize: '12px' }}>
              <thead>
                <tr>
                  <th style={{ width: '36px' }}>STT</th>
                  <th>Tên vật tư</th>
                  <th>Quy cách</th>
                  <th>ĐVT</th>
                  <th>Số lượng</th>
                  <th>Đơn giá</th>
                </tr>
              </thead>
              <tbody>
                {parsedRows.map((r, idx) => (
                  <tr key={idx}>
                    <td>{idx + 1}</td>
                    <td><strong>{r.materialName}</strong>{r.materialCode ? ` (${r.materialCode})` : ''}</td>
                    <td>{r.specification || '—'}</td>
                    <td>{r.unit || '—'}</td>
                    <td>{r.quantity ? Number(r.quantity).toLocaleString('vi-VN') : '—'}</td>
                    <td>{r.unitPrice ? Number(r.unitPrice).toLocaleString('vi-VN') : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="dialog-actions" style={{ justifyContent: 'space-between', marginTop: '16px' }}>
        <button type="button" className="secondary-button" onClick={onClose}>
          Hủy
        </button>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            type="button"
            className="secondary-button"
            disabled={!parsedRows.length}
            onClick={() => handleApply('append')}
            title="Thêm các dòng này vào sau danh sách hiện có"
          >
            <Icon name="plus" size={14} /> Thêm tiếp ({parsedRows.length} dòng)
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={!parsedRows.length}
            onClick={() => handleApply('replace')}
            title="Xóa danh sách cũ và thay thế bằng các dòng này"
          >
            <Icon name="check" size={14} /> Thay thế danh sách ({parsedRows.length} dòng)
          </button>
        </div>
      </div>
    </Dialog>
  )
}
