import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { Material, MaterialCommand, MaterialFilter, Supplier, SupplierCommand, SupplierFilter } from './types'

export const catalogApi = {
  materials: (q: string, signal?: AbortSignal) => apiClient.get<Material[]>(`/catalog/materials${queryParams({ q })}`, { signal }),
  suppliers: (q: string, signal?: AbortSignal) => apiClient.get<Supplier[]>(`/catalog/suppliers${queryParams({ q })}`, { signal }),
  material: (id: number, signal?: AbortSignal) => apiClient.get<Material>(`/catalog/materials/${id}`, { signal }),
  supplier: (id: number, signal?: AbortSignal) => apiClient.get<Supplier>(`/catalog/suppliers/${id}`, { signal }),
  materialPage: (filters: MaterialFilter, signal?: AbortSignal) => apiClient.get<PageResponse<Material>>(`/catalog/materials/page${queryParams(filters)}`, { signal }),
  supplierPage: (filters: SupplierFilter, signal?: AbortSignal) => apiClient.get<PageResponse<Supplier>>(`/catalog/suppliers/page${queryParams(filters)}`, { signal }),
  createMaterial: (data: MaterialCommand) => apiClient.post<Material>('/catalog/materials', data),
  updateMaterial: (id: number, data: MaterialCommand) => apiClient.put<Material>(`/catalog/materials/${id}`, data),
  createSupplier: (data: SupplierCommand) => apiClient.post<Supplier>('/catalog/suppliers', data),
  updateSupplier: (id: number, data: SupplierCommand) => apiClient.put<Supplier>(`/catalog/suppliers/${id}`, data),
}
