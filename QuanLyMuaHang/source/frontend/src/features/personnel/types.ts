export type EmployeeStatus = 'ACTIVE' | 'INACTIVE'
export type Employee = {
  id: number; employeeCode: string; fullName: string; email: string | null; phone: string | null
  departmentId: number | null; departmentName: string | null; positionId: number | null; positionName: string | null
  status: EmployeeStatus; joinedAt: string | null; leftAt: string | null; version: number
}
export type EmployeeCommand = {
  employeeCode: string; fullName: string; email: string | null; phone: string | null
  departmentId: number | null; positionId: number | null; joinedAt: string | null
}
export type Unit = { id: number; code: string | null; name: string; active: boolean; parentId?: number | null }
export type UnitCommand = { code: string | null; name: string; active: boolean; parentId?: number | null }
export type EmployeeFilter = { q?: string; status?: string; page: number; size: number }
