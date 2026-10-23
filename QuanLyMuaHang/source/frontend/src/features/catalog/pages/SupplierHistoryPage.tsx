import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../../components/PageHeader'
import { PageState } from '../../../components/PageState'
import { TableFeedback } from '../../../components/TableFeedback'
import { Pagination } from '../../../components/Pagination'
import { Icon } from '../../../components/Icon'
import { catalogApi } from '../catalogApi'
import { pricingApi } from '../../pricing/pricingApi'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { errorMessage } from '../../../services/errorMessage'
import type { PriceRecord } from '../../pricing/types'

export function SupplierHistoryPage() {
  const { id } = useParams<{ id: string }>()
  const supplierId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [page, setPage] = useState(0)
  const [currency, setCurrency] = useState('')
  const [sort, setSort] = useState('purchaseDate,desc')
  const [materialFilter, setMaterialFilter] = useState('')

  const supplierQuery = useQuery({
    queryKey: ['catalog', 'supplier', supplierId],
    queryFn: ({ signal }) => catalogApi.supplier(supplierId, signal),
    enabled: Number.isSafeInteger(supplierId) && supplierId > 0
  })

  const supplier = supplierQuery.data
  const queryTerm = supplier ? (supplier.code ? supplier.code : supplier.name) : ''

  const historyQuery = useQuery({
    queryKey: ['supplier-purchased-items-page', supplierId, queryTerm, currency, page, sort],
    queryFn: ({ signal }) => pricingApi.supplierHistory(queryTerm, currency, page, 25, sort, signal),
    enabled: !!queryTerm
  })

  if (!Number.isSafeInteger(supplierId) || supplierId <= 0) {
    return <PageState title="Mã nhà cung cấp không hợp lệ" message="Vui lòng kiểm tra lại đường dẫn." />
  }

  if (supplierQuery.isPending) {
    return <PageState title="Đang tải thông tin nhà cung cấp…" />
  }

  if (supplierQuery.isError || !supplier) {
    return (
      <PageState
        title="Không tìm thấy nhà cung cấp"
        message={errorMessage(supplierQuery.error)}
        onRetry={() => { void supplierQuery.refetch() }}
      />
    )
  }

  function handleCreateOrderWithItem(row: PriceRecord) {
    navigate('/purchase-orders/new', {
      state: {
        importDraft: {
          fileName: `Lịch sử ${supplier!.name}`,
          sourceType: 'PRICE_HISTORY',
          currency: row.currency,
          message: `Lập đơn từ lịch sử mua của ${supplier!.name}.`,
          items: [{
            materialCode: row.materialCode,
            materialName: row.materialName,
            unit: row.unit,
            unitPrice: row.unitPrice,
            quantity: row.quantity != null ? Number(row.quantity) : null,
            quantityText: row.quantityText ?? undefined,
            supplierName: supplier!.name,
            supplierCode: supplier!.code,
            supplierId: supplier!.id
          }],
          warnings: []
        }
      }
    })
  }

  const rawData = historyQuery.data
  const filteredContent = (rawData?.content ?? []).filter((r) => {
    if (!materialFilter.trim()) return true
    const term = materialFilter.trim().toLowerCase()
    return (
      (r.materialName && r.materialName.toLowerCase().includes(term)) ||
      (r.materialCode && r.materialCode.toLowerCase().includes(term))
    )
  })

  return (
    <>
      <PageHeader
        title={`Lịch sử mua hàng: ${supplier.name}`}
        description={`Mã NCC: ${supplier.code ?? 'Chưa có'} · ${supplier.address ? `Địa chỉ: ${supplier.address}` : 'Chưa có địa chỉ'}`}
        backTo="/catalog/suppliers"
        backLabel="Danh sách nhà cung cấp"
        actions={
          <div style={{ display: 'flex', gap: '8px' }}>
            {user && hasPermission(user, 'PO_CREATE') && (
              <Link to="/purchase-orders/new" className="primary-button">
                <Icon name="plus" size={16} /> Lập đơn mua mới
              </Link>
            )}
          </div>
        }
      />

      {/* Supplier Summary Card */}
      <section className="panel" style={{ marginBottom: '16px', background: 'var(--surface-subtle, #f8fafc)', border: '1px solid var(--border-color, #e2e8f0)', padding: '16px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px', fontSize: '13px' }}>
          <div>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Tên nhà cung cấp</span>
            <strong style={{ fontSize: '15px' }}>{supplier.name}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Mã nhà cung cấp</span>
            <strong>{supplier.code || '—'}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Mã số thuế</span>
            <span>{supplier.taxCode || '—'}</span>
          </div>
          <div>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Liên hệ (SĐT / Email)</span>
            <span>{supplier.phone || supplier.email ? `${supplier.phone || ''} ${supplier.email ? `(${supplier.email})` : ''}` : '—'}</span>
          </div>
          <div style={{ gridColumn: 'span 2' }}>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Địa chỉ</span>
            <span>{supplier.address || 'Chưa cập nhật địa chỉ'}</span>
          </div>
          <div>
            <span style={{ color: 'var(--text-muted, #64748b)', display: 'block', fontSize: '11px', textTransform: 'uppercase', fontWeight: 600 }}>Trạng thái</span>
            <span className={`status-badge ${supplier.active ? 'active' : 'inactive'}`}>
              {supplier.active ? 'Đang hoạt động' : 'Đã ngừng'}
            </span>
          </div>
        </div>
      </section>

      {/* Main History Table Section */}
      <section className="panel" aria-label="Bảng lịch sử mua hàng">
        <div className="filter-bar">
          <div className="form-field search-field">
            <label htmlFor="history-material-search">Lọc theo tên / mã vật tư</label>
            <input
              id="history-material-search"
              type="search"
              placeholder="Gõ tên hoặc mã vật tư để lọc nhanh…"
              value={materialFilter}
              onChange={(e) => setMaterialFilter(e.target.value)}
            />
          </div>

          <div className="form-field">
            <label htmlFor="history-currency">Loại tiền</label>
            <select
              id="history-currency"
              value={currency}
              onChange={(e) => {
                setCurrency(e.target.value)
                setPage(0)
              }}
            >
              <option value="">Tất cả tiền tệ</option>
              <option value="VND">VND</option>
              <option value="USD">USD</option>
            </select>
          </div>

          <div className="form-field">
            <label htmlFor="history-sort">Sắp xếp</label>
            <select
              id="history-sort"
              value={sort}
              onChange={(e) => {
                setSort(e.target.value)
                setPage(0)
              }}
            >
              <option value="purchaseDate,desc">Ngày mua: Mới nhất trước</option>
              <option value="purchaseDate,asc">Ngày mua: Cũ nhất trước</option>
              <option value="unitPrice,desc">Đơn giá: Cao đến thấp</option>
              <option value="unitPrice,asc">Đơn giá: Thấp đến cao</option>
            </select>
          </div>

          {rawData && !historyQuery.error && (
            <span className="table-count">
              Tổng cộng {rawData.totalElements} lượt mua hàng
            </span>
          )}
        </div>

        <TableFeedback
          pending={historyQuery.isPending}
          error={historyQuery.error}
          empty={!filteredContent.length}
          onRetry={() => { void historyQuery.refetch() }}
          emptyTitle="Chưa có dữ liệu lịch sử"
          emptyMessage={`Chưa tìm thấy bản ghi nhập kho hoặc đơn mua nào gắn với nhà cung cấp ${supplier.name}.`}
        />

        {!historyQuery.error && filteredContent.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: '45px', textAlign: 'center' }}>STT</th>
                  <th>Hàng hóa / Vật tư</th>
                  <th>Ngày mua</th>
                  <th>ĐVT</th>
                  <th className="numeric-cell">Số lượng</th>
                  <th className="numeric-cell">Đơn giá</th>
                  <th className="numeric-cell">Thành tiền</th>
                  <th>Nguồn</th>
                  <th style={{ textAlign: 'center' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredContent.map((row, index) => {
                  const q = Number(row.quantity)
                  const p = Number(row.unitPrice)
                  const total = !isNaN(q) && !isNaN(p) && q > 0 && p >= 0 ? q * p : null
                  const dateStr = row.purchaseDate
                    ? new Date(`${row.purchaseDate}T00:00:00`).toLocaleDateString('vi-VN')
                    : 'Chưa rõ'

                  return (
                    <tr key={row.id}>
                      <td style={{ textAlign: 'center', color: 'var(--text-muted, #64748b)' }}>
                        {page * 25 + index + 1}
                      </td>
                      <td>
                        <strong className="cell-title">{row.materialName}</strong>
                        {row.materialCode && (
                          <span className="cell-subtitle">Mã: {row.materialCode}</span>
                        )}
                      </td>
                      <td>{dateStr}</td>
                      <td>{row.unit || '—'}</td>
                      <td className="numeric-cell">
                        {row.quantity == null ? row.quantityText || '—' : Number(row.quantity).toLocaleString('vi-VN')}
                      </td>
                      <td className="numeric-cell">
                        <strong>{Number(row.unitPrice ?? 0).toLocaleString('vi-VN')}</strong> {row.currency}
                      </td>
                      <td className="numeric-cell" style={{ fontWeight: 600 }}>
                        {total != null ? `${Number(total).toLocaleString('vi-VN')} ${row.currency}` : '—'}
                      </td>
                      <td>
                        <span style={{ fontSize: '12px' }}>
                          {row.source === 'PURCHASE_ORDER' ? 'Đơn mua hàng' : 'Lịch sử nhập'}
                        </span>
                        {row.sourceSheet && (
                          <span className="cell-subtitle">{row.sourceSheet}</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        {user && hasPermission(user, 'PO_CREATE') && row.unitPrice != null && (
                          <button
                            type="button"
                            className="primary-button"
                            style={{ fontSize: '12px', padding: '4px 10px' }}
                            title="Tạo đơn mua mới với mặt hàng và đơn giá này"
                            onClick={() => handleCreateOrderWithItem(row)}
                          >
                            <Icon name="plus" size={13} /> Lập đơn với giá này
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {!historyQuery.error && rawData && (
          <Pagination
            page={rawData.number}
            size={rawData.size}
            total={rawData.totalElements}
            pages={rawData.totalPages}
            busy={historyQuery.isFetching}
            onChange={setPage}
          />
        )}
      </section>
    </>
  )
}
