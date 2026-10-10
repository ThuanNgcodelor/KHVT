# React, Vite, Tailwind và giao diện quản trị

## Công nghệ và nguyên tắc

- React + TypeScript + Vite; Tailwind CSS build local.
- React Router cho route; TanStack Query cho server state; React Hook Form + Zod cho forms.
- API đi qua apiClient; base URL duy nhất là frontend/src/config/baseApi.ts, dùng VITE_API_BASE_URL. Component không hard-code host/URL.
- Production cùng origin với Spring Boot; request dùng cookie và credentials include. Dev dùng Vite proxy để giữ hành vi gần production.
- UI không có OpenAI SDK/API key. Áp dụng [skill KHVT UI](../skills/khvt-ui/SKILL.md), tạo riêng trong repository theo yêu cầu người dùng; không gọi là skill chính thức của OpenAI. Ảnh tham chiếu hỗ trợ quyết định bố cục; palette hiện tại theo yêu cầu bỏ màu cam.

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
│   ├── portal (ModulesPage, cổng chọn ứng dụng)
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
- /modules: trang sau login/đổi mật khẩu; chỉ hiển thị các module backend cấp trong thông tin phiên. Không có quyền thì thông báo liên hệ admin. Có đường quay lại cổng để đổi ứng dụng từ khu vực làm việc.
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

Các route trên là phạm vi thiết kế. Code hiện có auth/cổng/dashboard/nhân sự/tài khoản, danh mục vật tư/NCC, giá, PO/giỏ/PDF/XLSX và import preview/commit. CRUD role/permission/audit UI chưa có. Danh mục/giá/PO phân trang và lọc từ backend; nhân viên hiện lọc mã/tên và trạng thái, tài khoản hiện phân trang nhưng chưa có API search/filter. Không dựng control rồi lọc riêng một trang để giả là đã hỗ trợ tìm kiếm toàn bộ. Bằng chứng hiện hành tại `docs/HE_THONG_HIEN_TAI.md`, đối chiếu ứng dụng cũ tại `docs/DOI_CHIEU_UNG_DUNG_CU.md`; có UI không tự đánh dấu nghiệm thu.

## Thiết kế kiểu hệ thống quản lý

Theo lựa chọn mới của người dùng: header/sidebar xanh rêu `#173f35`, thao tác chính xanh ngọc `#176b57`, nền trắng ngà `#f4f6f3`, chữ than `#23352e`; không dùng màu cam. Typography Segoe UI/system font hỗ trợ tiếng Việt, bảng rõ số liệu, viền nhẹ và góc bo nhỏ. Bỏ slogan/gradient/glow và trend giả; ưu tiên nghiệp vụ hằng ngày.

- Desktop: sidebar gọn + vùng nội dung; màn mua hàng có thể dùng tab tương tự giao diện cũ.
- Mobile: sidebar thu gọn, bảng chuyển thành card hoặc cuộn ngang có kiểm soát.
- Header có tên hệ thống, breadcrumb, user menu và logout.
- Cổng ứng dụng có ô biểu tượng/tên/mô tả, lấy danh sách thực tế từ backend. Tham khảo cách chọn ứng dụng trong ảnh người dùng, dùng nhãn KHVT và SVG thống nhất. Bán hàng chỉ thêm khi module/route/quyền thực sự được triển khai.
- Footer auth/cổng/khu vực làm việc: © Bản quyền thuộc về KHVT | Cung cấp bởi [ThuanNgcodelor](https://github.com/ThuanNgcodelor).
- Button nguy hiểm (disable/cancel/revoke) phải xác nhận; lưu xong có feedback.
- Bảng user/employee có search, filter status/department/role, pagination và sort.
- Role matrix có hàng permission, cột role, khóa wildcard ADMIN và cảnh báo trước khi lưu.
- Form có label, help/error text, keyboard navigation, focus states, aria labels.
- Loading/skeleton, empty, error/retry và unauthorized state cho mọi page.
- Không render raw HTML từ workbook; React escaping mặc định, không dùng dangerouslySetInnerHTML với dữ liệu ngoài.

## Auth state và quyền UI

- App bootstrap gọi GET /api/auth/me; 401 chuyển /login, 403 hiện trang không đủ quyền.
- Session cookie HttpOnly; JavaScript không đọc session id. API client bật credentials.
- Thông tin phiên trả roles/permissions/modules/mustChangePassword; không chứa hash/password. Danh sách module được backend tính từ quyền hiệu lực, không suy ra tại frontend chỉ từ tên vai trò.
- API đọc roles có permissions/moduleCodes do registry tính cho từng role; dùng để mô tả phạm vi vai trò, không coi là grant độc lập. Form tài khoản gửi roleCodes. Wildcard `*` trong permission hiệu lực mở quyền; tên ADMIN riêng lẻ không bypass guard.
- PermissionGuard chỉ ẩn/hiện menu/button để UX. Server luôn authorize.
- Khi logout, invalidate session server-side rồi xóa cache query/state nhạy cảm.
- Admin cấp ứng dụng qua vai trò ở Tài khoản; quyền vào ứng dụng và quyền đọc/ghi bên trong được kiểm tra riêng. Chưa có grant module riêng hoặc xin/duyệt quyền. Xem [mô hình hiện có và hướng mở rộng](../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md).
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

