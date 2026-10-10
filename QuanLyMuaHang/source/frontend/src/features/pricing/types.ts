export type PriceRecord = {
  id: number; purchaseDate: string | null; materialName: string; materialCode: string | null; unit: string | null
  quantity: number | null; quantityText: string | null; unitPrice: number | null; currency: 'VND' | 'USD'
  currencyBasis: string | null; supplierName: string | null; supplierCode: string | null
  source: string; sourceSheet: string | null; sourceRowNumber: number | null
}
