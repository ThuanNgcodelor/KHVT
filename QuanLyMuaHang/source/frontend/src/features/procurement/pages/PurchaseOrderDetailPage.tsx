import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { PageState } from '../../../components/PageState'
import { TableFeedback } from '../../../components/TableFeedback'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { saveDownload } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { useOrder, useOrderRevisions, useIssueOrder, useCancelOrder } from '../hooks/useProcurement'
import { procurementApi } from '../procurementApi'
import { formatMoney, formatQuantity, formatOrderDate } from '../format'
import { orderStatusLabels } from '../types'

export function PurchaseOrderDetailPage() {
  const id = Number(useParams().id), { user } = useAuth()
  const orderQuery = useOrder(id), revisions = useOrderRevisions(id), issue = useIssueOrder(id), cancel = useCancelOrder(id)
  const [action, setAction] = useState<'issue' | 'cancel' | null>(null), [reason, setReason] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const order = orderQuery.data
  if (!Number.isSafeInteger(id) || id <= 0) return <PageState title="Số đơn không hợp lệ" />
  if (orderQuery.isPending) return <PageState title="Đang tải đơn mua…" />
  if (orderQuery.error || !order) return <PageState title="Chưa tải được đơn mua" message={errorMessage(orderQuery.error)} onRetry={() => { void orderQuery.refetch() }} />
  const writable = !!user && (hasPermission(user, 'PO_CREATE') || hasPermission(user, 'PO_EDIT'))
  async function download(kind: 'pdf' | 'xlsx', revision?: number) {
    setBusy(true); setError(''); setNotice('')
    try { saveDownload(await (kind === 'pdf' ? procurementApi.pdf(id, revision) : procurementApi.xlsx(id))); setNotice('Đã tải tài liệu.') } catch (failure) { setError(errorMessage(failure)) } finally { setBusy(false) }
  }
  async function confirmAction() {
    setError('')
    try {
      if (action === 'issue') { await issue.mutateAsync(); setNotice('Đã phát hành đơn và ghi lịch sử giá. Có thể tải PDF.'); await download('pdf') }
      else { await cancel.mutateAsync(reason.trim()); setNotice('Đã hủy đơn mua.') }
      setAction(null)
    } catch (failure) { setError(errorMessage(failure)) }
  }
  const currentPdf = revisions.data?.some((revision) => revision.revision === order.revision && revision.pdfAvailable)
  return <>
    <PageHeader title={order.poNumber} description={`${orderStatusLabels[order.status]} · Phiên bản ${order.revision} · ${formatOrderDate(order.orderDate)}`} actions={<>
      <Link className="secondary-button" to="/purchase-orders">Danh sách</Link>
      {user && hasPermission(user, 'PO_EDIT') && order.status !== 'CANCELLED' && <Link className="secondary-button" to={`/purchase-orders/${id}/edit`}>Sửa đơn</Link>}
      {writable && order.status !== 'CANCELLED' && <button className="primary-button" disabled={busy} onClick={() => { setAction('issue'); setError('') }}>{order.status === 'EXPORTED' ? 'Kiểm tra / phát hành lại' : 'Phát hành PDF'}</button>}
    </>} />
    {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status" className="success-notice">{notice}</p>}
    <section className="panel import-panel"><dl className="record-info"><dt>Nhà cung cấp</dt><dd>{order.supplierName}</dd><dt>Địa chỉ</dt><dd>{order.supplierAddress || 'Chưa có địa chỉ'}</dd><dt>Tiền tệ / VAT</dt><dd>{order.currency} / {order.vatPercent === null ? 'Chưa xác định' : `${order.vatPercent}%`}</dd><dt>Người lập</dt><dd>{order.preparedBy || 'Chưa có'}</dd><dt>Ghi chú</dt><dd>{order.note || '—'}</dd></dl></section>
    <section className="panel"><div className="panel-heading"><h2>{order.items.length} dòng hàng</h2><div className="header-actions"><button className="secondary-button" disabled={busy || !currentPdf} onClick={() => { void download('pdf') }}>Tải PDF</button><button className="secondary-button" disabled={busy} onClick={() => { void download('xlsx') }}>Tải XLSX</button></div></div>
      {!currentPdf && <p className="import-note">Phiên bản hiện tại chưa có PDF để tải. Người có quyền lập/sửa cần phát hành đơn.</p>}
      <div className="table-wrap"><table><thead><tr><th>STT</th><th>Vật tư</th><th>Quy cách</th><th>ĐVT</th><th>Số lượng</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>{order.items.map((item) => <tr key={item.lineNo}><td>{item.lineNo}</td><td>{item.materialName}<span className="cell-subtitle">{item.materialCode}</span></td><td>{item.specification}</td><td>{item.unit}</td><td>{formatQuantity(item.quantity, item.quantityText)}</td><td className="numeric-cell">{formatMoney(item.unitPrice, order.currency)}</td><td className="numeric-cell">{item.lineTotal === null ? 'SL chữ — không cộng' : formatMoney(item.lineTotal, order.currency)}</td></tr>)}</tbody></table></div>
      <dl className="po-totals"><dt>Tạm tính</dt><dd>{formatMoney(order.subtotal, order.currency)} {order.currency}</dd><dt>VAT</dt><dd>{formatMoney(order.taxAmount, order.currency)} {order.currency}</dd><dt>Tổng cộng</dt><dd>{formatMoney(order.grandTotal, order.currency)} {order.currency}</dd></dl>
      {order.quantityTextLineCount > 0 && <p className="import-note">{order.quantityTextLineCount} dòng số lượng chữ được giữ trên chứng từ và không cộng vào tổng tiền.</p>}
    </section>
    <section className="panel"><div className="panel-heading"><h2>Lịch sử phiên bản</h2></div><TableFeedback pending={revisions.isPending} error={revisions.error} empty={!revisions.data?.length} onRetry={() => { void revisions.refetch() }} emptyMessage="Chưa có lịch sử phiên bản." />
      {!revisions.error && revisions.data && <div className="table-wrap"><table><thead><tr><th>Phiên bản</th><th>Thời điểm</th><th>Lý do</th><th>PDF</th></tr></thead><tbody>{revisions.data.map((revision) => <tr key={revision.revision}><td>{revision.revision}</td><td>{new Date(revision.createdAt).toLocaleString('vi-VN')}</td><td>{revision.changeReason}</td><td>{revision.pdfAvailable ? <button className="text-button" disabled={busy} onClick={() => { void download('pdf', revision.revision) }}>Tải PDF phiên bản {revision.revision}</button> : 'Chưa phát hành'}</td></tr>)}</tbody></table></div>}
    </section>
    {user && hasPermission(user, 'PO_CANCEL') && order.status !== 'CANCELLED' && <section className="panel import-panel"><div className="form-field"><label htmlFor="cancel-reason">Lý do hủy</label><input id="cancel-reason" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></div><button className="danger-button" onClick={() => { setAction('cancel'); setError('') }}>Hủy đơn mua</button></section>}
    {action && <ConfirmDialog title={action === 'issue' ? 'Phát hành đơn mua?' : 'Hủy đơn mua?'} message={action === 'issue' ? 'Phát hành sẽ tạo PDF, ghi lịch sử giá và đổi trạng thái đơn. Kiểm tra nhà cung cấp, loại tiền, VAT và số lượng trước khi tiếp tục.' : 'Đơn đã hủy không thể sửa hoặc phát hành mới. Tài liệu đã phát hành được giữ trong lịch sử.'} confirmLabel={action === 'issue' ? 'Phát hành đơn' : 'Hủy đơn'} danger={action === 'cancel'} busy={issue.isPending || cancel.isPending || busy} error={error} onClose={() => setAction(null)} onConfirm={() => { void confirmAction() }} />}
  </>
}
