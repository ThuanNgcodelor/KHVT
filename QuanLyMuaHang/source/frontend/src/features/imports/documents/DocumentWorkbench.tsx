import { useEffect, useState } from 'react'
import { saveDownload } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { documentApi } from './documentApi'
import { useDocumentImports } from './useDocumentImports'
import { DocumentPreview } from './DocumentPreview'
import { HistoryReview } from './HistoryReview'
import { ComparisonReview } from './ComparisonReview'
import type { Confirmation, DocumentKind, HistoryLookup, InquirySelection, QuoteComparison } from './types'
import './documents.css'

export function DocumentWorkbench({ canReadPrices, onBusy }: { canReadPrices: boolean; onBusy: (busy: boolean) => void }) {
  const state = useDocumentImports(), { active, busy, run } = state
  const [kind, setKind] = useState<DocumentKind>('REQUEST'), [file, setFile] = useState<File | null>(null)
  const [lookup, setLookup] = useState<HistoryLookup | null>(null), [comparison, setComparison] = useState<QuoteComparison | null>(null)
  useEffect(() => { setLookup(null); setComparison(null) }, [state.id])
  useEffect(() => { onBusy(busy); return () => onBusy(false) }, [busy, onBusy])
  async function preview(sheet = '') {
    await run(async () => {
      const source = sheet && active ? new File([(await documentApi.source(active.id)).blob], active.fileName) : file
      if (!source) throw new Error('Chọn tệp để kiểm tra trước.')
      if (source.size > 10 * 1024 * 1024) throw new Error('Tệp chứng từ không được vượt quá 10 MB.')
      await state.remember(await documentApi.preview(source, sheet && active ? active.kind : kind, sheet))
    })
  }
  async function confirm(data: Confirmation) {
    if (!active) return
    await run(async () => { await state.remember(await documentApi.confirm(active.id, data)); state.setNotice('Đã lưu chứng từ. Có thể mở lại sau khi tải lại trang; chưa ghi lịch sử mua hoặc tạo PO.') })
  }
  async function history() {
    if (!active) return
    await run(async () => { setLookup(await documentApi.lookup(active.id)); setComparison(null) })
  }
  async function previewHistory(lineKeys: string[]) {
    if (!active) return
    await run(async () => { setLookup(await documentApi.lookupPreview(active.id, lineKeys)); setComparison(null) })
  }
  async function compare() {
    if (!active) return
    await run(async () => { setComparison(await documentApi.comparison(active.id)) })
  }
  async function inquiries(choices: InquirySelection[], download: boolean) {
    if (!active) return
    await run(async () => {
      if (download) { saveDownload(await documentApi.inquiries(active.id, choices)); await state.remember(await documentApi.get(active.id)) }
      else await state.remember(await documentApi.saveInquiries(active.id, choices))
      state.setNotice(download ? 'Đã lưu nhóm hỏi giá và tải XLSX. Kiểm tra nội dung trước khi gửi NCC.' : 'Đã lưu nhóm hỏi giá; mở lại PCVT để tiếp tục.')
    })
  }
  return <div className="document-workbench">
    <section className="panel import-panel"><h2>PCVT và báo giá mới</h2><p className="page-description">Chọn các món cần mua → tra NCC và giá từng mua → gom hỏi giá → đối chiếu báo giá mới. Workbook QUANLYMUAHANGKHVT là dữ liệu Apps Script cũ, dùng tab dữ liệu lịch sử riêng.</p>
      <fieldset disabled={busy}><div className="form-grid"><div className="form-field"><label htmlFor="document-kind">Loại chứng từ</label><select id="document-kind" value={kind} onChange={(event) => { setKind(event.target.value as DocumentKind); setFile(null) }}><option value="REQUEST">PCVT / yêu cầu cần mua</option><option value="QUOTATION">Báo giá nhà cung cấp</option></select></div><div className="form-field"><label htmlFor="document-file">Tệp PCVT / báo giá</label><input key={kind} id="document-file" type="file" accept=".xlsx,.xls,.csv,.pdf" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /></div></div></fieldset>
      {file && <p className="field-help">Tệp đã chọn: {file.name}</p>}<div className="header-actions"><button className="primary-button" disabled={!file || busy} onClick={() => { void preview() }}>{busy ? 'Đang xử lý…' : 'Kiểm tra tệp chứng từ'}</button>{active && <button className="secondary-button" disabled={busy} onClick={() => { state.open(null); setFile(null) }}>Đóng chứng từ đang xem</button>}</div><p className="field-help">Chọn đúng sheet và phiếu trong preview. PDF scan hỗ trợ thêm dòng thủ công; hệ thống không tự đọc OCR.</p>
    </section>
    {state.error && <p className="form-error" role="alert">{state.error}</p>}{state.notice && <p className="success-notice" role="status">{state.notice}</p>}
    <section className="panel document-saved"><div className="panel-heading"><h2>Chứng từ đã lưu</h2><button className="secondary-button" disabled={busy || state.list.isFetching} onClick={() => { void state.list.refetch() }}>Tải lại danh sách</button></div>
      {state.list.isPending ? <p className="table-message" role="status">Đang tải chứng từ…</p> : state.list.isError ? <p className="form-error" role="alert">{errorMessage(state.list.error)} <button className="text-button" onClick={() => { void state.list.refetch() }}>Thử lại</button></p> : !state.list.data?.length ? <p className="empty-message">Chưa có PCVT hoặc báo giá. Chọn tệp ở trên để kiểm tra và lưu.</p> : <div className="table-wrap"><table><thead><tr><th>Chứng từ</th><th>Loại / số dòng</th><th>Trạng thái</th><th>Mở lại</th></tr></thead><tbody>{state.list.data.map((document) => <tr key={document.id}><td>{document.title}<span className="cell-subtitle">{document.fileName}{document.confirmed?.supplierName ? ` · ${document.confirmed.supplierName}` : ''}</span></td><td>{document.kind === 'REQUEST' ? 'PCVT / yêu cầu mua' : 'Báo giá NCC'}<span className="cell-subtitle">{document.confirmed?.lines.length ?? document.parsed.lines.length} dòng</span></td><td><span className={`status-badge ${document.confirmed ? 'active' : 'draft'}`}>{document.confirmed ? 'Đã xác nhận' : 'Chờ kiểm tra'}</span></td><td><button className="text-button" disabled={busy} aria-label={`Mở chứng từ ${document.id}`} onClick={() => { state.open(document.id); setFile(null) }}>Mở</button></td></tr>)}</tbody></table><p className="import-note">Hiển thị tối đa 50 chứng từ gần nhất. URL chứng từ đã mở giữ ID để tải lại đúng bản đang làm việc.</p></div>}
    </section>
    {state.id && state.detail.isPending && <p className="table-message" role="status">Đang mở chứng từ…</p>}
    {state.detail.isError && <p className="form-error" role="alert">{errorMessage(state.detail.error)} <button className="text-button" onClick={() => { void state.detail.refetch() }}>Thử lại</button></p>}
    {active && <DocumentPreview key={`${active.id}-${active.status}`} document={active} requests={state.list.data?.filter((document) => document.kind === 'REQUEST' && !!document.confirmed) ?? []} busy={busy} onConfirm={(data) => { void confirm(data) }} onSource={() => { void run(async () => saveDownload(await documentApi.source(active.id))) }} onSheet={(name) => { void preview(name) }} onLookup={(keys) => { void previewHistory(keys) }} />}
    {active && !active.confirmed && lookup && <section className="panel document-history" aria-label="Lịch sử NCC và giá trong bản xem trước"><div className="panel-heading"><div><h2>NCC và giá cũ cho dòng đã chọn</h2><p className="field-help">Chỉ đọc để đối chiếu trước khi xác nhận PCVT; chưa ghi lịch sử mua.</p></div></div><div className="table-wrap"><table><thead><tr><th>Vật tư trong PCVT</th><th>NCC từng mua</th><th>Giá cũ</th><th>Ngày</th><th>ĐVT / quy cách</th></tr></thead><tbody>{lookup.lines.flatMap((line) => {
      const source = active.parsed.lines.find((item) => item.lineKey === line.lineKey)
      if (!line.candidates.length) return [<tr key={line.lineKey}><td>{line.materialName}<span className="cell-subtitle">{source?.specification} · {source?.unit}</span></td><td colSpan={4}>Chưa có lịch sử phù hợp</td></tr>]
      return line.candidates.map((candidate, index) => <tr key={`${line.lineKey}-${candidate.supplierKey}-${index}`}><td>{line.materialName}<span className="cell-subtitle">{source?.specification} · SL {source?.quantity ?? source?.quantityText ?? '—'}</span></td><td>{candidate.supplierName}</td><td className="numeric-cell">{candidate.unitPrice ?? 'Chưa có giá'} {candidate.currency}</td><td>{candidate.purchaseDate ?? 'Chưa rõ ngày'}</td><td>{candidate.unit ?? 'Chưa rõ'}<span className="cell-subtitle">{candidate.specification || 'Quy cách chưa ghi nhận'}</span></td></tr>)
    })}</tbody></table></div></section>}
    {active?.confirmed && active.kind === 'REQUEST' && <section className="panel import-panel"><h2>Tra cứu cho PCVT đã lưu</h2>{active.sourceDocumentId && active.sourceDocumentId !== active.id && <button className="secondary-button" disabled={busy} onClick={() => { state.open(active.sourceDocumentId!); setFile(null) }}>Nhập phiếu khác từ cùng sổ</button>}{canReadPrices ? <div className="header-actions"><button className="primary-button" disabled={busy} onClick={() => { void history() }}>Tra NCC và giá cũ</button><button className="secondary-button" disabled={busy} onClick={() => { void compare() }}>Xem so sánh báo giá</button></div> : <p className="field-help">Tài khoản chưa có quyền tra giá. Liên hệ quản trị để được cấp quyền phù hợp.</p>}<p className="field-help">Báo giá mới cần chọn đúng PCVT và liên kết từng dòng sau khi đối chiếu tên, quy cách, ĐVT.</p></section>}
    {active && lookup && <HistoryReview key={active.id} document={active} lookup={lookup} busy={busy} onSave={(choices) => { void inquiries(choices, false) }} onExport={(choices) => { void inquiries(choices, true) }} />}
    {comparison && <ComparisonReview comparison={comparison} busy={busy} />}
  </div>
}
