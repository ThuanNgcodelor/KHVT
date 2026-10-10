import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { PriceRecord } from './types'
export type PriceFilter = { q: string; currency: string; category: string; page: number; size: number }
export const pricingApi = {
  history: (filters: PriceFilter, signal?: AbortSignal) => apiClient.get<PageResponse<PriceRecord>>(`/prices${queryParams(filters)}`, { signal }),
  latest: (row: PriceRecord) => apiClient.get<PriceRecord>(`/prices/latest${queryParams({ materialCode: row.materialCode, materialName: row.materialName, currency: row.currency })}`),
  xlsx: ({ q, currency, category }: PriceFilter) => apiClient.download(`/exports/prices.xlsx${queryParams({ q, currency, category })}`),
}
