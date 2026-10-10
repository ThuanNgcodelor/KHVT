import { apiClient } from '../../services/apiClient'
import { queryParams } from '../../services/queryParams'
import type { PageResponse } from '../../types/api'
import type { CreateUser, Role, UpdateUser, UserAccount } from './types'
export const userAdminApi = {
  users: (page: number, signal?: AbortSignal) => apiClient.get<PageResponse<UserAccount>>(`/admin/users${queryParams({ page, size: 25 })}`, { signal }),
  roles: (signal?: AbortSignal) => apiClient.get<Role[]>('/admin/roles', { signal }),
  create: (data: CreateUser) => apiClient.post<UserAccount>('/admin/users', data),
  update: (id: number, data: UpdateUser) => apiClient.put<UserAccount>(`/admin/users/${id}`, data),
  resetPassword: (id: number, temporaryPassword: string) => apiClient.post<void>(`/admin/users/${id}/reset-password`, { temporaryPassword }),
}
