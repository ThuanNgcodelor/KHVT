import type { LegacyCommit, LegacyPreview } from '../types'

const number = (value: number) => value.toLocaleString('vi-VN')

export function LegacyImportReview({ preview, report, busy, canCommit, acknowledged, onAcknowledge, onCommit, onRefresh, onDownload }: {
  preview: LegacyPreview; report: LegacyCommit | null; busy: boolean; canCommit: boolean; acknowledged: boolean
  onAcknowledge: (value: boolean) => void; onCommit: () => void; onRefresh: () => void; onDownload: () => void
}) {
  return <section className="panel import-panel">
    <div className="panel-heading"><h2>Preview workbook — lô {preview.batchId}</h2><button className="secondary-button" disabled={busy} onClick={onRefresh}>Kiểm tra lại lô</button></div>
    <p>{preview.fileName} · <strong>{preview.status}</strong></p><p>{preview.summary.note}</p>
    <dl className="record-info">{Object.entries(preview.summary.rowsPerSheet).map(([sheet, count]) => <div key={sheet}><dt>{sheet}</dt><dd>{number(count)} dòng</dd></div>)}</dl>
    <p>{number(preview.summary.legacyPurchaseOrderGroups)} nhóm PO · {number(preview.summary.warningRows)} dòng cảnh báo · {number(preview.summary.errorRows)} dòng lỗi</p>
    <p className="field-help">Trường thiếu mã, loại tiền hoặc VAT cần được đối chiếu với nguồn. Loại tiền không ghi rõ có thể dùng VND với dấu giả định; VAT thiếu giữ trạng thái chưa xác định. Số dòng khớp chưa xác nhận tính đúng đắn của nghiệp vụ.</p>
    {preview.message && <p role="status">{preview.message}</p>}
    <details open={preview.summary.errorRows > 0}><summary>Chi tiết cảnh báo/lỗi (tối đa 30 dòng)</summary><ul>{preview.rowIssues.map((row) => <li key={`${row.sheet}-${row.rowNumber}`}>{row.sheet} · dòng {row.rowNumber}: {row.issues.join('; ')}</li>)}</ul></details>
    {(preview.summary.warningRows > 0 || preview.summary.errorRows > 0) && <button className="secondary-button" disabled={busy} onClick={onDownload}>Tải toàn bộ cảnh báo/lỗi (CSV)</button>}
    {!report && canCommit && <><label className="checkbox-label"><input type="checkbox" checked={acknowledged} disabled={busy} onChange={(event) => onAcknowledge(event.target.checked)} />Tôi đã đối chiếu số dòng/cảnh báo; đây là database thử nghiệm hoặc đã có bản sao lưu phù hợp.</label><button className="primary-button" disabled={!acknowledged || busy} onClick={onCommit}>Commit dữ liệu cũ</button></>}
    {!canCommit && !report && <p className="field-help">{preview.status === 'COMMITTED' ? 'Lô đã commit; không gửi lại để tránh nhập trùng.' : 'Lô đã xử lý hoặc còn lỗi/xung đột; chưa thể commit.'}</p>}
    {report && <div role="status"><h3>Kết quả commit</h3><p>{report.message}</p><p>{number(report.historyRowsImported)} dòng lịch sử · {number(report.purchaseOrdersImported)} PO · {number(report.committedRows)} dòng xử lý</p>
      {report.verification && <><h3>Đối chiếu dữ liệu đã ghi</h3><dl className="record-info">
        <div><dt>Dòng nguồn</dt><dd>{number(report.verification.sourceRows)}</dd></div>
        <div><dt>Dòng đã đối chiếu</dt><dd>{number(report.verification.verifiedRows)}</dd></div>
        <div><dt>Dòng lịch sử</dt><dd>{number(report.verification.historyRows)}</dd></div>
        <div><dt>PO</dt><dd>{number(report.verification.purchaseOrders)}</dd></div>
        <div><dt>Dòng PO</dt><dd>{number(report.verification.purchaseOrderItems)}</dd></div>
        <div><dt>Dòng NCC</dt><dd>{number(report.verification.supplierRows)}</dd></div>
      </dl><p className="field-help">Kết quả đối chiếu do backend trả về. Các trường thiếu hoặc được ghi theo giả định vẫn cần kiểm tra nghiệp vụ.</p></>}
    </div>}
  </section>
}
