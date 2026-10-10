import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { Pagination } from '../../../components/Pagination'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { errorMessage } from '../../../services/errorMessage'
import { importApi } from '../importApi'
import type { DraftPreview, LegacyPreview, LegacyCommit } from '../types'

export function ImportsPage() {
  const { user } = useAuth(), navigate = useNavigate(), client = useQueryClient()
  const operational = !!user && hasPermission(user, 'IMPORT_OPERATIONAL'), legacyAllowed = !!user && hasPermission(user, 'IMPORT_LEGACY')
  const [mode, setMode] = useState<'operational' | 'legacy'>(operational ? 'operational' : 'legacy')
  const [file, setFile] = useState<File | null>(null), [paste, setPaste] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [draft, setDraft] = useState<DraftPreview | null>(null), [preview, setPreview] = useState<LegacyPreview | null>(null), [report, setReport] = useState<LegacyCommit | null>(null)
  const [selected, setSelected] = useState<number[]>([]), [page, setPage] = useState(0), [ack, setAck] = useState(false), [confirm, setConfirm] = useState(false)
  function resetPreview() { setDraft(null); setPreview(null); setReport(null); setSelected([]); setPage(0); setAck(false); setError('') }
  async function previewFile(fromPaste = false) {
    resetPreview(); setBusy(true)
    try {
      if (!fromPaste && !file) throw new Error('Chọn tệp trước khi preview.')
      if (mode === 'legacy') { if (file!.size > 25 * 1024 * 1024) throw new Error('Workbook không được vượt quá 25 MB.'); setPreview(await importApi.legacy(file!)) }
      else { if (!fromPaste && file!.size > 10 * 1024 * 1024) throw new Error('Tệp yêu cầu mua không được vượt quá 10 MB.'); const result = fromPaste ? await importApi.paste(paste) : await importApi.operational(file!); setDraft(result); setSelected(result.items.map((_, index) => index)) }
    } catch (failure) { setError(failure instanceof Error && !(failure.name === 'ApiError') ? failure.message : errorMessage(failure)) } finally { setBusy(false) }
  }
  async function commit() {
    if (!preview) return
    setBusy(true); setError('')
    try { setReport(await importApi.commit(preview.batchId)); setConfirm(false); await Promise.all(['catalog', 'pricing', 'procurement', 'dashboard'].map((key) => client.invalidateQueries({ queryKey: [key] }))) } catch (failure) { setError(errorMessage(failure)) } finally { setBusy(false) }
  }
  const canCommit = !!preview && preview.status === 'PREVIEW' && preview.summary.errorRows === 0 && !preview.rowIssues.some((row) => row.issues.some((issue) => issue.includes('PO_HEADER_CONFLICT')))
  return <>
    <PageHeader title="Nhập dữ liệu" description="Xem trước và kiểm tra cảnh báo trước khi chuyển sang lập đơn hoặc ghi dữ liệu cũ." />
    <div className="tab-list" aria-label="Loại dữ liệu nhập">{operational && <button className={mode === 'operational' ? 'active' : ''} disabled={busy} onClick={() => { setMode('operational'); setFile(null); resetPreview() }}>Yêu cầu mua / báo giá</button>}{legacyAllowed && <button className={mode === 'legacy' ? 'active' : ''} disabled={busy} onClick={() => { setMode('legacy'); setFile(null); resetPreview() }}>Workbook dữ liệu cũ</button>}</div>
    <section className="panel import-panel"><h2>{mode === 'legacy' ? 'Nhập lịch sử từ workbook' : 'Nhận yêu cầu mua'}</h2>
      <p className="page-description">{mode === 'legacy' ? 'Cần các sheet NCC, LICH_SU, DON_HANG. CONFIG được bỏ qua. Commit sẽ ghi NCC, vật tư, lịch sử và PO vào database đang kết nối.' : 'XLSX, XLS, CSV hoặc PDF có lớp text. Preview giữ dữ liệu thành bản nháp; kiểm tra tên, số lượng và đơn giá trước khi lập đơn.'}</p>
      <div className="form-field"><label htmlFor="import-file">Tệp dữ liệu</label><input key={mode} id="import-file" type="file" accept={mode === 'legacy' ? '.xlsx,.xls' : '.xlsx,.xls,.csv,.pdf'} disabled={busy} onChange={(event) => { setFile(event.target.files?.[0] ?? null); resetPreview() }} /></div>
      <div className="header-actions"><button className="primary-button" disabled={!file || busy} onClick={() => { void previewFile() }}>{busy ? 'Đang xử lý…' : 'Preview tệp'}</button></div>
      {mode === 'operational' && <div className="form-field"><label htmlFor="import-paste">Hoặc dán từ Excel</label><textarea id="import-paste" rows={5} maxLength={200000} value={paste} disabled={busy} onChange={(event) => { setPaste(event.target.value); resetPreview() }} placeholder="Tên hàng → ĐVT → Số lượng → Đơn giá. Nên dán cả hàng tiêu đề." /><button className="secondary-button" disabled={!paste.trim() || busy} onClick={() => { void previewFile(true) }}>Preview dữ liệu dán</button></div>}
      {mode === 'operational' && <p className="field-help">PDF scan chưa có OCR. Số lượng chữ được giữ nguyên và không tính vào tổng tiền.</p>}
    </section>
    {error && <p className="form-error" role="alert">{error}</p>}
    {draft && <section className="panel"><div className="panel-heading"><h2>Kiểm tra {draft.items.length} dòng</h2>{user && hasPermission(user, 'PO_CREATE') && <button className="primary-button" disabled={!selected.length || selected.length > 200} onClick={() => navigate('/purchase-orders/new', { state: { importDraft: { ...draft, items: draft.items.filter((_, index) => selected.includes(index)) } } })}>Chuyển {selected.length} dòng sang lập đơn</button>}</div>
      <p className="import-note">{draft.message} {selected.length > 200 && 'Chọn tối đa 200 dòng mỗi lượt chuyển sang lập đơn.'}</p>
      {draft.warnings.length > 0 && <details className="import-note"><summary>{draft.warnings.length} dòng có cảnh báo</summary><ul>{draft.warnings.map((row) => <li key={row.sourceRow}>Dòng {row.sourceRow}: {row.warnings.join('; ')}</li>)}</ul></details>}
      <div className="table-wrap"><table><thead><tr><th><input type="checkbox" aria-label="Chọn tất cả dòng nhập" checked={selected.length === draft.items.length && !!draft.items.length} onChange={(event) => setSelected(event.target.checked ? draft.items.map((_, index) => index) : [])} /></th><th>Dòng</th><th>Vật tư / Quy cách</th><th>ĐVT</th><th>Số lượng</th><th>Đơn giá nguồn</th></tr></thead><tbody>{draft.items.slice(page * 25, (page + 1) * 25).map((item, offset) => { const index = page * 25 + offset; return <tr key={index}><td><input type="checkbox" aria-label={`Chọn dòng ${item.sourceRow}`} checked={selected.includes(index)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, index] : current.filter((value) => value !== index))} /></td><td>{item.sourceRow}</td><td>{item.materialName}<span className="cell-subtitle">{item.materialCode} {item.specification}</span></td><td>{item.unit}</td><td>{item.quantity ?? item.quantityText ?? 'Chưa có'}</td><td>{item.unitPrice ?? 'Chưa có giá'}</td></tr> })}</tbody></table></div>
      <Pagination page={page} size={25} total={draft.items.length} pages={Math.ceil(draft.items.length / 25)} busy={false} onChange={setPage} />
    </section>}
    {preview && <section className="panel import-panel"><h2>Preview workbook — lô {preview.batchId}</h2><p>{preview.fileName} · {preview.status}</p><p>{preview.summary.note}</p>
      <dl className="record-info">{Object.entries(preview.summary.rowsPerSheet).map(([sheet, count]) => <div key={sheet}><dt>{sheet}</dt><dd>{count.toLocaleString('vi-VN')} dòng</dd></div>)}</dl>
      <p>{preview.summary.legacyPurchaseOrderGroups} nhóm PO · {preview.summary.warningRows} dòng cảnh báo · {preview.summary.errorRows} dòng lỗi</p>
      {preview.message && <p role="status">{preview.message}</p>}
      <details open={preview.summary.errorRows > 0}><summary>Chi tiết cảnh báo/lỗi (tối đa 30 dòng)</summary><ul>{preview.rowIssues.map((row) => <li key={`${row.sheet}-${row.rowNumber}`}>{row.sheet} · dòng {row.rowNumber}: {row.issues.join('; ')}</li>)}</ul></details>
      {!report && canCommit && <><label className="checkbox-label"><input type="checkbox" checked={ack} onChange={(event) => setAck(event.target.checked)} />Tôi đã đối chiếu số dòng/cảnh báo; đây là database thử nghiệm hoặc đã có bản sao lưu phù hợp.</label><button className="primary-button" disabled={!ack || busy} onClick={() => setConfirm(true)}>Commit dữ liệu cũ</button></>}
      {!canCommit && !report && <p className="field-help">Lô đã xử lý hoặc còn lỗi/xung đột; chưa thể commit.</p>}
      {report && <div role="status"><h3>Kết quả commit</h3><p>{report.message}</p><p>{report.historyRowsImported} dòng lịch sử · {report.purchaseOrdersImported} PO · {report.committedRows} dòng xử lý</p></div>}
    </section>}
    {confirm && <ConfirmDialog title="Ghi dữ liệu workbook?" message={`Lô ${preview?.batchId} sẽ ghi dữ liệu vào database đang kết nối. Chỉ tiếp tục sau khi đã đối chiếu và đáp ứng điều kiện dữ liệu đã xác nhận.`} confirmLabel="Commit dữ liệu" busy={busy} error={error} onClose={() => setConfirm(false)} onConfirm={() => { void commit() }} />}
  </>
}
