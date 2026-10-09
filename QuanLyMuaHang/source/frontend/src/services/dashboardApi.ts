import { apiClient } from './apiClient'
import type { DashboardResponse } from '../types/api'

export const dashboardApi = {
  getSummary: () => apiClient.get<DashboardResponse>('/dashboard'),
}
