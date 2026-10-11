import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createConnection } from 'node:net';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { connectBrowserMcp } from '../browser-mcp/client.mjs';

// This driver only writes to its generated schema and Redis namespace. Main
// database snapshots are read-only, and credentials/cookies never enter reports.
const root = fileURLToPath(new URL('../../', import.meta.url));
const source = join(root, 'source'), runtime = join(source, 'target/runtime');
await mkdir(runtime, { recursive: true });
const settings = {};
for (const line of (await readFile(join(source, '.env'), 'utf8')).split(/\r?\n/)) {
  const matched = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
  if (!matched) continue;
  let value = matched[2].trim();
  if (/^(['"]).*\1$/.test(value)) value = value.slice(1, -1);
  settings[matched[1]] = value;
}
assert(settings.DB_PASSWORD && settings.REDIS_PASSWORD, 'Private local MySQL/Redis configuration is required');
const db = `qmh_admin_phase1_${randomBytes(6).toString('hex')}`;
const namespace = `qmh:test:admin-phase1:${db}`, port = 8082;
const configuredDatabase = new URL((settings.SPRING_DATASOURCE_URL || 'jdbc:mysql://localhost:3307/quanlymuahang').replace(/^jdbc:/, ''));
assert(['localhost', '127.0.0.1'].includes(configuredDatabase.hostname) && configuredDatabase.port === '3307', 'Only the local project MySQL instance may be used');
const mainDatabase = configuredDatabase.pathname.slice(1);
assert(/^[A-Za-z0-9_]+$/.test(mainDatabase) && mainDatabase !== db);
const report = { checkedAt: new Date().toISOString(), database: db, namespace, checks: [], completed: false };
const contexts = [], generatedSecrets = [];
let backend, backendLog, browser, backendEnvironment, backendArgs, java;
const { request } = await import(pathToFileURL(join(source, 'frontend/node_modules/playwright/index.mjs')).href);
function secret() { const value = randomBytes(24).toString('base64url'); generatedSecrets.push(value); return value; }
const administratorEmail = 'phase1-admin@khvt.test', temporary = secret(), password = secret();
function checked(name) { report.checks.push(name); console.log('PASS ' + name); }
async function portOccupied() {
  return new Promise((done, failed) => {
    const socket = createConnection({ host: '127.0.0.1', port });
    socket.once('connect', () => { socket.destroy(); done(true); });
    socket.once('error', (error) => error.code === 'ECONNREFUSED' ? done(false) : failed(new Error('Unable to verify isolated test port availability')));
    socket.setTimeout(2000, () => { socket.destroy(); failed(new Error('Isolated test port availability probe timed out')); });
  });
}
async function sql(statement) {
  assert(/^[a-z0-9_]+$/.test(db));
  return new Promise((resolveResult, reject) => {
    const child = spawn('docker.exe', ['exec', '-i', 'qmh-local-mysql-1', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names'], { windowsHide: true });
    let result = '';
    child.stdout.setEncoding('utf8'); child.stdout.on('data', (chunk) => { result += chunk; }); child.stderr.resume();
    child.on('error', reject);
    child.on('exit', (code) => code === 0 ? resolveResult(result.trim()) : reject(new Error('Local database operation failed')));
    child.stdin.end(statement);
  });
}
async function mainSnapshot() {
  const counts = JSON.parse(await sql(`SELECT JSON_OBJECT('history',(SELECT COUNT(*) FROM \`${mainDatabase}\`.historical_purchases),'orders',(SELECT COUNT(*) FROM \`${mainDatabase}\`.purchase_orders),'items',(SELECT COUNT(*) FROM \`${mainDatabase}\`.purchase_order_items),'suppliers',(SELECT COUNT(*) FROM \`${mainDatabase}\`.suppliers),'materials',(SELECT COUNT(*) FROM \`${mainDatabase}\`.materials),'users',(SELECT COUNT(*) FROM \`${mainDatabase}\`.user_accounts),'employees',(SELECT COUNT(*) FROM \`${mainDatabase}\`.employees),'departments',(SELECT COUNT(*) FROM \`${mainDatabase}\`.departments),'positions',(SELECT COUNT(*) FROM \`${mainDatabase}\`.positions),'roles',(SELECT COUNT(*) FROM \`${mainDatabase}\`.roles),'userRoles',(SELECT COUNT(*) FROM \`${mainDatabase}\`.user_roles),'audits',(SELECT COUNT(*) FROM \`${mainDatabase}\`.audit_logs),'flywayVersion',(SELECT version FROM \`${mainDatabase}\`.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1));`));
  // Metadata makes a same-count account/role change visible without reading hashes.
  const accountMetadata = await sql(`SELECT JSON_OBJECT('id',id,'email',email,'displayName',display_name,'employeeId',employee_id,'status',status,'mustChangePassword',must_change_password,'updatedAt',updated_at) FROM \`${mainDatabase}\`.user_accounts ORDER BY id;`);
  const roleLinks = await sql(`SELECT CONCAT(user_id,':',role_id) FROM \`${mainDatabase}\`.user_roles ORDER BY user_id,role_id;`);
  return { counts, accountMetadata, roleLinks };
}
async function isolatedCounts() {
  return JSON.parse(await sql(`SELECT JSON_OBJECT('users',(SELECT COUNT(*) FROM \`${db}\`.user_accounts),'employees',(SELECT COUNT(*) FROM \`${db}\`.employees),'audits',(SELECT COUNT(*) FROM \`${db}\`.audit_logs));`));
}
function startBackend(suffix) {
  backend = spawn(java, backendArgs, { cwd: source, env: backendEnvironment, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  backendLog = createWriteStream(join(runtime, `${db}-${suffix}-backend.log`));
  backend.stdout.pipe(backendLog); backend.stderr.pipe(backendLog);
}
async function stopBackend() {
  if (backend && backend.exitCode === null) {
    const exited = new Promise((done) => backend.once('exit', done)); backend.kill(); await exited;
  }
  backendLog?.end();
}
async function ready() {
  const deadline = Date.now() + 100_000;
  while (Date.now() < deadline) {
    if (backend.exitCode !== null) throw new Error('Isolated backend exited; inspect its private runtime log');
    try { if ((await fetch(`http://127.0.0.1:${port}/actuator/health`)).ok) return; } catch {}
    await new Promise((done) => setTimeout(done, 400));
  }
  throw new Error('Isolated backend startup timed out');
}
async function context() {
  const ctx = await request.newContext({ baseURL: `http://127.0.0.1:${port}`, timeout: 60_000 }); contexts.push(ctx); return ctx;
}
async function call(ctx, path, method = 'GET', data, options = {}) {
  const headers = {};
  if (method !== 'GET' && !options.noCsrf) {
    const csrf = await (await ctx.get('/api/auth/csrf')).json(); headers[csrf.headerName] = csrf.token;
  }
  const response = await ctx.fetch(path, { method, data, headers });
  const expected = options.status ?? 200;
  if (response.status() !== expected) {
    let code = ''; try { code = (await response.json()).code ?? ''; } catch {}
    throw new Error(`${method} ${path}: HTTP ${response.status()}, expected ${expected}${code ? ' (' + code + ')' : ''}`);
  }
  if (options.code) assert.equal((await response.json()).code, options.code);
  return response;
}
async function json(ctx, path, method = 'GET', data, options) { return (await call(ctx, path, method, data, options)).json(); }
async function login(ctx, email, suppliedPassword) { return json(ctx, '/api/auth/login', 'POST', { email, password: suppliedPassword }); }
async function signInNew(account, initialPassword) {
  const ctx = await context(), currentPassword = secret();
  assert((await login(ctx, account.email, initialPassword)).mustChangePassword);
  await call(ctx, '/api/dashboard', 'GET', undefined, { status: 403 });
  await json(ctx, '/api/auth/change-password', 'POST', { currentPassword: initialPassword, newPassword: currentPassword });
  return { ctx, password: currentPassword };
}
function updateCommand(account, changes = {}) {
  return { displayName: account.displayName, employeeId: account.employeeId ?? null, status: account.status, roleCodes: account.roleCodes, ...changes };
}
async function assertSessionsRevoked(...sessions) {
  for (const ctx of sessions) await call(ctx, '/api/auth/me', 'GET', undefined, { status: 401 });
}
async function assertRollback(operation) {
  const before = await isolatedCounts(); await operation(); assert.deepEqual(await isolatedCounts(), before);
}
try {
  // Refuse an occupied port; never stop another Java/server process.
  assert(!(await portOccupied()), 'Isolated test port 8082 is already occupied');
  report.mainBefore = await mainSnapshot();
  const dbUser = settings.DB_USERNAME || 'app'; assert(/^[A-Za-z0-9_]+$/.test(dbUser));
  await sql(`CREATE DATABASE \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON \`${db}\`.* TO '${dbUser}'@'%';`);
  report.databaseCreated = true;
  backendEnvironment = { ...process.env, ...settings, JAVA_TOOL_OPTIONS: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:3307/${db}`, SPRING_DATASOURCE_USERNAME: dbUser, REDIS_HOST: '127.0.0.1', REDIS_PORT: '6380', SERVER_PORT: String(port), ADMIN_BOOTSTRAP_EMAIL: administratorEmail, ADMIN_BOOTSTRAP_PASSWORD: temporary, ADMIN_BOOTSTRAP_NAME: 'Quản trị nghiệm thu Phase 1', APP_FRONTEND_URL: `http://127.0.0.1:${port}`, APP_PDF_FONT_PATH: 'C:/Windows/Fonts/arial.ttf', FILE_STORAGE_ROOT: join(runtime, db, 'files') };
  java = process.env.QMH_TEST_JAVA || (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java.exe') : 'C:/Program Files/Java/jdk-21.0.12/bin/java.exe');
  backendArgs = ['-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k', '-Xms16m', '-Xmx256m', '-XX:MaxMetaspaceSize=128m', '-XX:ReservedCodeCacheSize=32m', '-XX:CompressedClassSpaceSize=32m', '-jar', process.env.QMH_TEST_JAR || join(source, 'target/quan-ly-mua-hang-0.0.1-SNAPSHOT.jar'), `--spring.session.redis.namespace=${namespace}`, '--server.servlet.session.cookie.name=QMH_PHASE1_TEST_SESSION', '--logging.level.org.springframework.web=INFO', '--logging.level.org.springframework.security=INFO', '--debug=false'];
  startBackend('first'); await ready();
  assert.equal(await sql(`SELECT COUNT(*) FROM \`${db}\`.flyway_schema_history WHERE version='7' AND success=1;`), '1');
  checked('Empty isolated MySQL schema, Flyway V7 and Redis runtime health');
  const deadline = Date.now() + 30_000;
  while ((await sql(`SELECT COUNT(*) FROM \`${db}\`.user_accounts WHERE email='${administratorEmail}';`)) !== '1') {
    if (Date.now() > deadline) throw new Error('Synthetic admin bootstrap timed out');
    await new Promise((done) => setTimeout(done, 300));
  }
  const admin = await context();
  assert((await login(admin, administratorEmail, temporary)).mustChangePassword);
  await call(admin, '/api/admin/users', 'GET', undefined, { status: 403 });
  await json(admin, '/api/auth/change-password', 'POST', { currentPassword: temporary, newPassword: password });
  const me = await json(admin, '/api/auth/me');
  assert.deepEqual(me.modules.map((item) => item.code).sort(), ['ADMINISTRATION', 'PURCHASING']);
  assert.equal(me.modules.find((item) => item.code === 'ADMINISTRATION').entryPath, '/admin/users');
  const sessionCookie = (await admin.storageState()).cookies.find((item) => item.name === 'QMH_PHASE1_TEST_SESSION');
  assert(sessionCookie?.httpOnly); assert.equal(sessionCookie.sameSite, 'Lax');
  const anonymous = await context(); await call(anonymous, '/api/admin/users', 'GET', undefined, { status: 401 });
  await call(admin, '/api/admin/users', 'POST', { email: 'csrf@khvt.test', displayName: 'Rejected', initialPassword: secret(), roleCodes: ['VIEWER'] }, { noCsrf: true, status: 403 });
  checked('Password-change enforcement, HttpOnly cookie, anonymous/CSRF protection and two application tiles');

  const selfCommand = { displayName: 'Quản trị nghiệm thu Phase 1', employeeId: null, status: 'ACTIVE', roleCodes: ['ADMIN'] };
  await call(admin, `/api/admin/users/${me.id}`, 'PUT', { ...selfCommand, status: 'DISABLED' }, { status: 400, code: 'CANNOT_DISABLE_SELF' });
  await call(admin, `/api/admin/users/${me.id}`, 'PUT', { ...selfCommand, roleCodes: ['VIEWER'] }, { status: 400, code: 'LAST_ADMIN' });
  let inactiveAdministrator = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-inactive-admin@khvt.test', displayName: 'Quản trị đã ngừng nghiệm thu', initialPassword: secret(), roleCodes: ['ADMIN'] });
  inactiveAdministrator = await json(admin, `/api/admin/users/${inactiveAdministrator.id}`, 'PUT', updateCommand(inactiveAdministrator, { status: 'DISABLED' }));
  inactiveAdministrator = await json(admin, `/api/admin/users/${inactiveAdministrator.id}`, 'PUT', updateCommand(inactiveAdministrator, { roleCodes: ['VIEWER'] }));
  assert.equal(inactiveAdministrator.status, 'DISABLED'); assert.deepEqual(inactiveAdministrator.roleCodes, ['VIEWER']);
  await call(admin, `/api/admin/users/${me.id}`, 'PUT', { ...selfCommand, roleCodes: ['VIEWER'] }, { status: 400, code: 'LAST_ADMIN' });
  checked('Self-disable and last-active-admin removal remain blocked; removing the ADMIN role of an already disabled account is allowed');

  const department = await json(admin, '/api/personnel/departments', 'POST', { code: 'P1-KHVT', name: 'Phòng KHVT nghiệm thu', active: true });
  const otherDepartment = await json(admin, '/api/personnel/departments', 'POST', { code: 'P1-KT', name: 'Phòng kỹ thuật nghiệm thu', active: true });
  const position = await json(admin, '/api/personnel/positions', 'POST', { code: 'P1-PHO', name: 'Phó phòng nghiệm thu', active: true });
  const options = await json(admin, '/api/admin/users/options');
  assert(options.departments.some((item) => item.id === department.id && item.active));
  assert(options.positions.some((item) => item.id === position.id && item.active));
  const viewerTemporary = secret();
  let viewer = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-viewer@khvt.test', displayName: 'Nguyễn kiểm thử Phase 1', initialPassword: viewerTemporary, roleCodes: ['VIEWER'], employeeProfile: { employeeCode: 'P1-VIEWER', departmentId: department.id, positionId: position.id } });
  assert(viewer.employeeId > 0); assert.equal(viewer.employeeCode, 'P1-VIEWER'); assert.equal(viewer.employeeName, viewer.displayName);
  assert.equal(viewer.departmentId, department.id); assert.equal(viewer.positionId, position.id);
  const persistedEmployee = await json(admin, `/api/personnel/employees/${viewer.employeeId}`);
  assert.equal(persistedEmployee.fullName, viewer.displayName); assert.equal(persistedEmployee.departmentId, department.id);
  const generatedCodeAccount = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-generated@khvt.test', displayName: 'Tự sinh mã nghiệm thu', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { departmentId: otherDepartment.id } });
  assert.match(generatedCodeAccount.employeeCode, /^NV-/); assert.equal(generatedCodeAccount.positionId, null);
  const optionalAccount = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-optional@khvt.test', displayName: 'Tài khoản không hồ sơ', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { employeeCode: null, departmentId: null, positionId: null } });
  assert.equal(optionalAccount.employeeId, null); assert.equal(optionalAccount.departmentId, null);
  const existingProfile = await json(admin, '/api/personnel/employees', 'POST', { employeeCode: 'P1-EXISTING', fullName: 'Hồ sơ có trước Phase 1', departmentId: department.id });
  const countBeforeLink = (await isolatedCounts()).employees;
  const linkedAccount = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-existing-profile@khvt.test', displayName: 'Tài khoản liên kết hồ sơ có trước', initialPassword: secret(), roleCodes: ['VIEWER'], employeeId: existingProfile.id });
  assert.equal(linkedAccount.employeeId, existingProfile.id); assert.equal(linkedAccount.employeeName, existingProfile.fullName);
  assert.equal((await isolatedCounts()).employees, countBeforeLink);
  checked('One-request account/profile creation, generated code, blank optional profile and existing-profile link without duplication');

  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: viewer.email.toUpperCase(), displayName: 'Trùng email', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { employeeCode: 'P1-NO-ORPHAN' } }, { status: 409, code: 'EMAIL_EXISTS' }));
  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: 'phase1-code-conflict@khvt.test', displayName: 'Trùng mã', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { employeeCode: 'p1-viewer' } }, { status: 409, code: 'EMPLOYEE_CODE_EXISTS' }));
  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: 'phase1-role-conflict@khvt.test', displayName: 'Sai vai trò', initialPassword: secret(), roleCodes: ['DOES_NOT_EXIST'], employeeProfile: { employeeCode: 'P1-NO-ORPHAN-ROLE' } }, { status: 400, code: 'UNKNOWN_ROLE' }));
  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: 'phase1-linked-twice@khvt.test', displayName: 'Liên kết trùng', initialPassword: secret(), roleCodes: ['VIEWER'], employeeId: viewer.employeeId }, { status: 409, code: 'EMPLOYEE_ALREADY_LINKED' }));
  checked('Duplicate email/code, invalid role and employee re-link failures roll back users, profiles and audit');

  const profileCountBeforeClear = (await isolatedCounts()).employees, originalViewerCode = viewer.employeeCode;
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { displayName: 'Đã xóa phòng ban và chức vụ', employeeProfile: { employeeCode: null, departmentId: null, positionId: null } }));
  assert.equal(viewer.employeeCode, originalViewerCode); assert.equal(viewer.employeeName, 'Đã xóa phòng ban và chức vụ');
  assert.equal(viewer.departmentId, null); assert.equal(viewer.positionId, null);
  assert.equal((await isolatedCounts()).employees, profileCountBeforeClear);
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { displayName: 'Nguyễn kiểm thử Phase 1', employeeProfile: { employeeCode: originalViewerCode, departmentId: department.id, positionId: position.id } }));
  await json(admin, `/api/personnel/departments/${otherDepartment.id}`, 'PUT', { code: otherDepartment.code, name: otherDepartment.name, active: false });
  const keptInactiveDepartment = await json(admin, `/api/admin/users/${generatedCodeAccount.id}`, 'PUT', updateCommand(generatedCodeAccount, { displayName: 'Giữ phòng ban cũ đã ngừng', employeeProfile: { employeeCode: generatedCodeAccount.employeeCode, departmentId: otherDepartment.id, positionId: null } }));
  assert.equal(keptInactiveDepartment.departmentId, otherDepartment.id); assert.equal(keptInactiveDepartment.employeeName, 'Giữ phòng ban cũ đã ngừng');
  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: 'phase1-inactive-department@khvt.test', displayName: 'Phòng ngừng', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { departmentId: otherDepartment.id } }, { status: 400, code: 'DEPARTMENT_NOT_ACTIVE' }));
  await json(admin, `/api/personnel/departments/${otherDepartment.id}`, 'PUT', { code: otherDepartment.code, name: otherDepartment.name, active: true });
  await json(admin, `/api/personnel/positions/${position.id}`, 'PUT', { code: position.code, name: position.name, active: false });
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { displayName: 'Giữ chức vụ cũ đã ngừng', employeeProfile: { employeeCode: viewer.employeeCode, departmentId: department.id, positionId: position.id } }));
  assert.equal(viewer.positionId, position.id); assert.equal(viewer.employeeName, 'Giữ chức vụ cũ đã ngừng');
  await assertRollback(() => call(admin, '/api/admin/users', 'POST', { email: 'phase1-inactive-position@khvt.test', displayName: 'Chức vụ ngừng', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { positionId: position.id } }, { status: 400, code: 'POSITION_NOT_ACTIVE' }));
  await json(admin, `/api/personnel/positions/${position.id}`, 'PUT', { code: position.code, name: position.name, active: true });
  checked('Existing profile clears optional fields and updates its name; inactive directory references can be retained but new assignments are rejected without orphans');

  const employeeCount = (await isolatedCounts()).employees;
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { displayName: 'Nguyễn đổi phòng Phase 1', employeeProfile: { employeeCode: viewer.employeeCode, departmentId: otherDepartment.id, positionId: position.id } }));
  assert.equal((await isolatedCounts()).employees, employeeCount);
  assert.equal(viewer.departmentId, otherDepartment.id); assert.equal(viewer.employeeName, 'Nguyễn đổi phòng Phase 1');
  const byCode = await json(admin, '/api/admin/users?query=' + encodeURIComponent('p1-viewer'));
  assert.equal(byCode.totalElements, 1); assert.equal(byCode.content[0].id, viewer.id);
  const byName = await json(admin, '/api/admin/users?query=' + encodeURIComponent('Nguyễn đổi phòng'));
  assert.equal(byName.totalElements, 1);
  const byEmail = await json(admin, '/api/admin/users?query=' + encodeURIComponent('phase1-optional@khvt.test'));
  assert.equal(byEmail.totalElements, 1); assert.equal(byEmail.content[0].id, optionalAccount.id);
  const byDepartment = await json(admin, '/api/admin/users?departmentId=' + otherDepartment.id);
  assert(byDepartment.content.some((item) => item.id === viewer.id)); assert(byDepartment.content.every((item) => item.departmentId === otherDepartment.id));
  const firstPage = await json(admin, '/api/admin/users?size=2&page=0'), secondPage = await json(admin, '/api/admin/users?size=2&page=1');
  assert.equal(firstPage.size, 2); assert.equal(secondPage.size, 2); assert(firstPage.totalElements >= 4);
  assert(firstPage.content.every((item) => !secondPage.content.some((other) => other.id === item.id)));
  assert.equal((await json(admin, '/api/admin/users?query=' + encodeURIComponent('Không có kết quả xyz'))).totalElements, 0);
  checked('Profile update without duplication and server-wide email/name/code/department filtering with stable pagination');

  const firstViewer = await signInNew(viewer, viewerTemporary), secondViewer = await context();
  await login(secondViewer, viewer.email, firstViewer.password);
  const viewerMe = await json(firstViewer.ctx, '/api/auth/me');
  assert.deepEqual(viewerMe.modules.map((item) => item.code), ['PURCHASING']);
  await call(firstViewer.ctx, '/api/admin/users', 'GET', undefined, { status: 403 });
  await call(firstViewer.ctx, '/api/admin/users/options', 'GET', undefined, { status: 403 });
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { roleCodes: ['PLANNER'] }));
  await assertSessionsRevoked(firstViewer.ctx, secondViewer);
  await login(firstViewer.ctx, viewer.email, firstViewer.password); await login(secondViewer, viewer.email, firstViewer.password);
  assert((await json(firstViewer.ctx, '/api/auth/me')).permissions.includes('IMPORT_OPERATIONAL'));
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { status: 'LOCKED' }));
  await assertSessionsRevoked(firstViewer.ctx, secondViewer);
  await call(firstViewer.ctx, '/api/auth/login', 'POST', { email: viewer.email, password: firstViewer.password }, { status: 401 });
  assert.equal((await json(admin, '/api/admin/users?status=LOCKED')).content.find((item) => item.id === viewer.id)?.status, 'LOCKED');
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { status: 'ACTIVE' }));
  await login(firstViewer.ctx, viewer.email, firstViewer.password); await login(secondViewer, viewer.email, firstViewer.password);
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { status: 'DISABLED' }));
  await assertSessionsRevoked(firstViewer.ctx, secondViewer);
  await call(firstViewer.ctx, '/api/auth/login', 'POST', { email: viewer.email, password: firstViewer.password }, { status: 401 });
  const disabled = await json(admin, '/api/admin/users?status=DISABLED&query=' + encodeURIComponent('phase1-viewer'));
  assert.equal(disabled.totalElements, 1); assert.equal(disabled.content[0].id, viewer.id);
  viewer = await json(admin, `/api/admin/users/${viewer.id}`, 'PUT', updateCommand(viewer, { status: 'ACTIVE' }));
  await login(firstViewer.ctx, viewer.email, firstViewer.password); await login(secondViewer, viewer.email, firstViewer.password);
  const resetTemporary = secret();
  await call(admin, `/api/admin/users/${viewer.id}/reset-password`, 'POST', { temporaryPassword: resetTemporary });
  await assertSessionsRevoked(firstViewer.ctx, secondViewer);
  await call(firstViewer.ctx, '/api/auth/login', 'POST', { email: viewer.email, password: firstViewer.password }, { status: 401 });
  assert((await login(firstViewer.ctx, viewer.email, resetTemporary)).mustChangePassword);
  await call(firstViewer.ctx, '/api/catalog/materials/page', 'GET', undefined, { status: 403 });
  const finalViewerPassword = secret();
  await json(firstViewer.ctx, '/api/auth/change-password', 'POST', { currentPassword: resetTemporary, newPassword: finalViewerPassword });
  checked('Real Redis revokes both sessions for role/status/password changes; locked/disabled login and temporary-password bypass are blocked');

  const hrTemporary = secret();
  const hr = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-directory@khvt.test', displayName: 'Quản lý danh bạ Phase 1', initialPassword: hrTemporary, roleCodes: ['HR_MANAGER'] });
  const signedHr = await signInNew(hr, hrTemporary), hrMe = await json(signedHr.ctx, '/api/auth/me');
  assert.deepEqual(hrMe.modules.map((item) => item.code), ['ADMINISTRATION']);
  assert.equal(hrMe.modules[0].entryPath, '/admin/employees');
  assert(!hrMe.permissions.includes('USER_READ')); assert(!hrMe.permissions.includes('USER_MANAGE'));
  await json(signedHr.ctx, '/api/personnel/employees');
  await call(signedHr.ctx, '/api/admin/users', 'GET', undefined, { status: 403 });
  await call(signedHr.ctx, '/api/admin/users/options', 'GET', undefined, { status: 403 });
  await call(signedHr.ctx, '/api/admin/users', 'POST', { email: 'hr-must-not-create@khvt.test', displayName: 'Rejected', initialPassword: secret(), roleCodes: ['VIEWER'] }, { status: 403 });
  checked('Existing HR_MANAGER reaches the directory under Administration without gaining account-management permissions');

  // A synthetic delegated manager proves the API is safe even if USER_MANAGE is
  // later delegated. The seed itself is limited to this generated test schema.
  await sql(`INSERT INTO \`${db}\`.roles(code,name,description,system_role,active) VALUES('PHASE1_MANAGER','Quản lý tài khoản nghiệm thu','Synthetic test-only delegation',0,1); INSERT INTO \`${db}\`.role_permissions(role_id,permission_id) SELECT r.id,p.id FROM \`${db}\`.roles r JOIN \`${db}\`.permissions p ON p.code IN ('USER_READ','USER_MANAGE','CATALOG_READ','PO_READ','PRICE_READ') WHERE r.code='PHASE1_MANAGER';`);
  const managerTemporary = secret();
  const manager = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-manager@khvt.test', displayName: 'Quản lý tài khoản giới hạn', initialPassword: managerTemporary, roleCodes: ['PHASE1_MANAGER'] });
  const signedManager = await signInNew(manager, managerTemporary);
  const assignable = await json(signedManager.ctx, '/api/admin/roles');
  assert.equal(assignable.find((item) => item.code === 'ADMIN').assignable, false);
  assert.equal(assignable.find((item) => item.code === 'VIEWER').assignable, true);
  assert.equal(assignable.find((item) => item.code === 'PLANNER').assignable, false);
  await assertRollback(() => call(signedManager.ctx, '/api/admin/users', 'POST', { email: 'phase1-escalation@khvt.test', displayName: 'Rejected', initialPassword: secret(), roleCodes: ['ADMIN'], employeeProfile: { employeeCode: 'P1-ESCALATION' } }, { status: 403, code: 'ROLE_ASSIGNMENT_FORBIDDEN' }));
  await call(signedManager.ctx, `/api/admin/users/${manager.id}`, 'PUT', updateCommand(manager, { roleCodes: ['ADMIN'] }), { status: 403, code: 'ROLE_ASSIGNMENT_FORBIDDEN' });
  await call(signedManager.ctx, `/api/admin/users/${me.id}`, 'PUT', selfCommand, { status: 403, code: 'ACCOUNT_MANAGEMENT_FORBIDDEN' });
  await call(signedManager.ctx, `/api/admin/users/${me.id}/reset-password`, 'POST', { temporaryPassword: secret() }, { status: 403, code: 'ACCOUNT_MANAGEMENT_FORBIDDEN' });
  const delegatedViewer = await json(signedManager.ctx, '/api/admin/users', 'POST', { email: 'phase1-delegated-viewer@khvt.test', displayName: 'Người xem do quản lý tạo', initialPassword: secret(), roleCodes: ['VIEWER'], employeeProfile: { departmentId: department.id } });
  assert(delegatedViewer.employeeId > 0); assert.equal(delegatedViewer.departmentId, department.id);
  checked('Delegated manager cannot grant ADMIN or manage/reset an administrator; assignable role metadata matches API enforcement');

  const beforeRestart = await json(admin, '/api/auth/me');
  await stopBackend(); startBackend('restart'); await ready();
  assert.equal((await json(admin, '/api/auth/me')).id, beforeRestart.id);
  const persisted = await json(admin, '/api/admin/users?query=' + encodeURIComponent(viewer.email));
  assert.equal(persisted.content[0].employeeId, viewer.employeeId); assert.equal(persisted.content[0].departmentId, otherDepartment.id);
  checked('Restart preserves reviewed account/profile data and existing Redis-authenticated session');

  if (process.env.QMH_PHASE1_BROWSER === 'true') {
    browser = await connectBrowserMcp();
    await browser.call('browser_navigate', { url: `http://127.0.0.1:${port}/login` });
    // Arguments and replies are intentionally retained in-process only.
    report.browserStep = 'Admin portal and landing';
    await browser.call('browser_run_code_unsafe', { code: `async(page)=>{await page.context().addCookies(${JSON.stringify((await admin.storageState()).cookies)});await page.goto('http://127.0.0.1:${port}/modules');await page.getByRole('link',{name:/Quản trị/}).waitFor();if(await page.getByRole('link',{name:/Nhân sự/}).count())throw new Error('Personnel must not remain a separate application tile');await page.getByRole('link',{name:/Quản trị/}).click();await page.getByRole('heading',{name:'Tài khoản',exact:true}).waitFor();return 'Admin portal and landing page checked';}` });
    const browserEmail = 'phase1-browser-created@khvt.test', browserSecret = secret();
    report.browserStep = 'Inline profile creation';
    const inlineResult = await browser.call('browser_run_code_unsafe', { code: `async(page)=>{
      let step='open-form';
      try {
        await page.getByRole('button',{name:'Thêm tài khoản',exact:true}).click();
        const form=page.getByRole('dialog',{name:'Thêm tài khoản',exact:true});
        step='email'; await form.getByLabel('Email đăng nhập *',{exact:true}).fill(${JSON.stringify(browserEmail)});
        step='name'; await form.getByLabel('Tên hiển thị *',{exact:true}).fill('Tài khoản tạo từ browser thật');
        step='password'; await form.getByLabel('Mật khẩu tạm *',{exact:true}).fill(${JSON.stringify(browserSecret)});
        step='confirmation'; await form.getByLabel('Nhập lại mật khẩu tạm *',{exact:true}).fill(${JSON.stringify(browserSecret)});
        step='department'; await form.getByLabel('Phòng ban',{exact:true}).selectOption('${department.id}');
        step='position'; await form.getByLabel('Chức vụ',{exact:true}).selectOption('${position.id}');
        step='viewer-role'; await form.locator('input[type="checkbox"][value="VIEWER"]').check();
        step='submit'; await form.getByRole('button',{name:'Tạo tài khoản',exact:true}).click();
        step='form-closed'; await form.waitFor({state:'hidden'});
        step='created-row'; await page.getByText(${JSON.stringify(browserEmail)},{exact:true}).waitFor();
        return {ok:true};
      } catch (error) {
        return {ok:false,step,reason:/strict mode/i.test(String(error.message))?'STRICT_MATCH':/timeout/i.test(String(error.message))?'TIMEOUT':'ACTION_FAILED'};
      }
    }` });
    // Inspect only the returned scalar diagnostics, never the echoed code or args.
    const inlineText = inlineResult.content.filter((part) => part.type === 'text').map((part) => part.text).join('\n');
    const resultSection = inlineText.match(/### Result\s*\n([\s\S]*?)(?=\n### |$)/);
    assert(resultSection, 'Missing safe inline browser diagnostic result');
    const inlineDiagnostic = JSON.parse(resultSection[1].trim());
    if (!inlineDiagnostic.ok) throw new Error(`Inline browser ${inlineDiagnostic.step}: ${inlineDiagnostic.reason}`);
    const fromBrowser = await json(admin, '/api/admin/users?query=' + encodeURIComponent(browserEmail));
    assert.equal(fromBrowser.totalElements, 1); assert.equal(fromBrowser.content[0].departmentId, department.id); assert.equal(fromBrowser.content[0].positionId, position.id); assert.match(fromBrowser.content[0].employeeCode, /^NV-/);
    report.browserStep = 'Filter, reload and mobile';
    await browser.call('browser_run_code_unsafe', { code: `async(page)=>{
      await page.getByLabel('Email, họ tên hoặc mã nhân viên',{exact:true}).fill(${JSON.stringify(browserEmail)});
      await page.getByRole('button',{name:'Tìm kiếm',exact:true}).click();
      await page.getByText(${JSON.stringify(browserEmail)},{exact:true}).waitFor();
      await page.waitForFunction(()=>document.querySelectorAll('tbody tr').length===1);
      if(await page.locator('tbody tr').count()!==1)throw new Error('Server filter should return one row');
      await page.reload();
      await page.getByRole('heading',{name:'Tài khoản',exact:true}).waitFor();
      await page.getByText(${JSON.stringify(browserEmail)},{exact:true}).waitFor();
      await page.setViewportSize({width:390,height:844});
      if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw new Error('Mobile page overflow');
      await page.screenshot({path:${JSON.stringify(join(runtime, `${db}-users-mobile.png`))},fullPage:true});
      return 'Real server filter, reload and mobile layout checked';
    }` });
    report.browserStep = 'Directory portal and denied accounts';
    await browser.call('browser_run_code_unsafe', { code: `async(page)=>{await page.context().clearCookies();await page.context().addCookies(${JSON.stringify((await signedHr.ctx.storageState()).cookies)});await page.goto('http://127.0.0.1:${port}/modules');await page.getByRole('link',{name:/Quản trị/}).click();await page.waitForURL('**/admin/employees');if(await page.getByRole('link',{name:'Tài khoản',exact:true}).count())throw new Error('Directory-only role must not see accounts navigation');await page.goto('http://127.0.0.1:${port}/admin/users');await page.getByRole('heading',{name:'Bạn không có quyền truy cập',exact:true}).waitFor();return 'Real directory-only portal and denied account route checked';}` });
    await browser.close(); browser = undefined;
    report.browser = 'Passed real MCP UI against isolated MySQL/Redis';
    delete report.browserStep;
    checked('Real MCP browser: admin/directory portal, inline account creation, server filtering, reload, direct-route denial and mobile overflow');
  } else report.browser = 'Not enabled; set QMH_PHASE1_BROWSER=true for actual MCP browser coverage';

  const concurrencyTemporary = secret();
  const otherAdministrator = await json(admin, '/api/admin/users', 'POST', { email: 'phase1-concurrent-admin@khvt.test', displayName: 'Quản trị kiểm tra đồng thời', initialPassword: concurrencyTemporary, roleCodes: ['ADMIN'] });
  const signedOtherAdministrator = await signInNew(otherAdministrator, concurrencyTemporary);
  const activeAdminCount = () => sql(`SELECT COUNT(DISTINCT a.id) FROM \`${db}\`.user_accounts a JOIN \`${db}\`.user_roles ur ON ur.user_id=a.id JOIN \`${db}\`.roles r ON r.id=ur.role_id WHERE a.status='ACTIVE' AND r.code='ADMIN';`);
  assert.equal(await activeAdminCount(), '2');
  const currentAdministrator = (await json(admin, '/api/admin/users?query=' + encodeURIComponent(administratorEmail))).content.find((item) => item.id === me.id);
  assert(currentAdministrator?.roleCodes.includes('ADMIN'));
  async function demoteSelf(ctx, account) {
    const csrf = await json(ctx, '/api/auth/csrf');
    const response = await ctx.put(`/api/admin/users/${account.id}`, { headers: { [csrf.headerName]: csrf.token }, data: updateCommand(account, { roleCodes: ['VIEWER'] }) });
    return { status: response.status(), body: await response.json() };
  }
  const concurrentDemotions = await Promise.all([demoteSelf(admin, currentAdministrator), demoteSelf(signedOtherAdministrator.ctx, otherAdministrator)]);
  assert.deepEqual(concurrentDemotions.map((item) => item.status).sort(), [200, 400]);
  assert.equal(concurrentDemotions.find((item) => item.status === 400).body.code, 'LAST_ADMIN');
  assert.equal(await activeAdminCount(), '1');
  const demotionContexts = [admin, signedOtherAdministrator.ctx];
  await call(demotionContexts[concurrentDemotions.findIndex((item) => item.status === 200)], '/api/auth/me', 'GET', undefined, { status: 401 });
  const remainingAdministrator = await json(demotionContexts[concurrentDemotions.findIndex((item) => item.status === 400)], '/api/auth/me');
  assert(remainingAdministrator.permissions.includes('*')); assert(remainingAdministrator.roles.includes('ADMIN'));
  report.concurrentDemotion = { statuses: concurrentDemotions.map((item) => item.status), rejection: 'LAST_ADMIN', activeAdministratorsAfter: 1 };
  checked('Concurrent MySQL self-demotion preserves one active administrator; the demoted current session is revoked and the rejected administrator remains authenticated');

  const audits = await sql(`SELECT after_json FROM \`${db}\`.audit_logs WHERE after_json IS NOT NULL;`);
  for (const value of generatedSecrets) assert(!audits.includes(value), 'A generated credential leaked into audit');
  assert(!/passwordHash|initialPassword|temporaryPassword|csrf|QMH_PHASE1_TEST_SESSION/i.test(audits), 'Audit must contain only safe account/profile metadata');
  checked('Audit stores safe account/profile metadata without password, hash, CSRF or session cookie');
  report.mainAfter = await mainSnapshot(); assert.deepEqual(report.mainAfter, report.mainBefore);
  report.mainUnchanged = true; report.finalCounts = await isolatedCounts();
  checked('Main account metadata, role links, business/personnel counts and Flyway version remain unchanged');
  report.completed = true;
} catch (error) {
  report.failure = String(error.message);
  console.error('FAIL ' + report.failure); process.exitCode = 1;
} finally {
  if (browser) await browser.close().catch(() => {});
  for (const ctx of contexts) await ctx.dispose().catch(() => {});
  await stopBackend();
  if (report.mainBefore && !report.mainAfter) {
    try { report.mainAfter = await mainSnapshot(); report.mainUnchanged = JSON.stringify(report.mainAfter) === JSON.stringify(report.mainBefore); }
    catch { report.mainAfterVerification = 'Read-only verification could not complete'; }
  }
  // Only counts/version are exposed in reports; account metadata stays private.
  if (report.mainBefore) report.mainBefore = report.mainBefore.counts;
  if (report.mainAfter) report.mainAfter = report.mainAfter.counts;
  await writeFile(join(runtime, 'admin-phase1-real-integration-report.json'), JSON.stringify(report, null, 2));
  await writeFile(join(runtime, `${db}-report.json`), JSON.stringify(report, null, 2));
  console.log(`Report: source/target/runtime/${db}-report.json; completed=${report.completed}; own backend stopped`);
}
