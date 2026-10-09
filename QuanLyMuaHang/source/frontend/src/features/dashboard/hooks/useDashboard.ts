import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../../services/dashboardApi'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasEveryPermission } from '../../auth/types'

export function useDashboard() {
  const { user } = useAuth()
  const purchasing = !!user && hasEveryPermission(user, ['PO_READ', 'CATALOG_READ'])
  const summary = useQuery({
    queryKey: ['dashboard', user?.id],
    queryFn: ({ signal }) => dashboardApi.getSummary(signal),
    enabled: purchasing,
  })
  return { user, purchasing, summary }
}
