# Kế hoạch chuyển login và quản lý nhân sự từ backend demo

## Mục tiêu và ranh giới

Backend demo là nguồn tham khảo hành vi, không phải codebase được copy nguyên khối. Chỉ chuyển login, quản trị tài khoản, role/permission và danh bạ nhân viên/phòng ban/chức vụ cần để cấp login. Không chuyển các nghiệp vụ đặt xe/phòng, payroll, chấm công, HR documents, onboarding, OCR, Telegram, notifications, push, Google OAuth/OTP hoặc database của hệ thống demo.

Không truy cập database hiện hữu của demo và không import credentials/seed records. Quyền truy cập hệ thống mới được cấp riêng từ đầu.

## Mapping code demo -> bounded context mới

| Demo component | Phần được tham khảo | Cách áp dụng vào source mới |
|---|---|---|
| AuthController | login/logout/current-user/password endpoints | Tạo AuthController trong identity.interfaces; bỏ endpoint Google/register/OTP |
| AuthService | normalize email, verify password/status, session lifecycle | Chuyển use case sang IdentityApplicationService; không kéo mail/notification/event dependency |
| JwtUtils, JwtAuthFilter, SecurityConfig | rule xác thực, route protection, role/status checks | Không copy JWT filter. Dùng Spring Security + Spring Session Redis, HttpOnly cookie, CSRF |
| User entity/UserRepository | email, full name, role/status, department/job title | Map vào UserAccount aggregate + persistence adapter; role/permission many-to-many, password hash riêng |
| UserController/UserAdminService | list/create/update/disable/reset, revoke sessions | Tạo UserAdminController/use cases có permission/audit; bỏ registration approvals/approvers nếu không cần mua hàng |
| RoleEnum/UserStatus | khái niệm role và trạng thái | Role mới ADMIN/HR_MANAGER/PLANNER/VIEWER; status ACTIVE/LOCKED/DISABLED |
| Department entity/controller/repository | danh mục phòng ban | Map sang Personnel Department; bổ sung Position; không dùng JPA entity demo |
| EmployeeDataSeeder/DataSeeder | chỉ giúp nhận biết dữ liệu seed không an toàn | Không chép tên/email/mật khẩu mặc định hoặc dữ liệu sample sang source mới |
| Demo HR modules/migrations | chỉ dùng để xác định phạm vi rộng cần loại trừ | Không mang schema/migrations payroll/chấm công/hợp đồng/tài liệu/OCR/Telegram/attendance sync |

## Authentication design được chọn cho dự án mới

Demo hiện trả JWT access/refresh token và Redis lưu refresh-token state. Vì hệ thống mới là React SPA cùng origin với Spring Boot, dùng Spring Session Data Redis + Spring Security session cookie đơn giản và dễ revoke hơn tự duy trì refresh-token protocol.

- Login email/password; BCrypt password hash.
- Cookie session id opaque, HttpOnly, Secure production, SameSite; không lưu trong localStorage.
- CSRF bật cho mutation; frontend lấy CSRF token theo Spring Security SPA pattern.
- Redis session TTL và namespace riêng của QuanLyMuaHang.
- Logout, reset password, đổi role, khóa account, deactivate employee revoke session.
- Tài khoản tạo bởi ADMIN, không public registration/Google/OTP/email recovery ở MVP.
- First account bootstrap an toàn bằng one-time environment/CLI secret; bắt buộc đổi password.
- Lockout/rate limit, generic login error, audit login success/failure mà không log credentials.

## Account và personnel workflow

1. ADMIN hoặc HR_MANAGER tạo employee record tối thiểu: mã, tên, email, phòng ban, chức vụ, trạng thái.
2. ADMIN tạo/liên kết account và cấp role; account không được tạo ngầm khi chỉ tạo hồ sơ.
3. Hệ thống hiển thị password tạm một lần/force change; không gửi qua log hoặc audit.
4. Người dùng đăng nhập, đổi password; mọi request dùng CurrentActor/permissions.
5. Nhân viên nghỉ việc: deactivate employee -> disable account liên kết -> revoke session -> audit.
6. Khi nhân viên quay lại, ADMIN/HR_MANAGER re-activate employee; ADMIN quyết định mở lại account và roles.

HR_MANAGER quản lý hồ sơ nhân viên/phòng ban/chức vụ nhưng không quản lý user account, role hoặc permission mặc định. ADMIN toàn quyền.

## Role/status translation

- Không tự map MANAGER cũ thành HR_MANAGER hay PLANNER; ý nghĩa cũ phụ thuộc hệ thống demo.
- Không giữ EMPLOYEE bị cấm login như demo. Nhân viên có account và role phù hợp được login; VIEWER là role read-only mặc định có thể gán.
- Không chuyển PENDING_APPROVAL/REJECTED vì bỏ public registration; trạng thái mới ACTIVE/LOCKED/DISABLED.
- Mọi account và role trong hệ thống mới được tạo/gán có chủ đích; không migrate old password hash.

## Implementation checklist

- [ ] Đọc source class liên quan, không đọc/import external application secrets.
- [ ] Thêm Spring Security, Spring Session Redis, password encoder, CSRF, session revocation.
- [ ] Tạo UserAccount/Role/Permission domain, repositories, migrations, seed roles.
- [ ] Tạo Employee/Department/Position CRUD, account link, status sync.
- [ ] Tạo ADMIN user/role screens và personnel screens.
- [ ] Thêm tests cho login/status/CSRF/permissions/revoke/last-admin.
- [ ] Kiểm tra không còn default account/secret, Google/OTP endpoints, demo HR table.
- [ ] Soát log/audit để password/token không bị ghi.
- [ ] Không thay đổi bất kỳ database, Docker project hoặc dữ liệu của demo/hệ thống khác.

