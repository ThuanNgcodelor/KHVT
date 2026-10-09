# React, Vite, Tailwind và giao diện quản trị

## Công nghệ và nguyên tắc

- React + TypeScript + Vite; Tailwind CSS build local.
- React Router cho route; TanStack Query cho server state; React Hook Form + Zod cho forms.
- API đi qua apiClient; base URL duy nhất là frontend/src/config/baseApi.ts, dùng VITE_API_BASE_URL. Component không hard-code host/URL.
- Production cùng origin với Spring Boot; request dùng cookie và credentials include. Dev dùng Vite proxy để giữ hành vi gần production.
- UI không có OpenAI SDK/API key. Skill UI chuyên biệt chưa có trong workspace hiện tại; dùng ảnh giao diện mua hàng người dùng cung cấp làm reference, không giả vờ đã áp dụng skill không tồn tại.

## Cấu trúc frontend đề xuất

~~~text
frontend/src
├── app (App, router, queryClient, auth bootstrap)
├── config (baseApi.ts, api.ts)
├── components
│   ├── AppShell, Sidebar, Header, Breadcrumbs
│   ├── DataTable, Pagination, FilterBar
│   ├── FormField, ConfirmDialog, Toast, ErrorState
│   └── PermissionGuard
├── features
│   ├── auth (LoginPage, ChangePasswordPage, authApi)
│   ├── dashboard
│   ├── procurement (draft, PO list/detail/edit)
│   ├── price-search
│   ├── imports (legacy migration, request/quotation)
│   ├── catalog (materials, suppliers)
│   ├── personnel (employees, departments, positions)
│   └── identity-admin (users, roles, permissions)
├── services (apiClient + typed endpoints)
├── types
└── tests/e2e
~~~

Mỗi feature sở hữu page, hook, service, schema validation và tests liên quan; tránh App.tsx chứa toàn bộ UI/nghiệp vụ.

## Điều hướng và trang

- /login: email, password, validation, show/hide password, trạng thái lỗi chung.
- /change-password: bắt buộc khi user mới dùng password tạm.
- /dashboard: KPI PO, vật tư, NCC, PO gần đây.
- /purchase-orders/new: import/dán/thêm dòng, autocomplete, draft cart, preview group NCC, issue và tải PDF.
- /purchase-orders: tìm kiếm/lọc/trang danh sách.
- /purchase-orders/:id: snapshot, lịch sử revision, edit/cancel/download PDF.
- /price-search: search không dấu, filter category/currency, giá gần nhất và lịch sử.
- /imports: legacy workbook preview/commit/report; operational import tách tab/mode.
- /catalog/materials và /catalog/suppliers: master data/alias.
- /admin/employees, /admin/departments, /admin/positions: danh bạ nhân sự.
- /admin/users: account, trạng thái, employee link, roles, reset password, revoke sessions.
- /admin/roles: ma trận permission; chỉ ADMIN.
- /admin/audit: tra cứu audit; không có chức năng sửa/xóa.

## Thiết kế kiểu hệ thống quản lý

Bám ảnh tham khảo: thanh điều hướng/header navy, nền xám sáng, bề mặt trắng, blue cho điều hướng, orange cho thao tác chính, typography tiếng Việt rõ. Ưu tiên bảng nghiệp vụ, số liệu dễ so sánh và trạng thái hiển thị nhất quán; không biến thành landing page.

- Desktop: sidebar gọn + vùng nội dung; màn mua hàng có thể dùng tab tương tự giao diện cũ.
- Mobile: sidebar thu gọn, bảng chuyển thành card hoặc cuộn ngang có kiểm soát.
- Header có tên hệ thống, breadcrumb, user menu và logout.
- Button nguy hiểm (disable/cancel/revoke) phải xác nhận; lưu xong có feedback.
- Bảng user/employee có search, filter status/department/role, pagination và sort.
- Role matrix có hàng permission, cột role, khóa wildcard ADMIN và cảnh báo trước khi lưu.
- Form có label, help/error text, keyboard navigation, focus states, aria labels.
- Loading/skeleton, empty, error/retry và unauthorized state cho mọi page.
- Không render raw HTML từ workbook; React escaping mặc định, không dùng dangerouslySetInnerHTML với dữ liệu ngoài.

## Auth state và quyền UI

- App bootstrap gọi GET /api/auth/me; 401 chuyển /login, 403 hiện trang không đủ quyền.
- Session cookie HttpOnly; JavaScript không đọc session id. API client bật credentials.
- CurrentUserDto chứa roles/permissions/mustChangePassword; không chứa hash/password.
- PermissionGuard chỉ ẩn/hiện menu/button để UX. Server luôn authorize.
- Khi logout, invalidate session server-side rồi xóa cache query/state nhạy cảm.
- Mọi API mutation gửi CSRF token theo Spring Security config; không tắt CSRF vì dùng cookie.

## Hợp đồng với API

- Dùng services typed như authApi, personnelApi, userAdminApi, purchaseOrderApi, importApi.
- apiClient thêm request id/CSRF, decode wrapper chung, xử lý 401/403/409.
- Chỉ src/config/baseApi.ts được quyết định API root; config/api.ts giữ alias tương thích nếu cần.
- UI không tính tổng PO làm nguồn đúng; preview/tổng từ backend.

## Kiểm thử UI

- Unit/component test cho form validation, role guard và parser UI nhỏ nếu có.
- Playwright test login, đổi mật khẩu đầu, user/employee admin, cấm route theo role, import preview, tạo/sửa PO.
- Test responsive tối thiểu viewport desktop và mobile.

