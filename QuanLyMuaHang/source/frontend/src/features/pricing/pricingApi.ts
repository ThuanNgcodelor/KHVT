import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { PriceRecord } from './types'
export type PriceFilter = { q: string; supplier?: string; currency: string; category: string; page: number; size: number; sort?: string }
export const pricingApi = {
  history: (filters: PriceFilter, signal?: AbortSignal) => apiClient.get<PageResponse<PriceRecord>>(`/prices${queryParams(filters)}`, { signal }),
  supplierHistory: (q: string, currency: string, page: number, size: number, sort: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<PriceRecord>>(`/prices/supplier-history${queryParams({ q, currency, page, size, sort })}`, { signal }),
  latest: (row: PriceRecord) => apiClient.get<PriceRecord>(`/prices/latest${queryParams({ materialCode: row.materialCode, materialName: row.materialName, currency: row.currency })}`),
  xlsx: ({ q, supplier, currency, category }: PriceFilter) => apiClient.download(`/exports/prices.xlsx${queryParams({ q, supplier, currency, category })}`),
}
