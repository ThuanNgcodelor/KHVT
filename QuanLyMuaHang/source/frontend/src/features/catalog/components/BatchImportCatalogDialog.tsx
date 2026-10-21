import { useState, useMemo } from 'react'
import { Dialog } from '../../../components/Dialog'
import { errorMessage } from '../../../services/errorMessage'
import { catalogApi } from '../catalogApi'
import type { MaterialCommand, SupplierCommand, BatchResult, MaterialCategory } from '../types'

type ImportKind = 'materials' | 'suppliers'

export function BatchImportCatalogDialog({
  kind,
  onClose,
  onSuccess
}: {
  kind: ImportKind
  onClose: () => void
  onSuccess: (result: BatchResult) => void
}) {
  const [rawText, setRawText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // Parse lines
  const parsedData = useMemo(() => {
    const lines = rawText.split('\n').map((l) => l.trim()).filter((l) => l.length > 0)
    if (kind === 'materials') {
      const items: MaterialCommand[] = []
      for (const line of lines) {
        // split by tab or comma if no tab
        const parts = line.includes('\t') ? line.split('\t') : line.split(/,(?=(?:(?:[^"]*"){2})*[^"]*$)/)
        const cleanParts = parts.map((p) => p.replace(/^"|"$/g, '').trim())
        if (cleanParts.length === 1) {
          items.push({ code: null, name: cleanParts[0], category: 'MATERIAL', defaultUnit: null, active: true })
        } else if (cleanParts.length === 2) {
          // Could be code, name or name, unit
          items.push({ code: null, name: cleanParts[0], category: 'MATERIAL', defaultUnit: cleanParts[1] || null, active: true })
        } else if (cleanParts.length === 3) {
          items.push({ code: cleanParts[0] || null, name: cleanParts[1], category: 'MATERIAL', defaultUnit: cleanParts[2] || null, active: true })
        } else {
          const catStr = cleanParts[2]?.toUpperCase()
          const cat: MaterialCategory = catStr === 'DỊCH VỤ' || catStr === 'SERVICE' ? 'SERVICE' : catStr === 'KHÁC' || catStr === 'OTHER' ? 'OTHER' : 'MATERIAL'
          items.push({ code: cleanParts[0] || null, name: cleanParts[1], category: cat, defaultUnit: cleanParts[3] || null, active: true })
        }
      }
      return items
    } else {
      const items: SupplierCommand[] = []
      for (const line of lines) {
        const parts = line.includes('\t') ? line.split('\t') : line.split(/,(?=(?:(?:[^"]*"){2})*[^"]*$)/)
        const cleanParts = parts.map((p) => p.replace(/^"|"$/g, '').trim())
        if (cleanParts.length === 1) {
          items.push({ code: null, name: cleanParts[0], address: null, taxCode: null, phone: null, email: null, active: true })
        } else if (cleanParts.length === 2) {
          items.push({ code: cleanParts[0] || null, name: cleanParts[1], address: null, taxCode: null, phone: null, email: null, active: true })
        } else {
          items.push({
            code: cleanParts[0] || null,
            name: cleanParts[1],
            address: cleanParts[2] || null,
            taxCode: cleanParts[3] || null,
            phone: cleanParts[4] || null,
            email: cleanParts[5] || null,
            active: true
          })
        }
      }
      return items
    }
  }, [rawText, kind])

  const validRows = parsedData.filter((r) => !!r.name?.trim())
  const invalidRows = parsedData.length - validRows.length

  async function handleImport() {
    if (!validRows.length) {
      setError('Chưa có dòng hợp lệ nào để nhập. Vui lòng dán danh sách có tên.')
      return
    }
    setBusy(true)
    setError('')
    try {
      let result: BatchResult
      if (kind === 'materials') {
        result = await catalogApi.batchMaterials(validRows as MaterialCommand[])
      } else {
        result = await catalogApi.batchSuppliers(validRows as SupplierCommand[])
      }
      onSuccess(result)
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      title={kind === 'materials' ? 'Thêm vật tư hàng loạt' : 'Thêm nhà cung cấp hàng loạt'}
      onClose={onClose}
      busy={busy}
    >
      <p className="dialog-description">
        Sao chép từ Excel và dán vào ô bên dưới (mỗi dòng một bản ghi, ngăn cách bằng phím Tab hoặc dấu phẩy).
        {kind === 'materials'
          ? ' Cột gợi ý: [Mã vật tư] [Tên vật tư] [Phân loại] [Đơn vị tính].'
          : ' Cột gợi ý: [Mã NCC] [Tên NCC] [Địa chỉ] [Mã số thuế] [Số điện thoại] [Email].'}
      </p>

      {error && <p className="form-error" role="alert">{error}</p>}

      <div className="form-field" style={{ marginBottom: '14px' }}>
        <label htmlFor="batch-paste-input">Dán dữ liệu danh sách tại đây</label>
        <textarea
          id="batch-paste-input"
          rows={6}
          placeholder={kind === 'materials'
            ? 'Vattu01\tDây cáp điện Cadivi 2.5\tVật tư\tMét\nVattu02\tỐng nhựa Tiền Phong D60\tVật tư\tCây'
            : 'NCC01\tCông ty TNHH Thiết bị Điện ABC\tCần Thơ\t1800123456\t02923888999\tabc@example.com'}
          value={rawText}
          onChange={(e) => setRawText(e.target.value)}
          style={{ width: '100%', fontFamily: 'monospace', fontSize: '13px' }}
        />
      </div>

      {parsedData.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
            <span>Nhận diện: <strong>{validRows.length}</strong> dòng hợp lệ</span>
            {invalidRows > 0 && <span style={{ color: '#c53030' }}>{invalidRows} dòng thiếu tên</span>}
          </div>
          <div className="table-wrap" style={{ maxHeight: '240px', overflowY: 'auto', border: '1px solid var(--line)' }}>
            <table>
              <thead>
                <tr>
                  <th>STT</th>
                  <th>Mã</th>
                  <th>Tên</th>
                  {kind === 'materials' ? <th>ĐVT</th> : <th>Địa chỉ / MST</th>}
                </tr>
              </thead>
              <tbody>
                {parsedData.slice(0, 50).map((row, idx) => (
                  <tr key={idx} style={{ opacity: row.name ? 1 : 0.4 }}>
                    <td>{idx + 1}</td>
                    <td>{row.code || '—'}</td>
                    <td><strong>{row.name || '(Thiếu tên)'}</strong></td>
                    {kind === 'materials' ? (
                      <td>{(row as MaterialCommand).defaultUnit || '—'}</td>
                    ) : (
                      <td>{(row as SupplierCommand).address || (row as SupplierCommand).taxCode || '—'}</td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {parsedData.length > 50 && (
            <p className="field-help" style={{ marginTop: '4px' }}>
              Hiển thị xem trước 50/{parsedData.length} dòng. Toàn bộ dòng hợp lệ sẽ được nạp khi xác nhận.
            </p>
          )}
        </div>
      )}

      <div className="dialog-actions">
        <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>Hủy</button>
        <button
          type="button"
          className="primary-button"
          disabled={busy || validRows.length === 0}
          onClick={() => { void handleImport() }}
        >
          {busy ? 'Đang nhập…' : `Xác nhận thêm ${validRows.length} ${kind === 'materials' ? 'vật tư' : 'nhà cung cấp'}`}
        </button>
      </div>
    </Dialog>
  )
}
