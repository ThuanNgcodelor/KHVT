# REST API, controller, application service và repository

## Quy ước API

- API prefix: /api; React dùng base URL duy nhất tại frontend/src/config/baseApi.ts.
- JSON có shape ổn định: data, error {code,message,fieldErrors}, traceId.
- Validation lỗi 400, chưa login 401, thiếu quyền 403, không tồn tại 404, conflict/version 409.
- Mọi list có phân trang/filter; không đưa toàn bộ 10k+ history về browser.
- Controller không gọi repository trực tiếp.

## Identity và phân quyền

| Method/path | Quyền | Mục đích |
|---|---|---|
| GET /api/auth/csrf | public | lấy/khởi tạo CSRF token cho SPA |
| POST /api/auth/login | public + CSRF | email/password, tạo session cookie |
| GET /api/auth/me | authenticated | user, employee summary, roles, permissions |
| POST /api/auth/logout | authenticated + CSRF | invalidate session |
| POST /api/auth/change-password | authenticated + CSRF | đổi mật khẩu hiện tại |
| GET /api/admin/users | USER_READ | filter/page tài khoản |
| POST /api/admin/users | USER_MANAGE | tạo account, liên kết employee, gán role |
| GET /api/admin/users/{id} | USER_READ | chi tiết |
| PATCH /api/admin/users/{id} | USER_MANAGE | profile/status update |
| PUT /api/admin/users/{id}/roles | ROLE_MANAGE | thay role assignments |
| POST /api/admin/users/{id}/reset-password | USER_MANAGE | tạo mật khẩu tạm/force-change |
| POST /api/admin/users/{id}/revoke-sessions | USER_MANAGE | revoke tất cả session |
| GET /api/admin/roles | ROLE_MANAGE | danh sách role/permission |
| POST /api/admin/roles | ROLE_MANAGE | tạo custom role |
| PATCH /api/admin/roles/{code} | ROLE_MANAGE | đổi tên/trạng thái role; role nền không xóa cứng |
| PUT /api/admin/roles/{code}/permissions | ROLE_MANAGE | thay permission có audit |
| GET /api/admin/permissions | ROLE_MANAGE | permission catalog |

Mật khẩu tạm chỉ hiện một lần trong response của thao tác admin, không log/ghi audit; UI yêu cầu chuyển qua kênh an toàn cho nhân viên và lần login đầu phải đổi.

## Nhân sự

| Method/path | Quyền | Mục đích |
|---|---|---|
| GET /api/personnel/employees | PERSONNEL_READ | tìm kiếm, filter, phân trang |
| POST /api/personnel/employees | PERSONNEL_MANAGE | tạo hồ sơ |
| GET /api/personnel/employees/{id} | PERSONNEL_READ | chi tiết, tài khoản liên kết |
| PUT /api/personnel/employees/{id} | PERSONNEL_MANAGE | cập nhật hồ sơ/version |
| POST /api/personnel/employees/{id}/deactivate | PERSONNEL_MANAGE | ngừng làm việc và revoke account session |
| POST /api/personnel/employees/{id}/account | USER_MANAGE | ADMIN tạo/liên kết account |
| GET/POST/PUT /api/personnel/departments | PERSONNEL_READ/PERSONNEL_MANAGE | phòng ban |
| GET/POST/PUT /api/personnel/positions | PERSONNEL_READ/PERSONNEL_MANAGE | chức vụ |

HR_MANAGER được cấp PERSONNEL_MANAGE, nhưng không được USER_MANAGE/ROLE_MANAGE mặc định. ADMIN có wildcard.

## Mua hàng, tra cứu, nhập xuất

