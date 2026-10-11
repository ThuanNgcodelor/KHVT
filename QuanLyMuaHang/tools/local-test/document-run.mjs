import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { join, resolve } from 'node:path';
import { readDocumentWorkbook, compareDocumentWorkbook, compareDocumentPdf, PDF_EXPECTATIONS, readJsonExact, equalDecimal } from './document-source.mjs';
import { connectBrowserMcp } from '../browser-mcp/client.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url)), source = join(root, 'source'), runtime = join(source, 'target/runtime');
await mkdir(runtime, { recursive: true });
const settings = {};
for (const line of (await readFile(join(source, '.env'), 'utf8')).split(/\r?\n/)) {
  const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/); if (!match) continue;
  let value = match[2].trim(); if (/^(['"]).*\1$/.test(value)) value = value.slice(1, -1); settings[match[1]] = value;
}
assert(settings.DB_PASSWORD && settings.REDIS_PASSWORD, 'Private MySQL/Redis configuration is required');
const db = `qmh_docs_test_${randomBytes(6).toString('hex')}`, namespace = `qmh:test:documents:${db}`, port = 8082;
const configuredDatabase = new URL((settings.SPRING_DATASOURCE_URL || 'jdbc:mysql://localhost:3307/quanlymuahang').replace(/^jdbc:/, ''));
assert(['localhost', '127.0.0.1'].includes(configuredDatabase.hostname) && configuredDatabase.port === '3307', 'Acceptance driver requires the local project MySQL instance');
const mainDatabase = configuredDatabase.pathname.slice(1);
assert(/^[A-Za-z0-9_]+$/.test(mainDatabase) && mainDatabase !== db, 'Main database identifier must be explicit and distinct from the isolated schema');
const report = { checkedAt: new Date().toISOString(), database: db, namespace, checks: [], samples: [], completed: false };
const email = 'document-admin@khvt.test', temporary = randomBytes(24).toString('base64url'), password = randomBytes(24).toString('base64url');
const contexts = []; let backend, browser;
const sampleRoot = process.env.QMH_DOCUMENT_SAMPLE_ROOT ? resolve(process.env.QMH_DOCUMENT_SAMPLE_ROOT) : resolve(root, '..');
const { request } = await import(pathToFileURL(join(source, 'frontend/node_modules/playwright/index.mjs')).href);
const checked = (name) => { report.checks.push(name); console.log('PASS ' + name); };
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
async function sql(statement) {
  assert(/^[a-z0-9_]+$/.test(db));
  return new Promise((done, failed) => {
    const child = spawn('docker.exe', ['exec', '-i', 'qmh-local-mysql-1', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names'], { windowsHide: true });
    let out = ''; child.stdout.setEncoding('utf8'); child.stdout.on('data', (chunk) => { out += chunk; }); child.stderr.resume(); child.on('error', failed);
    child.on('exit', (code) => code === 0 ? done(out.trim()) : failed(new Error('Isolated database operation failed'))); child.stdin.end(statement);
  });
}
async function ready() {
  const end = Date.now() + 100_000;
  while (Date.now() < end) { if (backend.exitCode !== null) throw new Error('Document test backend exited; inspect its private local log'); try { if ((await fetch(`http://127.0.0.1:${port}/actuator/health`)).ok) return; } catch {} await new Promise((done) => setTimeout(done, 500)); }
  throw new Error('Document test backend startup timed out');
}
async function context() { const ctx = await request.newContext({ baseURL: `http://127.0.0.1:${port}`, timeout: 180_000 }); contexts.push(ctx); return ctx; }
async function call(ctx, path, method = 'GET', data, options = {}) {
  const headers = {};
  if (method !== 'GET' && !options.noCsrf) { const csrf = await (await ctx.get('/api/auth/csrf')).json(); headers[csrf.headerName] = csrf.token; }
  const response = await ctx.fetch(path, { method, data, headers, ...(options.multipart ? { multipart: options.multipart } : {}) });
  const expected = options.status ?? 200;
  if (response.status() !== expected) { let code = ''; try { code = (await response.json()).code ?? ''; } catch {} throw new Error(`${method} ${path}: HTTP ${response.status()}, expected ${expected}${code ? ' (' + code + ')' : ''}`); }
  return response;
}
async function json(ctx, path, method = 'GET', body, options) { return readJsonExact(await (await call(ctx, path, method, body, options)).text()); }
async function upload(ctx, name, kind, sheet) {
  const bytes = await readFile(join(sampleRoot, name));
  const suffix = sheet ? '&sheet=' + encodeURIComponent(sheet) : '';
  const view = await json(ctx, '/api/imports/documents/preview?kind=' + kind + suffix, 'POST', undefined, { multipart: { file: { name, mimeType: name.endsWith('.pdf') ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: bytes } } });
  return { bytes, sha256: sha256(bytes), view };
}
function confirmation(view, title, lines = view.parsed.lines, extra = {}) {
  return { title, requestNumber: '', supplierName: view.parsed.supplierName ?? '', quotationDate: view.parsed.quotationDate ?? '', currency: view.parsed.currency || 'VND', priceBasis: view.parsed.priceBasis || 'UNKNOWN', vatPercent: view.parsed.vatPercent ?? null, lines, requestId: null, requestLineMappings: {}, acknowledged: true, ...extra };
}
function manual(key, name, unit, quantity, unitPrice, specification = '') { return { lineKey: key, sourceSheet: null, sourceRow: 0, sourcePage: 1, requestNumber: '', materialCode: '', materialName: name, specification, unit, quantity, quantityText: '', unitPrice, lineTotal: null, vatPercent: null, priceBasis: 'EXCL_VAT', currency: 'VND', note: '', raw: { ignored: 'client value' }, issues: [] }; }
async function counts() { const rows = await sql(`SELECT JSON_OBJECT('history',(SELECT COUNT(*) FROM \`${db}\`.historical_purchases),'orders',(SELECT COUNT(*) FROM \`${db}\`.purchase_orders),'items',(SELECT COUNT(*) FROM \`${db}\`.purchase_order_items),'documents',(SELECT COUNT(*) FROM \`${db}\`.source_document_imports));`); return JSON.parse(rows); }
async function mainCounts() {
  const rows = await sql(`SELECT JSON_OBJECT('history',(SELECT COUNT(*) FROM \`${mainDatabase}\`.historical_purchases),'orders',(SELECT COUNT(*) FROM \`${mainDatabase}\`.purchase_orders),'items',(SELECT COUNT(*) FROM \`${mainDatabase}\`.purchase_order_items),'suppliers',(SELECT COUNT(*) FROM \`${mainDatabase}\`.suppliers),'materials',(SELECT COUNT(*) FROM \`${mainDatabase}\`.materials),'users',(SELECT COUNT(*) FROM \`${mainDatabase}\`.user_accounts),'flywayVersion',(SELECT version FROM \`${mainDatabase}\`.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1));`);
  return JSON.parse(rows);
}
try {
  const dbUser = settings.DB_USERNAME || 'app'; assert(/^[A-Za-z0-9_]+$/.test(dbUser));
  report.mainBefore = await mainCounts();
  await sql(`CREATE DATABASE \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON \`${db}\`.* TO '${dbUser}'@'%';`);
  report.databaseCreated = true;
  const env = { ...process.env, ...settings, JAVA_TOOL_OPTIONS: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:3307/${db}`, SPRING_DATASOURCE_USERNAME: dbUser, REDIS_HOST: '127.0.0.1', REDIS_PORT: '6380', SERVER_PORT: String(port), ADMIN_BOOTSTRAP_EMAIL: email, ADMIN_BOOTSTRAP_PASSWORD: temporary, ADMIN_BOOTSTRAP_NAME: 'Document test administrator', APP_FRONTEND_URL: `http://127.0.0.1:${port}`, APP_PDF_FONT_PATH: 'C:/Windows/Fonts/arial.ttf', FILE_STORAGE_ROOT: join(runtime, db, 'files') };
  const java = process.env.QMH_TEST_JAVA || (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java.exe') : 'C:/Program Files/Java/jdk-21.0.12/bin/java.exe');
  const args = ['-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k', '-Xms16m', '-Xmx384m', '-XX:MaxMetaspaceSize=128m', '-XX:ReservedCodeCacheSize=32m', '-XX:CompressedClassSpaceSize=32m', '-jar', process.env.QMH_TEST_JAR || join(source, 'target/quan-ly-mua-hang-0.0.1-SNAPSHOT.jar'), `--spring.session.redis.namespace=${namespace}`, '--server.servlet.session.cookie.name=QMH_DOCUMENT_TEST_SESSION', '--logging.level.org.springframework.web=INFO', '--logging.level.org.springframework.security=INFO', '--debug=false'];
  backend = spawn(java, args, { cwd: source, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const log = createWriteStream(join(runtime, `${db}-backend.log`)); backend.stdout.pipe(log); backend.stderr.pipe(log);
  await ready();
  assert.equal(await sql(`SELECT COUNT(*) FROM \`${db}\`.flyway_schema_history WHERE version='7' AND success=1;`), '1');
  checked('Empty isolated MySQL schema: Flyway V7 and Redis health');
  const admin = await context();
  const end = Date.now() + 30_000;
  while ((await sql(`SELECT COUNT(*) FROM \`${db}\`.user_accounts WHERE email='${email}';`)) !== '1') { if (Date.now() > end) throw new Error('Synthetic administrator bootstrap timed out'); await new Promise((done) => setTimeout(done, 300)); }
  const login = await json(admin, '/api/auth/login', 'POST', { email, password: temporary }); assert(login.mustChangePassword);
  await json(admin, '/api/auth/change-password', 'POST', { currentPassword: temporary, newPassword: password });
  await call(admin, '/api/imports/documents/preview?kind=REQUEST', 'POST', undefined, { noCsrf: true, status: 403, multipart: { file: { name: 'test.csv', mimeType: 'text/csv', buffer: Buffer.from('Tên vật tư,ĐVT,Số lượng\nTest,Cái,1') } } });
  const anonymous = await context(); await call(anonymous, '/api/imports/documents', 'GET', undefined, { status: 401 });
  checked('Real HTTP login, forced password change, CSRF rejection and anonymous protection');

  const cvtName = 'Copy of CVT 2026 - SCTX.xlsx', cvt = await upload(admin, cvtName, 'REQUEST', '2026');
  const independentCvt = await readDocumentWorkbook(join(sampleRoot, cvtName));
  const cvtComparison = compareDocumentWorkbook(independentCvt, cvt.view.parsed, 'REQUEST', '2026');
  report.samples.push({ file: cvtName, sha256: cvt.sha256, ...cvtComparison }); assert.equal(cvtComparison.sourceRows, 2013); assert.deepEqual(cvtComparison.mismatches, []);
  assert.equal(new Set(cvt.view.parsed.lines.map((l) => l.requestNumber).filter(Boolean)).size, 40);
  assert(!cvt.view.parsed.lines.find((l) => Number(l.sourceRow) === 1416).requestNumber);
  checked('Actual CVT: 2,013 item rows, 40 phieu, no separator items and unassigned source row retained');
  const selected = cvt.view.parsed.lines.filter((l) => l.requestNumber === '1'); assert(selected.length > 0 && selected.length < 2013);
  const requestBody = confirmation(cvt.view, 'PCVT 1 acceptance sample', selected, { requestNumber: '1' });
  const confirmedCvt = await json(admin, `/api/imports/documents/${cvt.view.id}/confirm`, 'POST', requestBody);
  assert.equal(confirmedCvt.confirmed.lines.length, selected.length);
  assert.notEqual(confirmedCvt.id, cvt.view.id); assert.equal(confirmedCvt.sourceDocumentId, cvt.view.id);
  const cvtReloaded = await json(admin, `/api/imports/documents/${confirmedCvt.id}`); assert.deepEqual(cvtReloaded.confirmed, confirmedCvt.confirmed);
  const original = await call(admin, `/api/imports/documents/${cvt.view.id}/source`); assert.equal(sha256(await original.body()), cvt.sha256);
  const repeated = await upload(admin, cvtName, 'REQUEST', '2026'); assert.equal(repeated.view.id, cvt.view.id); assert(repeated.view.duplicate);
  assert.equal(repeated.view.status, 'PREVIEW'); assert.equal(repeated.view.confirmed, null);
  const selectedSecond = repeated.view.parsed.lines.filter((l) => l.requestNumber === '2'); assert(selectedSecond.length > 0);
  const secondRequest = await json(admin, `/api/imports/documents/${cvt.view.id}/confirm`, 'POST', confirmation(repeated.view, 'PCVT 2 acceptance sample', selectedSecond, { requestNumber: '2' }));
  assert.notEqual(secondRequest.id, confirmedCvt.id); assert.equal(secondRequest.sourceDocumentId, cvt.view.id); assert.equal(secondRequest.confirmed.lines.length, selectedSecond.length);
  const retriedFirst = await json(admin, `/api/imports/documents/${cvt.view.id}/confirm`, 'POST', requestBody); assert.equal(retriedFirst.id, confirmedCvt.id); assert(retriedFirst.duplicate);
  await json(admin, `/api/imports/documents/${cvt.view.id}/confirm`, 'POST', { ...requestBody, title: 'Forbidden rewrite' }, { status: 409 });
  checked('Two separately selected PCVT persist from one reusable year workbook; reload, checksum, duplicate upload and immutable confirmation');

  const quoteName = '08.09.2026-Báo Giá Công ty Cổ phần Phân Bón và Hóa Chất Cần Thơ.xlsx';
  const undecided = await upload(admin, quoteName, 'QUOTATION'); assert.equal(undecided.view.parsed.lines.length, 0); assert(undecided.view.parsed.warnings.some((s) => s.includes('SHEET_SELECTION_REQUIRED')));
  const independentQuote = await readDocumentWorkbook(join(sampleRoot, quoteName));
  for (const sheet of ['BG', 'Sheet1']) {
    const quoted = await upload(admin, quoteName, 'QUOTATION', sheet);
    const compared = compareDocumentWorkbook(independentQuote, quoted.view.parsed, 'QUOTATION', sheet);
    report.samples.push({ file: quoteName, sha256: quoted.sha256, ...compared }); assert.deepEqual(compared.mismatches, []); assert.equal(compared.sourceRows, sheet === 'BG' ? 43 : 18);
    assert.equal(quoted.view.parsed.quotationDate, sheet === 'BG' ? '2026-05-05' : '2026-09-08');
    assert(quoted.view.parsed.lines.every((l) => !l.materialCode));
    const saved = await json(admin, `/api/imports/documents/${quoted.view.id}/confirm`, 'POST', confirmation(quoted.view, 'Actual quotation ' + sheet));
    assert.equal(saved.confirmed.lines.length, compared.sourceRows);
  }
  checked('Actual multi-sheet quotation: explicit choice, 43+18 exact rows/fields, content dates, vendor codes kept separate');

  for (const [key, expectation] of Object.entries(PDF_EXPECTATIONS)) {
    const uploaded = await upload(admin, expectation.file, 'QUOTATION');
    const compared = compareDocumentPdf(uploaded.view.parsed, expectation);
    report.samples.push({ file: expectation.file, sha256: uploaded.sha256, ...compared }); assert.deepEqual(compared.mismatches, []);
    const saved = await json(admin, `/api/imports/documents/${uploaded.view.id}/confirm`, 'POST', confirmation(uploaded.view, 'Actual PDF quotation ' + key));
    assert.equal(saved.confirmed.lines.length, expectation.rows.length);
  }
  checked('Three actual text PDFs: 8/31/11 rows, exact prices/quantities/totals, model text, content dates and page provenance');
  const scanName = 'BAO GIA CTY HOA CHAT CAN THO - 081026.pdf', scan = await upload(admin, scanName, 'QUOTATION');
  assert.equal(scan.view.parsed.format, 'PDF_SCAN'); assert.equal(scan.view.parsed.lines.length, 0); assert(scan.view.parsed.warnings.some((s) => s.includes('OCR_NOT_SUPPORTED')));
  await json(admin, `/api/imports/documents/${scan.view.id}/confirm`, 'POST', confirmation(scan.view, 'Empty scan must fail'), { status: 400 });
  // Transcribed from the rendered source page reviewed by the test author; this is not OCR.
  const scanLine = { ...manual('manual-scan-1', 'Lưới inox 304', 'mét', '60', '592000', 'ô 3.8mm sợi 1.2mm khổ 1m0'), lineTotal: '35520000', vatPercent: '10', note: 'Nhập tay từ trang 1. Hiệu lực 7 ngày; giao 12 ngày sau xác nhận và tạm ứng. VND do người kiểm tra chọn, chứng từ không ghi ký hiệu tiền tệ.' };
  const manualSaved = await json(admin, `/api/imports/documents/${scan.view.id}/confirm`, 'POST', confirmation(scan.view, 'Báo giá Nam Phát - nhập tay đối chiếu ảnh', [scanLine], { supplierName: 'CÔNG TY TNHH SX&TM LƯỚI THÉP NAM PHÁT', quotationDate: '2026-10-08', currency: 'VND', priceBasis: 'EXCL_VAT', vatPercent: '10' }));
  assert.equal(manualSaved.parsed.lines.length, 0); assert.equal(manualSaved.confirmed.lines.length, 1); assert(manualSaved.confirmed.lines[0].issues.includes('MANUAL_ENTRY')); assert.deepEqual(manualSaved.confirmed.lines[0].raw, {});
  for (const field of ['quantity', 'unitPrice', 'lineTotal', 'vatPercent']) assert(equalDecimal(manualSaved.confirmed.lines[0][field], scanLine[field]));
  assert.equal(manualSaved.confirmed.lines[0].materialName, scanLine.materialName); assert.equal(manualSaved.confirmed.lines[0].specification, scanLine.specification); assert.equal(manualSaved.confirmed.lines[0].unit, 'mét');
  const scanReloaded = await json(admin, `/api/imports/documents/${scan.view.id}`); assert.deepEqual(scanReloaded.confirmed, manualSaved.confirmed);
  report.samples.push({ file: scanName, sha256: scan.sha256, sourceRows: 1, parsedRows: 0, automaticExtraction: false, manualTranscriptionSaved: true, checkedManualFields: 7, currencySelectedByReviewer: 'VND, source has no currency label' });
  checked('Actual scanned PDF: explicit unsupported OCR, empty save rejected, visually transcribed item persists with manual-entry provenance');
  const afterImports = await counts(); assert.equal(afterImports.history, 0); assert.equal(afterImports.orders, 0); assert.equal(afterImports.items, 0); report.afterImports = afterImports;
  checked('Import/confirmation of new requests and quotes creates no purchases or purchase orders');

  const syntheticCsv = Buffer.from('Tên vật tư,Quy cách,ĐVT,Số lượng\nVòng bi acceptance 6204,6204 2RS,Cái,2');
  const synthetic = await json(admin, '/api/imports/documents/preview?kind=REQUEST', 'POST', undefined, { multipart: { file: { name: 'synthetic.csv', mimeType: 'text/csv', buffer: syntheticCsv } } });
  const syntheticBody = confirmation(synthetic, 'Synthetic history lookup');
  await json(admin, `/api/imports/documents/${synthetic.id}/confirm`, 'POST', syntheticBody);
  async function seedPo(number, supplier, status, revision, price) {
    await sql(`INSERT INTO \`${db}\`.purchase_orders(po_number,order_date,supplier_name_snapshot,currency,vat_percent,status,revision,created_at,updated_at,version) VALUES('${number}','2026-10-07','${supplier}','VND',8,'${status}',${revision},NOW(),NOW(),0);`);
    const id = await sql(`SELECT id FROM \`${db}\`.purchase_orders WHERE po_number='${number}';`);
    await sql(`INSERT INTO \`${db}\`.purchase_order_items(purchase_order_id,line_no,material_name,specification,unit,quantity,unit_price) VALUES(${Number(id)},1,'Vòng bi acceptance 6204','6204 2RS','Cái',2,${price});`);
    return id;
  }
  async function seedHistory(supplier, price, reference, date) {
    await sql(`INSERT INTO \`${db}\`.historical_purchases(supplier_snapshot,material_name_snapshot,unit,quantity,unit_price,currency,currency_basis,source,source_reference,category,purchase_date) VALUES('${supplier}','Vòng bi acceptance 6204','Cái',2,${price},'VND','ASSUMED_LEGACY','LEGACY','${reference}','MATERIAL','${date}');`);
  }
  const po = await seedPo('DOC-TEST-CURRENT', 'NCC synthetic A', 'EXPORTED', 2, 120);
  await seedHistory('NCC synthetic A', 120, `PO:${po}:R:2:L:1`, '2026-10-07'); await seedHistory('NCC synthetic A', 99, `PO:${po}:R:1:L:1`, '2026-10-06');
  const cancelled = await seedPo('DOC-TEST-CANCELLED', 'NCC synthetic cancelled', 'CANCELLED', 1, 10); await seedHistory('NCC synthetic cancelled', 10, `PO:${cancelled}:R:1:L:1`, '2026-10-07');
  await seedHistory('NCC synthetic B', 100, 'LEGACY:DOC:B1', '2026-10-01'); await seedHistory('NCC synthetic B', 110, 'LEGACY:DOC:B2', '2026-10-02'); await seedHistory('NCC synthetic B 2', 130, 'LEGACY:DOC:C1', '2026-10-03');
  const beforeLookup = await counts();
  const lookup = await json(admin, `/api/imports/documents/${synthetic.id}/lookup`, 'POST');
  const candidates = lookup.lines[0].candidates; assert.equal(candidates.length, 3);
  const bySupplier = new Map(candidates.map((c) => [c.supplierName, c]));
  assert.equal(Number(bySupplier.get('NCC synthetic A').count), 1); assert.equal(bySupplier.get('NCC synthetic A').source, 'PURCHASE_ORDER'); assert.equal(Number(bySupplier.get('NCC synthetic B').count), 2); assert(equalDecimal(bySupplier.get('NCC synthetic B').unitPrice, '110')); assert(!bySupplier.has('NCC synthetic cancelled'));
  const selections = candidates.map((c) => ({ lineKey: lookup.lines[0].lineKey, supplierKey: c.supplierKey }));
  const inquiryBody = { selections };
  const inquiries = await json(admin, `/api/imports/documents/${synthetic.id}/inquiries`, 'POST', inquiryBody); assert.equal(inquiries.inquirySelections.length, 3);
  const xlsx = await call(admin, `/api/imports/documents/${synthetic.id}/inquiries.xlsx`, 'POST', inquiryBody); const xlsxBytes = await xlsx.body(); assert(xlsxBytes.subarray(0, 2).equals(Buffer.from('PK'))); await writeFile(join(runtime, `${db}-synthetic-inquiries.xlsx`), xlsxBytes);
  const exported = await readDocumentWorkbook(join(runtime, `${db}-synthetic-inquiries.xlsx`)); assert.equal(exported.sheets.length, 3);
  for (const sheet of exported.sheets) for (const row of sheet.rows.values()) for (const cell of Object.values(row)) assert(!['120', '110', '130', 'Đơn giá'].includes(cell.value), 'Supplier inquiry must not expose previous supplier prices');
  const reloadedSelections = await json(admin, `/api/imports/documents/${synthetic.id}`); assert.deepEqual(reloadedSelections.inquirySelections, inquiries.inquirySelections);
  assert.deepEqual(await counts(), beforeLookup);
  checked('MySQL history lookup: all distinct snapshot suppliers, PO-only fallback, duplicate lineage/old revisions/cancelled PO excluded, inquiry export read-only');

  await seedHistory('NCC synthetic B', 125, 'LEGACY:DOC:B3', '2026-10-09');
  const quoteCsv = Buffer.from('Tên vật tư,Quy cách,ĐVT,Số lượng,Đơn giá\nVòng bi acceptance 6204,6204 2RS,Cái,2,132');
  const quotePreview = await json(admin, '/api/imports/documents/preview?kind=QUOTATION', 'POST', undefined, { multipart: { file: { name: 'synthetic-comparison.csv', mimeType: 'text/csv', buffer: quoteCsv } } });
  const compatibleLine = manual('manual-comparison-vnd', 'Vòng bi acceptance 6204', 'Cái', '2', '132', '6204 2RS');
  const differentCurrency = { ...compatibleLine, lineKey: 'manual-comparison-usd', currency: 'USD' };
  const differentUnit = { ...compatibleLine, lineKey: 'manual-comparison-unit', unit: 'Sợi' };
  const requestLine = syntheticBody.lines[0].lineKey;
  const mappedQuote = confirmation(quotePreview, 'Synthetic comparison across compatible and blocked bases', [compatibleLine, differentCurrency, differentUnit], { supplierName: 'NCC synthetic quotation', quotationDate: '2026-10-10', currency: 'VND', priceBasis: 'EXCL_VAT', requestId: Number(synthetic.id), requestLineMappings: { [compatibleLine.lineKey]: requestLine, [differentCurrency.lineKey]: requestLine, [differentUnit.lineKey]: requestLine } });
  const beforeComparison = await counts();
  await json(admin, `/api/imports/documents/${quotePreview.id}/confirm`, 'POST', { ...mappedQuote, requestLineMappings: { [compatibleLine.lineKey]: 'unknown-request-line' } }, { status: 400 });
  const mappedSaved = await json(admin, `/api/imports/documents/${quotePreview.id}/confirm`, 'POST', mappedQuote); assert.equal(mappedSaved.confirmed.requestId, synthetic.id);
  const comparison = await json(admin, `/api/imports/documents/${synthetic.id}/comparison`);
  const comparedQuotes = comparison.lines[0].quotes; assert.equal(comparedQuotes.length, 3);
  assert(comparedQuotes[0].comparable); assert(equalDecimal(comparedQuotes[0].delta, '12')); assert(equalDecimal(comparedQuotes[0].percent, '10'));
  assert.equal(comparedQuotes[0].baseline.sourceReference, `PO:${po}:R:2:L:1`); assert.equal(comparedQuotes[0].baseline.purchaseDate, '2026-10-07');
  for (const blocked of comparedQuotes.slice(1)) { assert.equal(blocked.comparable, false); assert.equal(blocked.delta, null); assert(blocked.reason); }
  assert.deepEqual(await counts(), beforeComparison);
  checked('Real MySQL linked quote comparison: invalid mapping rejected, newest compatible baseline selected over newer unverified history, exact delta and FX/unit mismatch explanations');

  const viewerSecret = randomBytes(24).toString('base64url'), viewerPassword = randomBytes(24).toString('base64url');
  const viewerAccount = await json(admin, '/api/admin/users', 'POST', { email: 'document-viewer@khvt.test', displayName: 'Synthetic document viewer', initialPassword: viewerSecret, roleCodes: ['VIEWER'] });
  const viewer = await context(); await json(viewer, '/api/auth/login', 'POST', { email: viewerAccount.email, password: viewerSecret }); await json(viewer, '/api/auth/change-password', 'POST', { currentPassword: viewerSecret, newPassword: viewerPassword });
  await call(viewer, `/api/imports/documents/${cvt.view.id}`, 'GET', undefined, { status: 403 }); await call(viewer, `/api/imports/documents/${cvt.view.id}/source`, 'GET', undefined, { status: 403 });
  await call(viewer, `/api/imports/documents/${synthetic.id}/lookup`, 'POST', undefined, { status: 403 }); await call(viewer, `/api/imports/documents/${synthetic.id}/inquiries.xlsx`, 'POST', inquiryBody, { status: 403 });
  checked('Real role enforcement rejects viewer direct document APIs and original file downloads');
  if (process.env.QMH_DOCUMENT_BROWSER === 'true') {
    browser = await connectBrowserMcp(); const available = await browser.request('tools/list'); assert(available.tools.some((tool) => tool.name === 'browser_run_code_unsafe'));
    await browser.call('browser_navigate', { url: `http://127.0.0.1:${port}/login` });
    // Arguments/replies stay in-process: session cookies never enter reports or console logs.
    await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.context().addCookies(${JSON.stringify((await admin.storageState()).cookies)}); await page.goto('http://127.0.0.1:${port}/imports?document=${cvt.view.id}'); await page.getByRole('heading',{name:'Kiểm tra trước khi lưu',exact:true}).waitFor(); await page.getByText('2013 dòng phát hiện, 0 dòng được chọn',{exact:false}).waitFor(); if(await page.getByLabel('Lọc số phiếu nguồn',{exact:true}).locator('option').count() !== 42) throw new Error('Expected forty phieu plus all/unassigned options'); return 'Actual yearly preview loaded'; }` });
    await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.goto('http://127.0.0.1:${port}/imports?document=${confirmedCvt.id}'); await page.getByRole('heading',{name:'Chứng từ đã xác nhận',exact:true}).waitFor(); if(await page.getByLabel('Số phiếu đang xử lý',{exact:true}).inputValue() !== '1') throw new Error('Wrong saved phieu'); await page.getByRole('button',{name:'Nhập phiếu khác từ cùng sổ',exact:true}).waitFor(); await page.reload(); await page.getByRole('heading',{name:'Chứng từ đã xác nhận',exact:true}).waitFor(); return 'Real saved PCVT reload'; }` });
    await browser.call('browser_run_code_unsafe', { code: `async (page) => { await page.goto('http://127.0.0.1:${port}/imports?document=${synthetic.id}'); await page.getByRole('heading',{name:'Chứng từ đã xác nhận',exact:true}).waitFor(); await page.getByRole('button',{name:'Tra NCC và giá cũ',exact:true}).click(); await page.getByRole('heading',{name:'NCC từng mua và giá cũ',exact:true}).waitFor(); for(const name of ['NCC synthetic A','NCC synthetic B','NCC synthetic B 2']) await page.getByRole('cell',{name,exact:false}).first().waitFor(); await page.getByRole('button',{name:'Xem so sánh báo giá',exact:true}).click(); await page.getByRole('heading',{name:'So sánh báo giá mới với giá cũ',exact:true}).waitFor(); await page.getByText('So với NCC synthetic A',{exact:false}).waitFor(); if(await page.getByRole('alert').count()) throw new Error('Unexpected application error alert'); await page.setViewportSize({width:390,height:844}); if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)) throw new Error('Mobile page overflow'); await page.screenshot({path:${JSON.stringify(join(runtime, `${db}-real-import-mobile.png`))},fullPage:true}); return 'Real lookup and comparison browser checked'; }` });
    await browser.close(); browser = undefined; report.browser = 'passed real MCP browser on isolated MySQL/Redis';
    checked('Real browser MCP: actual 2013-row yearly preview, selected PCVT reload, persisted supplier/history lookup and linked price comparison, mobile overflow');
  } else report.browser = 'Not enabled; set QMH_DOCUMENT_BROWSER=true for real MCP/UI checks';
  for (const sample of report.samples) assert.equal(sha256(await readFile(join(sampleRoot, sample.file))), sample.sha256);
  checked('All six original source files retain their original checksums');
  report.mainAfter = await mainCounts(); assert.deepEqual(report.mainAfter, report.mainBefore);
  checked('Read-only before/after main database counts and Flyway version remain unchanged');
  report.finalCounts = await counts(); report.completed = true;
} catch (error) {
  report.failure = String(error.message); console.error('FAIL ' + report.failure); process.exitCode = 1;
} finally {
  if (browser) await browser.close().catch(() => {});
  for (const ctx of contexts) await ctx.dispose().catch(() => {});
  if (backend && backend.exitCode === null) { const exited = new Promise((done) => backend.once('exit', done)); backend.kill(); await exited; }
  if (report.mainBefore && !report.mainAfter) {
    try { report.mainAfter = await mainCounts(); report.mainUnchanged = JSON.stringify(report.mainAfter) === JSON.stringify(report.mainBefore); }
    catch { report.mainAfterVerification = 'Read-only verification could not complete'; }
  } else if (report.mainBefore) report.mainUnchanged = JSON.stringify(report.mainAfter) === JSON.stringify(report.mainBefore);
  await writeFile(join(runtime, 'document-real-integration-report.json'), JSON.stringify(report, null, 2));
  await writeFile(join(runtime, `${db}-report.json`), JSON.stringify(report, null, 2));
  console.log(`Report: source/target/runtime/${db}-report.json; completed=${report.completed}`);
}
