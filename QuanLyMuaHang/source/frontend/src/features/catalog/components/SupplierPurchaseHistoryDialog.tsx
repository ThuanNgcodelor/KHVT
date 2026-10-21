import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Dialog } from '../../../components/Dialog'
import { TableFeedback } from '../../../components/TableFeedback'
import { Pagination } from '../../../components/Pagination'
import { pricingApi } from '../../pricing/pricingApi'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import type { Supplier } from '../types'
import type { PriceRecord } from '../../pricing/types'

export function SupplierPurchaseHistoryDialog({ supplier, onClose }: { supplier: Supplier; onClose: () => void }) {
  const { user } = useAuth(), navigate = useNavigate()
  const [page, setPage] = useState(0)
  const queryTerm = supplier.code ? supplier.code : supplier.name

  const history = useQuery({
    queryKey: ['supplier-purchased-items', supplier.id, page],
    queryFn: ({ signal }) => pricingApi.supplierHistory(queryTerm, '', page, 20, 'purchaseDate,desc', signal)
  })

  function draftFrom(row: PriceRecord) {
    onClose()
    navigate('/purchase-orders/new', {
      state: {
        importDraft: {
          fileName: `Lịch sử ${supplier.name}`,
          sourceType: 'PRICE_HISTORY',
          currency: row.currency,
          message: `Lập đơn từ lịch sử mua của ${supplier.name}.`,
          items: [{
            materialCode: row.materialCode,
            materialName: row.materialName,
            unit: row.unit,
            unitPrice: row.unitPrice,
            quantity: null,
            supplierName: supplier.name,
            supplierCode: supplier.code,
            supplierId: supplier.id
          }],
          warnings: []
        }
      }
    })
  }

  const data = history.data

  return (
    <Dialog title={`Lịch sử mặt hàng đã mua từ ${supplier.name}`} onClose={onClose} busy={history.isPending}>
      <p className="dialog-description">
        Mã NCC: <strong>{supplier.code ?? 'Chưa có'}</strong> {supplier.address ? `· Địa chỉ: ${supplier.address}` : ''}
      </p>

      <TableFeedback
        pending={history.isPending}
        error={history.error}
        empty={!data?.content.length}
        onRetry={() => { void history.refetch() }}
        emptyTitle="Chưa có lịch sử mua hàng"
        emptyMessage={`Chưa tìm thấy bản ghi nhập hoặc đơn mua nào gắn với nhà cung cấp ${supplier.name}.`}
      />

      {!history.error && data && data.content.length > 0 && (
        <>
          <div className="table-wrap" style={{ maxHeight: '420px', overflowY: 'auto' }}>
            <table>
              <thead>
                <tr>
                  <th>Vật tư / Mã</th>
                  <th>Ngày mua</th>
                  <th className="numeric-cell">Số lượng</th>
                  <th className="numeric-cell">Đơn giá</th>
                  <th>Nguồn</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <strong className="cell-title">{row.materialName}</strong>
                      <span className="cell-subtitle">{row.materialCode || 'Chưa có mã'} · {row.unit || 'Chưa có ĐVT'}</span>
                    </td>
                    <td>{row.purchaseDate ? new Date(`${row.purchaseDate}T00:00:00`).toLocaleDateString('vi-VN') : 'Chưa rõ'}</td>
                    <td className="numeric-cell">
                      {row.quantity == null ? row.quantityText || '—' : Number(row.quantity).toLocaleString('vi-VN')} {row.unit || ''}
                    </td>
                    <td className="numeric-cell">
                      <strong>{Number(row.unitPrice ?? 0).toLocaleString('vi-VN')}</strong> {row.currency}
                    </td>
                    <td>
                      <span style={{ fontSize: '12px' }}>
                        {row.source === 'PURCHASE_ORDER' ? 'Đơn mua hàng' : 'Lịch sử nhập'}
                      </span>
                      {row.sourceSheet && <span className="cell-subtitle">{row.sourceSheet}</span>}
                    </td>
                    <td>
                      {user && hasPermission(user, 'PO_CREATE') && row.unitPrice != null && (
                        <button type="button" className="text-button" onClick={() => draftFrom(row)}>
                          Lập đơn từ dòng
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.number}
            size={data.size}
            total={data.totalElements}
            pages={data.totalPages}
            busy={history.isFetching}
            onChange={setPage}
          />
        </>
      )}

      <div className="dialog-actions">
        <button type="button" className="secondary-button" onClick={onClose}>Đóng</button>
      </div>
    </Dialog>
  )
}
