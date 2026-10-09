export type RecentOrder = {
  id: number
  poNumber: string
  orderDate: string | null
  supplierName: string
  currency: string
  status: 'DRAFT' | 'EXPORTED' | 'CANCELLED'
  grandTotal: number | null
}
export type DashboardResponse = {
  purchaseOrdersThisMonth: number
  activeMaterials: number
  activeSuppliers: number
  recentOrders: RecentOrder[]
  generatedAt: string
}
export type ApiEnvelope<T> = {
  data: T
  error: null | { code: string; message: string }
  traceId?: string
}
export type PageResponse<T> = { content: T[]; number: number; size: number; totalElements: number; totalPages: number }
