import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../../../components/PageHeader'
import { PageState } from '../../../components/PageState'
import { Dialog } from '../../../components/Dialog'
import { Icon } from '../../../components/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { apiClient, ApiError, saveDownload } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { queryParams } from '../../../services/queryParams'
import { catalogApi } from '../../catalog/catalogApi'
import { SupplierForm } from '../../catalog/components/SupplierForm'
import type { Supplier } from '../../catalog/types'
import { personnelApi } from '../../personnel/personnelApi'
import { MaterialLookup } from '../components/MaterialLookup'
import { useOrder, useRefreshProcurement } from '../hooks/useProcurement'
import { procurementApi } from '../procurementApi'
import type { ImportedOrderDraft, ImportedOrderItem, OrderCommand, PurchaseOrder, Currency } from '../types'
import type { PriceRecord } from '../../pricing/types'
import { DocumentWorkbench } from '../../imports/documents/DocumentWorkbench'

type CartRow = { key: string; selected: boolean; materialId: number | null; materialCode: string; materialName: string; specification: string; unit: string; quantity: string; quantityText: string; textQty: boolean; unitPrice: string; supplierId: string }
type Group = { supplierId: number; supplierName: string; command: OrderCommand; result?: PurchaseOrder; exportStatus?: 'SAVING' | 'CREATE_REJECTED' | 'CREATE_FAILED' | 'ISSUING' | 'ISSUED' | 'ISSUE_FAILED' | 'DOWNLOAD_FAILED'; exportError?: string }
const emptyRow = (item?: ImportedOrderItem): CartRow => ({ key: crypto.randomUUID(), selected: true, materialId: item?.materialId ?? null, materialCode: item?.materialCode ?? '', materialName: item?.materialName ?? '', specification: item?.specification ?? '', unit: item?.unit ?? '', quantity: item?.quantity == null ? '' : String(item.quantity), quantityText: item?.quantityText ?? '', textQty: item?.quantity == null && !!item?.quantityText, unitPrice: item?.unitPrice == null ? '' : String(item.unitPrice), supplierId: item?.supplierId ? String(item.supplierId) : '' })
const clean = (value: string) => value.trim() || null
const positive = (value: string, label: string) => { if (!/^\d+(?:\.\d+)?$/.test(value) || Number(value) <= 0 || !Number.isFinite(Number(value))) throw new Error(`${label} cần là số dương. Dùng dấu chấm khi nhập phần thập phân.`); return value }

