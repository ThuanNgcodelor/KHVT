import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { Employee, EmployeeCommand, EmployeeFilter, Unit, UnitCommand } from './types'
export const personnelApi = {
  employees: (filters: EmployeeFilter, signal?: AbortSignal) => apiClient.get<PageResponse<Employee>>(`/personnel/employees${queryParams(filters)}`, { signal }),
  employee: (id: number, signal?: AbortSignal) => apiClient.get<Employee>(`/personnel/employees/${id}`, { signal }),
  saveEmployee: (id: number | undefined, data: EmployeeCommand) => id === undefined
    ? apiClient.post<Employee>('/personnel/employees', data) : apiClient.put<Employee>(`/personnel/employees/${id}`, data),
  setEmployeeActive: (id: number, active: boolean) => apiClient.post<Employee>(`/personnel/employees/${id}/${active ? 'activate' : 'deactivate'}`),
  units: (kind: 'departments' | 'positions', signal?: AbortSignal) => apiClient.get<Unit[]>(`/personnel/${kind}`, { signal }),
  saveUnit: (kind: 'departments' | 'positions', id: number | undefined, data: UnitCommand) => id === undefined
    ? apiClient.post<Unit>(`/personnel/${kind}`, data) : apiClient.put<Unit>(`/personnel/${kind}/${id}`, data),
}
