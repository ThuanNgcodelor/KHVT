import type { Page } from '@playwright/test'
import type { ApplicationModule, CurrentUser } from '../../src/features/auth/types'
import type { Role, UserAccount } from '../../src/features/identity-admin/types'
import type { Employee, Unit } from '../../src/features/personnel/types'

// Synthetic, per-test API state. These fixtures never access real accounts or MySQL/Redis.
export const applicationModules: ApplicationModule[] = [
  { code: 'PURCHASING', name: 'Mua hàng', description: 'Đơn mua hàng và tra cứu giá', entryPath: '/dashboard' },
  { code: 'PERSONNEL', name: 'Nhân sự', description: 'Nhân viên, phòng ban và chức vụ', entryPath: '/admin/employees' },
  { code: 'ADMINISTRATION', name: 'Quản trị', description: 'Tài khoản và phân quyền', entryPath: '/admin/users' },
]
const rolePermissions: Record<string, string[]> = {
  ADMIN: ['*'], HR_MANAGER: ['PERSONNEL_READ', 'PERSONNEL_MANAGE'],
  VIEWER: ['PO_READ', 'CATALOG_READ', 'PRICE_READ'],
  PLANNER: ['PO_READ', 'PO_MANAGE', 'CATALOG_READ', 'CATALOG_MANAGE', 'PRICE_READ', 'IMPORT_OPERATIONAL'],
}
export const roles: Role[] = [
  { code: 'ADMIN', name: 'Quản trị viên', description: 'Quản trị hệ thống', permissions: ['*'], moduleCodes: applicationModules.map((module) => module.code) },
  { code: 'HR_MANAGER', name: 'Quản lý nhân sự', description: 'Quản lý danh bạ', permissions: rolePermissions.HR_MANAGER, moduleCodes: ['PERSONNEL'] },
  { code: 'VIEWER', name: 'Chỉ xem', description: 'Đọc dữ liệu mua hàng', permissions: rolePermissions.VIEWER, moduleCodes: ['PURCHASING'] },
  { code: 'PLANNER', name: 'Nhân viên kế hoạch', description: 'Quản lý mua hàng', permissions: rolePermissions.PLANNER, moduleCodes: ['PURCHASING'] },
]
type Options = { role?: string; permissions?: string[]; modules?: ApplicationModule[]; signedIn?: boolean; mustChange?: boolean }
type Write = { path: string; method: string; body: Record<string, unknown> }

