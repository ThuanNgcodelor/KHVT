import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../../services/dashboardApi'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasRole } from '../../auth/types'

export function useDashboard() {
  const { user } = useAuth()
  const purchasing = !!user && hasRole(user, ['ADMIN', 'PLANNER', 'VIEWER'])
  const summary = useQuery({
    queryKey: ['dashboard', user?.id],
    queryFn: ({ signal }) => dashboardApi.getSummary(signal),
    enabled: purchasing,
  })
  return { user, purchasing, summary }
}
