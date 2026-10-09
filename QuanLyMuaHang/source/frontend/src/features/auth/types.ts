export type RoleCode = 'ADMIN' | 'HR_MANAGER' | 'PLANNER' | 'VIEWER'
export type CurrentUser = {
  id: number
  email: string
  displayName: string
  employeeId: number | null
  roles: string[]
  mustChangePassword: boolean
  authenticatedAt: string
  modules: ApplicationModule[]
  permissions: string[]
}
export type ApplicationModule = { code: string; name: string; description: string; entryPath: string }
export function hasRole(user: CurrentUser, roles: readonly RoleCode[]) {
  return user.roles.includes('ADMIN') || roles.some((role) => user.roles.includes(role))
}
export function hasPermission(user: CurrentUser, permission: string) {
  return user.permissions?.includes('*') || user.permissions?.includes(permission) || false
}
export function hasEveryPermission(user: CurrentUser, permissions: readonly string[]) {
  return permissions.every((permission) => hasPermission(user, permission))
}
export const roleLabels: Record<RoleCode, string> = {
  ADMIN: 'Quản trị viên', HR_MANAGER: 'Quản lý nhân sự', PLANNER: 'Nhân viên kế hoạch', VIEWER: 'Chỉ xem',
}
