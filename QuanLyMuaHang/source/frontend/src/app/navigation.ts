import type { RoleCode } from '../features/auth/types'
import type { IconName } from '../components/Icon'

export const navigation: { path: string; label: string; icon: IconName; group: string; roles?: RoleCode[] }[] = [
  { path: '/dashboard', label: 'Tổng quan', icon: 'overview', group: 'Làm việc' },
  { path: '/purchase-orders', label: 'Mua hàng', icon: 'orders', group: 'Làm việc', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/price-search', label: 'Tra cứu giá', icon: 'search', group: 'Làm việc', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/imports', label: 'Nhập dữ liệu', icon: 'import', group: 'Làm việc', roles: ['ADMIN', 'PLANNER'] },
  { path: '/admin/employees', label: 'Nhân sự', icon: 'people', group: 'Quản trị', roles: ['ADMIN', 'HR_MANAGER'] },
  { path: '/admin/users', label: 'Tài khoản', icon: 'users', group: 'Quản trị', roles: ['ADMIN'] },
]
