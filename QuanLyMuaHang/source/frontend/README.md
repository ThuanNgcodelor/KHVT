# Frontend — Quản lý mua hàng KHVT

React + TypeScript + Vite, tổ chức theo tính năng. Router quản lý trang, TanStack Query quản lý dữ liệu API, React Hook Form + Zod xử lý form và kiểm tra đầu vào.

## Cấu trúc

```text
src/
  main.tsx                         mount React vào DOM
  App.tsx                          providers và routes
  app/
    AppShell.tsx                   ghép Header/Sidebar/nội dung/Footer
    navigation.ts                  cấu hình menu theo ứng dụng/quyền
    hooks/useSidebar.ts            trạng thái menu, Escape và cleanup listener
  components/
    PageState.tsx                  loading/error/forbidden/chưa triển khai
    PageHeader.tsx                 tiêu đề/mô tả/thao tác của trang
    Dialog.tsx                     form và xác nhận, focus/keyboard
    Field.tsx                      label/lỗi form
    Pagination.tsx                 phân trang API
    TableFeedback.tsx              trạng thái bảng
    Icon.tsx                       SVG thống nhất
    layout/
      Brand.tsx
      Header.tsx
      Navigation.tsx               navbar/menu điều hướng
      Sidebar.tsx
      Footer.tsx
  features/
    auth/
      pages/LoginPage.tsx
      pages/ChangePasswordPage.tsx
      components/AuthScreen.tsx    giao diện form dùng chung
      hooks/useAuth.ts             đọc auth context
      hooks/useAuthSession.ts      tải phiên, cập nhật cache, xử lý hết phiên
      hooks/useAuthForm.ts         validation và gửi form
      hooks/useLogout.ts           trạng thái đăng xuất
      AuthContext.ts
      AuthProvider.tsx
      authApi.ts
      types.ts
    dashboard/
      pages/DashboardPage.tsx      hiển thị tổng quan từ API
      hooks/useDashboard.ts        query và điều kiện tải dữ liệu
    portal/
      pages/ModulesPage.tsx        cổng chọn ứng dụng từ danh sách backend
    personnel/
      pages/EmployeesPage.tsx      danh sách/tìm kiếm/lọc/phân trang nhân viên
      pages/UnitsPage.tsx          phòng ban và chức vụ
      components/                 form, xác nhận trạng thái, tabs
      hooks/usePersonnel.ts       query và mutation nhân sự
      personnelApi.ts             API nhân viên/phòng ban/chức vụ
      types.ts
    identity-admin/
      pages/UsersPage.tsx          tài khoản và phân quyền qua role
      components/                 form, chọn nhân viên, reset mật khẩu
      hooks/useUsers.ts           query và mutation quản trị tài khoản
      userAdminApi.ts
      types.ts
  services/
    apiClient.ts                   cookies, CSRF và lỗi HTTP tập trung
    dashboardApi.ts
  config/baseApi.ts                base URL duy nhất
  types/api.ts                     kiểu dữ liệu API
tests/e2e/auth.spec.ts              auth/cổng ứng dụng bằng API fixtures
tests/e2e/personnel.spec.ts         nhân sự bằng API fixtures
tests/e2e/accounts.spec.ts          tài khoản bằng API fixtures
```

Page là điểm vào của một URL; component dựng giao diện; hook chứa logic trạng thái/đồng bộ; service gọi HTTP. Không cần tạo các tầng trống cho tính năng chưa làm.

`useEffect` hiện dùng cho đăng ký/hủy listener và đồng bộ trạng thái phiên/menu. Việc tải dữ liệu có cache, trạng thái tải/lỗi và hủy request nằm trong TanStack Query. `useMemo` chưa cần ở các phép lọc menu nhỏ; chỉ thêm khi có phép tính đáng kể và lý do đo được. React tự quản lý Virtual DOM/reconciliation và cập nhật DOM thật qua `react-dom`; ứng dụng không viết lớp Virtual DOM riêng.

