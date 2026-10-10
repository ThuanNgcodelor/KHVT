import { test, expect } from '@playwright/test'
import { login, mockApi } from './fixtures'

test('redirects protected pages, logs in, restores session on reload, and logs out', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/login$/)
  await login(page)
  await expect(page).toHaveURL(/\/modules$/)
  await expect(page.getByRole('heading', { name: 'Ứng dụng của bạn' })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Mua hàng/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Nhân sự/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Quản trị/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Bán hàng/ })).toHaveCount(0)
  await page.getByRole('link', { name: /^Mua hàng/ }).click()
  await expect(page.getByText('Chưa có đơn mua hàng.', { exact: true })).toBeVisible()
  expect(state.csrf).toBeGreaterThanOrEqual(2)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Tổng quan mua hàng' })).toBeVisible()
  await page.getByRole('link', { name: 'Đổi ứng dụng' }).click()
  await expect(page).toHaveURL(/\/modules$/)
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
  await expect(page).toHaveURL(/\/modules$/)
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
  await page.goto('/modules')
  await expect(page.getByRole('link', { name: /^Mua hàng/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /^Quản trị/ })).toHaveCount(0)
  await page.getByRole('link', { name: /^Nhân sự/ }).click()
  await expect(page.getByRole('heading', { name: 'Nhân sự', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Thêm nhân viên' })).toBeVisible()
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
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
  await expect(page.getByRole('heading', { name: 'Tổng quan mua hàng' })).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    const toggle = page.getByRole('button', { name: 'Mở menu' })
    await toggle.click()
    await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(toggle).toBeFocused()
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await expect(page.getByRole('contentinfo')).toContainText('© Bản quyền thuộc về KHVT | Cung cấp bởi')
  await expect(page.getByRole('link', { name: 'ThuanNgcodelor', exact: true })).toHaveAttribute('href', 'https://github.com/ThuanNgcodelor')
})

test('portal follows effective permissions for a custom role and guards direct URLs', async ({ page }) => {
  const state = await mockApi(page, { role: 'CUSTOM_READER', signedIn: true, permissions: ['PO_READ', 'CATALOG_READ', 'PRICE_READ'] })
  await page.goto('/modules')
  await expect(page.getByRole('link', { name: /^Mua hàng/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Nhân sự/ })).toHaveCount(0)
  await page.getByRole('link', { name: /^Mua hàng/ }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan mua hàng' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Import yêu cầu', exact: true })).toHaveCount(0)
  await page.goto('/admin/employees')
  await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  expect(state.writes).toHaveLength(0)
})

test('portal explains an account without granted modules', async ({ page }) => {
  const state = await mockApi(page, { role: 'NO_ACCESS', signedIn: true })
  await page.goto('/')
  await expect(page).toHaveURL(/\/modules$/)
  await expect(page.getByText('Chưa có ứng dụng được cấp quyền', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Mua hàng/ })).toHaveCount(0)
  expect(state.dashboardCalls).toBe(0)
})
