import type { IconName } from '../components/Icon'

export const navigation: { path: string; label: string; icon: IconName; group: string; moduleCode: string; permissions: string[]; anyPermissions?: string[] }[] = [
  { path: '/dashboard', label: 'Tổng quan', icon: 'overview', group: 'Mua hàng', moduleCode: 'PURCHASING', permissions: ['PO_READ', 'CATALOG_READ'] },
  { path: '/purchase-orders', label: 'Đơn mua hàng', icon: 'orders', group: 'Mua hàng', moduleCode: 'PURCHASING', permissions: ['PO_READ'] },
  { path: '/price-search', label: 'Tra cứu giá', icon: 'search', group: 'Mua hàng', moduleCode: 'PURCHASING', permissions: ['PRICE_READ'] },
  { path: '/imports', label: 'Nhập dữ liệu', icon: 'import', group: 'Mua hàng', moduleCode: 'PURCHASING', permissions: [], anyPermissions: ['IMPORT_OPERATIONAL', 'IMPORT_LEGACY'] },
  { path: '/catalog/materials', label: 'Vật tư', icon: 'orders', group: 'Danh mục', moduleCode: 'PURCHASING', permissions: ['CATALOG_READ'] },
  { path: '/catalog/suppliers', label: 'Nhà cung cấp', icon: 'users', group: 'Danh mục', moduleCode: 'PURCHASING', permissions: ['CATALOG_READ'] },
  { path: '/admin/employees', label: 'Nhân viên', icon: 'people', group: 'Nhân sự', moduleCode: 'PERSONNEL', permissions: ['PERSONNEL_READ'] },
  { path: '/admin/departments', label: 'Phòng ban', icon: 'overview', group: 'Nhân sự', moduleCode: 'PERSONNEL', permissions: ['PERSONNEL_READ'] },
  { path: '/admin/positions', label: 'Chức vụ', icon: 'users', group: 'Nhân sự', moduleCode: 'PERSONNEL', permissions: ['PERSONNEL_READ'] },
  { path: '/admin/users', label: 'Tài khoản', icon: 'users', group: 'Quản trị', moduleCode: 'ADMINISTRATION', permissions: ['USER_READ'] },
]
export function moduleForPath(pathname: string) {
  return navigation.find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))?.moduleCode
}