export function PurchaseOrderFormPage() {
  const id = Number(useParams().id), editing = Number.isSafeInteger(id) && id > 0
  const { user } = useAuth(), navigate = useNavigate(), location = useLocation(), refresh = useRefreshProcurement()
  const routeState = location.state as { importDraft?: ImportedOrderDraft; unlockedReason?: string } | null
  const routeImport = routeState?.importDraft
  const routeUnlockedReason = routeState?.unlockedReason
  const [imported, setImported] = useState(routeImport)
  const orderQuery = useOrder(id)
  const [rows, setRows] = useState<CartRow[]>(() => imported?.items.length ? imported.items.map(emptyRow) : [emptyRow()])
  const [currency, setCurrency] = useState<Currency>(imported?.currency ?? 'VND'), [supplierId, setSupplierId] = useState(''), [supplierSearch, setSupplierSearch] = useState(''), [supplierQ, setSupplierQ] = useState('')
  const [supplierSearchResults, setSupplierSearchResults] = useState<Supplier[] | null>(null)
  const [orderDate, setOrderDate] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())), [vat, setVat] = useState(''), [note, setNote] = useState(''), [preparedBy, setPreparedBy] = useState(user?.displayName ?? ''), [changeReason, setChangeReason] = useState(routeUnlockedReason ?? '')
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false), [documentBusy, setDocumentBusy] = useState(false), [groups, setGroups] = useState<Group[] | null>(null)
  const employeesQuery = useQuery({
    queryKey: ['personnel', 'active-employees'],
    queryFn: ({ signal }) => personnelApi.employees({ status: 'ACTIVE', page: 0, size: 200 }, signal)
  })
  useEffect(() => {
    if (routeUnlockedReason && !changeReason) {
      setChangeReason(routeUnlockedReason)
    }
  }, [routeUnlockedReason])
  useEffect(() => {
    if (!editing && user && !preparedBy) {
      setPreparedBy(user.displayName?.trim() || user.email || '')
    }
  }, [user, editing, preparedBy])
  const [showAddSupplier, setShowAddSupplier] = useState(false)
  const [supplierChoices, setSupplierChoices] = useState<Record<number, Supplier>>({})
  const [latestHistory, setLatestHistory] = useState<Record<string, { price: PriceRecord; supplierId: number | null }>>({})
  const suppliers = useQuery({ queryKey: ['catalog', 'lookup-suppliers', supplierQ], queryFn: ({ signal }) => catalogApi.suppliers(supplierQ, signal) })
  const currentSupplier = useQuery({ queryKey: ['catalog', 'supplier', supplierId], queryFn: ({ signal }) => catalogApi.supplier(Number(supplierId), signal), enabled: !!supplierId })
  const supplierOptions = [...new Map([...Object.values(supplierChoices), ...(suppliers.data ?? []), ...(currentSupplier.data ? [currentSupplier.data] : [])].map((supplier) => [supplier.id, supplier])).values()]
  useEffect(() => { if (suppliers.data) setSupplierChoices((current) => ({ ...current, ...Object.fromEntries(suppliers.data.map((supplier) => [supplier.id, supplier])) })) }, [suppliers.data])

  async function handleSearchSupplier(queryText: string) {
    const q = queryText.trim()
    if (!q) {
      setSupplierSearchResults(null)
      return
    }
    setSupplierQ(q)
    try {
      const list = await catalogApi.suppliers(q)
      if (list.length > 0) {
        setSupplierChoices((current) => ({ ...current, ...Object.fromEntries(list.map((s) => [s.id, s])) }))
        if (list.length === 1) {
          setSupplierId(String(list[0].id))
          setNotice(`Đã tự động chọn NCC: ${list[0].code ? `${list[0].code} — ` : ''}${list[0].name}`)
          setSupplierSearchResults(null)
        } else {
          setSupplierSearchResults(list)
          setNotice(`Tìm thấy ${list.length} nhà cung cấp phù hợp. Bấm để chọn bên dưới.`)
        }
      } else {
        setSupplierSearchResults([])
        setError(`Không tìm thấy nhà cung cấp nào với từ khóa "${q}". Bấm [Thêm nhanh NCC mới] nếu muốn tạo mới.`)
      }
    } catch (err) {
      setError(`Lỗi tìm NCC: ${errorMessage(err)}`)
    }
  }
  useEffect(() => {
    const ids = [...new Set((imported?.items ?? []).map((item) => item.supplierId).filter((value): value is number => typeof value === 'number' && value > 0))]
    if (!ids.length) return
    let cancelled = false
    void Promise.all(ids.map((id) => catalogApi.supplier(id)))
      .then((loaded) => { if (!cancelled) setSupplierChoices((current) => ({ ...current, ...Object.fromEntries(loaded.map((supplier) => [supplier.id, supplier])) })) })
      .catch((failure) => { if (!cancelled) setError(`Không tải được thông tin NCC đã chọn. Hãy tìm lại NCC trước khi lập đơn. ${errorMessage(failure)}`) })
    return () => { cancelled = true }
  }, [imported])
  useEffect(() => {
    if (routeImport && routeImport.sourceType === 'PRICE_HISTORY' && rows.length > 0) {
      const initHist: Record<string, { price: PriceRecord; supplierId: number | null }> = {}
      rows.forEach((r, idx) => {
        const item = routeImport.items[idx]
        if (item && item.unitPrice != null) {
          initHist[r.key] = {
            price: {
              id: 0,
              purchaseDate: new Date().toISOString().split('T')[0],
              materialName: item.materialName,
              materialCode: item.materialCode ?? null,
              unit: item.unit ?? null,
              quantity: item.quantity != null ? Number(item.quantity) : null,
              quantityText: item.quantityText ?? null,
              unitPrice: item.unitPrice != null ? Number(item.unitPrice) : null,
              currency: (routeImport.currency as 'VND' | 'USD') || 'VND',
              currencyBasis: null,
              supplierName: item.supplierName ?? null,
              supplierCode: item.supplierCode ?? null,
              source: 'PRICE_HISTORY',
              sourceSheet: null,
              sourceRowNumber: null
            },
            supplierId: item.supplierId || null
          }
        }
      })
      setLatestHistory((cur) => ({ ...cur, ...initHist }))
    }
  }, [routeImport, rows.length])
  useEffect(() => { const order = orderQuery.data; if (!editing || !order) return; setRows(order.items.map(emptyRow)); setCurrency(order.currency); setSupplierId(order.supplierId ? String(order.supplierId) : ''); setOrderDate(order.orderDate ?? ''); setVat(order.vatPercent == null ? '' : String(order.vatPercent)); setNote(order.note ?? ''); setPreparedBy(order.preparedBy ?? '') }, [editing, orderQuery.data])
  function patch(key: string, values: Partial<CartRow>) { setRows((current) => current.map((row) => row.key === key ? { ...row, ...values } : row)) }
  function acceptImport(draft: ImportedOrderDraft) {
    setImported(draft)
    const firstSupplierId = draft.items.find((i) => i.supplierId)?.supplierId
    if (draft.sourceType === 'QUOTATION' && firstSupplierId) {
      setSupplierId(String(firstSupplierId))
    }

    const initHist: Record<string, { price: PriceRecord; supplierId: number | null }> = {}
    const newRows = draft.items.length ? draft.items.map((item) => {
      const row = emptyRow({ ...item, supplierId: draft.sourceType === 'QUOTATION' ? item.supplierId : undefined })
      if (item.unitPrice != null || item.supplierName) {
        initHist[row.key] = {
          price: {
            id: 0,
            purchaseDate: new Date().toISOString().split('T')[0],
            materialName: item.materialName,
            materialCode: item.materialCode ?? null,
            unit: item.unit ?? null,
            quantity: item.quantity != null ? Number(item.quantity) : null,
            quantityText: item.quantityText ?? null,
            unitPrice: item.unitPrice != null ? Number(item.unitPrice) : null,
            currency: (draft.currency as 'VND' | 'USD') || 'VND',
            currencyBasis: null,
            supplierName: item.supplierName ?? null,
            supplierCode: item.supplierCode ?? null,
            source: 'IMPORT',
            sourceSheet: null,
            sourceRowNumber: item.sourceRow ?? null
          },
          supplierId: item.supplierId || null
        }
      }
      return row
    }) : [emptyRow()]

    if (Object.keys(initHist).length > 0) {
      setLatestHistory((cur) => ({ ...cur, ...initHist }))
    }

    setRows(newRows)
    setCurrency(draft.currency ?? 'VND')
    setGroups(null); setError(''); setNotice(`Đã chuyển ${draft.items.length} dòng vào bảng đơn mua. Chọn NCC mặc định ở trên để áp dụng cho các dòng.`)
    requestAnimationFrame(() => document.getElementById('po-order-details')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
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
  async function exportAllGroups() {
    if (!groups || editing || busy) return
    setBusy(true); setError('')
    const created = [...groups]
    try {
      for (let index = 0; index < created.length; index++) {
        if (created[index].result) continue
        setGroups((current) => current?.map((group, i) => i === index ? { ...group, exportStatus: 'SAVING' } : group) ?? null)
        try {
          const result = await procurementApi.create(created[index].command)
          created[index] = { ...created[index], result }
          setGroups([...created])
        } catch (failure) {
          const rejected = failure instanceof ApiError && failure.status >= 400 && failure.status < 500
          created[index] = { ...created[index], exportStatus: rejected ? 'CREATE_REJECTED' : 'CREATE_FAILED', exportError: errorMessage(failure) }
          setGroups([...created])
          setError(`${errorMessage(failure)} Đã dừng trước khi phát hành để tránh tạo PO trùng hoặc phát hành thiếu nhóm. Kiểm tra danh sách PO trước khi thử lại.`)
          await refresh()
          return
        }
      }
      let issueFailures = 0
      for (let index = 0; index < created.length; index++) {
        const group = created[index], order = group.result
        if (!order) continue
        setGroups((current) => current?.map((item, i) => i === index ? { ...item, exportStatus: 'ISSUING' } : item) ?? null)
        try {
          await procurementApi.issue(order.id)
          created[index] = { ...group, exportStatus: 'ISSUED' }
          setGroups([...created])
          try {
            saveDownload(await procurementApi.pdf(order.id))
          } catch (failure) {
            created[index] = { ...created[index], exportStatus: 'DOWNLOAD_FAILED', exportError: errorMessage(failure) }
            issueFailures++
            setGroups([...created])
            continue
          }
        } catch (failure) {
          created[index] = { ...group, exportStatus: 'ISSUE_FAILED', exportError: errorMessage(failure) }
          issueFailures++
          setGroups([...created])
        }
      }
      await refresh()
      if (issueFailures) setError(`${issueFailures} nhóm chưa tải PDF đầy đủ. Kiểm tra trạng thái từng PO bên dưới trước khi phát hành lại; không bấm tạo đơn lần nữa.`)
    } finally { setBusy(false) }
  }
  async function copyLatest() {
    setBusy(true); setError(''); setNotice('')
    try {
      const targets = rows.filter((item) => item.selected && item.materialName.trim())
      if (!targets.length) {
        setNotice('Chưa có dòng nào được chọn để tìm giá gần nhất.')
        return
      }
      const results = await Promise.all(
        targets.map(async (row) => {
          try {
            const latest = await apiClient.get<PriceRecord>(`/prices/latest${queryParams({ materialCode: clean(row.materialCode), materialName: row.materialName.trim(), currency })}`)
            let supplier: Supplier | undefined
            if (latest.supplierCode) {
              const candidates = await catalogApi.suppliers(latest.supplierCode)
              supplier = candidates.find((item) => item.code?.toLocaleLowerCase() === latest.supplierCode?.toLocaleLowerCase())
            }
            return { rowKey: row.key, price: latest, supplier }
          } catch {
            return { rowKey: row.key, price: null, supplier: null }
          }
        })
      )
      let applied = 0, unmatched = 0
      const newSupplierChoices: Record<number, Supplier> = {}
      const newHistory: Record<string, { price: PriceRecord; supplierId: number | null }> = {}
      for (const res of results) {
        if (res.price) {
          applied++
          if (res.supplier) {
            newSupplierChoices[res.supplier.id] = res.supplier
            newHistory[res.rowKey] = { price: res.price, supplierId: res.supplier.id }
          } else {
            unmatched++
            newHistory[res.rowKey] = { price: res.price, supplierId: null }
          }
        }
      }
      if (Object.keys(newSupplierChoices).length > 0) {
        setSupplierChoices((current) => ({ ...current, ...newSupplierChoices }))
      }
      setLatestHistory((current) => ({ ...current, ...newHistory }))
      setNotice(`Đã đối chiếu giá gần nhất cho ${applied}/${targets.length} dòng theo ${currency}. Giá cũ chỉ để tham khảo; bấm [Dùng giá này] hoặc [Dùng NCC này] để áp dụng vào dòng. ${unmatched ? `${unmatched} dòng chưa xác định mã NCC.` : ''}`)
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }
  if (editing && orderQuery.isPending) return <PageState title="Đang tải đơn mua…" />
  if (editing && orderQuery.error) return <PageState title="Chưa tải được đơn mua" message={errorMessage(orderQuery.error)} onRetry={() => { void orderQuery.refetch() }} />
  if (editing && orderQuery.data?.status === 'CANCELLED') return <PageState title="Đơn đã hủy không thể sửa" />
  if (editing && orderQuery.data?.status === 'EXPORTED') {
    return (
      <PageState
        title={`Đơn mua ${orderQuery.data.poNumber} đã phát hành và đang bị khóa`}
        message="Đơn mua đã gửi nhà cung cấp nên bị khóa sửa trực tiếp để đảm bảo tính toàn vẹn chứng từ. Hãy quay lại trang chi tiết đơn để chọn [Mở khóa để sửa (Tạo Revision)]."
        onRetry={() => navigate(`/purchase-orders/${id}`)}
      />
    )
  }
  return <>
    <PageHeader title={editing ? `Sửa ${orderQuery.data?.poNumber ?? 'đơn mua'}` : 'Mua hàng'} description="Nhập PCVT, tìm vật tư, xem giá/NCC đã mua và chuẩn bị đơn theo từng nhà cung cấp." actions={<Link className="secondary-button" to="/purchase-orders">Danh sách đơn</Link>} />
    {imported && <p className="import-note">Nhận {imported.items.length} dòng từ {imported.fileName || 'bản nháp'}. {imported.message} {imported.warnings?.length ? `${imported.warnings.length} dòng có cảnh báo cần đối chiếu.` : ''}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="success-notice" role="status">{notice}</p>}
    {!editing && user && hasPermission(user, 'IMPORT_OPERATIONAL') && <DocumentWorkbench embedded canReadPrices={hasPermission(user, 'PRICE_READ')} canCreateOrders onBusy={setDocumentBusy} onCreateOrder={acceptImport} />}
    <section className="panel import-panel" id="po-order-details"><div className="document-step-title"><span aria-hidden="true">3</span><div><h2>Danh sách chuẩn bị mua</h2><p>Nhập thủ công hoặc chỉnh các dòng PCVT trước khi chia đơn theo NCC.</p></div></div><div className="form-grid">
      <div className="form-field"><label htmlFor="po-date">Ngày mua</label><input id="po-date" type="date" value={orderDate} onChange={(event) => setOrderDate(event.target.value)} /></div>
      <div className="form-field"><label htmlFor="po-currency">Loại tiền</label><select id="po-currency" value={currency} onChange={(event) => { setCurrency(event.target.value as Currency); setNotice('Đã đổi loại tiền. Đơn giá hiện tại được giữ; hãy đối chiếu lại trước khi lưu.') }}><option>VND</option><option>USD</option></select></div>
      <div className="form-field"><label htmlFor="po-vat">VAT (%)</label><select id="po-vat" value={vat} onChange={(event) => setVat(event.target.value)}><option value="">Chọn VAT đã xác minh</option>{['0', '5', '8', '10'].map((value) => <option key={value} value={value}>{value}%</option>)}</select></div>
      <div className="form-field">
        <label htmlFor="po-prepared">Người lập</label>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            id="po-prepared"
            list="po-employee-list"
            maxLength={255}
            value={preparedBy}
            onChange={(event) => setPreparedBy(event.target.value)}
            placeholder="Chọn hoặc nhập tên người lập…"
            style={{ flex: 1 }}
          />
          {employeesQuery.data?.content && employeesQuery.data.content.length > 0 && (
            <select
              aria-label="Chọn người lập từ danh sách nhân viên"
              value=""
              onChange={(e) => {
                if (e.target.value) setPreparedBy(e.target.value)
              }}
              style={{ width: 'auto', minWidth: '120px' }}
            >
              <option value="">Chọn từ DS…</option>
              {employeesQuery.data.content.map((emp) => (
                <option key={emp.id} value={emp.fullName}>
                  {emp.fullName} {emp.employeeCode ? `(${emp.employeeCode})` : ''}
                </option>
              ))}
            </select>
          )}
        </div>
        <datalist id="po-employee-list">
          {employeesQuery.data?.content?.map((emp) => (
            <option key={emp.id} value={emp.fullName}>
              {emp.employeeCode} — {emp.departmentName || ''}
            </option>
          ))}
        </datalist>
      </div>
      <div className="form-field" style={{ position: 'relative' }}>
        <label htmlFor="po-supplier-q">Tìm nhà cung cấp</label>
        <div className="header-actions">
          <input
            id="po-supplier-q"
            value={supplierSearch}
            onChange={(event) => {
              setSupplierSearch(event.target.value)
              if (!event.target.value.trim()) setSupplierSearchResults(null)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                void handleSearchSupplier(supplierSearch)
              }
            }}
            placeholder="Mã hoặc tên NCC… (Nhấn Enter để tìm)"
          />
          <button type="button" className="secondary-button" onClick={() => void handleSearchSupplier(supplierSearch)}>Tìm NCC</button>
          <button type="button" className="secondary-button" onClick={() => setShowAddSupplier(true)}><Icon name="plus" size={16} />Thêm nhanh NCC mới</button>
        </div>
        {supplierSearchResults && supplierSearchResults.length > 0 && (
          <div className="supplier-search-dropdown" style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            marginTop: '4px',
            background: 'var(--surface-color, #ffffff)',
            border: '1px solid var(--border-color, #cbd5e1)',
            borderRadius: '6px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
            maxHeight: '220px',
            overflowY: 'auto',
            zIndex: 100
          }}>
            <div style={{ padding: '6px 12px', fontSize: '12px', background: 'var(--surface-subtle, #f1f5f9)', color: 'var(--text-muted, #64748b)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-subtle, #e2e8f0)' }}>
              <span>Tìm thấy {supplierSearchResults.length} NCC:</span>
              <button type="button" className="text-button" style={{ fontSize: '11px', padding: 0 }} onClick={() => setSupplierSearchResults(null)}>Đóng [✕]</button>
            </div>
            {supplierSearchResults.map((s) => (
              <div
                key={s.id}
                style={{ padding: '8px 12px', borderBottom: '1px solid var(--border-subtle, #e2e8f0)', cursor: 'pointer' }}
                className="supplier-search-item"
                onClick={() => {
                  setSupplierChoices((current) => ({ ...current, [s.id]: s }))
                  setSupplierId(String(s.id))
                  setSupplierSearchResults(null)
                  setNotice(`Đã chọn NCC: ${s.code ? `${s.code} — ` : ''}${s.name}`)
                }}
              >
                <div><strong>{s.code ? `${s.code} — ` : ''}{s.name}</strong></div>
                {s.address && <div style={{ fontSize: '12px', color: 'var(--text-muted, #64748b)' }}>{s.address}</div>}
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="form-field">
        <label htmlFor="po-supplier">NCC mặc định</label>
        <select id="po-supplier" value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
          <option value="">Chọn nhà cung cấp</option>
          {supplierOptions.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.code ? `${supplier.code} — ` : ''}{supplier.name}</option>)}
        </select>
        <p className="field-help">{currentSupplier.data?.address || 'Tìm theo mã/tên để lấy tối đa 50 NCC đang hoạt động.'}</p>
        {supplierId && (
          <div style={{ display: 'flex', gap: '8px', marginTop: '6px', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="secondary-button"
              style={{ fontSize: '12px', padding: '4px 8px' }}
              title="Gán NCC này cho tất cả dòng hàng trong danh sách bên dưới"
              onClick={() => {
                setRows((current) => current.map((r) => ({ ...r, supplierId: supplierId })))
                const supp = supplierOptions.find((s) => String(s.id) === supplierId)
                setNotice(`Đã áp dụng NCC "${supp?.name ?? supplierId}" cho tất cả ${rows.length} dòng hàng.`)
              }}
            >
              <Icon name="check" size={14} /> Áp dụng NCC này cho tất cả dòng
            </button>
            <button
              type="button"
              className="text-button"
              style={{ fontSize: '12px' }}
              title="Xóa NCC riêng của từng dòng để dùng theo NCC mặc định"
              onClick={() => {
                setRows((current) => current.map((r) => ({ ...r, supplierId: '' })))
                setNotice('Đã đặt tất cả dòng về dùng NCC mặc định.')
              }}
            >
              Dùng NCC mặc định cho tất cả dòng
            </button>
          </div>
        )}
      </div>
    </div>{suppliers.error && <p role="alert">Chưa tải được NCC. <button className="text-button" onClick={() => { void suppliers.refetch() }}>Thử lại</button></p>}
    <div className="form-field"><label htmlFor="po-note">Ghi chú</label><textarea id="po-note" maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></div>
    {editing && <div className="form-field"><label htmlFor="po-change">Lý do sửa đơn</label><input id="po-change" maxLength={500} value={changeReason} onChange={(event) => setChangeReason(event.target.value)} /></div>}
    </section>
    <section className="panel import-panel"><MaterialLookup onPick={(material) => setRows((current) => [...current.filter((row) => !!row.materialName.trim()), emptyRow({ materialId: material.id, materialCode: material.code, materialName: material.name, unit: material.defaultUnit })])} />
      <div className="header-actions"><button className="secondary-button" onClick={() => setRows((current) => [...current, emptyRow()])}>Thêm dòng thủ công</button>{user && hasPermission(user, 'PRICE_READ') && <button className="secondary-button" disabled={busy} onClick={() => { void copyLatest() }}>Lấy giá / NCC gần nhất</button>}{user && (hasPermission(user,'IMPORT_OPERATIONAL') || hasPermission(user,'IMPORT_LEGACY')) && <Link to="/imports">Nhận từ Excel / PDF / dữ liệu dán</Link>}</div>
    </section>
    <section className="panel"><div className="panel-heading"><h2>Danh sách chuẩn bị mua</h2><span>{rows.filter((row) => row.selected).length} / {rows.length} dòng đã chọn</span></div>
      <div className="table-wrap">
        <table className="po-cart">
          <thead>
            <tr>
              <th><input type="checkbox" aria-label="Chọn tất cả dòng hàng" checked={!!rows.length && rows.every((row) => row.selected)} onChange={(event) => setRows((current) => current.map((row) => ({ ...row, selected: event.target.checked })))} /></th>
              <th>Vật tư / Mã</th>
              <th>Quy cách / ĐVT</th>
              <th>Số lượng</th>
              <th>Đơn giá ({currency})</th>
              <th className="numeric-cell">Thành tiền ({currency})</th>
              {!editing && <th>NCC riêng</th>}
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const qtyVal = Number(row.quantity)
              const priceVal = Number(row.unitPrice)
              const hasValidQty = !row.textQty && !isNaN(qtyVal) && qtyVal > 0
              const hasValidPrice = !isNaN(priceVal) && priceVal >= 0
              const rowSubtotal = hasValidQty && hasValidPrice ? qtyVal * priceVal : null
              const isQtyInvalid = row.selected && !row.textQty && (row.quantity.trim() === '' || isNaN(qtyVal) || qtyVal <= 0)
              const isPriceInvalid = row.selected && (row.unitPrice.trim() === '' || isNaN(priceVal) || priceVal <= 0)

              const histPrice = latestHistory[row.key]?.price?.unitPrice != null ? Number(latestHistory[row.key].price.unitPrice) : null
              const priceDiffPercent = (histPrice != null && histPrice > 0 && hasValidPrice && priceVal > 0)
                ? Math.round(((priceVal - histPrice) / histPrice) * 100)
                : null
              const hasPriceWarning = priceDiffPercent != null && Math.abs(priceDiffPercent) >= 10

              return (
                <tr key={row.key}>
                  <td><input aria-label={`Chọn dòng ${index + 1}`} type="checkbox" checked={row.selected} onChange={(event) => patch(row.key, { selected: event.target.checked })} /></td>
                  <td>
                    <input aria-label={`Tên vật tư dòng ${index + 1}`} maxLength={500} value={row.materialName} placeholder="Tên hàng hóa, vật tư…" onChange={(event) => patch(row.key, { materialName: event.target.value, materialId: null })} />
                    <input aria-label={`Mã vật tư dòng ${index + 1}`} maxLength={80} value={row.materialCode} placeholder="Mã vật tư (nếu có)…" onChange={(event) => patch(row.key, { materialCode: event.target.value, materialId: null })} />
                  </td>
                  <td>
                    <input aria-label={`Quy cách dòng ${index + 1}`} maxLength={1000} value={row.specification} placeholder="Quy cách, quy chuẩn…" onChange={(event) => patch(row.key, { specification: event.target.value })} />
                    <input aria-label={`Đơn vị dòng ${index + 1}`} maxLength={100} value={row.unit} placeholder="Đơn vị tính…" onChange={(event) => patch(row.key, { unit: event.target.value })} />
                  </td>
                  <td>
                    {row.textQty && <span className="cell-subtitle">SL chữ từ nguồn: {row.quantityText}. Cần xác nhận số lượng dạng số trước khi lập PO.</span>}
                    <input aria-label={`Số lượng dòng ${index + 1}`} inputMode="decimal" maxLength={24} placeholder="Nhập SL…" className={isQtyInvalid ? 'input-invalid' : ''} value={row.quantity} onChange={(event) => patch(row.key, { quantity: event.target.value, textQty: false })} />
                    {isQtyInvalid && <span className="cell-invalid">Vui lòng nhập SL &gt; 0</span>}
                  </td>
                  <td>
                    {latestHistory[row.key] && (
                      <div className="history-preview-badge">
                        <div className="history-preview-text">
                          <span>Giá cũ: <strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: latestHistory[row.key].price.currency === 'USD' ? 2 : 0 }).format(Number(latestHistory[row.key].price.unitPrice ?? 0))} {latestHistory[row.key].price.currency}</strong></span>
                          <span> · NCC: <strong>{latestHistory[row.key].price.supplierName ?? 'Chưa xác định'}</strong></span>
                          <span> · {latestHistory[row.key].price.purchaseDate ?? 'Chưa rõ ngày'}</span>
                        </div>
                        <div className="history-preview-actions">
                          {latestHistory[row.key].price.unitPrice != null && (
                            <button type="button" className="text-button history-btn" title="Chỉ áp dụng giá cũ vào ô đơn giá" onClick={() => {
                              patch(row.key, { unitPrice: String(latestHistory[row.key].price.unitPrice ?? '') })
                            }}>
                              <Icon name="check" size={12} /> Dùng giá này
                            </button>
                          )}
                          {latestHistory[row.key].supplierId && (
                            <button type="button" className="text-button history-btn" title="Chỉ áp dụng NCC này cho dòng" onClick={() => {
                              const suppId = String(latestHistory[row.key].supplierId)
                              patch(row.key, { supplierId: suppId })
                              if (!supplierId) setSupplierId(suppId)
                            }}>
                              <Icon name="check" size={12} /> Dùng NCC này
                            </button>
                          )}
                        </div>
                      </div>
                    )}
                    <label className="field-help" htmlFor={`po-price-${row.key}`}>Giá hiện tại gửi NCC</label>
                    <input id={`po-price-${row.key}`} aria-label={`Đơn giá dòng ${index + 1}`} inputMode="decimal" maxLength={24} placeholder="Nhập đơn giá…" className={isPriceInvalid ? 'input-invalid' : ''} value={row.unitPrice} onChange={(event) => patch(row.key, { unitPrice: event.target.value })} />
                    {isPriceInvalid && <span className="cell-invalid">Vui lòng nhập đơn giá &gt; 0</span>}
                    {hasPriceWarning && (
                      <span className="cell-warning" title={`Giá cũ: ${new Intl.NumberFormat('vi-VN').format(histPrice!)} ${latestHistory[row.key].price.currency}`}>
                        <Icon name="alert" size={12} />
                        <span>Chênh lệch {priceDiffPercent! > 0 ? `+${priceDiffPercent}%` : `${priceDiffPercent}%`} so với giá cũ ({new Intl.NumberFormat('vi-VN').format(histPrice!)})</span>
                      </span>
                    )}
                  </td>
                  <td className="numeric-cell subtotal-cell">
                    {rowSubtotal != null ? `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: currency === 'USD' ? 2 : 0 }).format(rowSubtotal)}` : '—'}
                  </td>
                  {!editing && <td>
                    <select aria-label={`NCC dòng ${index + 1}`} value={row.supplierId} onChange={(event) => patch(row.key, { supplierId: event.target.value })}>
                      <option value="">Dùng NCC mặc định</option>
                      {supplierOptions.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
                    </select>
                  </td>}
                  <td><button className="text-button" aria-label={`Bỏ dòng ${index + 1}`} onClick={() => setRows((current) => current.filter((item) => item.key !== row.key))}>Bỏ dòng</button></td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {(() => {
        const selectedRows = rows.filter((row) => row.selected)
        const totalLines = selectedRows.length
        const totalQty = selectedRows.reduce((sum, r) => {
          const q = Number(r.quantity)
          return sum + (!r.textQty && !isNaN(q) && q > 0 ? q : 0)
        }, 0)
        const totalPreVat = selectedRows.reduce((sum, r) => {
          const q = Number(r.quantity), p = Number(r.unitPrice)
          return sum + (!r.textQty && !isNaN(q) && q > 0 && !isNaN(p) && p >= 0 ? q * p : 0)
        }, 0)
        const vatRate = vat && !isNaN(Number(vat)) ? Number(vat) : 0
        const vatAmount = totalPreVat * (vatRate / 100)
        const grandTotal = totalPreVat + vatAmount
        const missingCount = selectedRows.filter((r) => !r.textQty && (!Number(r.quantity) || Number(r.quantity) <= 0 || !Number(r.unitPrice) || Number(r.unitPrice) <= 0)).length

        return (
          <div className="po-summary-box">
            <div className="po-summary-metrics">
              <div className="po-summary-row">
                <span className="summary-label">Mặt hàng đã chọn:</span>
                <span className="summary-val"><strong>{totalLines}</strong> dòng ({totalQty.toLocaleString('vi-VN')} đơn vị)</span>
              </div>
              <div className="po-summary-row">
                <span className="summary-label">Tổng tiền hàng (chưa VAT):</span>
                <span className="summary-val"><strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: currency === 'USD' ? 2 : 0 }).format(totalPreVat)}</strong> {currency}</span>
              </div>
              <div className="po-summary-row">
                <span className="summary-label">Thuế GTGT ({vat ? `${vat}%` : 'chưa chọn'}):</span>
                <span className="summary-val">{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: currency === 'USD' ? 2 : 0 }).format(vatAmount)} {currency}</span>
              </div>
              <div className="po-summary-row summary-grand">
                <span className="summary-label">Tổng thanh toán (sau VAT):</span>
                <span className="summary-val">{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: currency === 'USD' ? 2 : 0 }).format(grandTotal)} {currency}</span>
              </div>
            </div>
            {missingCount > 0 && (
              <div className="po-summary-warning">
                <Icon name="alert" size={16} />
                <span>Còn {missingCount} dòng được chọn chưa nhập đủ số lượng hoặc đơn giá hợp lệ (&gt; 0).</span>
              </div>
            )}
          </div>
        )
      })()}

      <p className="import-note">Đơn mới yêu cầu số lượng dạng số. Số lượng chữ từ chứng từ chỉ được hiển thị để đối chiếu và không tự chuyển thành số. Giá cũ/NCC cũ không thay giá hiện tại khi chưa bấm chọn dùng. Mẫu PDF gửi NCC giữ nguyên định dạng chỉ gồm Số lượng và Đơn giá chưa VAT.</p>
    </section>
    <div className="header-actions"><button className="primary-button" disabled={busy || documentBusy} onClick={previewGroups}>{editing ? 'Kiểm tra phiên bản mới' : 'Kiểm tra và xuất PO'}</button></div>
    {groups && <Dialog title={editing ? 'Xác nhận sửa đơn' : `Kiểm tra ${groups.length} nhóm nhà cung cấp`} onClose={() => { if (!busy) { if (groups.some((group) => group.result)) navigate('/purchase-orders'); else setGroups(null) } }} busy={busy}>
      <p className="dialog-description">{editing ? 'Kiểm tra nội dung và lưu phiên bản mới. Phát hành phiên bản sau khi rà soát.' : 'Xác nhận một lần để tạo từng PO theo NCC và phát hành PDF. Mỗi PO được lưu riêng; nếu bước tạo PO lỗi thì hệ thống dừng trước khi phát hành, còn lỗi phát hành sẽ được báo theo từng NCC.'}</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {groups.map((group, index) => <div className="group-preview" key={group.supplierId}><strong>{group.supplierName}</strong><p>{group.command.items.length} dòng · {currency} · VAT {vat}%</p>{group.result ? <><Link to={`/purchase-orders/${group.result.id}`}>{group.result.poNumber} — Xem đơn</Link><p role="status">{group.exportStatus === 'SAVING' ? 'Đang tạo PO…' : group.exportStatus === 'ISSUING' ? 'Đang phát hành PDF…' : group.exportStatus === 'ISSUED' ? 'Đã phát hành và gửi tải PDF.' : group.exportStatus === 'DOWNLOAD_FAILED' ? `Đã phát hành; chưa tải được PDF. Mở PO để tải lại. ${group.exportError ?? ''}` : group.exportStatus === 'ISSUE_FAILED' ? `Chưa xác nhận được kết quả phát hành. Mở PO kiểm tra trước khi thử lại. ${group.exportError ?? ''}` : 'Đã tạo PO.'}</p></> : group.exportStatus === 'CREATE_FAILED' ? <p role="alert">Chưa xác định được kết quả tạo PO. Kiểm tra danh sách trước khi tiếp tục. {group.exportError}</p> : group.exportStatus === 'CREATE_REJECTED' ? <p role="alert">Máy chủ từ chối tạo PO; chưa phát hành nhóm nào. Quay lại sửa hoặc kiểm tra dữ liệu. {group.exportError}</p> : editing ? <button className="primary-button" disabled={busy} onClick={() => { void saveGroup(index) }}>Lưu phiên bản mới</button> : <span>{group.exportStatus === 'SAVING' ? 'Đang tạo PO…' : 'Chờ xác nhận'}</span>}</div>)}
      <div className="dialog-actions"><button className="secondary-button" disabled={busy} onClick={() => groups.some((group) => group.result || group.exportStatus === 'CREATE_FAILED') ? navigate('/purchase-orders') : setGroups(null)}>{groups.some((group) => group.result || group.exportStatus === 'CREATE_FAILED') ? 'Về danh sách đơn' : 'Quay lại chỉnh sửa'}</button>{!editing && !groups.some((group) => group.result || group.exportStatus === 'CREATE_FAILED' || group.exportStatus === 'CREATE_REJECTED') && <button className="primary-button" disabled={busy} onClick={() => { void exportAllGroups() }}>{busy ? 'Đang tạo PO và phát hành PDF…' : `Xác nhận, tạo ${groups.length} PO và xuất PDF`}</button>}</div>
    </Dialog>}
    {showAddSupplier && <SupplierForm onClose={() => setShowAddSupplier(false)} onSaved={(name, newSupplier) => {
      setShowAddSupplier(false)
      if (newSupplier) {
        setSupplierChoices((current) => ({ ...current, [newSupplier.id]: newSupplier }))
        setSupplierId(String(newSupplier.id))
        setNotice(`Đã thêm nhà cung cấp "${name}" và chọn làm NCC mặc định.`)
      }
    }} />}
  </>
}
