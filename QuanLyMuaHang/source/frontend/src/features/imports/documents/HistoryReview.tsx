import { useMemo, useState } from 'react'
import { Pagination } from '../../../components/Pagination'
import { decimalLabel, matchLabels, sourceLabels } from './documentLabels'
import type { DocumentView, HistoryLookup, InquirySelection } from './types'

export function HistoryReview({ document, lookup, busy, onSave, onExport }: {
  document: DocumentView; lookup: HistoryLookup; busy: boolean
  onSave: (choices: InquirySelection[]) => void; onExport: (choices: InquirySelection[]) => void
}) {
  const [choices, setChoices] = useState<InquirySelection[]>(document.inquirySelections ?? []), [page, setPage] = useState(0)
  const groups = useMemo(() => {
    const result = new Map<string, { name: string; lines: Set<string> }>()
    for (const choice of choices) {
      const candidate = lookup.lines.find((line) => line.lineKey === choice.lineKey)?.candidates.find((row) => row.supplierKey === choice.supplierKey)
      if (!candidate) continue
      const group = result.get(choice.supplierKey) ?? { name: candidate.supplierName, lines: new Set<string>() }
      group.lines.add(choice.lineKey); result.set(choice.supplierKey, group)
    }
    return [...result.entries()]
  }, [choices, lookup])
  function toggle(lineKey: string, supplierKey: string, checked: boolean) {
    setChoices((current) => checked ? [...current.filter((row) => row.lineKey !== lineKey || row.supplierKey !== supplierKey), { lineKey, supplierKey }] : current.filter((row) => row.lineKey !== lineKey || row.supplierKey !== supplierKey))
  }
  return <section className="panel document-history" aria-label="Nhà cung cấp và giá cũ">
    <div className="panel-heading"><div><h2>NCC từng mua và giá cũ</h2><p className="field-help">Một món có thể hỏi nhiều NCC. Giá cũ chỉ để tham khảo; không chứng minh đã nhận hàng hoặc đã thanh toán.</p></div></div>
    <p className="import-note">Tra cả lịch sử Apps Script và dòng PO phù hợp. Cần đối chiếu mã, tên, quy cách, ĐVT, ngày và tiền tệ; cảnh báo nguồn được giữ nguyên. Nhóm hỏi giá xuất ra không chứa giá cũ hay giá đối thủ.</p>
    <div className="table-wrap" tabIndex={0} aria-label="Bảng lịch sử giá, cuộn ngang để xem đủ cột"><table><thead><tr><th>Dòng PCVT</th><th>Chọn hỏi</th><th>NCC từng mua</th><th>Giá / ĐVT / tiền tệ</th><th>Ngày / nguồn</th><th>Đối chiếu</th></tr></thead><tbody>{lookup.lines.slice(page * 25, (page + 1) * 25).flatMap((line) => {
      const item = document.confirmed?.lines.find((row) => row.lineKey === line.lineKey)
      if (!line.candidates.length) return [<tr key={line.lineKey}><td>{line.materialName}<span className="cell-subtitle">{item?.specification} · {item?.unit} · SL {decimalLabel(item?.quantity)}</span></td><td colSpan={5}>Chưa có NCC / giá cũ phù hợp. Cần kiểm tra mã, tên và tìm NCC mới.</td></tr>]
      return line.candidates.map((candidate, index) => <tr key={`${line.lineKey}-${candidate.supplierKey}-${index}`}>
        <td>{line.materialName}<span className="cell-subtitle">{item?.specification} · {item?.unit} · SL {decimalLabel(item?.quantity)}</span></td>
        <td><input type="checkbox" aria-label={`Hỏi ${candidate.supplierName} cho ${line.materialName}`} disabled={busy} checked={choices.some((row) => row.lineKey === line.lineKey && row.supplierKey === candidate.supplierKey)} onChange={(event) => toggle(line.lineKey, candidate.supplierKey, event.target.checked)} /></td>
        <td>{candidate.supplierName}<span className="cell-subtitle">{candidate.supplierCode ?? 'Thiếu mã NCC'} · {candidate.count} bản ghi phù hợp</span></td>
        <td className="numeric-cell">{decimalLabel(candidate.unitPrice)}<span className="cell-subtitle">{candidate.currency ?? 'Chưa rõ tiền tệ'} / {candidate.unit ?? 'Chưa rõ ĐVT'}</span>{candidate.currencyBasis === 'ASSUMED_LEGACY' && <span className="cell-subtitle">Tiền tệ giả định từ nguồn cũ</span>}</td>
        <td>{candidate.purchaseDate ?? 'Chưa xác định ngày'}<span className="cell-subtitle">{sourceLabels[candidate.source] ?? candidate.source} · {candidate.sourceReference}</span></td>
        <td>{matchLabels[line.matchStatus] ?? line.matchStatus}<span className="cell-subtitle">{candidate.materialCode} · {candidate.materialName}</span>{!!candidate.qualityWarnings?.length && <ul className="document-warnings">{candidate.qualityWarnings.map((warning, offset) => <li key={offset}>{warning}</li>)}</ul>}</td>
      </tr>)
    })}</tbody></table>{!lookup.lines.length && <p className="empty-message">PCVT không có dòng để tra.</p>}</div>
    <Pagination page={page} size={25} total={lookup.lines.length} pages={Math.ceil(lookup.lines.length / 25)} busy={busy} onChange={setPage} />
    <div className="document-confirm"><h3>Nhóm hỏi giá đã chọn</h3>{groups.length ? <ul>{groups.map(([key, group]) => <li key={key}>{group.name}: {group.lines.size} món</li>)}</ul> : <p className="field-help">Chọn NCC cho từng món ở bảng bên trên. Có thể gửi cùng một món cho nhiều NCC.</p>}<div className="header-actions"><button className="secondary-button" disabled={busy || !choices.length} onClick={() => onSave(choices)}>Lưu nhóm hỏi giá</button><button className="primary-button" disabled={busy || !choices.length} onClick={() => onExport(choices)}>Lưu nhóm hỏi giá và tải XLSX</button></div><p className="field-help">File chứa số lượng nhu cầu của PCVT và các dòng đã chọn. Bạn tự gửi file cho NCC sau khi kiểm tra; hệ thống chưa gửi email.</p></div>
  </section>
}
