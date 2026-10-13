import { expect, test } from '@playwright/test'
import { purchasing } from './purchasingFixtures'

test('catalog create preserves form on conflict; deactivate requires confirmation and filter updates', async ({page}) => {
  const state = await purchasing(page); await page.goto('/catalog/materials')
  await page.getByRole('button',{name:'Thêm vật tư'}).click(); await page.getByLabel('Tên vật tư *',{exact:true}).fill('Vật tư mới'); await page.getByLabel('Mã vật tư',{exact:true}).fill('0002')
  state.failSave=true; await page.getByRole('button',{name:'Lưu',exact:true}).click(); await expect(page.getByRole('alert')).toHaveText('Mã đã tồn tại'); await expect(page.getByLabel('Mã vật tư',{exact:true})).toHaveValue('0002')
  await page.getByRole('button',{name:'Lưu',exact:true}).click(); await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button',{name:'Ngừng Vật tư mới',exact:true}).click(); expect(state.materials.find((item)=>item.code==='0002')!.active).toBe(true)
  await page.getByRole('button',{name:'Xác nhận ngừng'}).click(); await expect(page.getByRole('status')).toContainText('Đã ngừng')
  await page.getByLabel('Trạng thái',{exact:true}).selectOption('false'); await expect(page.getByRole('row').filter({hasText:'0002'})).toBeVisible()
  expect(state.writes.every((write)=>write.data.code === '0002')).toBe(true)
})
test('viewer reads catalog and prices, but create and imports routes are forbidden', async ({page}) => {
  await purchasing(page,'VIEWER'); await page.goto('/catalog/materials'); await expect(page.getByRole('button',{name:'Thêm vật tư'})).toHaveCount(0)
  await page.goto('/price-search'); await page.getByRole('button',{name:'Giá gần nhất',exact:true}).click(); await expect(page.getByRole('dialog')).toContainText('NCC mẫu A'); await expect(page.getByRole('button',{name:'Lập đơn từ giá này'})).toHaveCount(0)
  await page.goto('/purchase-orders/new'); await expect(page.getByRole('heading',{name:'Bạn không có quyền truy cập'})).toBeVisible(); await page.goto('/imports'); await expect(page.getByRole('heading',{name:'Bạn không có quyền truy cập'})).toBeVisible()
})
test('supplier lookup shows purchased items and purchase history without opening each PO', async ({page}) => {
  const state = await purchasing(page); await page.goto('/price-search')
  await page.getByLabel('Nhà cung cấp đã mua',{exact:true}).fill('NCC mẫu A')
  await page.getByRole('button',{name:'Xem mặt hàng và lịch sử mua'}).click()
  const results = page.getByRole('region',{name:'Mặt hàng đã mua từ nhà cung cấp'})
  await expect(results.getByText('Thép kiểm thử',{exact:true})).toBeVisible()
  await expect(results.getByText('10/10/2026')).toBeVisible()
  expect(state.supplierHistoryQueries).toEqual(['NCC mẫu A'])
})
test('purchasing navigation collapses, remembers its preference and leaves administration expanded', async ({page}) => {
  await purchasing(page); await page.setViewportSize({ width: 1365, height: 900 }); await page.goto('/imports')
  const sidebar = page.locator('#main-navigation')
  const collapse = page.getByRole('button', { name: 'Thu gọn thanh điều hướng' })
  await expect(collapse).toBeVisible(); await expect(sidebar).toHaveCSS('width', '232px')
  await collapse.click(); await expect(sidebar).toHaveCSS('width', '76px')
  await expect(page.getByRole('button', { name: 'Mở rộng thanh điều hướng' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('qmh:ui:purchasing-sidebar:v1'))).toBe('true')
  await page.reload(); await expect(sidebar).toHaveCSS('width', '76px')
  await page.goto('/admin/users'); await expect(page.getByRole('button', { name: /thanh điều hướng/ })).toHaveCount(0); await expect(sidebar).toHaveCSS('width', '232px')
  await page.goto('/imports'); await expect(sidebar).toHaveCSS('width', '76px')
})
test('mobile navigation remains a drawer and does not expose the desktop collapse control', async ({page}) => {
  await purchasing(page); await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/imports')
  await expect(page.getByRole('button', { name: /thanh điều hướng/ })).toHaveCount(0)
  await page.getByRole('button', { name: 'Mở menu' }).click(); await expect(page.locator('#main-navigation')).toBeVisible()
  await expect(page.locator('#main-navigation')).toHaveCSS('width', '252px')
  await page.keyboard.press('Escape'); await expect(page.locator('#main-navigation')).toBeHidden()
})
test('catalog network error shows retry and succeeds after recovery', async ({page}) => {
  const state=await purchasing(page); state.failCatalog=true; await page.goto('/catalog/suppliers'); await expect(page.getByRole('alert')).toContainText('Danh mục tạm thời'); state.failCatalog=false; await page.getByRole('button',{name:'Thử lại'}).click(); await expect(page.getByRole('table').getByText('NCC mẫu A',{exact:true})).toBeVisible()
})
test('one confirmation creates one PO per supplier, issues both PDFs and downloads them', async ({page}) => {
  const state = await purchasing(page); await page.goto('/purchase-orders/new'); await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8')
  await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư A'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('2'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('100')
  await page.getByRole('button',{name:'Thêm dòng thủ công'}).click(); await page.getByLabel('Tên vật tư dòng 2',{exact:true}).fill('Vật tư B'); await page.getByLabel('Số lượng dòng 2',{exact:true}).fill('3'); await page.getByLabel('Đơn giá dòng 2',{exact:true}).fill('200'); await page.getByLabel('NCC dòng 2',{exact:true}).selectOption('2')
  await page.getByRole('button',{name:'Kiểm tra và xuất PO'}).click(); await expect(page.getByRole('dialog')).toContainText('2 nhóm nhà cung cấp')
  const downloads = Promise.all([page.waitForEvent('download'), page.waitForEvent('download')])
  await page.getByRole('button',{name:'Xác nhận, tạo 2 PO và xuất PDF'}).click()
  expect((await downloads).map((download) => download.suggestedFilename())).toEqual(['test.pdf','test.pdf'])
  expect(state.orders).toHaveLength(2); expect(state.orders[1].items[0].quantity).toBe('3'); expect(state.orders[1].items[0].quantityText).toBeNull()
  await expect(page.getByText('Đã phát hành và gửi tải PDF.')).toHaveCount(2)
  expect(state.issueCalls).toEqual([1,2]); expect(state.orders.map((order)=>order.status)).toEqual(['EXPORTED','EXPORTED'])
})
test('PO validation and failed save preserve cart for correction', async ({page}) => {
  const state=await purchasing(page); await page.goto('/purchase-orders/new'); await page.getByRole('button',{name:'Kiểm tra và xuất PO'}).click(); await expect(page.getByRole('alert')).toContainText('VAT')
  await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8'); await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư giữ lại'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('1'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('99'); await page.getByRole('button',{name:'Kiểm tra và xuất PO'}).click(); state.failSave=true; await page.getByRole('button',{name:'Xác nhận, tạo 1 PO và xuất PDF'}).click(); await expect(page.getByRole('dialog').getByRole('alert').first()).toContainText('Chưa thể lưu đơn'); expect(state.issueCalls).toHaveLength(0); await expect(page.getByRole('button',{name:'Quay lại chỉnh sửa'})).toBeVisible(); await page.getByRole('button',{name:'Quay lại chỉnh sửa'}).click(); await expect(page.getByLabel('Tên vật tư dòng 1',{exact:true})).toHaveValue('Vật tư giữ lại')
})
test('one supplier issue failure is shown while other supplier PDFs still issue', async ({page}) => {
  const state=await purchasing(page); state.failIssueId=1; await page.goto('/purchase-orders/new'); await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8')
  await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư A'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('2'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('100')
  await page.getByRole('button',{name:'Thêm dòng thủ công'}).click(); await page.getByLabel('Tên vật tư dòng 2',{exact:true}).fill('Vật tư B'); await page.getByLabel('Số lượng dòng 2',{exact:true}).fill('3'); await page.getByLabel('Đơn giá dòng 2',{exact:true}).fill('200'); await page.getByLabel('NCC dòng 2',{exact:true}).selectOption('2')
  await page.getByRole('button',{name:'Kiểm tra và xuất PO'}).click(); await page.getByRole('button',{name:'Xác nhận, tạo 2 PO và xuất PDF'}).click()
  await expect(page.getByRole('status').filter({hasText:'Chưa xác nhận được kết quả phát hành'})).toBeVisible()
  await expect(page.getByText('Đã phát hành và gửi tải PDF.')).toBeVisible(); expect(state.orders.map((order)=>order.status)).toEqual(['DRAFT','EXPORTED']); expect(state.issueCalls).toEqual([1,2])
})
test('operational paste preview transfers draft without writing PO', async ({page}) => {
  const state=await purchasing(page); await page.goto('/imports'); await page.getByRole('button',{name:'Lập đơn nhanh từ tệp',exact:true}).click(); await page.getByLabel('Hoặc dán từ Excel').fill('Tên hàng\tĐVT\tSố lượng\nThép\tkg\tQua cân thực tế'); await page.getByRole('button',{name:'Preview dữ liệu dán'}).click(); await expect(page.getByRole('cell',{name:'Qua cân thực tế',exact:true})).toBeVisible(); expect(state.orders).toHaveLength(0); await page.getByRole('button',{name:'Chuyển 1 dòng sang lập đơn'}).click(); await expect(page.getByText(/SL chữ từ nguồn: Qua cân thực tế/)).toBeVisible(); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('4'); await expect(page.getByLabel('Số lượng dòng 1',{exact:true})).toHaveValue('4')
})
test('legacy commit requires review acknowledgement and explicit confirmation', async ({page}) => {
  const state=await purchasing(page); await page.goto('/imports'); await page.getByRole('button',{name:'Workbook dữ liệu cũ'}).click(); await page.getByLabel('Tệp dữ liệu').setInputFiles({name:'fixture.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('synthetic mocked workbook')}); await page.getByRole('button',{name:'Preview tệp'}).click(); await expect(page.getByRole('button',{name:'Commit dữ liệu cũ'})).toBeDisabled(); await page.getByRole('checkbox',{name:/Tôi đã đối chiếu/}).check(); await page.getByRole('button',{name:'Commit dữ liệu cũ'}).click(); expect(state.commits).toBe(0); await page.getByRole('button',{name:'Commit dữ liệu',exact:true}).click(); await expect(page.getByRole('status')).toContainText('Đã commit dữ liệu thử'); expect(state.commits).toBe(1)
})
test('new purchasing pages stay within mobile viewport', async ({page}) => {
  await purchasing(page); for(const path of ['/catalog/materials','/catalog/suppliers','/price-search','/purchase-orders/new','/imports']) { await page.goto(path); await expect(page.locator('h1')).toBeVisible(); expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth+1)).toBe(true) }
  await page.goto('/purchase-orders/new'); await page.screenshot({path:`../target/runtime/ui-po-form-${test.info().project.name}.png`,fullPage:true})
})
