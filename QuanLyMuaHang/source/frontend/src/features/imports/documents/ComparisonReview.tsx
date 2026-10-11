import { useState } from 'react'
import { Pagination } from '../../../components/Pagination'
import { decimalLabel, priceBasisLabels, sourceLabels } from './documentLabels'
import type { QuoteComparison } from './types'

export function ComparisonReview({ comparison, busy }: { comparison: QuoteComparison; busy: boolean }) {
  const [page, setPage] = useState(0)
  return <section className="panel document-comparison"><div className="panel-heading"><h2>So sánh báo giá mới với giá cũ</h2></div><p className="import-note">Chỉ tính chênh lệch khi backend xác nhận cùng cơ sở so sánh. Khác ĐVT, quy cách, tiền tệ hoặc VAT chưa rõ sẽ hiển thị lý do. Chọn NCC và lập PO là bước riêng.</p>
    <div className="table-wrap" tabIndex={0} aria-label="Bảng so sánh báo giá"><table><thead><tr><th>Vật tư / nhu cầu</th><th>Lịch sử tham khảo</th><th>NCC / báo giá mới</th><th>Đơn giá mới</th><th>Chênh lệch</th></tr></thead><tbody>{comparison.lines.slice(page * 25, (page + 1) * 25).flatMap((line) => {
      const history = <>{line.history.map((row, index) => <div key={index} className="comparison-history"><strong>{row.supplierName}</strong><span className="cell-subtitle">{decimalLabel(row.unitPrice)} {row.currency ?? '?'} / {row.unit ?? '?'} · {row.purchaseDate ?? 'Chưa rõ ngày'}</span><span className="cell-subtitle">{sourceLabels[row.source] ?? row.source}{row.currencyBasis === 'ASSUMED_LEGACY' ? ' · Tiền tệ giả định' : ''}</span></div>)}{!line.history.length && 'Chưa có giá cũ phù hợp'}</>
      if (!line.quotes.length) return [<tr key={line.lineKey}><td>{line.materialName}<span className="cell-subtitle">{line.unit} · SL {decimalLabel(line.quantity)}</span></td><td>{history}</td><td colSpan={3}>Chưa có dòng báo giá được liên kết.</td></tr>]
      return line.quotes.map((quote, index) => <tr key={`${line.lineKey}-${quote.documentId}-${index}`}><td>{line.materialName}<span className="cell-subtitle">{line.unit} · SL {decimalLabel(line.quantity)}</span></td><td>{history}</td><td>{quote.supplierName}<span className="cell-subtitle">Chứng từ #{quote.documentId} · SL chào {decimalLabel(quote.quantity)}</span></td><td className="numeric-cell">{decimalLabel(quote.unitPrice)}<span className="cell-subtitle">{quote.currency ?? '?'} / {quote.unit ?? '?'} · {priceBasisLabels[quote.priceBasis ?? 'UNKNOWN'] ?? quote.priceBasis} · VAT {decimalLabel(quote.vatPercent)}%</span></td><td>{quote.comparable ? <>{decimalLabel(quote.delta)}<span className="cell-subtitle">{quote.percent == null ? 'Chưa tính được phần trăm' : `${decimalLabel(quote.percent)}%`}</span></> : <span className="field-error">Chưa thể so sánh: {quote.reason ?? 'Cơ sở giá chưa được xác nhận'}</span>}</td></tr>)
    })}</tbody></table></div><Pagination page={page} size={25} total={comparison.lines.length} pages={Math.ceil(comparison.lines.length / 25)} busy={busy} onChange={setPage} />
  </section>
}
