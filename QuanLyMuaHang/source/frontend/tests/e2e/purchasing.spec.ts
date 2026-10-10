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
test('catalog network error shows retry and succeeds after recovery', async ({page}) => {
  const state=await purchasing(page); state.failCatalog=true; await page.goto('/catalog/suppliers'); await expect(page.getByRole('alert')).toContainText('Danh mục tạm thời'); state.failCatalog=false; await page.getByRole('button',{name:'Thử lại'}).click(); await expect(page.getByRole('table').getByText('NCC mẫu A',{exact:true})).toBeVisible()
})
test('multi supplier cart creates separate drafts, preserves text quantity, and issues PDF explicitly', async ({page}) => {
  const state = await purchasing(page); await page.goto('/purchase-orders/new'); await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8')
  await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư A'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('2'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('100')
  await page.getByRole('button',{name:'Thêm dòng thủ công'}).click(); await page.getByLabel('Tên vật tư dòng 2',{exact:true}).fill('Vật tư B'); await page.getByLabel('Kiểu số lượng dòng 2',{exact:true}).selectOption('text'); await page.getByLabel('Số lượng dòng 2',{exact:true}).fill('Qua cân thực tế'); await page.getByLabel('Đơn giá dòng 2',{exact:true}).fill('200'); await page.getByLabel('NCC dòng 2',{exact:true}).selectOption('2')
  await page.getByRole('button',{name:'Kiểm tra và lưu bản nháp'}).click(); await expect(page.getByRole('dialog')).toContainText('2 nhóm nhà cung cấp'); await page.getByRole('button',{name:'Lưu bản nháp nhóm này'}).first().click(); await expect(page.getByRole('link',{name:/Đã lưu PO-TEST-1/})).toBeVisible(); await page.getByRole('button',{name:'Lưu bản nháp nhóm này'}).click(); await expect(page.getByRole('link',{name:/Đã lưu PO-TEST-2/})).toBeVisible()
  expect(state.orders).toHaveLength(2); expect(state.orders[1].items[0].quantity).toBeNull(); expect(state.orders[1].items[0].quantityText).toBe('Qua cân thực tế')
  await page.getByRole('link',{name:/Đã lưu PO-TEST-1/}).click(); await expect(page.getByRole('button',{name:'Tải PDF',exact:true})).toBeDisabled(); await page.getByRole('button',{name:'Phát hành PDF',exact:true}).click()
  const download = page.waitForEvent('download'); await page.getByRole('button',{name:'Phát hành đơn',exact:true}).click(); expect((await download).suggestedFilename()).toBe('test.pdf'); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.getByRole('button',{name:'Tải PDF',exact:true})).toBeEnabled(); expect(state.orders[0].status).toBe('EXPORTED')
})
test('PO validation and failed save preserve cart for correction', async ({page}) => {
  const state=await purchasing(page); await page.goto('/purchase-orders/new'); await page.getByRole('button',{name:'Kiểm tra và lưu bản nháp'}).click(); await expect(page.getByRole('alert')).toContainText('VAT')
  await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8'); await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư giữ lại'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('1'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('99'); await page.getByRole('button',{name:'Kiểm tra và lưu bản nháp'}).click(); state.failSave=true; await page.getByRole('button',{name:'Lưu bản nháp nhóm này'}).click(); await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Chưa thể lưu đơn'); await page.getByRole('button',{name:'Quay lại chỉnh sửa'}).click(); await expect(page.getByLabel('Tên vật tư dòng 1',{exact:true})).toHaveValue('Vật tư giữ lại')
})
test('operational paste preview transfers draft without writing PO', async ({page}) => {
  const state=await purchasing(page); await page.goto('/imports'); await page.getByRole('button',{name:'Yêu cầu mua / báo giá',exact:true}).click(); await page.getByLabel('Hoặc dán từ Excel').fill('Tên hàng\tĐVT\tSố lượng\nThép\tkg\tQua cân thực tế'); await page.getByRole('button',{name:'Preview dữ liệu dán'}).click(); await expect(page.getByRole('cell',{name:'Qua cân thực tế',exact:true})).toBeVisible(); expect(state.orders).toHaveLength(0); await page.getByRole('button',{name:'Chuyển 1 dòng sang lập đơn'}).click(); await expect(page.getByLabel('Số lượng dòng 1',{exact:true})).toHaveValue('Qua cân thực tế'); await expect(page.getByLabel('Kiểu số lượng dòng 1',{exact:true})).toHaveValue('text')
})
test('legacy commit requires review acknowledgement and explicit confirmation', async ({page}) => {
  const state=await purchasing(page); await page.goto('/imports'); await page.getByRole('button',{name:'Workbook dữ liệu cũ'}).click(); await page.getByLabel('Tệp dữ liệu').setInputFiles({name:'fixture.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('synthetic mocked workbook')}); await page.getByRole('button',{name:'Preview tệp'}).click(); await expect(page.getByRole('button',{name:'Commit dữ liệu cũ'})).toBeDisabled(); await page.getByRole('checkbox',{name:/Tôi đã đối chiếu/}).check(); await page.getByRole('button',{name:'Commit dữ liệu cũ'}).click(); expect(state.commits).toBe(0); await page.getByRole('button',{name:'Commit dữ liệu',exact:true}).click(); await expect(page.getByRole('status')).toContainText('Đã commit dữ liệu thử'); expect(state.commits).toBe(1)
})
test('new purchasing pages stay within mobile viewport', async ({page}) => {
  await purchasing(page); for(const path of ['/catalog/materials','/catalog/suppliers','/price-search','/purchase-orders/new','/imports']) { await page.goto(path); await expect(page.locator('h1')).toBeVisible(); expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth+1)).toBe(true) }
  await page.goto('/purchase-orders/new'); await page.screenshot({path:`../target/runtime/ui-po-form-${test.info().project.name}.png`,fullPage:true})
})
