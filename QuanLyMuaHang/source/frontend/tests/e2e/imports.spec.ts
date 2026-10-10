import { readFile } from 'node:fs/promises'
import { expect, test, type Page } from '@playwright/test'
import { purchasing } from './purchasingFixtures'

const workbook = { name: 'fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('synthetic mocked workbook') }
async function preview(page: Page) {
  await page.getByLabel('Tệp dữ liệu').setInputFiles(workbook)
  await page.getByRole('button', { name: 'Preview tệp', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Preview workbook — lô 1' })).toBeVisible()
}
async function confirmCommit(page: Page) {
  await page.getByRole('checkbox', { name: /Tôi đã đối chiếu/ }).check()
  await page.getByRole('button', { name: 'Commit dữ liệu cũ', exact: true }).click()
}

test('legacy is the default and commit updates status with backend verification', async ({ page }) => {
  const state = await purchasing(page)
  await page.goto('/imports')
  await expect(page.getByRole('heading', { name: 'Nhập lịch sử từ workbook' })).toBeVisible()
  await preview(page)
  await confirmCommit(page)
  await page.getByRole('button', { name: 'Commit dữ liệu', exact: true }).click()
  await expect(page.getByText('COMMITTED', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Đối chiếu dữ liệu đã ghi' })).toBeVisible()
  const verification = page.getByRole('status').locator('dl')
  await expect(verification.locator('div').filter({ hasText: 'Dòng nguồn' })).toHaveText('Dòng nguồn4')
  await expect(verification.locator('div').filter({ hasText: 'Dòng đã đối chiếu' })).toHaveText('Dòng đã đối chiếu4')
  await expect(verification.locator('div').filter({ hasText: 'Dòng lịch sử' })).toHaveText('Dòng lịch sử2')
  await expect(verification.locator('div').filter({ hasText: 'Dòng PO' })).toHaveText('Dòng PO1')
  await expect(page.getByRole('button', { name: 'Commit dữ liệu cũ', exact: true })).toHaveCount(0)
  await expect(page.getByText(/trường thiếu hoặc được ghi theo giả định/)).toBeVisible()
  expect(state.commits).toBe(1)
})

test('a workbook uploaded as an operational request switches to full legacy preview with the same file', async ({ page }) => {
  const state = await purchasing(page); state.legacyDetected = true
  await page.goto('/imports')
  await page.getByRole('button', { name: 'Yêu cầu mua / báo giá', exact: true }).click()
  await preview(page)
  await expect(page.getByRole('heading', { name: 'Nhập lịch sử từ workbook' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('Tệp đã chọn được giữ nguyên')
  await expect(page.getByText('Tệp đã chọn: fixture.xlsx', { exact: true })).toBeVisible()
  expect(state.operationalPreviewCalls).toBe(1); expect(state.legacyPreviewCalls).toBe(1); expect(state.commits).toBe(0)
  await page.getByRole('button', { name: 'Preview tệp', exact: true }).click()
  await expect.poll(() => state.legacyPreviewCalls).toBe(2)
})

test('detecting a legacy workbook without permission explains the required admin action', async ({ page }) => {
  const state = await purchasing(page, 'PLANNER'); state.legacyDetected = true
  await page.goto('/imports')
  await page.getByLabel('Tệp dữ liệu').setInputFiles(workbook)
  await page.getByRole('button', { name: 'Preview tệp', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Tài khoản chưa có quyền nhập workbook')
  await expect(page.getByRole('button', { name: 'Workbook dữ liệu cũ', exact: true })).toHaveCount(0)
  expect(state.legacyPreviewCalls).toBe(0); expect(state.commits).toBe(0)
})

test('the CSV download includes warnings beyond the 30-row preview', async ({ page }) => {
  const state = await purchasing(page)
  const issues = Array.from({ length: 31 }, (_, index) => ({ sheet: 'LICH_SU', rowNumber: index + 2, status: 'WARNING', issues: [`WARNING:MATERIAL_CODE_MISSING: Dòng ${index + 2}`] }))
  state.legacyPreview.rowIssues = issues.slice(0, 30)
  state.legacyPreview.summary = { ...state.legacyPreview.summary, rowsPerSheet: { NCC: 1, LICH_SU: 31, DON_HANG: 1 }, totalRows: 33, warningRows: 31 }
  state.issueCsv = 'sheet,rowNumber,status,issue\r\n' + issues.map((row) => `${row.sheet},${row.rowNumber},${row.status},${row.issues[0]}\r\n`).join('')
  await page.goto('/imports'); await preview(page)
  await page.getByText('Chi tiết cảnh báo/lỗi (tối đa 30 dòng)', { exact: true }).click()
  await expect(page.getByText('LICH_SU · dòng 32:', { exact: false })).toHaveCount(0)
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Tải toàn bộ cảnh báo/lỗi (CSV)', exact: true }).click()
  const file = await downloaded
  expect(file.suggestedFilename()).toBe('legacy-import-1-issues.csv')
  expect(await readFile((await file.path())!, 'utf8')).toContain('LICH_SU,32,WARNING,WARNING:MATERIAL_CODE_MISSING: Dòng 32')
  expect(state.commits).toBe(0)
})

test('rapid duplicate confirmation sends only one commit while the first request is pending', async ({ page }) => {
  const state = await purchasing(page)
  let calls = 0, release!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/imports/legacy/1/commit', async (route) => {
    calls++; await pending
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(state.legacyCommit) })
  })
  await page.goto('/imports'); await preview(page); await confirmCommit(page)
  await page.getByRole('button', { name: 'Commit dữ liệu', exact: true }).evaluate((button) => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  await expect.poll(() => calls).toBe(1)
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Đang xử lý…', exact: true })).toBeDisabled()
  release()
  await expect(page.getByText('COMMITTED', { exact: true })).toBeVisible()
  expect(calls).toBe(1)
})

test('a lost commit response can be checked without sending another commit', async ({ page }) => {
  const state = await purchasing(page)
  await page.route('**/api/imports/legacy/1/commit', async (route) => { state.legacyPreview.status = 'COMMITTED'; await route.abort('failed') })
  await page.goto('/imports'); await preview(page); await confirmCommit(page)
  await page.getByRole('button', { name: 'Commit dữ liệu', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Kiểm tra kết nối')
  await page.getByRole('button', { name: 'Quay lại', exact: true }).click()
  await page.getByRole('button', { name: 'Kiểm tra lại lô', exact: true }).click()
  await expect(page.getByText('COMMITTED', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Commit dữ liệu cũ', exact: true })).toHaveCount(0)
  await expect(page.getByText('Lô đã commit; không gửi lại để tránh nhập trùng.', { exact: true })).toBeVisible()
})