## Chạy local

Khởi chạy backend tại `localhost:8080` theo [README backend](../README.md), rồi mở terminal khác:

```powershell
cd source/frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Mở `http://localhost:5173`, khớp `APP_FRONTEND_URL` mặc định của backend. Nếu dùng origin khác, cấu hình `APP_FRONTEND_URL` tương ứng. Vite proxy `/api` và `/actuator` về backend. Đăng nhập bằng tài khoản được cấp; email miền nội bộ được chấp nhận và backend kiểm tra tài khoản. Admin bootstrap có mật khẩu tạm được chuyển tới trang đổi mật khẩu, sau đó vào cổng `/modules`. Giữ mật khẩu riêng trong `.env`, không đưa lên chat. Bootstrap chỉ tạo tài khoản khi chưa tồn tại; đổi mật khẩu đăng nhập rồi thì mật khẩu trong `.env` không tự ghi đè lại.

API dùng cookie session HttpOnly, không lưu token đăng nhập trong localStorage. Mutation gửi CSRF; sau login lấy lại CSRF. Khi hết phiên, frontend xóa cache dữ liệu nghiệp vụ và chuyển về login. Cổng lấy `modules` từ backend, menu/route/thao tác dùng `permissions` hiệu lực; wildcard `*` mở quyền, không kiểm tra bypass chỉ bằng tên ADMIN. Backend vẫn kiểm tra quyền từng API.

## Phạm vi hiện có

