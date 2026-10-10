import { apiClient, refreshCsrf, resetCsrf } from '../../services/apiClient'
import type { CurrentUser } from './types'
export const authApi = {
  me: (signal?: AbortSignal) => apiClient.get<CurrentUser>('/auth/me', { signal }),
  login: async (email: string, password: string) => {
    // Another tab may have changed the CSRF cookie after this tab cached its token.
    await refreshCsrf()
    const user = await apiClient.post<CurrentUser>('/auth/login', { email, password })
    resetCsrf()
    return user
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.post<CurrentUser>('/auth/change-password', { currentPassword, newPassword }),
  logout: async () => { await apiClient.post<void>('/auth/logout'); resetCsrf() },
  refreshCsrf,
}
