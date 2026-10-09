# DDD Identity, phân quyền và quản lý nhân sự

Phạm vi này tái sử dụng nghiệp vụ login/quản trị user và danh bạ nhân viên cần thiết từ backend demo. Không chuyển hệ thống HR/payroll đầy đủ. Chi tiết nguồn được ánh xạ trong 14-demo-auth-personnel-port.md.

## 1. Domain model

### Identity & Access

- UserAccount aggregate: email, displayName, PasswordHash, status, employeeId nullable, mustChangePassword, failedLoginCount, lock expiry, timestamps/version.
- Role: role code/name/status.
- Permission: mã permission; role-permission và user-role many-to-many.
- CurrentActor: user id, linked employee id, effective permissions, request id.
- Spring Session Redis lưu phiên; session id opaque chỉ ở HttpOnly cookie.
- AuthorizationService.can(actor, permission) chấp nhận wildcard * khi ADMIN được cấp; không controller bypass.

### Personnel

- Employee aggregate: employeeCode, fullName, email, phone, departmentId, positionId, managerId nullable, status, joinedAt/leftAt, version.
- Department: code/name/parentId/active.
- Position: code/name/active.
- Employee không chứa password, account roles, payroll, bank details, attendance/contract documents.
- UserAccount giữ employeeId; hai context trao đổi ID/command/event, không dùng entity context khác.

## 2. Role và permission mặc định

| Permission | ADMIN | HR_MANAGER | PLANNER | VIEWER |
|---|:---:|:---:|:---:|:---:|
| * (wildcard) | ✓ |  |  |  |
| USER_READ / USER_MANAGE | ✓ |  |  |  |
| ROLE_MANAGE | ✓ |  |  |  |
| PERSONNEL_READ / PERSONNEL_MANAGE | ✓ | ✓ |  |  |
| CATALOG_READ / CATALOG_MANAGE | ✓ |  | ✓ |  |
| IMPORT_LEGACY | ✓ |  |  |  |
| IMPORT_OPERATIONAL | ✓ |  | ✓ |  |
| PRICE_READ / PO_READ | ✓ |  | ✓ | ✓ |
| PO_CREATE / PO_EDIT / PO_CANCEL | ✓ |  | ✓ |  |
| AUDIT_READ | ✓ |  |  |  |

PLANNER được import nghiệp vụ và quản lý workflow mua hàng; import legacy workbook chỉ ADMIN. Mọi role assignment đổi đều audit. ADMIN có thể tạo custom role và quản lý permission matrix nhưng không thể cấp wildcard cho role khác, xóa wildcard khỏi ADMIN cuối hoặc xóa cứng role hệ thống nền.

VIEWER là role read-only cho nhân viên được phép truy cập; không có role EMPLOYEE bị cấm login. Login thành công khi account ACTIVE, employee liên kết nếu có ACTIVE, và account có role phù hợp.

## 3. Invariants

1. Email canonical lowercase/trim unique; employeeCode unique.
2. Một employee tối đa một account; account admin bootstrap có thể employeeId null.
3. Account LOCKED/DISABLED hoặc linked employee INACTIVE không login; đổi status phải revoke Redis sessions.
4. Không tự gán role từ request thiếu quyền; chỉ use case có ROLE_MANAGE.
5. Không xóa cứng account/employee/role đã được tham chiếu.
6. Không disable/xóa ADMIN cuối; không tự khóa tài khoản đang dùng nếu khiến hệ thống mất Admin.
7. Reset password invalidates sessions và bật mustChangePassword.
8. Password/hash/session/CSRF secret không có trong audit/log/API response.
9. Thay đổi user, role/permission, employee/department/position ghi audit before/after.
10. Deactivate employee + disable account liên kết là một application use case nhất quán.

## 4. Application use cases

- Login, current user, logout, change own password.
- Search/list/create/update/disable account; assign roles; reset temporary password; revoke sessions.
- Search/create/update/deactivate employee.
- CRUD department/position; không cho deactivate nếu đang được dùng mà chưa chọn phương án thay thế.
- Tạo custom role, đổi tên/active và quản lý permission assignments; role code là value object/string do DB quản lý, không đóng cứng thành Java enum.
- Link/unlink user to employee với unique check.
- ADMIN bootstrap một lần, seed roles/permissions idempotent.
- Không có public signup/approval, Google OAuth, OTP email hoặc password reset email ở MVP.

Password reset flow: ADMIN thực hiện -> system tạo password tạm khó đoán -> hiển thị đúng một lần -> employee login -> bắt đổi ngay -> password cũ và sessions revoke. Không gửi mật khẩu qua log/chat/email tự động.

## 5. HTTP contract

- GET /api/auth/csrf, POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout, POST /api/auth/change-password.
- GET/POST/PATCH /api/admin/users; PUT /api/admin/users/{id}/roles; POST /api/admin/users/{id}/reset-password; POST /api/admin/users/{id}/revoke-sessions.
- GET/POST/PATCH /api/admin/roles; PUT /api/admin/roles/{code}/permissions; GET /api/admin/permissions.
- GET/POST/PUT /api/personnel/employees; POST /api/personnel/employees/{id}/deactivate.
- GET/POST/PUT /api/personnel/departments và /positions.
- Controller check authorization tổng quát; Application service kiểm tra permission cụ thể lần cuối.

## 6. Luồng bảo mật

~~~text
Request -> CSRF/session filter -> CurrentActorResolver
 -> controller validation -> application permission check
 -> domain invariant -> repository transaction
 -> audit -> response DTO
~~~

Frontend chỉ nhận roles/permissions cần dựng menu. Same-origin cookie auth + CSRF; không bearer token lưu trong browser. Redis session namespace riêng; admin role/status/password changes revoke all affected sessions.

## 7. Seed và vận hành

- Bootstrap đầu qua command/secret environment; không có default password trong source/migration.
- Chỉ tạo ADMIN khi chưa có ADMIN; nếu biến secret thiếu thì dừng bootstrap với hướng dẫn an toàn.
- Force đổi password đầu tiên; roles/permissions seed idempotent.
- Không dùng danh sách người thật/password mẫu của backend demo.
- Backup identity/personnel/audit cùng MySQL; Redis session có thể mất mà không mất account.
- Có break-glass runbook để khôi phục Admin khi khóa nhầm; dùng offline command có audit, không mở public backdoor.

## 8. Acceptance

- ADMIN login, có wildcard, quản lý account, employee, role/permission và nghiệp vụ.
- HR_MANAGER CRUD personnel nhưng không thể gọi USER_MANAGE/ROLE_MANAGE API.
- PLANNER làm nghiệp vụ mua hàng nhưng không vào personnel admin.
- VIEWER chỉ đọc; UI ẩn controls và backend trả 403 cho mutation.
- Disable employee/account, reset password, role change thu hồi session.
- ADMIN cuối được bảo vệ; mọi thay đổi audit đúng.

