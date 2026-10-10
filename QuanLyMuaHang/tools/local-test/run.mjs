import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { randomBytes, createHash } from 'node:crypto'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createWriteStream } from 'node:fs'
import { resolve, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { connectBrowserMcp } from '../browser-mcp/client.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url)), source = join(root, 'source'), runtime = join(source, 'target/runtime')
await mkdir(runtime, { recursive: true })
const settings = {}
for (const line of (await readFile(join(source, '.env'), 'utf8')).split(/\r?\n/)) {
  const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/)
  if (match) { let value = match[2].trim(); if (/^(['"]).*\1$/.test(value)) value = value.slice(1, -1); settings[match[1]] = value }
}
assert(settings.DB_PASSWORD && settings.REDIS_PASSWORD, 'Private database/cache configuration required')
const db = `qmh_test_${randomBytes(6).toString('hex')}`, email = 'mcp-admin@khvt.test'
const temporary = randomBytes(24).toString('base64url'), password = randomBytes(24).toString('base64url')
const report = { database: db, namespace: `qmh:test:http:${db}`, checkedAt: new Date().toISOString(), checks: [], workbook: null }
const children = [], clients = []
let browser
function checked(name) { report.checks.push(name); console.log(`PASS ${name}`) }
function launch(command, args, env, logName, cwd = source) {
  const child = spawn(command, args, { cwd, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  const log = createWriteStream(join(runtime, logName)); child.stdout.pipe(log); child.stderr.pipe(log); children.push(child); return child
}
async function sql(statement) {
  assert(/^[a-z0-9_]+$/.test(db))
  return new Promise((resolveResult, reject) => {
    const child = spawn('docker.exe', ['exec', '-i', 'qmh-local-mysql-1', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names'], { windowsHide: true })
    let result = ''; child.stdout.on('data', (chunk) => { result += chunk }); child.stderr.resume()
    child.on('error', reject); child.on('exit', (code) => code === 0 ? resolveResult(result.trim()) : reject(new Error('Isolated database operation failed')))
    child.stdin.end(statement)
  })
}
async function ready(url, child) {
  const end = Date.now() + 75_000
  while (Date.now() < end) {
    if (child.exitCode !== null) throw new Error('Local test service exited; inspect its private runtime log')
    try { const response = await fetch(url); if (response.ok) return } catch { /* Wait for local startup. */ }
    await new Promise((resolveWait) => setTimeout(resolveWait, 500))
  }
  throw new Error('Local test service startup timed out')
}
const { request } = await import(pathToFileURL(join(source, 'frontend/node_modules/playwright/index.mjs')).href)
async function context() { const ctx = await request.newContext({ baseURL: 'http://127.0.0.1:8081', timeout: 180_000 }); clients.push(ctx); return ctx }
async function call(ctx, path, method = 'GET', data, options = {}) {
  const headers = {}
  if (method !== 'GET' && !options.noCsrf) { const token = await ctx.get('/api/auth/csrf'); const csrf = await token.json(); headers[csrf.headerName] = csrf.token }
  const response = await ctx.fetch(path, { method, headers, ...(data === undefined ? {} : { data }), ...options })
  if (options.status !== undefined) assert.equal(response.status(), options.status, `${method} ${path} status`)
  else if (!response.ok()) {
    const error = await response.json().catch(() => ({})); throw new Error(`${method} ${path}: HTTP ${response.status()} ${error.code ?? error.error?.code ?? ''}`)
  }
  return response
}
async function json(ctx, path, method = 'GET', data, options) { return (await call(ctx, path, method, data, options)).json() }
async function login(ctx, accountEmail, secret) { return json(ctx, '/api/auth/login', 'POST', { email: accountEmail, password: secret }) }
async function change(ctx, oldPassword, newPassword) { return json(ctx, '/api/auth/change-password', 'POST', { currentPassword: oldPassword, newPassword }) }
try {
  await sql(`CREATE DATABASE \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON \`${db}\`.* TO 'app'@'%';`)
  report.databaseCreated = true
  console.log(`Created isolated test database ${db}; application database is unchanged`)
  const env = { ...process.env, ...settings, JAVA_TOOL_OPTIONS: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:3307/${db}`, SPRING_DATASOURCE_USERNAME: 'app', REDIS_HOST: '127.0.0.1', REDIS_PORT: '6380', SERVER_PORT: '8081', ADMIN_BOOTSTRAP_EMAIL: email, ADMIN_BOOTSTRAP_PASSWORD: temporary, ADMIN_BOOTSTRAP_NAME: 'Quản trị kiểm thử', APP_FRONTEND_URL: 'http://127.0.0.1:5174', APP_PDF_FONT_PATH: 'C:/Windows/Fonts/arial.ttf', FILE_STORAGE_ROOT: join(runtime, db, 'files') }
  const javaArgs = ['-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k', '-Xms16m', '-Xmx128m', '-XX:MaxMetaspaceSize=128m', '-XX:ReservedCodeCacheSize=32m', '-XX:CompressedClassSpaceSize=32m', '-jar', join(source, 'target/quan-ly-mua-hang-0.0.1-SNAPSHOT.jar'), `--spring.session.redis.namespace=${report.namespace}`, '--server.servlet.session.cookie.name=QMH_TEST_SESSION', '--logging.level.org.springframework.web=INFO', '--logging.level.org.springframework.security=INFO', '--debug=false']
  let backend = launch('C:/Program Files/Java/jdk-21.0.12/bin/java.exe', javaArgs, env, 'isolated-backend.log')
  await ready('http://127.0.0.1:8081/actuator/health', backend); checked('MySQL Flyway from empty schema and Redis runtime health')
  const bootstrapDeadline = Date.now() + 30_000
  while ((await sql(`SELECT COUNT(*) FROM \`${db}\`.user_accounts WHERE email='${email}';`)) !== '1') {
    if (Date.now() > bootstrapDeadline) throw new Error('Test admin bootstrap did not complete')
    await new Promise((wait) => setTimeout(wait, 300))
  }
  const admin = await context()
  const first = await login(admin, email, temporary); assert(first.mustChangePassword)
  await call(admin, '/api/dashboard', 'GET', undefined, { status: 403 })
  await change(admin, temporary, password)
  const me = await json(admin, '/api/auth/me'); assert(me.permissions.includes('*')); assert.equal(me.modules.length, 3)
  const state = await admin.storageState(); const sessionCookie = state.cookies.find((cookie) => cookie.name === 'QMH_TEST_SESSION'); assert(sessionCookie?.httpOnly); assert.equal(sessionCookie.sameSite, 'Lax')
  await call(admin, '/api/catalog/materials', 'POST', { name: 'Should be rejected', category: 'MATERIAL' }, { noCsrf: true, status: 403 }); checked('HTTP login, forced password change, HttpOnly cookie, effective modules, CSRF rejection')
  const department = await json(admin, '/api/personnel/departments', 'POST', { code: 'TEST', name: 'Phòng kiểm thử', active: true })
  const position = await json(admin, '/api/personnel/positions', 'POST', { code: 'TEST', name: 'Chuyên viên thử nghiệm', active: true })
  const employee = await json(admin, '/api/personnel/employees', 'POST', { employeeCode: 'TEST-001', fullName: 'Nhân viên kiểm thử', departmentId: department.id, positionId: position.id })
  const employeeUpdated = await json(admin, `/api/personnel/employees/${employee.id}`, 'PUT', { employeeCode: 'TEST-001', fullName: 'Nhân viên kiểm thử cập nhật', departmentId: department.id, positionId: position.id })
  assert(employeeUpdated.fullName.endsWith('cập nhật')); checked('Personnel department, position, employee create and update on MySQL')
  const userSecret = randomBytes(24).toString('base64url'), userNewSecret = randomBytes(24).toString('base64url')
  const viewerAccount = await json(admin, '/api/admin/users', 'POST', { email: 'viewer@khvt.test', displayName: 'Người xem thử', initialPassword: userSecret, employeeId: employee.id, roleCodes: ['VIEWER'] })
  const viewer1 = await context(), viewer2 = await context(); await login(viewer1, viewerAccount.email, userSecret); await change(viewer1, userSecret, userNewSecret); await login(viewer2, viewerAccount.email, userNewSecret)
  const viewerMe = await json(viewer1, '/api/auth/me'); assert.equal(viewerMe.modules.length, 1); assert(viewerMe.permissions.includes('PRICE_READ')); await call(viewer1, '/api/admin/users', 'GET', undefined, { status: 403 })
  const supplier = await json(admin, '/api/catalog/suppliers', 'POST', { code: 'TEST-NCC', name: 'NCC kiểm thử', address: 'Địa chỉ tổng hợp' })
  const material = await json(admin, '/api/catalog/materials', 'POST', { code: '000.TEST', name: 'Vật tư kiểm thử có dấu', category: 'MATERIAL', defaultUnit: 'kg' })
  await call(viewer1, '/api/catalog/materials', 'POST', { name: 'Forbidden', category: 'MATERIAL' }, { status: 403 })
  await json(admin, `/api/catalog/materials/${material.id}`, 'PUT', { ...material, active: false }); const inactive = await json(admin, '/api/catalog/materials/page?active=false&q=000.TEST'); assert.equal(inactive.totalElements, 1)
  await json(admin, `/api/catalog/materials/${material.id}`, 'PUT', { ...material, active: true }); checked('Catalog CRUD, inactive server filter, exact code, viewer write denial')
  const draftData = { supplierId: supplier.id, orderDate: '2026-10-10', currency: 'VND', vatPercent: 8, preparedBy: 'Người lập thử', note: 'Dữ liệu tổng hợp', items: [{ materialId: material.id, materialName: material.name, specification: 'Quy cách tổng hợp', unit: 'kg', quantity: 2, unitPrice: 100 }, { materialName: 'Vật tư qua cân', unit: 'kg', quantity: null, quantityText: 'Qua cân thực tế', unitPrice: 500 }] }
  const order = await json(admin, '/api/purchase-orders', 'POST', draftData); assert.equal(Number(order.grandTotal), 216); assert.equal(order.quantityTextLineCount, 1)
  await call(viewer1, `/api/purchase-orders/${order.id}/pdf`, 'GET', undefined, { status: 409 }); assert.equal((await json(admin, `/api/purchase-orders/${order.id}`)).status, 'DRAFT')
  await call(viewer1, `/api/purchase-orders/${order.id}/issue`, 'POST', undefined, { status: 403 })
  const issued = await json(admin, `/api/purchase-orders/${order.id}/issue`, 'POST'); assert.equal(issued.status, 'EXPORTED')
  const pdf1 = await call(viewer1, `/api/purchase-orders/${order.id}/pdf`); const bytes1 = await pdf1.body(); assert(bytes1.subarray(0, 4).equals(Buffer.from('%PDF'))); await writeFile(join(runtime, 'synthetic-po-r1.pdf'), bytes1)
  const xlsx = await call(viewer1, `/api/exports/purchase-orders/${order.id}.xlsx`); assert((await xlsx.body()).subarray(0, 2).equals(Buffer.from('PK')))
  const latest = await json(admin, '/api/prices/latest?materialCode=000.TEST&currency=VND'); assert.equal(Number(latest.unitPrice), 100)
  await call(admin, '/api/prices/latest?materialCode=000.TEST&currency=USD', 'GET', undefined, { status: 404 })
  const edited = await json(admin, `/api/purchase-orders/${order.id}`, 'PUT', { ...draftData, changeReason: 'Điều chỉnh đơn giá thử nghiệm', items: [{ ...draftData.items[0], unitPrice: 150 }, draftData.items[1]] }); assert.equal(edited.revision, 2); assert.equal(edited.status, 'DRAFT')
  await json(admin, `/api/purchase-orders/${order.id}/issue`, 'POST'); const old = await call(admin, `/api/purchase-orders/${order.id}/revisions/1/pdf`); assert((await old.body()).equals(bytes1))
  assert.equal((await json(admin, `/api/purchase-orders/${order.id}/revisions`)).filter((revision) => revision.pdfAvailable).length, 2)
  await json(admin, `/api/purchase-orders/${order.id}/cancel`, 'POST', { reason: 'Kết thúc thử nghiệm' }); await call(admin, `/api/purchase-orders/${order.id}/issue`, 'POST', undefined, { status: 409 }); checked('PO draft, text quantity totals, explicit issue authorization, PDF/XLSX, latest currency, revision preservation, cancellation')
  const concurrent = await Promise.all([json(admin, '/api/purchase-orders', 'POST', draftData), json(admin, '/api/purchase-orders', 'POST', draftData)]); assert.notEqual(concurrent[0].poNumber, concurrent[1].poNumber); checked('Concurrent MySQL PO numbering remains unique')
  const operational = await json(admin, '/api/imports/operational/paste', 'POST', { content: 'Tên hàng\tĐVT\tSố lượng\tĐơn giá\nThép thử\tkg\tQua cân thực tế\t690.000' }); assert.equal(operational.items[0].quantityText, 'Qua cân thực tế'); assert.equal(Number(operational.items[0].unitPrice), 690000); checked('Operational paste preview preserves text quantity and Vietnamese numeric input')
  await json(admin, `/api/admin/users/${viewerAccount.id}`, 'PUT', { displayName: 'Người xem thử', employeeId: employee.id, status: 'ACTIVE', roleCodes: ['PLANNER'] }); await call(viewer1, '/api/auth/me', 'GET', undefined, { status: 401 }); await call(viewer2, '/api/auth/me', 'GET', undefined, { status: 401 }); checked('Role update revokes two real HTTP sessions in indexed Redis')
  await login(viewer1, viewerAccount.email, userNewSecret); await login(viewer2, viewerAccount.email, userNewSecret)
  await json(admin, `/api/personnel/employees/${employee.id}/deactivate`, 'POST'); await call(viewer1, '/api/auth/me', 'GET', undefined, { status: 401 }); await call(viewer2, '/api/auth/me', 'GET', undefined, { status: 401 }); checked('Employee deactivation disables linked account and revokes both HTTP sessions')
  const selfDisable = await json(admin, `/api/admin/users/${me.id}`, 'PUT', { displayName: me.displayName, status: 'DISABLED', roleCodes: ['ADMIN'] }, { status: 400 }); assert.equal(selfDisable.code, 'CANNOT_DISABLE_SELF')
  const lastAdmin = await json(admin, `/api/admin/users/${me.id}`, 'PUT', { displayName: me.displayName, status: 'ACTIVE', roleCodes: ['VIEWER'] }, { status: 400 }); assert.equal(lastAdmin.code, 'LAST_ADMIN'); checked('Self-disable and removal of last active administrator are rejected')
  // UI/MCP runs before real workbook import so screenshots contain synthetic data only.
  if (process.env.QMH_TEST_SKIP_BROWSER !== 'true') {
  const vite = launch(process.execPath, [join(root, 'tools/local-test/serve.mjs')], { ...process.env, NODE_OPTIONS: '--max-old-space-size=48 --max-semi-space-size=2' }, 'isolated-frontend.log', root)
  await ready('http://127.0.0.1:5174/login', vite)
  browser = await connectBrowserMcp(); const list = await browser.request('tools/list'); assert(list.tools.some((tool) => tool.name === 'browser_run_code_unsafe'))
  await browser.call('browser_navigate', { url: 'http://127.0.0.1:5174/login' })
  await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.context().addCookies(${JSON.stringify((await admin.storageState()).cookies)}); await page.goto('http://127.0.0.1:5174/modules'); await page.getByRole('heading', {name:'Ứng dụng của bạn'}).waitFor(); return 'Authenticated module portal'; }` })
  for (const [path, heading] of [['catalog/materials', 'Vật tư'], ['catalog/suppliers', 'Nhà cung cấp'], ['price-search', 'Tra cứu giá'], ['purchase-orders', 'Đơn mua hàng'], [`purchase-orders/${order.id}`, order.poNumber], ['purchase-orders/new', 'Lập đơn mua'], ['imports', 'Nhập dữ liệu']]) {
    await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.goto('http://127.0.0.1:5174/${path}'); await page.getByRole('heading', {name:${JSON.stringify(heading)},exact:true}).waitFor(); await page.waitForLoadState('networkidle'); if(await page.getByRole('alert').count()) throw new Error('Unexpected UI alert'); return 'Page ready'; }` })
  }
  await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.goto('http://127.0.0.1:5174/purchase-orders/new'); await page.getByLabel('NCC mặc định', {exact:true}).selectOption('${supplier.id}'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8'); await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư lập từ MCP'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('2'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('100'); await page.getByRole('button',{name:'Kiểm tra và lưu bản nháp'}).click(); await page.getByRole('button',{name:'Lưu bản nháp nhóm này'}).click(); await page.getByRole('link',{name:/Đã lưu PO-/}).waitFor(); return 'MCP created real MySQL draft'; }` })
  await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.getByRole('button',{name:'Về danh sách đơn'}).click(); await page.setViewportSize({width:390,height:844}); await page.goto('http://127.0.0.1:5174/catalog/materials'); await page.getByRole('heading',{name:'Vật tư',exact:true}).waitFor(); if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)) throw new Error('Mobile page overflow'); await page.screenshot({path:'source/target/runtime/ui-materials-real-mobile.png',fullPage:true}); return 'Mobile layout checked'; }` })
  await browser.close(); browser = undefined; vite.kill(); checked('Real MCP handshake, authenticated module portal, seven purchasing pages, browser PO create, mobile overflow')
  } else { report.browser = 'Run separately using synthetic API fixtures because of Windows commit-memory limits' }
  // Give POI more heap only after the browser and frontend have been closed.
  const stopped = new Promise((wait) => backend.once('exit',wait)); backend.kill(); await stopped
  backend = launch('C:/Program Files/Java/jdk-21.0.12/bin/java.exe', javaArgs.map((arg) => arg === '-Xmx128m' ? '-Xmx384m' : arg), env, 'isolated-import-backend.log')
  await ready('http://127.0.0.1:8081/actuator/health',backend)
  const workbookPath = process.env.QMH_TEST_WORKBOOK ? resolve(process.env.QMH_TEST_WORKBOOK) : resolve(root, '../QUANLYMUAHANGKHVT.xlsx')
  const workbook = await readFile(workbookPath), sha = createHash('sha256').update(workbook).digest('hex')
  const upload = { multipart: { file: { name: 'QUANLYMUAHANGKHVT.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: workbook } } }
  const preview = await json(admin, '/api/imports/legacy/preview', 'POST', undefined, upload)
  report.workbook = { sha256: sha, batchId: preview.batchId, status: preview.status, summary: preview.summary, issueCodes: preview.rowIssues.map((row) => ({ sheet: row.sheet, row: row.rowNumber, status: row.status, codes: row.issues.map((issue) => issue.split(':').slice(0,2).join(':')) })) }
  assert.equal(preview.sha256, sha); checked(`Workbook preview: ${preview.summary.totalRows} rows, ${preview.summary.errorRows} errors, ${preview.summary.warningRows} warning rows, ${preview.summary.legacyPurchaseOrderGroups} PO groups`)
  if (preview.summary.errorRows > 0 || preview.rowIssues.some((row) => row.issues.some((issue) => issue.includes('PO_HEADER_CONFLICT')))) {
    await call(admin, `/api/imports/legacy/${preview.batchId}/commit`, 'POST', undefined, { status: 409 }); report.workbook.commit = 'REJECTED_DATA_ERRORS'; checked('Workbook commit safely rejects source errors/header conflicts')
  } else {
    const committed = await json(admin, `/api/imports/legacy/${preview.batchId}/commit`, 'POST'); report.workbook.commit = committed
    assert.equal(committed.historyRowsImported, preview.summary.rowsPerSheet.LICH_SU); assert.equal(committed.purchaseOrdersImported, preview.summary.legacyPurchaseOrderGroups)
    await call(admin, `/api/imports/legacy/${preview.batchId}/commit`, 'POST', undefined, { status: 409 }); checked(`Workbook committed in isolated MySQL: ${committed.historyRowsImported} historical rows, ${committed.purchaseOrdersImported} PO`)
  }
  const duplicate = await json(admin, '/api/imports/legacy/preview', 'POST', undefined, upload); assert(duplicate.duplicate); assert.equal(duplicate.batchId, preview.batchId); checked('Workbook checksum replay reuses batch without duplicate business records')
  const priceExport = await call(admin, '/api/exports/prices.xlsx?currency=VND&category=MATERIAL'); assert((await priceExport.body()).subarray(0,2).equals(Buffer.from('PK'))); checked('Filtered price XLSX export after workbook processing')
  assert.equal(createHash('sha256').update(await readFile(workbookPath)).digest('hex'), sha); checked('Original workbook remains byte-for-byte unchanged')
  await call(admin, '/api/auth/logout', 'POST'); await call(admin, '/api/auth/me', 'GET', undefined, { status: 401 }); checked('HTTP logout invalidates session')
  const audit = await sql(`SELECT COALESCE(GROUP_CONCAT(after_json SEPARATOR '\n'),'') FROM \`${db}\`.audit_logs;`)
  for (const secret of [temporary, password, userSecret, userNewSecret, settings.DB_PASSWORD, settings.REDIS_PASSWORD]) assert(!audit.includes(secret), 'Secret must not be written to audit')
  checked('Test passwords and database/cache secrets are absent from audit payloads')
  report.completed = true
} catch (error) { report.completed = false; report.failure = error.message; console.error(`FAILED ${error.message}`); process.exitCode = 1 }
finally {
  if (browser) await browser.close().catch(() => {})
  for (const client of clients) await client.dispose().catch(() => {})
  for (const child of children.reverse()) if (child.exitCode === null) child.kill()
  await writeFile(join(runtime, 'real-integration-report.json'), JSON.stringify(report, null, 2))
  await writeFile(join(runtime, `${db}-report.json`), JSON.stringify(report, null, 2))
  console.log(`Report: source/target/runtime/real-integration-report.json; ${report.databaseCreated ? 'isolated database retained for review' : 'database was not created'}`)
}
