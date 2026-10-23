import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { OrderCommand, OrderFilter, OrderRevision, PurchaseOrder } from './types'

export const procurementApi = {
  orders: (filters: OrderFilter, signal?: AbortSignal) => apiClient.get<PageResponse<PurchaseOrder>>(`/purchase-orders${queryParams(filters)}`, { signal }),
  order: (id: number, signal?: AbortSignal) => apiClient.get<PurchaseOrder>(`/purchase-orders/${id}`, { signal }),
  revisions: (id: number, signal?: AbortSignal) => apiClient.get<OrderRevision[]>(`/purchase-orders/${id}/revisions`, { signal }),
  create: (data: OrderCommand) => apiClient.post<PurchaseOrder>('/purchase-orders', data),
  update: (id: number, data: OrderCommand) => apiClient.put<PurchaseOrder>(`/purchase-orders/${id}`, data),
  cancel: (id: number, reason: string) => apiClient.post<PurchaseOrder>(`/purchase-orders/${id}/cancel`, { reason }),
  issue: (id: number) => apiClient.post<PurchaseOrder>(`/purchase-orders/${id}/issue`),
  unlock: (id: number, reason: string) => apiClient.post<PurchaseOrder>(`/purchase-orders/${id}/unlock`, { reason }),
  reorder: (id: number, data: {
    orderDate?: string; poNumber?: string; note?: string; preparedBy?: string; autoIssue?: boolean;
    vatPercent?: number;
    items?: Array<{ materialName: string; specification?: string; unit?: string; quantity: number; unitPrice: number }>
  }) =>
    apiClient.post<PurchaseOrder>(`/purchase-orders/${id}/reorder`, data),

  pdf: (id: number, revision?: number) => apiClient.download(revision === undefined ? `/purchase-orders/${id}/pdf` : `/purchase-orders/${id}/revisions/${revision}/pdf`),
  xlsx: (id: number) => apiClient.download(`/exports/purchase-orders/${id}.xlsx`),
  masterWorkbook: () => apiClient.download('/exports/master-workbook.xlsx'),
}
