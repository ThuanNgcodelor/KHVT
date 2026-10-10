export type Decimal = number | string
export type Currency = 'VND' | 'USD'
export type OrderStatus = 'DRAFT' | 'EXPORTED' | 'CANCELLED'
export const orderStatusLabels: Record<OrderStatus, string> = { DRAFT: 'Bản nháp', EXPORTED: 'Đã phát hành', CANCELLED: 'Đã hủy' }
export type OrderItem = {
  id: number | null; lineNo: number; materialId: number | null; materialCode: string | null
  materialName: string; specification: string | null; unit: string | null
  quantity: Decimal | null; quantityText: string | null; unitPrice: Decimal; lineTotal: Decimal | null
}
export type PurchaseOrder = {
  id: number; poNumber: string; orderDate: string | null; supplierId: number | null; supplierName: string
  supplierAddress: string | null; currency: Currency; vatPercent: Decimal | null; note: string | null
  preparedBy: string | null; status: OrderStatus; revision: number; version: number; items: OrderItem[]
  subtotal: Decimal; taxAmount: Decimal | null; grandTotal: Decimal | null; quantityTextLineCount: number
}
export type OrderItemCommand = Omit<OrderItem, 'id' | 'lineNo' | 'lineTotal'>
export type OrderCommand = {
  supplierId: number; orderDate: string | null; currency: Currency; vatPercent: Decimal
  note: string | null; preparedBy: string | null; changeReason: string | null; items: OrderItemCommand[]
}
export type OrderFilter = { q?: string; status?: OrderStatus | ''; page: number; size: number }
export type OrderRevision = { revision: number; changedBy: number | null; changeReason: string | null; createdAt: string; pdfAvailable: boolean }

// A preview stays in memory until the user confirms each supplier's draft.
export type ImportedOrderItem = {
  sourceRow?: number; materialId?: number | null; materialCode?: string | null; materialName: string
  specification?: string | null; unit?: string | null; quantity?: Decimal | null; quantityText?: string | null
  unitPrice?: Decimal | null; supplierId?: number | null; supplierName?: string | null; supplierCode?: string | null
  warnings?: string[]
}
export type ImportedOrderDraft = {
  fileName?: string; sourceType?: string; currency?: Currency; items: ImportedOrderItem[]
  warnings?: { sourceRow: number; warnings: string[] }[]; message?: string
}
