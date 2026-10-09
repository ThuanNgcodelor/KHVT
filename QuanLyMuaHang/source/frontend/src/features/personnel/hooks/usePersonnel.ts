import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { personnelApi } from '../personnelApi'
import type { EmployeeCommand, EmployeeFilter, UnitCommand } from '../types'

export function useEmployees(filters: EmployeeFilter) {
  return useQuery({ queryKey: ['personnel', 'employees', filters], queryFn: ({ signal }) => personnelApi.employees(filters, signal) })
}
export function useUnits(kind: 'departments' | 'positions') {
  return useQuery({ queryKey: ['personnel', kind], queryFn: ({ signal }) => personnelApi.units(kind, signal) })
}
function useRefreshPersonnel() {
  const client = useQueryClient()
  return async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ['personnel'] }), client.invalidateQueries({ queryKey: ['users'] })])
  }
}
export function useSaveEmployee(id?: number) {
  const refresh = useRefreshPersonnel()
  return useMutation({ mutationFn: (data: EmployeeCommand) => personnelApi.saveEmployee(id, data), onSuccess: refresh })
}
export function useEmployeeStatus() {
  const refresh = useRefreshPersonnel()
  return useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => personnelApi.setEmployeeActive(id, active), onSuccess: refresh })
}
export function useSaveUnit(kind: 'departments' | 'positions', id?: number) {
  const refresh = useRefreshPersonnel()
  return useMutation({ mutationFn: (data: UnitCommand) => personnelApi.saveUnit(kind, id, data), onSuccess: refresh })
}
