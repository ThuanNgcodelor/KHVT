import { expect, test } from '@playwright/test'
import { confirmedRequest, doc, documentFixtures, line } from './documentFixtures'

const workbook = { name: 'request-fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('synthetic mocked file') }
async function upload(page: import('@playwright/test').Page) { await page.getByLabel('Tệp PCVT / báo giá').setInputFiles(workbook); await page.getByRole('button', { name: 'Kiểm tra tệp chứng từ', exact: true }).click() }
async function acknowledge(page: import('@playwright/test').Page) { await page.getByRole('checkbox', { name: /Tôi đã kiểm tra tệp gốc/ }).check() }

test('selected PCVT preview lines can be looked up before confirmation without writing a purchase', async ({ page }) => {
  const state = await documentFixtures(page)
  await page.goto('/imports'); await upload(page)
  await page.getByLabel('Lọc số phiếu nguồn').selectOption('1')
  await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click()
  await page.getByRole('button', { name: 'Tra NCC / giá cũ cho 1 dòng đã chọn' }).click()
  await expect(page.getByRole('heading', { name: 'NCC và giá cũ cho dòng đã chọn' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'NCC mẫu A', exact: true })).toBeVisible()
  expect(state.previewLookupCalls).toEqual([['row5']]); expect(state.confirmCalls).toHaveLength(0)
})

test('PCVT price board transfers selected lines and current price into a new PO draft', async ({ page }) => {
  await documentFixtures(page, [], ['IMPORT_OPERATIONAL', 'PRICE_READ', 'PO_CREATE', 'PO_READ', 'CATALOG_READ'])
  await page.goto('/imports'); await upload(page)
  await page.getByLabel('Lọc số phiếu nguồn').selectOption('1')
  await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click()
  await page.getByRole('button', { name: 'Tra NCC / giá cũ cho 1 dòng đã chọn' }).click()
  await expect(page.getByLabel('NCC cũ row5')).toBeVisible()
  await page.getByLabel('NCC cũ row5').selectOption('id:2')
  await expect(page.getByLabel('NCC hiện tại row5')).toHaveValue('id:1')
  await expect(page.getByLabel('Giá hiện tại row5')).toBeVisible()
  await page.getByLabel('Giá hiện tại row5').fill('125000')
  await page.getByLabel('NCC hiện tại row5').selectOption('id:2')
  await page.getByRole('button', { name: 'Lập PO từ 1 dòng đã chọn' }).click()
  await expect(page.getByRole('heading', { name: 'Lập đơn mua' })).toBeVisible()
  await expect(page.getByLabel('Tên vật tư dòng 1')).toHaveValue('Thép kiểm thử')
  await expect(page.getByLabel('Đơn giá dòng 1')).toHaveValue('125000')
  await expect(page.getByLabel('Số lượng dòng 1')).toHaveValue('3')
})

test('Excel pasted rows use the same PCVT preview and price board as uploaded files', async ({ page }) => {
  await documentFixtures(page)
  await page.goto('/imports')
  await page.getByLabel('Hoặc dán các dòng từ Excel').fill('Tên vật tư\tQuy cách\tĐVT\tSố lượng\tSố phiếu\nThép kiểm thử\tDày 2 mm\tkg\t3\t1')
  await page.getByRole('button', { name: 'Kiểm tra dữ liệu dán' }).click()
  await expect(page.getByLabel('Tên vật tư row5')).toHaveValue('Thép kiểm thử')
  await page.getByRole('button', { name: /^Chọn tất cả 3 dòng$/ }).click()
  await expect(page.getByRole('button', { name: /Tra NCC \/ giá cũ/ })).toBeVisible()
})

test('a yearly CVT requires explicit selection of one request and survives reload without prices', async ({ page }) => {
  const state = await documentFixtures(page)
  await page.goto('/imports'); await expect(page.getByRole('heading', { name: 'PCVT và báo giá mới' })).toBeVisible(); await upload(page)
  await expect(page.getByRole('button', { name: 'Xác nhận và lưu 0 dòng', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Xác nhận và lưu 0 dòng', exact: true })).toBeDisabled()
  await page.getByLabel('Lọc số phiếu nguồn').selectOption('1'); await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click(); await acknowledge(page)
  await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible()
  expect(state.confirmCalls[0].lines).toHaveLength(1); expect(state.confirmCalls[0].requestNumber).toBe('1'); expect(state.confirmCalls[0].lines[0].unitPrice).toBeNull()
  await page.reload(); await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible(); await expect(page.getByRole('button', { name: 'Tra NCC và giá cũ', exact: true })).toBeVisible()
})

test('one item can be asked from two old suppliers and inquiry choices persist after export', async ({ page }) => {
  const state = await documentFixtures(page, [confirmedRequest()])
  await page.goto('/imports?document=1'); await page.getByRole('button', { name: 'Tra NCC và giá cũ', exact: true }).click()
  await page.getByRole('checkbox', { name: 'Hỏi NCC mẫu A cho Thép kiểm thử', exact: true }).check(); await page.getByRole('checkbox', { name: 'Hỏi NCC mẫu B cho Thép kiểm thử', exact: true }).check()
  await expect(page.getByText('NCC mẫu A: 1 món', { exact: true })).toBeVisible(); await expect(page.getByText('NCC mẫu B: 1 món', { exact: true })).toBeVisible()
  await page.screenshot({ path: `../target/runtime/ui-document-history-${test.info().project.name}.png`, fullPage: true })
  const download = page.waitForEvent('download'); await page.getByRole('button', { name: 'Lưu nhóm hỏi giá và tải XLSX', exact: true }).click(); expect((await download).suggestedFilename()).toBe('hoi-gia-mau.xlsx')
  expect(state.inquiryCalls[0].selections).toHaveLength(2); expect(state.confirmCalls).toHaveLength(0)
  await page.reload(); await page.getByRole('button', { name: 'Tra NCC và giá cũ', exact: true }).click(); await expect(page.getByRole('checkbox', { name: 'Hỏi NCC mẫu A cho Thép kiểm thử', exact: true })).toBeChecked(); await expect(page.getByRole('checkbox', { name: 'Hỏi NCC mẫu B cho Thép kiểm thử', exact: true })).toBeChecked()
})

test('two PCVT can be selected separately from the same annual source', async ({ page }) => {
  const state = await documentFixtures(page)
  await page.goto('/imports'); await upload(page)
  await page.getByLabel('Lọc số phiếu nguồn').selectOption('1'); await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click(); await acknowledge(page); await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click()
  await page.getByRole('button', { name: 'Nhập phiếu khác từ cùng sổ', exact: true }).click(); await page.getByLabel('Lọc số phiếu nguồn').selectOption('2'); await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click(); await acknowledge(page); await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible(); expect(state.confirmCalls.map((call) => call.requestNumber)).toEqual(['1', '2']); expect(state.documents[0].status).toBe('PREVIEW'); expect(state.documents.filter((item) => item.status === 'CONFIRMED')).toHaveLength(2)
})

test('quotation links are explicitly chosen and incompatible historical prices do not show a delta', async ({ page }) => {
  const request = confirmedRequest(), quotation = doc(2)
  quotation.kind = 'QUOTATION'; quotation.parsed.kind = 'QUOTATION'; quotation.parsed.supplierName = 'NCC mẫu mới'; quotation.parsed.quotationDate = '2026-10-08'; quotation.parsed.currency = 'VND'; quotation.parsed.priceBasis = 'EXCL_VAT'
  quotation.parsed.lines = [line('quote1', { materialCode: null, unitPrice: 120, requestNumber: null, raw: { manufacturerCode: 'HANG-123' } })]
  const state = await documentFixtures(page, [request]); state.preview = quotation
  await page.goto('/imports'); await page.getByLabel('Loại chứng từ').selectOption('QUOTATION'); await upload(page)
  await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click(); await page.getByLabel('Liên kết với PCVT đã lưu').selectOption('1')
  await expect(page.getByLabel('Liên kết PCVT quote1', { exact: true })).toHaveValue(''); await page.getByLabel('Liên kết PCVT quote1', { exact: true }).selectOption('row5'); await acknowledge(page)
  await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible()
  expect(state.confirmCalls[0].requestLineMappings).toEqual({ quote1: 'row5' }); expect(state.confirmCalls[0].lines[0].materialCode).toBeNull()
  await page.goto('/imports?document=1'); await page.getByRole('button', { name: 'Xem so sánh báo giá', exact: true }).click(); await expect(page.getByText('Chưa thể so sánh: Tiền tệ lịch sử chỉ là giả định', { exact: true })).toBeVisible()
})

test('scan fallback allows checked manual data and preserves the exact decimal input on save failure', async ({ page }) => {
  const state = await documentFixtures(page); state.preview.kind = 'QUOTATION'; state.preview.parsed.kind = 'QUOTATION'; state.preview.parsed.format = 'PDF_SCAN'; state.preview.parsed.lines = []; state.preview.parsed.warnings = ['OCR_NOT_SUPPORTED']; state.failConfirm = true
  await page.goto('/imports'); await page.getByLabel('Loại chứng từ').selectOption('QUOTATION'); await upload(page)
  await expect(page.getByText(/PDF scan chưa có OCR; tải bản gốc/)).toBeVisible(); await page.getByLabel('Nhà cung cấp báo giá').fill('NCC nhập tay'); await page.getByLabel('Ngày báo giá').fill('2026-10-08'); await page.getByLabel('Tiền tệ của báo giá', { exact: true }).selectOption('VND'); await page.getByRole('button', { name: 'Thêm dòng thủ công', exact: true }).click()
  await page.getByLabel(/^Tên vật tư manual-/).fill('Lưới thép mẫu'); await page.getByLabel(/^ĐVT manual-/).fill('m2'); await page.getByLabel(/^Đơn giá manual-/).fill('123.123456789123456789'); await acknowledge(page)
  await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Chưa lưu được'); await expect(page.getByLabel(/^Đơn giá manual-/)).toHaveValue('123.123456789123456789')
  await page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible(); expect(state.confirmCalls[1].lines[0].unitPrice).toBe('123.123456789123456789')
  await page.reload(); await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible(); await expect(page.getByRole('cell', { name: /123\.123456789123456789/ })).toContainText('123.123456789123456789')
})

test('an initially empty multisheet preview permits choosing the first sheet and then another date', async ({ page }) => {
  const state = await documentFixtures(page); state.preview.parsed.sheets = [{ name: 'BG', detectedLines: 3, requestNumbers: [], quotationDate: '2026-05-05' }, { name: 'Sheet1', detectedLines: 1, requestNumbers: [], quotationDate: '2026-09-08' }]
  state.preview.kind = 'QUOTATION'; state.preview.parsed.kind = 'QUOTATION'; state.preview.parsed.lines = []; state.preview.parsed.warnings = ['SHEET_SELECTION_REQUIRED']
  await page.goto('/imports'); await page.getByLabel('Loại chứng từ').selectOption('QUOTATION'); await upload(page); await expect(page.getByLabel('Sheet / phiên bản nguồn')).toHaveValue(''); await page.getByLabel('Sheet / phiên bản nguồn').selectOption('BG'); await expect(page.getByLabel('Ngày báo giá')).toHaveValue('2026-05-05'); await page.getByLabel('Sheet / phiên bản nguồn').selectOption('Sheet1')
  await expect.poll(() => state.previewSheets).toEqual(['', 'BG', 'Sheet1']); await expect(page.getByLabel('Ngày báo giá')).toHaveValue('2026-09-08'); await expect(page.getByText('1 dòng phát hiện, 0 dòng được chọn', { exact: false })).toBeVisible()
})

test('price permission is required for lookup and the documents screen fits small viewports', async ({ page }) => {
  await documentFixtures(page, [confirmedRequest()], ['PO_READ', 'CATALOG_READ', 'IMPORT_OPERATIONAL'])
  await page.goto('/imports?document=1'); await expect(page.getByText('Tài khoản chưa có quyền tra giá.', { exact: false })).toBeVisible(); await expect(page.getByRole('button', { name: 'Tra NCC và giá cũ', exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
})

test('document list permission errors stay visible and retry after access recovers', async ({ page }) => {
  const state = await documentFixtures(page, [confirmedRequest()]); state.denyList = true
  await page.goto('/imports'); await expect(page.getByRole('alert')).toContainText('Không có quyền đọc chứng từ.')
  await expect(page.getByText('Chưa có PCVT hoặc báo giá.', { exact: false })).toHaveCount(0)
  state.denyList = false; await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await page.locator('.document-saved > summary').click()
  await expect(page.getByRole('button', { name: 'Mở chứng từ 1', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Mở chứng từ 1', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận' })).toBeVisible()
})

test('a quotation can link an older PCVT by ID only after missing and wrong-kind IDs are rejected', async ({ page }) => {
  const request = confirmedRequest(); request.id = 123; request.title = 'PCVT cũ ngoài danh sách gần đây'
  const wrongKind = doc(2); wrongKind.kind = 'QUOTATION'; wrongKind.parsed.kind = 'QUOTATION'
  const quotation = doc(3); quotation.kind = 'QUOTATION'; quotation.parsed.kind = 'QUOTATION'
  quotation.parsed.supplierName = 'NCC mẫu mới'; quotation.parsed.quotationDate = '2026-10-08'; quotation.parsed.currency = 'VND'; quotation.parsed.priceBasis = 'EXCL_VAT'
  quotation.parsed.lines = [line('quote-old-id', { requestNumber: null, unitPrice: '120' })]
  const state = await documentFixtures(page, [request, wrongKind]); state.hiddenListIds = [123, 2]; state.preview = quotation
  await page.goto('/imports'); await page.getByLabel('Loại chứng từ').selectOption('QUOTATION'); await upload(page)
  await page.getByRole('button', { name: /^Chọn tất cả 1 dòng$/ }).click()
  const idInput = page.getByLabel('ID PCVT ngoài danh sách gần đây', { exact: true })
  const find = page.getByRole('button', { name: 'Tìm PCVT theo ID', exact: true })
  const save = page.getByRole('button', { name: 'Xác nhận và lưu 1 dòng', exact: true })
  await idInput.fill('999'); await find.click(); await expect(page.getByRole('alert')).toContainText('Không tìm thấy chứng từ'); await expect(save).toBeDisabled()
  await idInput.fill('2'); await find.click(); await expect(page.getByRole('alert')).toContainText('không phải PCVT đã xác nhận'); await expect(save).toBeDisabled()
  expect(state.confirmCalls).toHaveLength(0)
  await idInput.fill('123'); await find.click()
  const mapping = page.getByLabel('Liên kết PCVT quote-old-id', { exact: true })
  await expect(mapping).toBeEnabled(); await mapping.selectOption('row5'); await acknowledge(page); await save.click()
  await expect(page.getByRole('heading', { name: 'Chứng từ đã xác nhận', exact: true })).toBeVisible()
  expect(state.confirmCalls[0].requestId).toBe(123); expect(state.confirmCalls[0].requestLineMappings).toEqual({ 'quote-old-id': 'row5' })
  await page.reload(); await expect(mapping).toHaveValue('row5'); await expect(page.getByLabel('Liên kết với PCVT đã lưu', { exact: true })).toHaveValue('123')
})
