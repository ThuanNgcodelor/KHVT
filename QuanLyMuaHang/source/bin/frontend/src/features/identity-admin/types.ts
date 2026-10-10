export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED'
export type UserAccount = {
  id: number; email: string; displayName: string; employeeId: number | null; status: AccountStatus
  mustChangePassword: boolean; failedLoginCount: number; lockedUntil: string | null; lastLoginAt: string | null; roleCodes: string[]
}
export type Role = { code: string; name: string; description: string | null; permissions: string[]; moduleCodes: string[] }
export type CreateUser = { email: string; displayName: string; initialPassword: string; employeeId: number | null; roleCodes: string[] }
export type UpdateUser = { displayName: string; employeeId: number | null; status: AccountStatus; roleCodes: string[] }
