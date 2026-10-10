import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { PageState } from '../../../components/PageState'
import { Dialog } from '../../../components/Dialog'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { apiClient, ApiError } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { queryParams } from '../../../services/queryParams'
import { catalogApi } from '../../catalog/catalogApi'
import type { Supplier } from '../../catalog/types'
import { MaterialLookup } from '../components/MaterialLookup'
import { useOrder, useRefreshProcurement } from '../hooks/useProcurement'
import { procurementApi } from '../procurementApi'
import type { ImportedOrderDraft, ImportedOrderItem, OrderCommand, PurchaseOrder, Currency } from '../types'
import type { PriceRecord } from '../../pricing/types'

type CartRow = { key: string; selected: boolean; materialId: number | null; materialCode: string; materialName: string; specification: string; unit: string; quantity: string; quantityText: string; textQty: boolean; unitPrice: string; supplierId: string }
type Group = { supplierId: number; supplierName: string; command: OrderCommand; result?: PurchaseOrder }
const emptyRow = (item?: ImportedOrderItem): CartRow => ({ key: crypto.randomUUID(), selected: true, materialId: item?.materialId ?? null, materialCode: item?.materialCode ?? '', materialName: item?.materialName ?? '', specification: item?.specification ?? '', unit: item?.unit ?? '', quantity: item?.quantity == null ? '' : String(item.quantity), quantityText: item?.quantityText ?? '', textQty: item?.quantity == null && !!item?.quantityText, unitPrice: item?.unitPrice == null ? '' : String(item.unitPrice), supplierId: item?.supplierId ? String(item.supplierId) : '' })
const clean = (value: string) => value.trim() || null
const positive = (value: string, label: string) => { if (!/^\d+(?:\.\d+)?$/.test(value) || Number(value) <= 0 || !Number.isFinite(Number(value))) throw new Error(`${label} cần là số dương. Dùng dấu chấm khi nhập phần thập phân.`); return value }

