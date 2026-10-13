import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { TableFeedback } from '../../../components/TableFeedback'
import { Pagination } from '../../../components/Pagination'
import { Dialog } from '../../../components/Dialog'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { saveDownload } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { pricingApi } from '../pricingApi'
import type { PriceRecord } from '../types'
import { SortTh } from '../../../components/SortTh'

const money = (row: PriceRecord) => row.unitPrice == null ? 'Chưa có giá' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: row.currency === 'USD' ? 2 : 0 }).format(Number(row.unitPrice))
export function PriceSearchPage() {
  const { user } = useAuth(), navigate = useNavigate()
  const [search, setSearch] = useState(''), [q, setQ] = useState(''), [currency, setCurrency] = useState(''), [category, setCategory] = useState(''), [page, setPage] = useState(0)
  const [supplierSearch, setSupplierSearch] = useState(''), [supplierQ, setSupplierQ] = useState(''), [supplierPage, setSupplierPage] = useState(0)
  const [sort, setSort] = useState('purchaseDate,desc')
  const [busy, setBusy] = useState(false), [notice, setNotice] = useState(''), [failure, setFailure] = useState('')
  const [latest, setLatest] = useState<PriceRecord | null>(null)
  const filters = { q, currency, category, page, size: 25, sort }
  const history = useQuery({ queryKey: ['pricing', filters], queryFn: ({ signal }) => pricingApi.history(filters, signal) })
  const supplierHistory = useQuery({
    queryKey: ['pricing-by-supplier', supplierQ, currency, supplierPage],
    queryFn: ({ signal }) => pricingApi.supplierHistory(supplierQ, currency, supplierPage, 25, 'purchaseDate,desc', signal),
    enabled: supplierQ.length > 0,
  })
  const data = history.data

  function handleSort(newSort: string) {
    setSort(newSort)
    setPage(0)
  }
  async function latestFor(row: PriceRecord) {
    setBusy(true); setFailure(''); setNotice('')
    try { setLatest(await pricingApi.latest(row)) } catch (error) { setFailure(errorMessage(error)) } finally { setBusy(false) }
  }
  async function exportFile() {
    setBusy(true); setFailure(''); setNotice('')
    try { const file = await pricingApi.xlsx(filters); saveDownload(file); setNotice(file.truncated ? 'Đã tải file. Kết quả vượt giới hạn xuất; hãy thu hẹp bộ lọc để lấy đủ dữ liệu.' : 'Đã tải lịch sử giá theo bộ lọc đang áp dụng.') } catch (error) { setFailure(errorMessage(error)) } finally { setBusy(false) }
  }
  function draftFrom(row: PriceRecord) {
    navigate('/purchase-orders/new', { state: { importDraft: { fileName: 'Tra cứu giá', sourceType: 'PRICE_HISTORY', currency: row.currency,
      message: 'Đơn giá và NCC lấy từ lịch sử; kiểm tra lại báo giá hiện tại trước khi lập đơn.',
      items: [{ materialCode: row.materialCode, materialName: row.materialName, unit: row.unit, unitPrice: row.unitPrice, quantity: 1, supplierName: row.supplierName, supplierCode: row.supplierCode }], warnings: [] } } })
  }
  return <>
    <PageHeader title="Tra cứu giá" description="Tìm theo mã hoặc tên không dấu. Giá gần nhất được đối chiếu riêng theo vật tư và loại tiền." actions={<button className="secondary-button" disabled={busy} onClick={() => { void exportFile() }}>Tải XLSX</button>} />
    {failure && <p className="form-error" role="alert">{failure}</p>}{notice && <p className="success-notice" role="status">{notice}</p>}
    <section className="panel" aria-label="Lịch sử giá">
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); setQ(search.trim()); setPage(0) }}>
        <div className="form-field search-field"><label htmlFor="price-q">Mã hoặc tên vật tư</label><input id="price-q" type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
        <div className="form-field"><label htmlFor="price-currency">Loại tiền</label><select id="price-currency" value={currency} onChange={(event) => { setCurrency(event.target.value); setPage(0) }}><option value="">Tất cả</option><option>VND</option><option>USD</option></select></div>
        <div className="form-field"><label htmlFor="price-category">Nhóm</label><select id="price-category" value={category} onChange={(event) => { setCategory(event.target.value); setPage(0) }}><option value="">Tất cả</option><option value="MATERIAL">Vật tư</option><option value="SERVICE">Dịch vụ</option><option value="OTHER">Khác</option></select></div>
        <button type="submit" className="secondary-button">Tìm kiếm</button>
        <button type="button" className="text-button" onClick={() => { setSearch(''); setQ(''); setCurrency(''); setCategory(''); setSort('purchaseDate,desc'); setPage(0) }}>Xóa lọc</button>
      </form>
      <form className="filter-bar" aria-label="Tra cứu theo nhà cung cấp" onSubmit={(event) => { event.preventDefault(); setSupplierQ(supplierSearch.trim()); setSupplierPage(0) }}>
        <div className="form-field search-field"><label htmlFor="price-supplier-q">Nhà cung cấp đã mua</label><input id="price-supplier-q" type="search" value={supplierSearch} onChange={(event) => setSupplierSearch(event.target.value)} placeholder="Nhập tên hoặc mã NCC" /></div>
        <button type="submit" className="secondary-button">Xem mặt hàng và lịch sử mua</button>
        {supplierQ && <button type="button" className="text-button" onClick={() => { setSupplierSearch(''); setSupplierQ(''); setSupplierPage(0) }}>Xóa tra NCC</button>}
      </form>
      {supplierQ && <section className="panel" aria-label="Mặt hàng đã mua từ nhà cung cấp">
        <h2 className="section-title">Mặt hàng đã mua từ NCC: {supplierQ}</h2>
        <TableFeedback pending={supplierHistory.isPending} error={supplierHistory.error} empty={!supplierHistory.data?.content.length} onRetry={() => { void supplierHistory.refetch() }} emptyTitle="Chưa tìm thấy lịch sử mua" emptyMessage="Thử tên hoặc mã nhà cung cấp khác." />
        {!supplierHistory.error && supplierHistory.data && supplierHistory.data.content.length > 0 && <div className="table-wrap"><table><caption className="sr-only">Mặt hàng và lịch sử mua theo nhà cung cấp</caption><thead><tr><th scope="col">Mặt hàng</th><th scope="col">Ngày mua</th><th scope="col" className="numeric-cell">Số lượng</th><th scope="col" className="numeric-cell">Đơn giá</th><th scope="col">Nguồn</th><th scope="col">Thao tác</th></tr></thead><tbody>{supplierHistory.data.content.map((row) => <tr key={row.id}>
          <td><strong className="cell-title">{row.materialName}</strong><span className="cell-subtitle">{row.materialCode || 'Chưa có mã'} · {row.unit || 'Chưa có ĐVT'}</span></td>
          <td>{row.purchaseDate ? new Date(`${row.purchaseDate}T00:00:00`).toLocaleDateString('vi-VN') : 'Chưa có ngày'}</td>
          <td className="numeric-cell">{row.quantity == null ? row.quantityText || '—' : new Intl.NumberFormat('vi-VN').format(row.quantity)} {row.unit || ''}</td>
          <td className="numeric-cell">{money(row)} {row.currency}</td><td>{row.source === 'PURCHASE_ORDER' ? 'Đơn mua hàng' : 'Lịch sử nhập'}<span className="cell-subtitle">{row.sourceSheet}{row.sourceRowNumber ? ` · dòng ${row.sourceRowNumber}` : ''}</span></td>
          <td>{user && hasPermission(user, 'PO_CREATE') && row.unitPrice != null && <button className="text-button" onClick={() => draftFrom(row)}>Lập đơn từ dòng</button>}</td>
        </tr>)}</tbody></table></div>}
        {!supplierHistory.error && supplierHistory.data && <Pagination page={supplierHistory.data.number} size={supplierHistory.data.size} total={supplierHistory.data.totalElements} pages={supplierHistory.data.totalPages} busy={supplierHistory.isFetching} onChange={setSupplierPage} />}
      </section>}
      <TableFeedback pending={history.isPending} error={history.error} empty={!data?.content.length} onRetry={() => { void history.refetch() }} emptyTitle="Chưa có lịch sử giá phù hợp" emptyMessage="Thử tên khác hoặc xóa bộ lọc. Giá được ghi khi import lịch sử hoặc phát hành đơn." />
      {!history.error && data && data.content.length > 0 && <div className="table-wrap"><table><caption className="sr-only">Lịch sử giá mua hàng</caption><thead><tr>
        <SortTh label="Vật tư" column="materialName" currentSort={sort} onSort={handleSort} defaultDirection="asc" />
        <SortTh label="Ngày mua" column="purchaseDate" currentSort={sort} onSort={handleSort} defaultDirection="desc" />
        <SortTh label="Đơn giá" column="unitPrice" currentSort={sort} onSort={handleSort} defaultDirection="desc" className="numeric-cell" />
        <SortTh label="NCC" column="supplierName" currentSort={sort} onSort={handleSort} defaultDirection="asc" />
        <th scope="col">Nguồn</th>
        <th scope="col">Thao tác</th>
      </tr></thead><tbody>{data.content.map((row) => <tr key={row.id}>
        <td><strong className="cell-title">{row.materialName}</strong><span className="cell-subtitle">{row.materialCode || 'Chưa có mã'} · {row.unit || 'Chưa có ĐVT'}</span></td>
        <td>{row.purchaseDate ? new Date(`${row.purchaseDate}T00:00:00`).toLocaleDateString('vi-VN') : 'Chưa có ngày'}</td>
        <td className="numeric-cell">{money(row)} {row.currency}<span className="cell-subtitle">{row.currencyBasis === 'ASSUMED_LEGACY' ? 'Loại tiền giả định từ dữ liệu cũ' : ''}</span></td>
        <td>{row.supplierName || 'Chưa xác định'}<span className="cell-subtitle">{row.supplierCode}</span></td><td>{row.source === 'PURCHASE_ORDER' ? 'Đơn mua hàng' : 'Lịch sử nhập'}<span className="cell-subtitle">{row.sourceSheet}{row.sourceRowNumber ? ` · dòng ${row.sourceRowNumber}` : ''}</span></td>
        <td><button className="text-button" disabled={busy} onClick={() => { void latestFor(row) }}>Giá gần nhất</button>{user && hasPermission(user, 'PO_CREATE') && row.unitPrice != null && <button className="text-button" onClick={() => draftFrom(row)}>Lập đơn từ dòng</button>}</td>
      </tr>)}</tbody></table></div>}
      {!history.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={history.isFetching} onChange={setPage} />}
    </section>
    {latest && <Dialog title="Giá mua gần nhất" onClose={() => setLatest(null)}>
      <p className="dialog-description">Bản ghi có ngày mua gần nhất, cùng vật tư và loại tiền. Kiểm tra báo giá hiện tại trước khi dùng.</p>
      <dl className="record-info"><dt>Vật tư</dt><dd>{latest.materialName}</dd><dt>Ngày mua</dt><dd>{latest.purchaseDate}</dd><dt>Đơn giá</dt><dd>{money(latest)} {latest.currency} / {latest.unit || 'Chưa có ĐVT'}</dd><dt>Nhà cung cấp</dt><dd>{latest.supplierName || 'Chưa xác định'}</dd></dl>
      {latest.currencyBasis === 'ASSUMED_LEGACY' && <p className="field-help">Loại tiền của bản ghi cũ đang được giả định là VND.</p>}
      <div className="dialog-actions"><button className="secondary-button" onClick={() => setLatest(null)}>Đóng</button>{user && hasPermission(user, 'PO_CREATE') && latest.unitPrice != null && <button className="primary-button" onClick={() => draftFrom(latest)}>Lập đơn từ giá này</button>}</div>
    </Dialog>}
  </>
}
