import { apiClient } from './apiClient'
import type { DashboardResponse } from '../types/api'

export const dashboardApi = {
  getSummary: (signal?: AbortSignal) => apiClient.get<DashboardResponse>('/dashboard', { signal }),
}
