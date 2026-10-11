import { useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pagination } from '../../../components/Pagination'
import { errorMessage } from '../../../services/errorMessage'
import { documentApi } from './documentApi'
import { decimalLabel, newManualLine, priceBasisLabels, sourceLocation, warningLabels } from './documentLabels'
import type { Confirmation, DocumentLine, DocumentView } from './types'

export function DocumentPreview({ document, requests, busy, onConfirm, onSource, onSheet }: {
  document: DocumentView; requests: DocumentView[]; busy: boolean
  onConfirm: (data: Confirmation) => void; onSource: () => void; onSheet: (name: string) => void
}) {
  const saved = document.confirmed, quotation = document.kind === 'QUOTATION', readOnly = !!saved
  const [lines, setLines] = useState<DocumentLine[]>(saved?.lines ?? document.parsed.lines)
  const [selected, setSelected] = useState<string[]>([]), [filter, setFilter] = useState(''), [page, setPage] = useState(0), [ack, setAck] = useState(false)
  const [title, setTitle] = useState(saved?.title ?? document.title ?? document.fileName)
  const [requestNumber, setRequestNumber] = useState(saved?.requestNumber ?? '')
  const [supplierName, setSupplierName] = useState(saved?.supplierName ?? document.parsed.supplierName ?? '')
  const [quotationDate, setQuotationDate] = useState(saved?.quotationDate ?? document.parsed.quotationDate ?? '')
  const [currency, setCurrency] = useState(saved?.currency ?? document.parsed.currency ?? '')
  const [priceBasis, setPriceBasis] = useState(saved?.priceBasis ?? document.parsed.priceBasis ?? 'UNKNOWN')
  const [vat, setVat] = useState(String(saved?.vatPercent ?? document.parsed.vatPercent ?? ''))
  const [requestId, setRequestId] = useState<number | null>(saved?.requestId ?? null)
  const [requestIdInput, setRequestIdInput] = useState('')
  const [mappings, setMappings] = useState<Record<string, string>>(saved?.requestLineMappings ?? {})
  const lastClickedIndexRef = useRef<number | null>(null)

  const listedRequest = requests.find((item) => item.id === requestId)
  const requestQuery = useQuery({ queryKey: ['imports', 'documents', requestId], queryFn: () => documentApi.get(requestId!), enabled: quotation && !!requestId && !listedRequest, retry: false })
  const request = listedRequest ?? requestQuery.data
  const requestValid = !!request && request.kind === 'REQUEST' && !!request.confirmed
  const inputId = Number(requestIdInput), inputIdValid = /^\d+$/.test(requestIdInput) && Number.isSafeInteger(inputId) && inputId > 0
  const requestNumbers = useMemo(() => [...new Set(lines.map((line) => line.requestNumber?.trim()).filter((number): number is string => !!number))], [lines])
  const visible = useMemo(() => lines.filter((line) => !filter || (filter === '__unassigned' ? !line.requestNumber?.trim() : line.requestNumber === filter)), [lines, filter])
  const chosen = lines.filter((line) => selected.includes(line.lineKey))
  const multiRequest = new Set(chosen.map((line) => line.requestNumber).filter(Boolean)).size > 1
  const canConfirm = chosen.length > 0 && title.trim() && (!quotation || (supplierName.trim() && quotationDate && currency && (!requestId || requestValid))) && !multiRequest

  const pageRows = useMemo(() => visible.slice(page * 25, (page + 1) * 25), [visible, page])
  const pageKeys = useMemo(() => pageRows.map((line) => line.lineKey), [pageRows])
  const isAllPageSelected = pageKeys.length > 0 && pageKeys.every((key) => selected.includes(key))
  const isSomePageSelected = pageKeys.some((key) => selected.includes(key)) && !isAllPageSelected
  const isAllVisibleSelected = visible.length > 0 && visible.every((line) => selected.includes(line.lineKey))

  function changeLine(key: string, field: keyof DocumentLine, value: string) {
    setLines((current) => current.map((line) => line.lineKey === key ? { ...line, [field]: value === '' && field !== 'materialName' ? null : value } : line)); setAck(false)
  }
  function chooseFilter(value: string) { setFilter(value); setPage(0); setSelected([]); setAck(false); if (value && value !== '__unassigned') setRequestNumber(value) }

  function handleHeaderCheckbox(checked: boolean) {
    if (checked) {
      setSelected((prev) => Array.from(new Set([...prev, ...pageKeys])))
    } else {
      setSelected((prev) => prev.filter((key) => !pageKeys.includes(key)))
    }
    setAck(false)
  }

  function handleRowCheck(key: string, indexInPage: number, shiftKey: boolean) {
    const isChecked = selected.includes(key)
    if (shiftKey && lastClickedIndexRef.current !== null) {
      const start = Math.min(lastClickedIndexRef.current, indexInPage)
      const end = Math.max(lastClickedIndexRef.current, indexInPage)
      const rangeKeys = pageRows.slice(start, end + 1).map((l) => l.lineKey)
      setSelected((prev) => Array.from(new Set([...prev, ...rangeKeys])))
    } else {
      setSelected((prev) => (isChecked ? prev.filter((k) => k !== key) : [...prev, key]))
      lastClickedIndexRef.current = indexInPage
    }
    setAck(false)
  }

  function confirm() {
    if (!canConfirm || !ack || busy) return
    onConfirm({ title: title.trim(), requestNumber: requestNumber.trim() || null, supplierName: quotation ? supplierName.trim() : null, quotationDate: quotationDate || null, currency: currency || null, priceBasis, vatPercent: vat || null, lines: chosen, requestId, requestLineMappings: Object.fromEntries(chosen.filter((line) => mappings[line.lineKey]).map((line) => [line.lineKey, mappings[line.lineKey]])), acknowledged: true })
  }
  return <section className="panel document-preview" aria-label="Kiểm tra chứng từ">
    <div className="panel-heading"><div><h2>{readOnly ? 'Chứng từ đã xác nhận' : 'Kiểm tra trước khi lưu'}</h2><p className="field-help">{document.fileName} · {document.kind === 'REQUEST' ? 'Yêu cầu mua' : 'Báo giá NCC'} · {readOnly ? `${lines.length} dòng đã lưu` : `${lines.length} dòng phát hiện, ${chosen.length} dòng được chọn`}</p></div><button className="secondary-button" disabled={busy} onClick={onSource}>Tải tệp gốc để đối chiếu</button></div>
    {document.duplicate && <p className="import-note" role="status">Tệp đã tồn tại. Đang mở lại chứng từ đã lưu; không tạo thêm bản trùng.</p>}
    {!!document.parsed.warnings?.length && <details className="import-note" open={!lines.length}><summary>Cảnh báo nguồn ({document.parsed.warnings.length})</summary><ul>{document.parsed.warnings.map((warning, index) => <li key={index}>{warningLabels[warning] ?? warning}</li>)}</ul></details>}
    {!lines.length && <p className="import-note">{document.parsed.warnings?.includes('SHEET_SELECTION_REQUIRED') ? 'Workbook có nhiều sheet. Chọn đúng sheet / phiên bản nguồn bên dưới để đọc các dòng hàng.' : document.parsed.format === 'PDF_SCAN' || document.parsed.warnings?.includes('OCR_NOT_SUPPORTED') ? 'PDF scan chưa có OCR; tải bản gốc để xem và thêm dòng thủ công. Chưa có dữ liệu nào được xác nhận.' : 'Chưa trích được dòng hàng. Kiểm tra sheet, tiêu đề và tệp gốc; có thể thêm dòng thủ công sau khi đối chiếu.'}</p>}
    {document.parsed.validityNote && <p className="import-note">Hiệu lực / điều kiện nguồn: {document.parsed.validityNote}</p>}
    <fieldset disabled={busy || readOnly} className="document-fields"><div className="form-grid">
      <div className="form-field span-two"><label htmlFor="document-title">Tên chứng từ</label><input id="document-title" maxLength={255} value={title} onChange={(event) => { setTitle(event.target.value); setAck(false) }} /></div>
      {!quotation && <div className="form-field"><label htmlFor="document-request-number">Số phiếu đang xử lý</label><input id="document-request-number" maxLength={100} value={requestNumber} onChange={(event) => { setRequestNumber(event.target.value); setAck(false) }} /><p className="field-help">Chỉ chọn các món cần mua của một phiếu. Sổ CVT là nguồn tham khảo, không tự coi mọi dòng là nhu cầu mua mới.</p></div>}
      {quotation && <>
        <div className="form-field"><label htmlFor="document-supplier">Nhà cung cấp báo giá</label><input id="document-supplier" maxLength={500} value={supplierName} onChange={(event) => { setSupplierName(event.target.value); setAck(false) }} /></div>
        <div className="form-field"><label htmlFor="document-date">Ngày báo giá</label><input id="document-date" type="date" value={quotationDate} onChange={(event) => { setQuotationDate(event.target.value); setAck(false) }} /></div>
        <div className="form-field"><label htmlFor="document-currency">Tiền tệ của báo giá</label><select id="document-currency" value={currency} onChange={(event) => { setCurrency(event.target.value); setAck(false) }}><option value="">Chưa xác định</option><option value="VND">VND</option><option value="USD">USD</option></select></div>
        <div className="form-field"><label htmlFor="document-price-basis">Cơ sở đơn giá</label><select id="document-price-basis" value={priceBasis} onChange={(event) => { setPriceBasis(event.target.value); setAck(false) }}>{Object.entries(priceBasisLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <div className="form-field"><label htmlFor="document-vat">VAT chung (%)</label><input id="document-vat" inputMode="decimal" value={vat} onChange={(event) => { setVat(event.target.value); setAck(false) }} /><p className="field-help">Để trống khi chưa rõ. VAT từng dòng được giữ riêng.</p></div>
        <div className="form-field"><label htmlFor="document-link-request">Liên kết với PCVT đã lưu</label><select id="document-link-request" value={requestId ?? ''} onChange={(event) => { setRequestId(event.target.value ? Number(event.target.value) : null); setMappings({}); setAck(false) }}><option value="">Chưa liên kết</option>{requestId && !listedRequest && <option value={requestId}>#{requestId} · {request?.title ?? 'Đang tìm PCVT'}</option>}{requests.map((item) => <option key={item.id} value={item.id}>{item.confirmed?.requestNumber ? `Phiếu ${item.confirmed.requestNumber} · ` : ''}{item.title}</option>)}</select><p className="field-help">Chọn từng dòng PCVT tương ứng ở bảng. Mã hàng của hãng không tự thành mã vật tư nội bộ.</p></div>
        {!readOnly && <div className="form-field"><label htmlFor="document-request-id">ID PCVT ngoài danh sách gần đây</label><input id="document-request-id" inputMode="numeric" maxLength={16} value={requestIdInput} onChange={(event) => setRequestIdInput(event.target.value.trim())} /><button className="secondary-button" disabled={!inputIdValid || busy} onClick={() => { setRequestId(inputId); setMappings({}); setAck(false) }}>Tìm PCVT theo ID</button><p className="field-help">ID nằm trong URL khi mở PCVT. Chỉ liên kết sau khi đã kiểm tra đúng phiếu và dòng hàng.</p></div>}
      </>}
    </div></fieldset>
    {quotation && requestId && !listedRequest && requestQuery.isFetching && <p className="table-message" role="status">Đang tải PCVT #{requestId}…</p>}
    {quotation && requestId && !listedRequest && requestQuery.isError && <p className="form-error" role="alert">{errorMessage(requestQuery.error)} <button className="text-button" disabled={busy} onClick={() => { void requestQuery.refetch() }}>Tìm lại PCVT</button></p>}
    {quotation && requestId && request && !requestValid && <p className="form-error" role="alert">Chứng từ #{requestId} không phải PCVT đã xác nhận. Chọn đúng PCVT hoặc bỏ liên kết trước khi lưu.</p>}
    <div className="filter-bar">
      {document.parsed.sheets?.length > 1 && <div className="form-field"><label htmlFor="document-sheet">Sheet / phiên bản nguồn</label><select id="document-sheet" disabled={busy || readOnly} value={lines[0]?.sourceSheet ?? ''} onChange={(event) => { if (event.target.value) onSheet(event.target.value) }}><option value="">Chọn sheet cần kiểm tra</option>{document.parsed.sheets.map((sheet) => <option key={sheet.name} value={sheet.name}>{sheet.name} · {sheet.detectedLines} dòng{sheet.quotationDate ? ` · ${sheet.quotationDate}` : ''}</option>)}</select></div>}
      {!quotation && !!requestNumbers.length && <div className="form-field"><label htmlFor="document-request-filter">Lọc số phiếu nguồn</label><select id="document-request-filter" value={filter} disabled={busy} onChange={(event) => chooseFilter(event.target.value)}><option value="">Tất cả phiếu — chọn từng dòng</option>{requestNumbers.map((number) => <option key={number} value={number}>Phiếu {number}</option>)}{lines.some((line) => !line.requestNumber) && <option value="__unassigned">Chưa gán phiếu</option>}</select></div>}
      {!readOnly && <button className="secondary-button" disabled={busy} onClick={() => { const line = newManualLine(); line.requestNumber = requestNumber || null; setLines((current) => [...current, line]); setSelected((current) => [...current, line.lineKey]); setFilter(''); setPage(Math.floor(lines.length / 25)); setAck(false) }}>Thêm dòng thủ công</button>}
      <span className="table-count">{visible.length} dòng trong bộ lọc</span>
    </div>

    {!readOnly && <div className="selection-toolbar" role="region" aria-label="Thao tác chọn dòng">
      <div className="selection-stats">
        <span className="selection-badge"><strong>{chosen.length}</strong> / {visible.length} dòng đã chọn</span>
        {visible.length > 25 && <span className="selection-hint">Mẹo: Giữ phím <strong>Shift + Click</strong> để chọn nhanh nhiều dòng liên tiếp</span>}
      </div>
      <div className="selection-actions">
        <button type="button" className="secondary-button" disabled={busy || isAllPageSelected || !pageKeys.length} onClick={() => { setSelected((prev) => Array.from(new Set([...prev, ...pageKeys]))); setAck(false) }}>
          Chọn trang này ({pageKeys.length} dòng)
        </button>
        <button type="button" className="primary-button" disabled={busy || isAllVisibleSelected || !visible.length} onClick={() => { setSelected(visible.map((line) => line.lineKey)); setAck(false) }}>
          {isAllVisibleSelected ? 'Đã chọn tất cả' : `Chọn tất cả ${visible.length} dòng`}
        </button>
        {selected.length > 0 && <button type="button" className="text-button" disabled={busy} onClick={() => { setSelected([]); setAck(false) }}>Bỏ chọn tất cả</button>}
      </div>
    </div>}

    <p className="import-note">Số thập phân dùng dấu chấm, không nhập dấu phân nhóm nghìn. Giữ tên và quy cách nguồn; sửa mã nội bộ chỉ khi đã đối chiếu. {!readOnly && 'Dòng không chọn sẽ không nằm trong chứng từ xác nhận; tệp gốc vẫn được giữ.'}</p>
    <div className="table-wrap" tabIndex={0} aria-label="Bảng dòng chứng từ, cuộn ngang để xem đủ cột"><table className="document-lines"><thead><tr>
      {!readOnly && <th className="select-col" scope="col"><input type="checkbox" aria-label="Chọn tất cả dòng trang này" title={isAllPageSelected ? 'Bỏ chọn trang này' : 'Chọn cả 25 dòng trang này'} disabled={busy || !pageKeys.length} checked={isAllPageSelected} ref={(el) => { if (el) el.indeterminate = isSomePageSelected }} onChange={(e) => handleHeaderCheckbox(e.target.checked)} /></th>}
      <th scope="col">Nguồn</th><th scope="col">Tên vật tư / mã nội bộ</th><th scope="col">Quy cách</th><th scope="col">ĐVT</th><th scope="col">Số lượng</th>
      {quotation && <><th scope="col">Đơn giá</th><th scope="col">VAT / cơ sở / tiền tệ</th>{requestId && <th scope="col">Dòng PCVT tương ứng</th>}</>}
      <th scope="col">Ghi chú / kiểm tra</th>
    </tr></thead><tbody>{pageRows.map((line, indexInPage) => {
      const isSelected = selected.includes(line.lineKey)
      return <tr key={line.lineKey} className={isSelected ? 'document-line-row row-selected' : 'document-line-row'}>
        {!readOnly && <td className="select-col"><input type="checkbox" aria-label={`Chọn ${sourceLocation(line)}`} disabled={busy} checked={isSelected} onChange={(event) => { const nativeEvent = event.nativeEvent as MouseEvent; handleRowCheck(line.lineKey, indexInPage, !!nativeEvent.shiftKey) }} /></td>}
        <td className="source-cell">{sourceLocation(line)}{line.requestNumber && <span className="cell-subtitle">Phiếu {line.requestNumber}</span>}</td>
        <td>{readOnly ? <>{line.materialName}<span className="cell-subtitle">{line.materialCode}</span></> : <><input aria-label={`Tên vật tư ${line.lineKey}`} disabled={busy} value={line.materialName} maxLength={500} onChange={(event) => changeLine(line.lineKey, 'materialName', event.target.value)} /><input aria-label={`Mã nội bộ ${line.lineKey}`} disabled={busy} value={line.materialCode ?? ''} placeholder="Mã nội bộ đã xác nhận" maxLength={80} onChange={(event) => changeLine(line.lineKey, 'materialCode', event.target.value)} /></>}</td>
        <td>{readOnly ? line.specification : <textarea aria-label={`Quy cách ${line.lineKey}`} disabled={busy} rows={2} value={line.specification ?? ''} maxLength={1000} onChange={(event) => changeLine(line.lineKey, 'specification', event.target.value)} />}</td>
        <td>{readOnly ? line.unit : <input aria-label={`ĐVT ${line.lineKey}`} disabled={busy} value={line.unit ?? ''} maxLength={50} onChange={(event) => changeLine(line.lineKey, 'unit', event.target.value)} />}</td>
        <td>{readOnly ? decimalLabel(line.quantity ?? line.quantityText) : <><input aria-label={`Số lượng ${line.lineKey}`} disabled={busy} inputMode="decimal" value={line.quantity ?? ''} onChange={(event) => changeLine(line.lineKey, 'quantity', event.target.value)} /><input aria-label={`Số lượng chữ ${line.lineKey}`} disabled={busy} value={line.quantityText ?? ''} placeholder="SL bằng chữ nếu có" maxLength={200} onChange={(event) => changeLine(line.lineKey, 'quantityText', event.target.value)} /></>}</td>
        {quotation && <><td>{readOnly ? decimalLabel(line.unitPrice) : <input aria-label={`Đơn giá ${line.lineKey}`} disabled={busy} inputMode="decimal" value={line.unitPrice ?? ''} onChange={(event) => changeLine(line.lineKey, 'unitPrice', event.target.value)} />}<span className="cell-subtitle">Thành tiền nguồn: {decimalLabel(line.lineTotal)}</span></td><td>{readOnly ? <>{line.vatPercent == null ? 'Chưa rõ VAT' : `${decimalLabel(line.vatPercent)}%`}<span className="cell-subtitle">{priceBasisLabels[line.priceBasis ?? 'UNKNOWN'] ?? line.priceBasis} · {line.currency ?? currency ?? 'Chưa rõ tiền tệ'}</span></> : <><input aria-label={`VAT ${line.lineKey}`} disabled={busy} inputMode="decimal" value={line.vatPercent ?? ''} placeholder="Theo VAT chung nếu trống" onChange={(event) => changeLine(line.lineKey, 'vatPercent', event.target.value)} /><select aria-label={`Cơ sở giá ${line.lineKey}`} disabled={busy} value={line.priceBasis ?? 'UNKNOWN'} onChange={(event) => changeLine(line.lineKey, 'priceBasis', event.target.value)}>{Object.entries(priceBasisLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><select aria-label={`Tiền tệ ${line.lineKey}`} disabled={busy} value={line.currency ?? ''} onChange={(event) => changeLine(line.lineKey, 'currency', event.target.value)}><option value="">Theo chứng từ</option><option value="VND">VND</option><option value="USD">USD</option></select></>}</td>{requestId && <td><select aria-label={`Liên kết PCVT ${line.lineKey}`} value={mappings[line.lineKey] ?? ''} disabled={busy || readOnly || !requestValid} onChange={(event) => { setMappings((current) => ({ ...current, [line.lineKey]: event.target.value })); setAck(false) }}><option value="">Chọn dòng đã đối chiếu</option>{requestValid && request.confirmed!.lines.map((target) => <option key={target.lineKey} value={target.lineKey}>{target.materialName} · {target.specification} · {target.unit}</option>)}</select></td>}</>}
        <td>{!readOnly && <textarea aria-label={`Ghi chú ${line.lineKey}`} disabled={busy} value={line.note ?? ''} rows={2} maxLength={2000} onChange={(event) => changeLine(line.lineKey, 'note', event.target.value)} />}{readOnly && line.note}{!!line.issues?.length && <details><summary>{line.issues.length} cảnh báo</summary><ul>{line.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul></details>}{Object.keys(line.raw ?? {}).length > 0 && <details><summary>Giá trị gốc</summary><dl className="document-raw">{Object.entries(line.raw).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></details>}</td>
      </tr>
    })}</tbody></table>{!visible.length && <p className="empty-message">Không có dòng trong bộ lọc này.</p>}</div>
    <Pagination page={page} size={25} total={visible.length} pages={Math.ceil(visible.length / 25)} busy={busy} onChange={setPage} />
    {!readOnly && <div className="document-confirm"><label className="checkbox-field"><input type="checkbox" checked={ack} disabled={busy || !canConfirm} onChange={(event) => setAck(event.target.checked)} />Tôi đã kiểm tra tệp gốc, các dòng được chọn và thông tin đã sửa.</label>{multiRequest && <p className="field-error">Các dòng thuộc nhiều số phiếu. Lọc và xác nhận từng PCVT riêng.</p>}{requestId && chosen.some((line) => !mappings[line.lineKey]) && <p className="field-help">Dòng báo giá chưa liên kết sẽ được lưu riêng và chưa tham gia so sánh PCVT.</p>}<button className="primary-button" disabled={busy || !canConfirm || !ack} onClick={confirm}>{busy ? 'Đang xử lý…' : `Xác nhận và lưu ${chosen.length} dòng`}</button><p className="field-help">Lưu chứng từ để tra và so giá. Thao tác này không ghi lịch sử mua, không phát hành PO.</p></div>}
  </section>
}
