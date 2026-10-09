# Kiến trúc DDD và cấu trúc source

## Kiến trúc tổng thể

Dùng modular monolith: một ứng dụng Spring Boot, một MySQL, một Redis và một React SPA. Chia backend theo bounded context; không tách microservice khi chưa có nhu cầu vận hành độc lập. Domain giữ nghiệp vụ; controller chỉ là REST adapter.

~~~text
React/Vite same-origin browser
  -> REST Controller / DTO
    -> Application use case (@Transactional, authorization)
      -> Domain aggregate / domain service
        -> Repository port
          -> JPA adapter / MySQL
Session filter -> Redis (Spring Session)
PDF/import adapters -> filesystem riêng + MySQL metadata
~~~

Không để controller gọi repository trực tiếp. Domain không phụ thuộc Spring, JPA, HTTP, Redis hay React. Entity JPA không trả ra API. Mỗi command kiểm tra permission ở application layer và ghi audit khi thay đổi dữ liệu.

## Bounded context

| Context | Aggregate/model | Trách nhiệm |
|---|---|---|
| Identity & Access | UserAccount, Role, Permission | Login, session, mật khẩu, quản trị tài khoản, cấp quyền |
| Personnel | Employee, Department, Position | Danh bạ nhân viên/phòng ban/chức vụ, trạng thái, liên kết user |
| Catalog | Material, Supplier, aliases | Danh mục vật tư/NCC, chuẩn hóa và trạng thái |
| Pricing | HistoricalPurchase / PriceObservation | Nhập lịch sử và truy vấn giá gần nhất theo currency |
| Procurement | PurchaseOrder, PurchaseOrderLine | Giỏ hàng, PO theo NCC/currency, revision và trạng thái |
| Import | ImportBatch, ImportRow | Staging, preview, validate, commit, báo cáo lỗi |
| Documents | GeneratedDocument | PDF/XLSX, file storage, retry và tải xuống |
| Audit | AuditEntry | Actor, action, đối tượng, before/after, request ID |

Identity không nhúng entity JPA của Personnel; liên kết bằng employeeId. Procurement giữ supplierId/materialId và snapshot tên/địa chỉ/mã tại thời điểm phát hành.

## Cấu trúc package đề xuất

~~~text
com.example.quanlymuahang
├── sharedkernel
│   ├── domain (Money, Currency, Quantity, NormalizedText, Clock)
│   └── application (CurrentActor, PageQuery, Result)
├── identity
│   ├── domain (UserAccount, Role, RoleCode value object, Permission, ports)
│   ├── application (login, logout, change password, admin users/roles)
│   ├── infrastructure (JPA, Spring Security, Spring Session Redis)
│   └── interfaces (AuthController, UserAdminController, DTO)
├── personnel
│   ├── domain (Employee, Department, Position, repository ports)
│   ├── application (create/update/list/deactivate/link account)
│   ├── infrastructure (JPA adapters/mappers)
│   └── interfaces (EmployeeController, DepartmentController)
├── catalog
├── pricing
├── procurement
├── importdata
├── documents
├── audit
└── config
~~~

Mỗi context tự sở hữu domain/application/infrastructure/interfaces. Có thể đặt package thực tế sâu hơn hoặc nông hơn để hợp với starter hiện có; phải giữ dependency direction và không gom logic vào package service/repository dùng chung.

## Quy tắc DDD thực dụng

1. Aggregate bảo vệ invariant: PurchaseOrder quản lý trạng thái, revision, currency và lines; UserAccount quản lý status/password policy; Employee quản lý profile/status.
2. Dùng value object bất biến cho Money, Currency, Quantity, PoNumber, NormalizedText ở nơi có quy tắc.
3. Application service điều phối actor, permission, transaction, aggregate, repository port, audit và DTO.
4. Repository interface ở domain/application port; Spring Data JPA adapter ở infrastructure.
5. Controller chỉ parse/validate HTTP request, gọi use case, map lỗi/status code.
6. Domain event chỉ dùng khi cần tách side effect; audit thay đổi quan trọng phải cùng transaction hoặc dùng outbox nếu side effect bất đồng bộ.
7. Không dùng JPA entity giữa các bounded context; chỉ trao đổi ID, command/query DTO hoặc domain event.
8. Dùng optimistic locking/version cho employee và PO edit; dùng DB unique/row lock cho PO sequence.

## Luồng mẫu

### Login

AuthController -> LoginUseCase -> AccountRepository + PasswordEncoder -> kiểm tra account/employee active -> tạo Spring Security principal -> Spring Session lưu ở Redis -> trả CurrentUserDto. Cookie chỉ chứa session id opaque, không chứa profile/quyền.

### Cấp account cho nhân viên

Admin tạo/tra Employee -> CreateAccountForEmployeeUseCase kiểm tra email/account unique -> gán role khởi tạo -> tạo mật khẩu tạm -> force đổi mật khẩu -> ghi audit. Employee không chứa password/role.

### Lập và xuất PO

PurchaseOrderController -> Preview/IssuePurchaseOrdersUseCase -> validate dòng -> group theo supplier/currency -> reserve daily sequence -> lưu aggregate/revision/audit trong transaction -> render PDF từ snapshot sau commit -> lưu file/metadata. Nếu render thất bại, dữ liệu PO còn nguyên và trạng thái file cho phép retry.

### Di trú workbook

LegacyWorkbookImportController -> tạo batch/checksum -> Apache POI adapter đọc sheet -> staging/validation -> preview/report -> admin xác nhận -> commit theo batch/chunk có idempotency -> audit và báo cáo cuối.

## Transaction và lỗi

- Tạo/sửa PO, sequence và audit trong transaction DB; sinh file sau commit.
- Một PO trong nhóm NCC lỗi không được báo đã phát hành; trả kết quả theo từng group.
- Import hỗ trợ preview, commit theo batch/chunk và resume an toàn; giữ raw source row.
- Disable employee/account, gán role/reset password và revoke session phải có audit.
- Lỗi API dùng mã ổn định: VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, CONFLICT, IMPORT_ROW_ERROR, FILE_GENERATION_FAILED.
- File upload/download không nằm trong transaction DB; dùng temporary file + atomic rename.

## Frontend mapping

~~~text
frontend/src
├── app (router, queryClient, auth bootstrap)
├── config (baseApi.ts: URL duy nhất)
├── features
│   ├── auth
│   ├── dashboard
│   ├── procurement
│   ├── price-search
│   ├── imports
│   ├── catalog
│   ├── personnel
│   └── identity-admin
├── components (AppShell, DataTable, Form, Dialog, PermissionGuard)
├── services (apiClient + typed endpoints)
├── types
└── tests/e2e
~~~

React guard chỉ điều khiển UX. Backend luôn là nguồn sự thật cho authorization.

