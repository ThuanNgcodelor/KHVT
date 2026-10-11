export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED'
export type UserAccount = {
  id: number; email: string; displayName: string; employeeId: number | null; status: AccountStatus
  mustChangePassword: boolean; failedLoginCount: number; lockedUntil: string | null; lastLoginAt: string | null; roleCodes: string[]
  employeeCode?: string | null; employeeName?: string | null; employeeStatus?: 'ACTIVE' | 'INACTIVE' | null
  departmentId?: number | null; departmentName?: string | null; positionId?: number | null; positionName?: string | null
}
export type Role = { code: string; name: string; description: string | null; permissions: string[]; moduleCodes: string[]; assignable?: boolean }
export type EmployeeProfile = { employeeCode: string | null; departmentId: number | null; positionId: number | null }
export type CreateUser = { email: string; displayName: string; initialPassword: string; employeeId: number | null; roleCodes: string[]; employeeProfile?: EmployeeProfile | null }
export type UpdateUser = { displayName: string; employeeId: number | null; status: AccountStatus; roleCodes: string[]; employeeProfile?: EmployeeProfile | null }
export type UserFilters = { query?: string; status?: AccountStatus | ''; departmentId?: number; page: number; size: number }
export type UserOptions = { departments: import('../personnel/types').Unit[]; positions: import('../personnel/types').Unit[] }
export type EmployeeOption = Pick<import('../personnel/types').Employee, 'id' | 'employeeCode' | 'fullName' | 'departmentId' | 'departmentName' | 'positionId' | 'positionName' | 'status'>
