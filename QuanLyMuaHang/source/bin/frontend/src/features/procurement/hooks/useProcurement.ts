import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { procurementApi } from '../procurementApi'
import type { OrderCommand, OrderFilter } from '../types'

export function useOrders(filters: OrderFilter) {
  return useQuery({ queryKey: ['procurement', 'orders', filters], queryFn: ({ signal }) => procurementApi.orders(filters, signal) })
}
export function useOrder(id: number) {
  return useQuery({ queryKey: ['procurement', 'order', id], queryFn: ({ signal }) => procurementApi.order(id, signal), enabled: Number.isSafeInteger(id) && id > 0 })
}
export function useOrderRevisions(id: number) {
  return useQuery({ queryKey: ['procurement', 'revisions', id], queryFn: ({ signal }) => procurementApi.revisions(id, signal), enabled: Number.isSafeInteger(id) && id > 0 })
}
export function useRefreshProcurement() {
  const client = useQueryClient()
  return async () => {
    await Promise.all(['procurement', 'dashboard', 'pricing'].map((key) => client.invalidateQueries({ queryKey: [key] })))
  }
}
export function useSaveOrder(id?: number) {
  const refresh = useRefreshProcurement()
  return useMutation({ mutationFn: (data: OrderCommand) => id ? procurementApi.update(id, data) : procurementApi.create(data), onSuccess: refresh })
}
export function useIssueOrder(id: number) {
  const refresh = useRefreshProcurement()
  return useMutation({ mutationFn: () => procurementApi.issue(id), onSuccess: refresh })
}
export function useCancelOrder(id: number) {
  const refresh = useRefreshProcurement()
  return useMutation({ mutationFn: (reason: string) => procurementApi.cancel(id, reason), onSuccess: refresh })
}
