import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '../catalogApi'
import type { MaterialCommand, MaterialFilter, SupplierCommand, SupplierFilter } from '../types'

export function useMaterials(filters: MaterialFilter) {
  return useQuery({ queryKey: ['catalog', 'materials', 'page', filters], queryFn: ({ signal }) => catalogApi.materialPage(filters, signal) })
}
export function useSuppliers(filters: SupplierFilter) {
  return useQuery({ queryKey: ['catalog', 'suppliers', 'page', filters], queryFn: ({ signal }) => catalogApi.supplierPage(filters, signal) })
}
function useRefreshCatalog() {
  const client = useQueryClient()
  return async () => { await Promise.all([client.invalidateQueries({ queryKey: ['catalog'] }), client.invalidateQueries({ queryKey: ['dashboard'] })]) }
}
export function useSaveMaterial(id?: number) {
  const refresh = useRefreshCatalog()
  return useMutation({ mutationFn: (data: MaterialCommand) => id === undefined ? catalogApi.createMaterial(data) : catalogApi.updateMaterial(id, data), onSuccess: refresh })
}
export function useSaveSupplier(id?: number) {
  const refresh = useRefreshCatalog()
  return useMutation({ mutationFn: (data: SupplierCommand) => id === undefined ? catalogApi.createSupplier(data) : catalogApi.updateSupplier(id, data), onSuccess: refresh })
}
export function useMaterialStatus() {
  const refresh = useRefreshCatalog()
  return useMutation({ mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
    const { id: _id, ...current } = await catalogApi.material(id)
    return catalogApi.updateMaterial(id, { ...current, active })
  }, onSuccess: refresh })
}
export function useSupplierStatus() {
  const refresh = useRefreshCatalog()
  return useMutation({ mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
    const { id: _id, ...current } = await catalogApi.supplier(id)
    return catalogApi.updateSupplier(id, { ...current, active })
  }, onSuccess: refresh })
}