- GET /api/dashboard: tổng quan.
- POST /api/purchase-orders/preview: validate cart và group NCC/currency.
- POST /api/purchase-orders: issue từng group PO; trả kết quả từng PO.
- GET /api/purchase-orders: list/filter/page; GET /{id}: detail; PUT /{id}: new revision; POST /{id}/cancel: hủy có lý do.
- POST /api/purchase-orders/{id}/pdf: retry generation; GET /{id}/files/{fileId}: authorized download.
- GET /api/price-search?q=&category=&currency=&from=&to=: search summary.
- GET /api/materials/suggest?q=: autocomplete; GET /api/materials/{id}/history: paginated history.
- CRUD /api/suppliers và /api/materials, alias theo CATALOG_READ/CATALOG_MANAGE.
- POST /api/imports/legacy-workbook/preview; POST /api/imports/{id}/commit; GET /api/imports/{id}/report (ADMIN only).
- POST /api/imports/operational-request/preview: parse list lines to draft cart; không ghi lịch sử/PO.
- GET /api/exports/purchase-orders.xlsx và /api/exports/price-history.xlsx có filter.
- GET /api/admin/audit-logs: filter/page, ADMIN hoặc AUDIT_READ.

## Application services

### IdentityApplicationService

~~~java
CurrentUserDto login(LoginCommand command, HttpRequestContext context);
CurrentUserDto currentUser(CurrentActor actor);
void logout(CurrentActor actor);
void changePassword(ChangePasswordCommand command, CurrentActor actor);
Page<UserSummaryDto> searchUsers(UserFilter filter, CurrentActor actor);
UserDto createUser(CreateUserCommand command, CurrentActor actor);
UserDto updateUser(long id, UpdateUserCommand command, CurrentActor actor);
void assignRoles(long id, Set<RoleCode> roles, CurrentActor actor);
RoleDto createRole(CreateRoleCommand command, CurrentActor actor);
RoleDto updateRole(RoleCode role, UpdateRoleCommand command, CurrentActor actor);
TemporaryPasswordResult resetPassword(long id, CurrentActor actor);
void revokeSessions(long id, CurrentActor actor);
void updateRolePermissions(RoleCode role, Set<PermissionCode> permissions, CurrentActor actor);
~~~

Application service hash password, validate status/last-admin rules, revoke Redis sessions as needed and audit. No mail service in MVP.

### PersonnelApplicationService

~~~java
Page<EmployeeDto> search(EmployeeFilter filter, CurrentActor actor);
EmployeeDto create(CreateEmployeeCommand command, CurrentActor actor);
EmployeeDto update(long id, UpdateEmployeeCommand command, CurrentActor actor);
void deactivate(long id, CurrentActor actor);
DepartmentDto saveDepartment(DepartmentCommand command, CurrentActor actor);
PositionDto savePosition(PositionCommand command, CurrentActor actor);
~~~

Deactivation atomically marks employee inactive and linked account disabled; records audit. Re-activation requires explicit ADMIN/HR_MANAGER action and does not silently restore old roles.

### Procurement/Import/PDF

- PurchaseOrderApplicationService.preview/issue/update/cancel loads aggregate, checks permissions, reserve sequence, stores snapshot/revision and audit.
- PricingApplicationService.search/history/latest filters category/currency and excludes undated records from latest.
- LegacyWorkbookImportService parses, stages, previews and commits mapping; OperationalImportService produces editable draft lines.
- PdfService renders immutable snapshot; FileStorageService writes outside DB and records checksum/metadata.
- CatalogApplicationService manages masters/aliases; referenced records deactivate instead of hard delete.

## Repository ports/adapters

Ports: UserAccountRepository, RoleRepository, PermissionRepository, EmployeeRepository, DepartmentRepository, PositionRepository, SupplierRepository, MaterialRepository, HistoricalPurchaseRepository, PurchaseOrderRepository, DailySequenceRepository, ImportBatchRepository, GeneratedFileRepository, AuditLogRepository.

Spring Data JPA interfaces/entities live only in context infrastructure.persistence. Query methods must be paginated and select the projection needed; avoid N+1 and findAll on large history.

## Main DTOs

LoginRequest; CurrentUserDto (no password/hash); Create/UpdateUserRequest; EmployeeForm; RolePermissionDto; PurchaseOrderDraft; PurchaseOrderPreview; PurchaseOrderRevisionDto; ImportPreview/ImportIssue/ImportReport; PriceSearchRow.

