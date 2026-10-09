import { Link } from 'react-router-dom'
import { hasRole } from '../../auth/types'
import { PageState } from '../../../components/PageState'
import { useDashboard } from '../hooks/useDashboard'

const statusLabels = { DRAFT: 'Nháp', EXPORTED: 'Đã xuất', CANCELLED: 'Đã hủy' }
export function DashboardPage() {
  const { user, purchasing, summary } = useDashboard()
  const date = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'full', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
  if (!purchasing) return <><div className="page-intro"><div><p className="eyebrow">{date}</p><h1>Xin chào, {user?.displayName}</h1><p className="page-description">Chọn chức năng phù hợp với công việc và quyền được cấp.</p></div></div>{user && hasRole(user, ['HR_MANAGER']) && <Link className="secondary-button" to="/admin/employees">Quản lý nhân sự</Link>}</>
  if (summary.isPending) return <PageState title="Đang tải tổng quan…" />
  if (summary.error) return <PageState title="Chưa tải được tổng quan" message="Hệ thống chưa trả được dữ liệu. Hãy thử lại." onRetry={() => { void summary.refetch() }} />
  const data = summary.data
  const kpis = [
    { label: 'Đơn mua trong tháng', value: data.purchaseOrdersThisMonth, hint: 'Tính đến hôm nay' },
    { label: 'Vật tư đang hoạt động', value: data.activeMaterials, hint: 'Trong danh mục vật tư' },
    { label: 'Nhà cung cấp đang hoạt động', value: data.activeSuppliers, hint: 'Trong danh mục nhà cung cấp' },
  ]
  return <>
    <div className="page-intro"><div><p className="eyebrow">{date}</p><h1>Xin chào, {user?.displayName}</h1><p className="page-description">Theo dõi dữ liệu mua hàng của hệ thống.</p></div><button className="secondary-button" type="button" disabled={summary.isFetching} onClick={() => { void summary.refetch() }}>{summary.isFetching ? 'Đang cập nhật…' : 'Cập nhật dữ liệu'}</button></div>
    <p className="data-updated">Cập nhật lúc {new Intl.DateTimeFormat('vi-VN', { timeStyle: 'short', dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(data.generatedAt))}</p>
    <section className="kpi-grid live-kpis" aria-label="Chỉ số tổng quan">{kpis.map((kpi) => <div className="kpi-card" key={kpi.label}><p className="kpi-label">{kpi.label}</p><strong className="kpi-value">{kpi.value.toLocaleString('vi-VN')}</strong><p className="kpi-hint">{kpi.hint}</p></div>)}</section>
    <section className="panel orders-panel"><div className="panel-heading"><div><p className="section-kicker">Theo dõi mua hàng</p><h2>Đơn mua hàng gần đây</h2></div></div>{data.recentOrders.length === 0 ? <p className="empty-message">Chưa có đơn mua hàng.</p> : <div className="table-wrap"><table><caption className="sr-only">Các đơn mua hàng gần đây trong hệ thống</caption><thead><tr><th scope="col">Số PO</th><th scope="col">Nhà cung cấp</th><th scope="col">Ngày đặt</th><th scope="col">Tổng tiền</th><th scope="col">Trạng thái</th></tr></thead><tbody>{data.recentOrders.map((order) => <tr key={order.id}><td><strong className="po-code">{order.poNumber}</strong></td><td>{order.supplierName}</td><td>{order.orderDate ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(new Date(order.orderDate + 'T00:00:00Z')) : 'Chưa có ngày'}</td><td>{order.grandTotal === null ? 'Chưa xác định' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: order.currency }).format(order.grandTotal)}</td><td><span className={`status-badge ${order.status === 'EXPORTED' ? 'exported' : order.status === 'CANCELLED' ? 'cancelled' : 'draft'}`}>{statusLabels[order.status] ?? order.status}</span></td></tr>)}</tbody></table></div>}</section>
  </>
}