export function PurchaseOrderFormPage() {
  const id = Number(useParams().id), editing = Number.isSafeInteger(id) && id > 0
  const { user } = useAuth(), navigate = useNavigate(), location = useLocation(), refresh = useRefreshProcurement()
  const imported = (location.state as { importDraft?: ImportedOrderDraft } | null)?.importDraft
  const orderQuery = useOrder(id)
  const [rows, setRows] = useState<CartRow[]>(() => imported?.items.length ? imported.items.map(emptyRow) : [emptyRow()])
  const [currency, setCurrency] = useState<Currency>(imported?.currency ?? 'VND'), [supplierId, setSupplierId] = useState(''), [supplierSearch, setSupplierSearch] = useState(''), [supplierQ, setSupplierQ] = useState('')
  const [orderDate, setOrderDate] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())), [vat, setVat] = useState(''), [note, setNote] = useState(''), [preparedBy, setPreparedBy] = useState(user?.displayName ?? ''), [changeReason, setChangeReason] = useState('')
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false), [groups, setGroups] = useState<Group[] | null>(null)
  const [supplierChoices, setSupplierChoices] = useState<Record<number, Supplier>>({})
  const suppliers = useQuery({ queryKey: ['catalog', 'lookup-suppliers', supplierQ], queryFn: ({ signal }) => catalogApi.suppliers(supplierQ, signal) })
  const currentSupplier = useQuery({ queryKey: ['catalog', 'supplier', supplierId], queryFn: ({ signal }) => catalogApi.supplier(Number(supplierId), signal), enabled: !!supplierId })
  useEffect(() => { if (suppliers.data) setSupplierChoices((current) => ({ ...current, ...Object.fromEntries(suppliers.data.map((supplier) => [supplier.id, supplier])) })) }, [suppliers.data])
  useEffect(() => { const order = orderQuery.data; if (!editing || !order) return; setRows(order.items.map(emptyRow)); setCurrency(order.currency); setSupplierId(order.supplierId ? String(order.supplierId) : ''); setOrderDate(order.orderDate ?? ''); setVat(order.vatPercent == null ? '' : String(order.vatPercent)); setNote(order.note ?? ''); setPreparedBy(order.preparedBy ?? '') }, [editing, orderQuery.data])
  function patch(key: string, values: Partial<CartRow>) { setRows((current) => current.map((row) => row.key === key ? { ...row, ...values } : row)) }
  function previewGroups() {
    setError(''); setNotice('')
    try {
      if (!vat || !/^\d+(?:\.\d+)?$/.test(vat) || Number(vat) > 100) throw new Error('Chọn VAT từ 0 đến 100%. VAT chưa biết cần được xác minh trước khi lập đơn.')
      if (!orderDate) throw new Error('Chọn ngày mua.')
      if (editing && !changeReason.trim()) throw new Error('Nhập lý do sửa đơn để lưu lịch sử phiên bản.')
      const chosen = rows.filter((row) => row.selected)
      if (!chosen.length) throw new Error('Chọn ít nhất một dòng hàng.')
      const planned = new Map<number, Group>()
      for (const [index, row] of chosen.entries()) {
        const target = Number(editing ? supplierId : row.supplierId || supplierId)
        const supplier = supplierChoices[target] ?? (currentSupplier.data?.id === target ? currentSupplier.data : undefined)
        if (!supplier?.active) throw new Error(`Dòng ${index + 1}: chọn nhà cung cấp đang hoạt động.`)
        if (!row.materialName.trim()) throw new Error(`Dòng ${index + 1}: nhập tên vật tư.`)
        const quantity = row.textQty ? null : positive(row.quantity.trim(), `Số lượng dòng ${index + 1}`)
        if (row.textQty && !row.quantityText.trim()) throw new Error(`Dòng ${index + 1}: nhập mô tả số lượng chữ.`)
        const unitPrice = positive(row.unitPrice.trim(), `Đơn giá dòng ${index + 1}`)
        if (!planned.has(target)) planned.set(target, { supplierId: target, supplierName: supplier.name, command: { supplierId: target, orderDate, currency, vatPercent: vat, note: clean(note), preparedBy: clean(preparedBy), changeReason: clean(changeReason), items: [] } })
        planned.get(target)!.command.items.push({ materialId: row.materialId, materialCode: clean(row.materialCode), materialName: row.materialName.trim(), specification: clean(row.specification), unit: clean(row.unit), quantity, quantityText: row.textQty ? row.quantityText.trim() : null, unitPrice })
      }
      if ([...planned.values()].some((group) => group.command.items.length > 200)) throw new Error('Mỗi đơn chỉ nhận tối đa 200 dòng. Chia nhỏ danh sách trước khi lưu.')
      setGroups([...planned.values()])
    } catch (failure) { setError(failure instanceof Error ? failure.message : errorMessage(failure)) }
  }
  async function saveGroup(index: number) {
    if (!groups || groups[index].result) return
    setBusy(true); setError('')
    try {
      const result = editing ? await procurementApi.update(id, groups[index].command) : await procurementApi.create(groups[index].command)
      setGroups((current) => current?.map((group, i) => i === index ? { ...group, result } : group) ?? null)
      await refresh()
    } catch (failure) { setError(`${errorMessage(failure)} Nếu mất kết nối sau khi lưu, kiểm tra danh sách PO trước khi thử lại để tránh tạo trùng.`) } finally { setBusy(false) }
  }
  async function copyLatest() {
    setBusy(true); setError(''); let applied = 0, unmatched = 0
    try {
      for (const row of rows.filter((item) => item.selected && item.materialName.trim())) {
        try {
          const latest = await apiClient.get<PriceRecord>(`/prices/latest${queryParams({ materialCode: clean(row.materialCode), materialName: row.materialName.trim(), currency })}`)
          const candidates = latest.supplierCode ? await catalogApi.suppliers(latest.supplierCode) : []
          const supplier = candidates.find((item) => item.code?.toLocaleLowerCase() === latest.supplierCode?.toLocaleLowerCase())
          if (supplier) setSupplierChoices((current) => ({ ...current, [supplier.id]: supplier }))
          patch(row.key, { unitPrice: latest.unitPrice == null ? row.unitPrice : String(latest.unitPrice), supplierId: supplier ? String(supplier.id) : row.supplierId }); applied++; if (!supplier) unmatched++
        } catch (failure) { if (failure instanceof ApiError && [401,403].includes(failure.status)) { setError(failure.message); break } unmatched++ }
      }
      setNotice(`Đã đối chiếu ${applied} dòng theo ${currency}. ${unmatched ? `${unmatched} dòng cần chọn NCC hoặc kiểm tra giá thủ công.` : ''} Rà lại báo giá hiện tại trước khi lưu.`)
    } finally { setBusy(false) }
  }
  if (editing && orderQuery.isPending) return <PageState title="Đang tải đơn mua…" />
  if (editing && orderQuery.error) return <PageState title="Chưa tải được đơn mua" message={errorMessage(orderQuery.error)} onRetry={() => { void orderQuery.refetch() }} />
  if (editing && orderQuery.data?.status === 'CANCELLED') return <PageState title="Đơn đã hủy không thể sửa" />
  return <>
    <PageHeader title={editing ? `Sửa ${orderQuery.data?.poNumber ?? 'đơn mua'}` : 'Lập đơn mua'} description="Chuẩn bị các dòng hàng, kiểm tra giá/NCC và lưu bản nháp theo từng nhà cung cấp." actions={<Link className="secondary-button" to="/purchase-orders">Danh sách đơn</Link>} />
    {imported && <p className="import-note">Nhận {imported.items.length} dòng từ {imported.fileName || 'bản nháp'}. {imported.message} {imported.warnings?.length ? `${imported.warnings.length} dòng có cảnh báo cần đối chiếu.` : ''}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="success-notice" role="status">{notice}</p>}
    <section className="panel import-panel"><div className="form-grid">
      <div className="form-field"><label htmlFor="po-date">Ngày mua</label><input id="po-date" type="date" value={orderDate} onChange={(event) => setOrderDate(event.target.value)} /></div>
      <div className="form-field"><label htmlFor="po-currency">Loại tiền</label><select id="po-currency" value={currency} onChange={(event) => { setCurrency(event.target.value as Currency); setNotice('Đã đổi loại tiền. Đơn giá hiện tại được giữ; hãy đối chiếu lại trước khi lưu.') }}><option>VND</option><option>USD</option></select></div>
      <div className="form-field"><label htmlFor="po-vat">VAT (%)</label><select id="po-vat" value={vat} onChange={(event) => setVat(event.target.value)}><option value="">Chọn VAT đã xác minh</option>{['0', '5', '8', '10'].map((value) => <option key={value} value={value}>{value}%</option>)}</select></div>
      <div className="form-field"><label htmlFor="po-prepared">Người lập</label><input id="po-prepared" maxLength={255} value={preparedBy} onChange={(event) => setPreparedBy(event.target.value)} /></div>
      <div className="form-field"><label htmlFor="po-supplier-q">Tìm nhà cung cấp</label><div className="header-actions"><input id="po-supplier-q" value={supplierSearch} onChange={(event) => setSupplierSearch(event.target.value)} /><button type="button" className="secondary-button" onClick={() => setSupplierQ(supplierSearch.trim())}>Tìm NCC</button></div></div>
      <div className="form-field"><label htmlFor="po-supplier">NCC mặc định</label><select id="po-supplier" value={supplierId} onChange={(event) => setSupplierId(event.target.value)}><option value="">Chọn nhà cung cấp</option>{currentSupplier.data && !suppliers.data?.some((supplier) => supplier.id === currentSupplier.data.id) && <option value={currentSupplier.data.id}>{currentSupplier.data.name}</option>}{suppliers.data?.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.code ? `${supplier.code} — ` : ''}{supplier.name}</option>)}</select><p className="field-help">{currentSupplier.data?.address || 'Tìm theo mã/tên để lấy tối đa 50 NCC đang hoạt động.'}</p></div>
    </div>{suppliers.error && <p role="alert">Chưa tải được NCC. <button className="text-button" onClick={() => { void suppliers.refetch() }}>Thử lại</button></p>}
    <div className="form-field"><label htmlFor="po-note">Ghi chú</label><textarea id="po-note" maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></div>
    {editing && <div className="form-field"><label htmlFor="po-change">Lý do sửa đơn</label><input id="po-change" maxLength={500} value={changeReason} onChange={(event) => setChangeReason(event.target.value)} /></div>}
    </section>
    <section className="panel import-panel"><MaterialLookup onPick={(material) => setRows((current) => [...current.filter((row) => !!row.materialName.trim()), emptyRow({ materialId: material.id, materialCode: material.code, materialName: material.name, unit: material.defaultUnit, quantity: 1 })])} />
      <div className="header-actions"><button className="secondary-button" onClick={() => setRows((current) => [...current, emptyRow()])}>Thêm dòng thủ công</button>{user && hasPermission(user, 'PRICE_READ') && <button className="secondary-button" disabled={busy} onClick={() => { void copyLatest() }}>Lấy giá / NCC gần nhất</button>}{user && (hasPermission(user,'IMPORT_OPERATIONAL') || hasPermission(user,'IMPORT_LEGACY')) && <Link to="/imports">Nhận từ Excel / PDF / dữ liệu dán</Link>}</div>
    </section>
    <section className="panel"><div className="panel-heading"><h2>Danh sách chuẩn bị mua</h2><span>{rows.filter((row) => row.selected).length} / {rows.length} dòng đã chọn</span></div><div className="table-wrap"><table className="po-cart"><thead><tr><th><input type="checkbox" aria-label="Chọn tất cả dòng hàng" checked={!!rows.length && rows.every((row) => row.selected)} onChange={(event) => setRows((current) => current.map((row) => ({ ...row, selected: event.target.checked })))} /></th><th>Vật tư / Mã</th><th>Quy cách / ĐVT</th><th>Số lượng</th><th>Đơn giá ({currency})</th>{!editing && <th>NCC riêng</th>}<th>Thao tác</th></tr></thead><tbody>{rows.map((row, index) => <tr key={row.key}><td><input aria-label={`Chọn dòng ${index + 1}`} type="checkbox" checked={row.selected} onChange={(event) => patch(row.key, { selected: event.target.checked })} /></td>
      <td><input aria-label={`Tên vật tư dòng ${index + 1}`} maxLength={500} value={row.materialName} onChange={(event) => patch(row.key, { materialName: event.target.value, materialId: null })} /><input aria-label={`Mã vật tư dòng ${index + 1}`} maxLength={80} value={row.materialCode} onChange={(event) => patch(row.key, { materialCode: event.target.value, materialId: null })} /></td>
      <td><input aria-label={`Quy cách dòng ${index + 1}`} maxLength={1000} value={row.specification} onChange={(event) => patch(row.key, { specification: event.target.value })} /><input aria-label={`Đơn vị dòng ${index + 1}`} maxLength={100} value={row.unit} onChange={(event) => patch(row.key, { unit: event.target.value })} /></td>
      <td><select aria-label={`Kiểu số lượng dòng ${index + 1}`} value={row.textQty ? 'text' : 'number'} onChange={(event) => patch(row.key, { textQty: event.target.value === 'text' })}><option value="number">Số</option><option value="text">Chữ</option></select><input aria-label={`Số lượng dòng ${index + 1}`} maxLength={row.textQty ? 255 : 24} inputMode={row.textQty ? 'text' : 'decimal'} value={row.textQty ? row.quantityText : row.quantity} onChange={(event) => patch(row.key, row.textQty ? { quantityText: event.target.value } : { quantity: event.target.value })} /></td>
      <td><input aria-label={`Đơn giá dòng ${index + 1}`} inputMode="decimal" maxLength={24} value={row.unitPrice} onChange={(event) => patch(row.key, { unitPrice: event.target.value })} /></td>
      {!editing && <td><select aria-label={`NCC dòng ${index + 1}`} value={row.supplierId} onChange={(event) => patch(row.key, { supplierId: event.target.value })}><option value="">Dùng NCC mặc định</option>{suppliers.data?.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></td>}
      <td><button className="text-button" aria-label={`Bỏ dòng ${index + 1}`} onClick={() => setRows((current) => current.filter((item) => item.key !== row.key))}>Bỏ dòng</button></td>
    </tr>)}</tbody></table></div><p className="import-note">Số lượng chữ vẫn được in trên đơn và không cộng tiền. Tổng tiền chính thức do backend tính sau khi lưu. Đơn giá nhập số, dùng dấu chấm cho phần thập phân.</p></section>
    <div className="header-actions"><button className="primary-button" disabled={busy} onClick={previewGroups}>Kiểm tra và lưu bản nháp</button></div>
    {groups && <Dialog title={editing ? 'Xác nhận sửa đơn' : `Kiểm tra ${groups.length} nhóm nhà cung cấp`} onClose={() => { if (!busy) { if (groups.some((group) => group.result)) navigate('/purchase-orders'); else setGroups(null) } }} busy={busy}>
      <p className="dialog-description">Mỗi NCC được lưu thành một bản nháp. Phát hành PDF ở trang chi tiết sau khi kiểm tra tổng tiền. Nhóm đã lưu giữ kết quả và không gửi lại.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {groups.map((group, index) => <div className="group-preview" key={group.supplierId}><strong>{group.supplierName}</strong><p>{group.command.items.length} dòng · {currency} · VAT {vat}%</p>{group.result ? <Link to={`/purchase-orders/${group.result.id}`}>Đã lưu {group.result.poNumber} — Xem đơn</Link> : <button className="primary-button" disabled={busy} onClick={() => { void saveGroup(index) }}>{editing ? 'Lưu phiên bản mới' : 'Lưu bản nháp nhóm này'}</button>}</div>)}
      <div className="dialog-actions"><button className="secondary-button" disabled={busy} onClick={() => groups.some((group) => group.result) ? navigate('/purchase-orders') : setGroups(null)}>{groups.some((group) => group.result) ? 'Về danh sách đơn' : 'Quay lại chỉnh sửa'}</button></div>
    </Dialog>}
  </>
}
