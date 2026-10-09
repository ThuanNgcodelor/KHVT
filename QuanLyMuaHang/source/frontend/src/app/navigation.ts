import type { RoleCode } from '../features/auth/types'

export const navigation: { path: string; label: string; icon: string; roles?: RoleCode[] }[] = [
  { path: '/dashboard', label: 'Tổng quan', icon: '⌂' },
  { path: '/purchase-orders', label: 'Mua hàng', icon: '▣', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/price-search', label: 'Tra cứu giá', icon: '⌕', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/imports', label: 'Nhập dữ liệu', icon: '↥', roles: ['ADMIN', 'PLANNER'] },
  { path: '/admin/employees', label: 'Nhân sự', icon: '♙', roles: ['ADMIN', 'HR_MANAGER'] },
  { path: '/admin/users', label: 'Tài khoản', icon: '⚙', roles: ['ADMIN'] },
]
