import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { CreateUser, EmployeeOption, Role, UpdateUser, UserAccount, UserFilters, UserOptions } from './types'
export const userAdminApi = {
  users: (filters: UserFilters, signal?: AbortSignal) => apiClient.get<PageResponse<UserAccount>>(`/admin/users${queryParams(filters)}`, { signal }),
  options: (signal?: AbortSignal) => apiClient.get<UserOptions>('/admin/users/options', { signal }),
  employees: (filters: { query?: string; employeeId?: number; page: number; size: number }, signal?: AbortSignal) => apiClient.get<PageResponse<EmployeeOption>>(`/admin/users/employees${queryParams(filters)}`, { signal }),
  roles: (signal?: AbortSignal) => apiClient.get<Role[]>('/admin/roles', { signal }),
  create: (data: CreateUser) => apiClient.post<UserAccount>('/admin/users', data),
  update: (id: number, data: UpdateUser) => apiClient.put<UserAccount>(`/admin/users/${id}`, data),
  resetPassword: (id: number, temporaryPassword: string) => apiClient.post<void>(`/admin/users/${id}/reset-password`, { temporaryPassword }),
}