- Đăng nhập, đổi mật khẩu lần đầu, khôi phục phiên, đăng xuất.
- Cổng chọn ứng dụng theo quyền sau đăng nhập: Mua hàng, Nhân sự, Quản trị. Nút Đổi ứng dụng để trở về cổng; không có quyền thì hiển thị thông báo liên hệ admin.
- Layout dùng chung, menu riêng theo ứng dụng, keyboard focus, menu mobile. Palette xanh rêu/xanh ngọc; footer © Bản quyền thuộc về KHVT | Cung cấp bởi [ThuanNgcodelor](https://github.com/ThuanNgcodelor).
- Dashboard Mua hàng lấy dữ liệu thật, có loading/error/retry/empty; không fallback demo. HR không có quyền Mua hàng thì không được vào dashboard và không gửi request tổng quan mua hàng.
- Nhân sự: danh sách/tìm kiếm/lọc trạng thái/phân trang, tạo/sửa hồ sơ, ngừng/kích hoạt; tạo/sửa phòng ban và chức vụ.
- Tài khoản: danh sách/phân trang, tạo/sửa, liên kết nhân viên hoạt động, gán role và trạng thái, reset mật khẩu tạm. Admin cấp Mua hàng qua PLANNER/VIEWER, Nhân sự qua HR_MANAGER; một người có thể có nhiều role. Thay quyền/trạng thái/reset thu hồi phiên theo backend.
- API role trả `permissions` và `moduleCodes` suy ra theo registry để giải thích ứng dụng của từng vai trò. Form lưu `roleCodes`; không gửi grant hoặc moduleCodes. Danh sách ứng dụng của phiên được backend tính lại từ hợp permission.
- Trang PO/giá/import hiện báo đang triển khai, chưa có CRUD UI. Chưa có CRUD role/permission, grant module độc lập, luồng xin/duyệt quyền hay Bán hàng. Xem [mô hình phân quyền và mở rộng](../../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md).

Hồ sơ nhân viên và tài khoản đăng nhập là hai đối tượng riêng. Ngừng nhân viên vô hiệu hóa tài khoản liên kết; kích hoạt nhân viên không tự khôi phục tài khoản. Khi cần mở lại, admin kiểm tra trạng thái tài khoản riêng. Bộ lọc hiện là mã/tên và trạng thái nhân viên; chưa có bộ lọc phòng ban hay search tài khoản vì API danh sách tài khoản hiện chỉ phân trang.

## Kiểm tra

```powershell
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Ngày 2026-10-10, build `tsc -b`/Vite cuối cùng và 7 Vitest tests đã qua. Auth/cổng ứng dụng có 10 tình huống trên hai viewport (20 trường hợp); nhân sự/tài khoản có 11 tình huống trên hai viewport (22 trường hợp). Lượt auth đầu qua 18/20; 2 lỗi khoảng trắng footer đã sửa và chạy lại qua. Đã xem ảnh desktop/mobile, sửa panel tài khoản bị co hẹp trên mobile rồi chạy lại 2 trường hợp tạo tài khoản qua; sau chỉnh heading/checkbox, 2 trường hợp bố cục cổng/nhân sự chạy lại cũng qua. Có **42 trường hợp browser riêng biệt đã qua** sau các lượt sửa/chạy lại; không cộng lượt chạy lại vào số trường hợp. Lịch sử ngày 2026-10-09 có 6 Vitest tests và 16 lượt Playwright qua, không dùng số cũ để xác nhận phần mới.

Test mới kiểm tra auth/quyền/cổng ứng dụng, thêm/sửa/ngừng/kích hoạt nhân viên, phòng ban/chức vụ, thêm/sửa/reset tài khoản, giữ form khi lỗi API, phân trang, xác nhận và bàn phím/mobile. Unit regression kiểm tra API trả HTTP 200 với body rỗng khi reset mật khẩu. Log local ở `../target/runtime/frontend-build.log`, `frontend-unit.log`, `frontend-auth-e2e-new.log`, `frontend-auth-e2e-retry.log`, `frontend-crud-e2e.log`, `frontend-account-layout-retry.log` và `frontend-personnel-layout-retry.log`. Ảnh `ui-*` ở cùng thư mục dùng dữ liệu tổng hợp.

Playwright dùng server riêng cổng 5190 và **API giả lập**, không xác nhận MySQL/Redis hay nghiệp vụ thật đã được nghiệm thu. Fixture dùng dữ liệu/tài khoản tổng hợp; kiểm tra cả đăng nhập bằng email miền nội bộ. Báo cáo/trace ở `test-results/` đã được ignore; không ghi mật khẩu thật vào test hoặc trace. Test với backend/MySQL/Redis thật phải được thực hiện riêng trên dữ liệu thử và ghi rõ phạm vi.

Ngày 2026-10-10 đã khởi động lại frontend/backend; frontend HTTP 200, backend và Vite proxy health `UP`, MySQL/Redis healthy. Một lần thử login thật bằng thông tin bootstrap nạp riêng nhận HTTP 401, đã dừng không retry/đổi mật khẩu. Chưa xác định thông tin đăng nhập hiện hành; không báo auth thật mới đã qua hoặc suy ra người dùng đã đổi mật khẩu. CRUD browser vẫn dùng API giả lập.

Ngày 2026-10-09 đã kiểm tra riêng qua browser với backend/MySQL/Redis local thật: đăng nhập admin bootstrap, chuyển tới trang đổi mật khẩu, reload vẫn giữ phiên, truy cập dashboard bị chuyển lại trang đổi mật khẩu, rồi logout thành công. Không đổi mật khẩu admin trong smoke test; chưa kiểm tra luồng ghi nghiệp vụ thật hay hoàn tất đổi mật khẩu bằng tài khoản này. Đây là bằng chứng lịch sử trước thay đổi indexed session, không xác nhận auth thật của runtime mới. Backend đã kiểm tra service thu hồi phiên bằng một test Redis thật riêng; xem [README backend](../README.md) để biết phạm vi và cách bật.

Đọc [skill KHVT UI](../../skills/khvt-ui/SKILL.md) khi sửa frontend, kèm [plan 05](../../plan/05-react-vite-tailwind-ui.md) và [plan 12](../../plan/12-react-vite-playwright-openai.md). Skill được tạo riêng trong repository theo yêu cầu người dùng và được AGENTS.md tham chiếu; không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục.
