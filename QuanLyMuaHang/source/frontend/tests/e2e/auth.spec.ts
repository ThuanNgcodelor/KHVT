import { test, expect, type Page } from '@playwright/test'

// Synthetic API fixtures: these tests do not connect to MySQL/Redis or real accounts.
async function mockApi(page: Page, options: { role?: string; signedIn?: boolean; mustChange?: boolean } = {}) {
  const state = { signedIn: options.signedIn ?? false, mustChange: options.mustChange ?? false, csrf: 0, dashboardCalls: 0, dashboardStatus: 200, changes: 0 }
  const user = () => ({ id: 1, email: 'fixture@example.test', displayName: 'Người dùng kiểm thử', employeeId: null,
    roles: [options.role ?? 'ADMIN'], mustChangePassword: state.mustChange, authenticatedAt: '2026-10-09T08:00:00Z' })
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-XSRF-TOKEN', token: `fixture-${++state.csrf}` })
    if (request.method() === 'POST' && request.headers()['x-xsrf-token'] !== `fixture-${state.csrf}`) return reply({ code: 'FORBIDDEN', message: 'CSRF test failed' }, 403)
    if (path === '/api/auth/me') return state.signedIn ? reply(user()) : reply({ code: 'UNAUTHENTICATED' }, 401)
    if (path === '/api/auth/login') {
      if (request.postDataJSON().password !== 'fixture-password') return reply({ message: 'Thông tin đăng nhập không đúng.' }, 401)
      state.signedIn = true
      return reply(user())
    }
    if (path === '/api/auth/logout') { state.signedIn = false; return route.fulfill({ status: 204 }) }
    if (path === '/api/auth/change-password') {
      state.changes++
      if (state.csrf < 2) return reply({ message: 'Expected CSRF rotation' }, 403)
      state.mustChange = false
      return reply(user())
    }
    if (path === '/api/dashboard') {
      state.dashboardCalls++
      if (state.dashboardStatus !== 200) {
        if (state.dashboardStatus === 401) state.signedIn = false
        return reply({ code: state.dashboardStatus === 401 ? 'UNAUTHENTICATED' : 'INTERNAL_ERROR' }, state.dashboardStatus)
      }
      return reply({ purchaseOrdersThisMonth: 3, activeMaterials: 12, activeSuppliers: 4, recentOrders: [], generatedAt: '2026-10-09T08:00:00Z' })
    }
    return reply({ code: 'UNEXPECTED_TEST_REQUEST' }, 404)
  })
  return state
}

async function login(page: Page, password = 'fixture-password') {
  await page.getByLabel('Email', { exact: true }).fill('fixture@example.test')
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
}

test('redirects protected pages, logs in, restores session on reload, and logs out', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/login$/)
  await login(page)
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByText('Chưa có đơn mua hàng.', { exact: true })).toBeVisible()
  expect(state.csrf).toBeGreaterThanOrEqual(2)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Xin chào, Người dùng kiểm thử' })).toBeVisible()
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login$/)
})

test('requires a first password change and validates before sending', async ({ page }) => {
  const state = await mockApi(page, { mustChange: true })
  await page.goto('/login')
  await login(page)
  await expect(page).toHaveURL(/\/change-password$/)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/change-password$/)
  expect(state.dashboardCalls).toBe(0)
  await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill('fixture-password')
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill('short')
  await page.getByLabel('Nhập lại mật khẩu mới', { exact: true }).fill('different')
  await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
  await expect(page.getByText('Mật khẩu mới cần từ 12 đến 200 ký tự.', { exact: true })).toBeVisible()
  expect(state.changes).toBe(0)
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill('new-fixture-password')
  await page.getByLabel('Nhập lại mật khẩu mới', { exact: true }).fill('new-fixture-password')
  await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  expect(state.changes).toBe(1)
})

test('shows rejected credentials and clears the password field', async ({ page }) => {
  await mockApi(page)
  await page.goto('/login')
  await login(page, 'wrong-password')
  await expect(page.getByRole('alert')).toHaveText('Thông tin đăng nhập không đúng.')
  await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveValue('')
  await expect(page).toHaveURL(/\/login$/)
})

test('viewer cannot open account administration', async ({ page }) => {
  await mockApi(page, { role: 'VIEWER', signedIn: true })
  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Tài khoản', exact: true })).toHaveCount(0)
})

test('HR sees personnel navigation without purchasing data requests', async ({ page }) => {
  const state = await mockApi(page, { role: 'HR_MANAGER', signedIn: true })
  await page.goto('/dashboard')
  await expect(page.getByRole('link', { name: 'Quản lý nhân sự', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Đơn mua hàng gần đây' })).toHaveCount(0)
  expect(state.dashboardCalls).toBe(0)
})

test('dashboard error can be retried without showing demo data', async ({ page }) => {
  const state = await mockApi(page, { signedIn: true })
  state.dashboardStatus = 500
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Chưa tải được tổng quan' })).toBeVisible()
  state.dashboardStatus = 200
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.getByText('Chưa có đơn mua hàng.', { exact: true })).toBeVisible()
})

test('expired API session redirects to login and removes private content', async ({ page }) => {
  const state = await mockApi(page, { signedIn: true })
  await page.goto('/dashboard')
  await expect(page.getByText('Chưa có đơn mua hàng.', { exact: true })).toBeVisible()
  state.dashboardStatus = 401
  await page.getByRole('button', { name: 'Cập nhật dữ liệu' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('Đơn mua hàng gần đây', { exact: true })).toHaveCount(0)
})

test('layout fits the viewport and mobile navigation closes with Escape', async ({ page }, testInfo) => {
  await mockApi(page, { signedIn: true })
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Xin chào, Người dùng kiểm thử' })).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    const toggle = page.getByRole('button', { name: 'Mở menu' })
    await toggle.click()
    await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(toggle).toBeFocused()
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await expect(page.getByRole('contentinfo')).toContainText('Quản lý mua hàng KHVT')
})
