export type DashboardKpi = {
  label: string
  value: number
  hint: string
}

export type RecentOrder = {
  poNumber: string
  supplier: string
  status: string
  total: string
}

export type DashboardResponse = {
  purchaseOrders: DashboardKpi
  materials: DashboardKpi
  suppliers: DashboardKpi
  recentOrders: RecentOrder[]
  generatedAt: string
}

export type ApiEnvelope<T> = {
  data: T
  error: null | { code: string; message: string }
  traceId?: string
}
