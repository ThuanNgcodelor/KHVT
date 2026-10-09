import { PageHeader } from '../../../components/PageHeader'
import { PageState } from '../../../components/PageState'
import { Icon } from '../../../components/Icon'
import { useDashboard } from '../hooks/useDashboard'

const statusLabels = { DRAFT: 'Nháp', EXPORTED: 'Đã xuất', CANCELLED: 'Đã hủy' }
const dateTime = new Intl.DateTimeFormat('vi-VN', { timeStyle: 'short', dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })
const orderDate = new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' })

export function DashboardPage() {
  const { summary } = useDashboard()
  if (summary.isPending) return <PageState title="Đang tải tổng quan…" />
  if (summary.error) return <PageState title="Chưa tải được tổng quan" message="Hệ thống chưa trả được dữ liệu. Hãy thử lại." onRetry={() => { void summary.refetch() }} />
  const data = summary.data
  const kpis = [
    { label: 'Đơn mua trong tháng', value: data.purchaseOrdersThisMonth, hint: 'Tính đến hôm nay' },
    { label: 'Vật tư đang hoạt động', value: data.activeMaterials, hint: 'Trong danh mục vật tư' },
    { label: 'Nhà cung cấp đang hoạt động', value: data.activeSuppliers, hint: 'Trong danh mục nhà cung cấp' },
  ]
  return <>
    <PageHeader title="Tổng quan mua hàng" description="Theo dõi danh mục và các đơn mua hàng của đơn vị."
      actions={<button className="secondary-button" type="button" disabled={summary.isFetching} onClick={() => { void summary.refetch() }}>
        <Icon name="refresh" size={16} />{summary.isFetching ? 'Đang cập nhật…' : 'Cập nhật dữ liệu'}
      </button>} />
    <p className="data-updated">Dữ liệu lúc {dateTime.format(new Date(data.generatedAt))}</p>
    <section className="kpi-grid" aria-label="Chỉ số tổng quan">
      {kpis.map((kpi) => <div className="kpi-card" key={kpi.label}>
        <p className="kpi-label">{kpi.label}</p>
        <strong className="kpi-value">{kpi.value.toLocaleString('vi-VN')}</strong>
        <p className="kpi-hint">{kpi.hint}</p>
      </div>)}
    </section>
    <section className="panel orders-panel">
      <div className="panel-heading"><h2>Đơn mua hàng gần đây</h2></div>
      {data.recentOrders.length === 0 ? <p className="empty-message">Chưa có đơn mua hàng.</p> : <div className="table-wrap"><table>
        <caption className="sr-only">Các đơn mua hàng gần đây trong hệ thống</caption>
        <thead><tr><th scope="col">Số PO</th><th scope="col">Nhà cung cấp</th><th scope="col">Ngày đặt</th><th scope="col" className="numeric-cell">Tổng tiền</th><th scope="col">Trạng thái</th></tr></thead>
        <tbody>{data.recentOrders.map((order) => <tr key={order.id}>
          <td><strong className="po-code">{order.poNumber}</strong></td>
          <td>{order.supplierName}</td>
          <td>{order.orderDate ? orderDate.format(new Date(`${order.orderDate}T00:00:00Z`)) : 'Chưa có ngày'}</td>
          <td className="numeric-cell">{order.grandTotal === null ? 'Chưa xác định' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: order.currency }).format(order.grandTotal)}</td>
          <td><span className={`status-badge ${order.status === 'EXPORTED' ? 'exported' : order.status === 'CANCELLED' ? 'cancelled' : 'draft'}`}>{statusLabels[order.status] ?? order.status}</span></td>
        </tr>)}</tbody>
      </table></div>}
    </section>
  </>
}
