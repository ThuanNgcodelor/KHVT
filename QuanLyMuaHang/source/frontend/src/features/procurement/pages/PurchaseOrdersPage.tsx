import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { Icon } from '../../../components/Icon'
import { Pagination } from '../../../components/Pagination'
import { TableFeedback } from '../../../components/TableFeedback'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { useOrders } from '../hooks/useProcurement'
import { formatMoney, formatOrderDate } from '../format'
import { orderStatusLabels, type OrderStatus } from '../types'

export function PurchaseOrdersPage() {
  const { user } = useAuth()
  const [search, setSearch] = useState('')
  const [q, setQuery] = useState('')
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [page, setPage] = useState(0)
  const orders = useOrders({ q, status, page, size: 25 })
  const data = orders.data
  return <>
    <PageHeader title="Đơn mua hàng" description="Lập bản nháp, phát hành đơn và theo dõi các lần sửa."
      actions={user && hasPermission(user, 'PO_CREATE') && <Link className="primary-button" to="/purchase-orders/new"><Icon name="plus" />Lập đơn mua</Link>} />
    <section className="panel" aria-label="Danh sách đơn mua">
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); setPage(0) }}>
        <div className="form-field search-field"><label htmlFor="po-search">Số PO hoặc nhà cung cấp</label><input id="po-search" type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Nhập số PO hoặc tên NCC…" /></div>
        <div className="form-field"><label htmlFor="po-status">Trạng thái</label><select id="po-status" value={status} onChange={(event) => { setStatus(event.target.value as OrderStatus | ''); setPage(0) }}><option value="">Tất cả</option>{Object.entries(orderStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <button className="secondary-button" type="submit"><Icon name="search" size={16} />Tìm kiếm</button>
        <button className="text-button" type="button" onClick={() => { setSearch(''); setQuery(''); setStatus(''); setPage(0) }}>Xóa lọc</button>
        {data && !orders.error && <span className="table-count">{data.totalElements} đơn mua</span>}
      </form>
      <TableFeedback pending={orders.isPending} error={orders.error} empty={!data?.content.length} onRetry={() => { void orders.refetch() }} emptyTitle={q || status ? 'Không có đơn mua phù hợp' : 'Chưa có đơn mua'} emptyMessage={q || status ? 'Thử số PO khác hoặc xóa bộ lọc.' : 'Lập đơn mua mới hoặc nhận dữ liệu từ màn Import.'} />
      {!orders.error && data && data.content.length > 0 && <div className="table-wrap"><table>
        <caption className="sr-only">Đơn mua hàng KHVT</caption>
        <thead><tr><th scope="col">Số PO</th><th scope="col">Ngày</th><th scope="col">Nhà cung cấp</th><th scope="col">Trạng thái</th><th scope="col" className="numeric-cell">Tổng sau VAT</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.content.map((order) => <tr key={order.id}>
          <td><Link className="cell-title" to={`/purchase-orders/${order.id}`}>{order.poNumber}</Link><span className="cell-subtitle">Phiên bản {order.revision} · {order.items.length} dòng</span></td>
          <td>{formatOrderDate(order.orderDate)}</td><td>{order.supplierName}</td>
          <td><span className={`status-badge ${order.status === 'EXPORTED' ? 'active' : 'inactive'}`}>{orderStatusLabels[order.status]}</span></td>
          <td className="numeric-cell">{formatMoney(order.grandTotal, order.currency)}<span className="cell-subtitle">{order.currency}</span>{order.quantityTextLineCount > 0 && <span className="cell-subtitle">{order.quantityTextLineCount} dòng SL chữ không cộng tiền</span>}</td>
          <td><Link className="text-button" to={`/purchase-orders/${order.id}`} aria-label={`Xem ${order.poNumber}`}>Xem đơn</Link></td>
        </tr>)}</tbody>
      </table></div>}
      {!orders.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={orders.isFetching} onChange={setPage} />}
    </section>
  </>
}
