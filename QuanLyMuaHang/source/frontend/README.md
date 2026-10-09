# Frontend — Quản lý mua hàng KHVT

React + TypeScript + Vite, tổ chức theo tính năng. Router quản lý trang, TanStack Query quản lý dữ liệu API, React Hook Form + Zod xử lý form và kiểm tra đầu vào.

## Cấu trúc

```text
src/
  main.tsx                         mount React vào DOM
  App.tsx                          providers và routes
  app/
    AppShell.tsx                   ghép Header/Sidebar/nội dung/Footer
    navigation.ts                  cấu hình menu và vai trò mặc định
    hooks/useSidebar.ts            trạng thái menu, Escape và cleanup listener
  components/
    PageState.tsx                  loading/error/forbidden/chưa triển khai
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
  services/
    apiClient.ts                   cookies, CSRF và lỗi HTTP tập trung
    dashboardApi.ts
  config/baseApi.ts                base URL duy nhất
  types/api.ts                     kiểu dữ liệu API
tests/e2e/auth.spec.ts              kiểm thử browser bằng API fixtures
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

Mở `http://127.0.0.1:5173`. Vite proxy `/api` và `/actuator` về backend. Đăng nhập bằng tài khoản được cấp; admin bootstrap có mật khẩu tạm sẽ được chuyển tới trang đổi mật khẩu. Giữ mật khẩu riêng trong `.env`, không đưa lên chat.

API dùng cookie session HttpOnly, không lưu token đăng nhập trong localStorage. Mutation gửi CSRF; sau login lấy lại CSRF. Khi hết phiên, frontend xóa cache dữ liệu nghiệp vụ và chuyển về login. Menu/route phản ánh vai trò mặc định; backend vẫn là nơi kiểm tra quyền.

## Phạm vi hiện có

- Đăng nhập, đổi mật khẩu lần đầu, khôi phục phiên, đăng xuất.
- Layout dùng chung, menu theo vai trò, keyboard focus, menu mobile.
- Dashboard lấy dữ liệu thật, có loading/error/retry/empty; không fallback demo. HR chỉ có lời chào và đường dẫn nhân sự, không gọi API dashboard mua hàng.
- Trang nghiệp vụ PO/giá/import/nhân sự/tài khoản hiện báo đang triển khai; chưa có CRUD UI.

## Kiểm tra

```powershell
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Đã chạy build thành công, 6 Vitest tests và 16 Playwright lượt chạy thành công (8 tình huống trên Chromium desktop và viewport mobile). Playwright dùng server riêng cổng 5190 và **API giả lập**, không xác nhận MySQL/Redis hay nghiệp vụ thật đã được nghiệm thu. Fixture dùng dữ liệu/tài khoản tổng hợp. Báo cáo/trace ở `test-results/` đã được ignore; không ghi mật khẩu thật vào test hoặc trace.

Hướng dẫn UI ở [plan 05](../../plan/05-react-vite-tailwind-ui.md) và [plan 12](../../plan/12-react-vite-playwright-openai.md). Chưa có skill frontend chuyên biệt được tạo/cài.
