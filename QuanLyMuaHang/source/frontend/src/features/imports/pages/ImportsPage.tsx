import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { Pagination } from '../../../components/Pagination'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { errorMessage } from '../../../services/errorMessage'
import { ApiError, saveDownload } from '../../../services/apiClient'
import { importApi } from '../importApi'
import { LegacyImportReview } from '../components/LegacyImportReview'
import type { DraftPreview, LegacyPreview, LegacyCommit } from '../types'

export function ImportsPage() {
  const { user } = useAuth(), navigate = useNavigate(), client = useQueryClient()
  const operational = !!user && hasPermission(user, 'IMPORT_OPERATIONAL'), legacyAllowed = !!user && hasPermission(user, 'IMPORT_LEGACY')
  const [mode, setMode] = useState<'operational' | 'legacy'>(legacyAllowed ? 'legacy' : 'operational')
  const [file, setFile] = useState<File | null>(null), [paste, setPaste] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [draft, setDraft] = useState<DraftPreview | null>(null), [preview, setPreview] = useState<LegacyPreview | null>(null), [report, setReport] = useState<LegacyCommit | null>(null)
  const [selected, setSelected] = useState<number[]>([]), [page, setPage] = useState(0), [ack, setAck] = useState(false), [confirm, setConfirm] = useState(false), [notice, setNotice] = useState('')
  const pending = useRef(false)
  function begin() { if (pending.current) return false; pending.current = true; setBusy(true); setError(''); return true }
  function end() { pending.current = false; setBusy(false) }
  function resetPreview() { setDraft(null); setPreview(null); setReport(null); setSelected([]); setPage(0); setAck(false); setConfirm(false); setError(''); setNotice('') }
  async function previewLegacy(selectedFile: File) { if (selectedFile.size > 25 * 1024 * 1024) throw new Error('Workbook không được vượt quá 25 MB.'); setPreview(await importApi.legacy(selectedFile)) }
  async function previewFile(fromPaste = false) {
    if (!begin()) return
    resetPreview()
    try {
      if (!fromPaste && !file) throw new Error('Chọn tệp trước khi preview.')
      if (mode === 'legacy') await previewLegacy(file!)
      else {
        if (!fromPaste && file!.size > 10 * 1024 * 1024) throw new Error('Tệp yêu cầu mua không được vượt quá 10 MB.')
        try { const result = fromPaste ? await importApi.paste(paste) : await importApi.operational(file!); setDraft(result); setSelected(result.items.map((_, index) => index)) }
        catch (failure) {
          if (!(failure instanceof ApiError) || failure.code !== 'LEGACY_WORKBOOK_DETECTED' || fromPaste || !file) throw failure
          if (!legacyAllowed) throw new Error('Đây là workbook dữ liệu cũ gồm NCC, LICH_SU và DON_HANG. Tài khoản chưa có quyền nhập workbook; hãy liên hệ quản trị viên để cấp quyền hoặc nhập giúp.')
          setMode('legacy'); setNotice('Đã nhận diện workbook dữ liệu cũ và chuyển sang kiểm tra đủ các sheet. Tệp đã chọn được giữ nguyên; chưa ghi dữ liệu nghiệp vụ.')
          await previewLegacy(file)
        }
      }
    } catch (failure) { setError(failure instanceof Error && !(failure instanceof ApiError) ? failure.message : errorMessage(failure)) } finally { end() }
  }
  async function commit() {
    if (!preview || !canCommit || !ack || report || !begin()) return
    try {
      const result = await importApi.commit(preview.batchId)
      setReport(result); setPreview((current) => current ? { ...current, status: result.status } : current); setAck(false); setConfirm(false)
      await Promise.all(['catalog', 'pricing', 'procurement', 'dashboard'].map((key) => client.invalidateQueries({ queryKey: [key] })))
    } catch (failure) { setError(errorMessage(failure)) } finally { end() }
  }
  async function refreshPreview() {
    if (!preview || !begin()) return
    try { setPreview(await importApi.batch(preview.batchId)); setAck(false); setConfirm(false) } catch (failure) { setError(errorMessage(failure)) } finally { end() }
  }
  async function downloadIssues() {
    if (!preview || !begin()) return
    try { saveDownload(await importApi.issues(preview.batchId)) } catch (failure) { setError(errorMessage(failure)) } finally { end() }
  }
  const canCommit = !!preview && preview.status === 'PREVIEW' && preview.summary.errorRows === 0 && !preview.rowIssues.some((row) => row.issues.some((issue) => issue.includes('PO_HEADER_CONFLICT')))
  return <>
    <PageHeader title="Nhập dữ liệu" description="Xem trước và kiểm tra cảnh báo trước khi chuyển sang lập đơn hoặc ghi dữ liệu cũ." />
    <div className="tab-list" aria-label="Loại dữ liệu nhập">{operational && <button className={mode === 'operational' ? 'active' : ''} disabled={busy} onClick={() => { setMode('operational'); setFile(null); resetPreview() }}>Yêu cầu mua / báo giá</button>}{legacyAllowed && <button className={mode === 'legacy' ? 'active' : ''} disabled={busy} onClick={() => { setMode('legacy'); setFile(null); resetPreview() }}>Workbook dữ liệu cũ</button>}</div>
    <section className="panel import-panel"><h2>{mode === 'legacy' ? 'Nhập lịch sử từ workbook' : 'Nhận yêu cầu mua'}</h2>
      <p className="page-description">{mode === 'legacy' ? 'Cần các sheet NCC, LICH_SU, DON_HANG. CONFIG được bỏ qua. Commit sẽ ghi NCC, vật tư, lịch sử và PO vào database đang kết nối.' : 'XLSX, XLS, CSV hoặc PDF có lớp text. Preview giữ dữ liệu thành bản nháp; kiểm tra tên, số lượng và đơn giá trước khi lập đơn.'}</p>
      <div className="form-field"><label htmlFor="import-file">Tệp dữ liệu</label><input key={mode} id="import-file" type="file" accept={mode === 'legacy' ? '.xlsx,.xls' : '.xlsx,.xls,.csv,.pdf'} disabled={busy} onChange={(event) => { setFile(event.target.files?.[0] ?? null); resetPreview() }} /></div>
      {file && <p className="field-help">Tệp đã chọn: {file.name}</p>}
      <div className="header-actions"><button className="primary-button" disabled={!file || busy} onClick={() => { void previewFile() }}>{busy ? 'Đang xử lý…' : 'Preview tệp'}</button></div>
      {mode === 'operational' && <div className="form-field"><label htmlFor="import-paste">Hoặc dán từ Excel</label><textarea id="import-paste" rows={5} maxLength={200000} value={paste} disabled={busy} onChange={(event) => { setPaste(event.target.value); resetPreview() }} placeholder="Tên hàng → ĐVT → Số lượng → Đơn giá. Nên dán cả hàng tiêu đề." /><button className="secondary-button" disabled={!paste.trim() || busy} onClick={() => { void previewFile(true) }}>Preview dữ liệu dán</button></div>}
      {mode === 'operational' && <p className="field-help">PDF scan chưa có OCR. Số lượng chữ được giữ nguyên và không tính vào tổng tiền.</p>}
    </section>
    {error && <p className="form-error" role="alert">{error}</p>}
    {notice && <p className="import-note" role="status">{notice}</p>}
    {draft && <section className="panel"><div className="panel-heading"><h2>Kiểm tra {draft.items.length} dòng</h2>{user && hasPermission(user, 'PO_CREATE') && <button className="primary-button" disabled={!selected.length || selected.length > 200} onClick={() => navigate('/purchase-orders/new', { state: { importDraft: { ...draft, items: draft.items.filter((_, index) => selected.includes(index)) } } })}>Chuyển {selected.length} dòng sang lập đơn</button>}</div>
      <p className="import-note">{draft.message} {selected.length > 200 && 'Chọn tối đa 200 dòng mỗi lượt chuyển sang lập đơn.'}</p>
      {draft.warnings.length > 0 && <details className="import-note"><summary>{draft.warnings.length} dòng có cảnh báo</summary><ul>{draft.warnings.map((row) => <li key={row.sourceRow}>Dòng {row.sourceRow}: {row.warnings.join('; ')}</li>)}</ul></details>}
      <div className="table-wrap"><table><thead><tr><th><input type="checkbox" aria-label="Chọn tất cả dòng nhập" checked={selected.length === draft.items.length && !!draft.items.length} onChange={(event) => setSelected(event.target.checked ? draft.items.map((_, index) => index) : [])} /></th><th>Dòng</th><th>Vật tư / Quy cách</th><th>ĐVT</th><th>Số lượng</th><th>Đơn giá nguồn</th></tr></thead><tbody>{draft.items.slice(page * 25, (page + 1) * 25).map((item, offset) => { const index = page * 25 + offset; return <tr key={index}><td><input type="checkbox" aria-label={`Chọn dòng ${item.sourceRow}`} checked={selected.includes(index)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, index] : current.filter((value) => value !== index))} /></td><td>{item.sourceRow}</td><td>{item.materialName}<span className="cell-subtitle">{item.materialCode} {item.specification}</span></td><td>{item.unit}</td><td>{item.quantity ?? item.quantityText ?? 'Chưa có'}</td><td>{item.unitPrice ?? 'Chưa có giá'}</td></tr> })}</tbody></table></div>
      <Pagination page={page} size={25} total={draft.items.length} pages={Math.ceil(draft.items.length / 25)} busy={false} onChange={setPage} />
    </section>}
    {preview && <LegacyImportReview preview={preview} report={report} busy={busy} canCommit={canCommit} acknowledged={ack} onAcknowledge={setAck} onCommit={() => setConfirm(true)} onRefresh={() => { void refreshPreview() }} onDownload={() => { void downloadIssues() }} />}
    {confirm && <ConfirmDialog title="Ghi dữ liệu workbook?" message={`Lô ${preview?.batchId} sẽ ghi dữ liệu vào database đang kết nối. Chỉ tiếp tục sau khi đã đối chiếu và đáp ứng điều kiện dữ liệu đã xác nhận.`} confirmLabel="Commit dữ liệu" busy={busy} error={error} onClose={() => setConfirm(false)} onConfirm={() => { void commit() }} />}
  </>
}