export async function mockApi(page: Page, options: Options = {}) {
  const permissions = options.permissions ?? rolePermissions[options.role ?? 'ADMIN'] ?? []
  const allowed = (permission: string) => permissions.includes('*') || permissions.includes(permission)
  const modules = options.modules ?? applicationModules.filter((module) => module.code === 'PURCHASING'
    ? allowed('PO_READ') && allowed('CATALOG_READ') : allowed(module.code === 'PERSONNEL' ? 'PERSONNEL_READ' : 'USER_READ'))
  const state = {
    signedIn: options.signedIn ?? false, mustChange: options.mustChange ?? false,
    csrf: 0, dashboardCalls: 0, dashboardStatus: 200, changes: 0,
    employeeFailures: 0, employeeListStatus: 200, lookupStatus: 200, roleStatus: 200, userStatus: 200,
    writes: [] as Write[], unexpectedRequests: [] as string[],
    employees: [{ id: 10, employeeCode: 'NV001', fullName: 'Nhân viên mẫu An', email: 'an@fixture.test', phone: null,
      departmentId: 1, departmentName: 'Phòng KHVT', positionId: 1, positionName: 'Chuyên viên', status: 'ACTIVE',
      joinedAt: '2026-01-01', leftAt: null, version: 0 }] as Employee[],
    departments: [{ id: 1, code: 'KHVT', name: 'Phòng KHVT', active: true, parentId: null }] as Unit[],
    positions: [{ id: 1, code: 'CV', name: 'Chuyên viên', active: true }] as Unit[],
    accounts: [{ id: 20, email: 'member@fixture.test', displayName: 'Tài khoản mẫu', employeeId: null, status: 'ACTIVE',
      mustChangePassword: false, failedLoginCount: 0, lockedUntil: null, lastLoginAt: null, roleCodes: ['VIEWER'] }] as UserAccount[],
  }
  const user = (): CurrentUser => ({ id: 1, email: 'fixture@localhost', displayName: 'Người dùng kiểm thử', employeeId: null,
    roles: [options.role ?? 'ADMIN'], permissions, modules, mustChangePassword: state.mustChange, authenticatedAt: '2026-10-10T08:00:00Z' })
  const paged = <T>(data: T[], params: URLSearchParams) => {
    const number = Number(params.get('page') ?? 0), size = Number(params.get('size') ?? 25)
    return { content: data.slice(number * size, (number + 1) * size), number, size, totalElements: data.length, totalPages: Math.ceil(data.length / size) }
  }
  await page.route('**/api/**', async (route) => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    const forbidden = () => reply({ code: 'FORBIDDEN', message: 'Bạn không có quyền thực hiện thao tác này.' }, 403)
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-XSRF-TOKEN', token: `fixture-${++state.csrf}` })
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && request.headers()['x-xsrf-token'] !== `fixture-${state.csrf}`) return forbidden()
    if (path === '/api/auth/me') return state.signedIn ? reply(user()) : reply({ code: 'UNAUTHENTICATED' }, 401)
    if (path === '/api/auth/login') {
      if (request.postDataJSON().password !== 'fixture-password') return reply({ message: 'Thông tin đăng nhập không đúng.' }, 401)
      state.signedIn = true
      return reply(user())
    }
    if (!state.signedIn) return reply({ code: 'UNAUTHENTICATED' }, 401)
    if (path === '/api/auth/logout') { state.signedIn = false; return route.fulfill({ status: 204 }) }
    if (path === '/api/auth/change-password') {
      state.changes++
      if (state.csrf < 2) return forbidden()
      state.mustChange = false
      return reply(user())
    }
    if (state.mustChange) return reply({ code: 'PASSWORD_CHANGE_REQUIRED' }, 403)
    if (path === '/api/dashboard') {
      state.dashboardCalls++
      if (!allowed('PO_READ') || !allowed('CATALOG_READ')) return forbidden()
      if (state.dashboardStatus !== 200) {
        if (state.dashboardStatus === 401) state.signedIn = false
        return reply({ code: state.dashboardStatus === 401 ? 'UNAUTHENTICATED' : 'INTERNAL_ERROR' }, state.dashboardStatus)
      }
      return reply({ purchaseOrdersThisMonth: 3, activeMaterials: 12, activeSuppliers: 4, recentOrders: [], generatedAt: '2026-10-10T08:00:00Z' })
    }
    if (path.startsWith('/api/personnel/')) {
      if (!allowed('PERSONNEL_READ') || (method !== 'GET' && !allowed('PERSONNEL_MANAGE'))) return forbidden()
      if (method !== 'GET') state.writes.push({ path, method, body: request.postData() ? request.postDataJSON() : {} })
      const employeeId = path.match(/^\/api\/personnel\/employees\/(\d+)(?:\/(activate|deactivate))?$/)
      if (path === '/api/personnel/employees' && method === 'GET') {
        if (state.employeeListStatus !== 200) return reply({ message: 'Chưa tải được danh sách nhân viên.' }, state.employeeListStatus)
        const q = (url.searchParams.get('q') ?? '').toLocaleLowerCase(), status = url.searchParams.get('status')
        return reply(paged(state.employees.filter((employee) => (!q || `${employee.employeeCode} ${employee.fullName}`.toLocaleLowerCase().includes(q)) && (!status || employee.status === status)), url.searchParams))
      }
      if (employeeId && method === 'GET') return reply(state.employees.find((employee) => employee.id === Number(employeeId[1])))
      if (employeeId?.[2]) {
        const employee = state.employees.find((item) => item.id === Number(employeeId[1]))!
        employee.status = employeeId[2] === 'activate' ? 'ACTIVE' : 'INACTIVE'
        if (employee.status === 'INACTIVE') state.accounts.filter((account) => account.employeeId === employee.id).forEach((account) => { account.status = 'DISABLED' })
        return reply(employee)
      }
      if ((path === '/api/personnel/employees' || employeeId) && method !== 'GET') {
        if (state.employeeFailures > 0) { state.employeeFailures--; return reply({ code: 'EMPLOYEE_CODE_EXISTS', message: 'Mã nhân viên đã tồn tại' }, 409) }
        const data = request.postDataJSON(), id = employeeId ? Number(employeeId[1]) : Math.max(...state.employees.map((item) => item.id), 0) + 1
        const original = state.employees.find((item) => item.id === id)
        const employee: Employee = { id, status: 'ACTIVE', leftAt: null, version: 0, ...original, ...data,
          departmentName: state.departments.find((item) => item.id === data.departmentId)?.name ?? null,
          positionName: state.positions.find((item) => item.id === data.positionId)?.name ?? null }
        state.employees = [...state.employees.filter((item) => item.id !== id), employee]
        return reply(employee)
      }
      const unitPath = path.match(/^\/api\/personnel\/(departments|positions)(?:\/(\d+))?$/)
      if (unitPath) {
        const kind = unitPath[1] as 'departments' | 'positions'
        if (method === 'GET') return state.lookupStatus === 200 ? reply(state[kind]) : reply({ message: 'Danh mục tạm thời không khả dụng.' }, state.lookupStatus)
        const data = request.postDataJSON(), id = unitPath[2] ? Number(unitPath[2]) : Math.max(...state[kind].map((item) => item.id), 0) + 1
        const unit: Unit = { id, ...data }
        state[kind] = [...state[kind].filter((item) => item.id !== id), unit]
        return reply(unit)
      }
    }
    if (path.startsWith('/api/admin/')) {
      if (path === '/api/admin/roles') return allowed('USER_READ')
        ? state.roleStatus === 200 ? reply(roles) : reply({ message: 'Chưa tải được vai trò.' }, state.roleStatus) : forbidden()
      if (!allowed('USER_READ') || (method !== 'GET' && !allowed('USER_MANAGE'))) return forbidden()
      if (method !== 'GET') state.writes.push({ path, method, body: request.postDataJSON() })
      if (state.userStatus !== 200) return reply({ message: 'Chưa thực hiện được thao tác tài khoản.' }, state.userStatus)
      if (path === '/api/admin/users' && method === 'GET') return reply(paged(state.accounts, url.searchParams))
      const accountPath = path.match(/^\/api\/admin\/users\/(\d+)(?:\/(reset-password))?$/)
      if (accountPath?.[2]) {
        state.accounts.find((account) => account.id === Number(accountPath[1]))!.mustChangePassword = true
        return route.fulfill({ status: 200, body: '' })
      }
      if (path === '/api/admin/users' || accountPath) {
        const data = request.postDataJSON(), id = accountPath ? Number(accountPath[1]) : Math.max(...state.accounts.map((item) => item.id), 0) + 1
        const original = state.accounts.find((account) => account.id === id)
        const { initialPassword: _password, ...publicData } = data
        const account: UserAccount = { id, status: 'ACTIVE', mustChangePassword: true, failedLoginCount: 0, lockedUntil: null, lastLoginAt: null, ...original, ...publicData }
        state.accounts = [...state.accounts.filter((item) => item.id !== id), account]
        return reply(account)
      }
    }
    state.unexpectedRequests.push(`${method} ${path}`)
    return reply({ code: 'UNEXPECTED_TEST_REQUEST' }, 404)
  })
  return state
}

export async function login(page: Page, password = 'fixture-password') {
  await page.getByLabel('Email', { exact: true }).fill('fixture@localhost')
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
}
