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
    catalog/                       vật tư/NCC: pages, forms, hooks, API, types
    pricing/                       lịch sử/giá gần nhất, lọc và XLSX
    procurement/                   danh sách/giỏ/lập/sửa/chi tiết PO và tài liệu
    imports/                       preview yêu cầu, dữ liệu dán, workbook cũ
  services/
    apiClient.ts                   cookies, CSRF và lỗi HTTP tập trung
    dashboardApi.ts
  config/baseApi.ts                base URL duy nhất
  types/api.ts                     kiểu dữ liệu API
tests/e2e/auth.spec.ts              auth/cổng ứng dụng bằng API fixtures
tests/e2e/personnel.spec.ts         nhân sự bằng API fixtures
tests/e2e/accounts.spec.ts          tài khoản bằng API fixtures
tests/e2e/purchasing.spec.ts        danh mục/giá/PO/import bằng API fixtures
```

Page là điểm vào của một URL; component dựng giao diện; hook chứa logic trạng thái/đồng bộ; service gọi HTTP. Không cần tạo các tầng trống cho tính năng chưa làm.

`useEffect` hiện dùng cho đăng ký/hủy listener và đồng bộ trạng thái phiên/menu. Việc tải dữ liệu có cache, trạng thái tải/lỗi và hủy request nằm trong TanStack Query. `useMemo` chưa cần ở các phép lọc menu nhỏ; chỉ thêm khi có phép tính đáng kể và lý do đo được. React tự quản lý Virtual DOM/reconciliation và cập nhật DOM thật qua `react-dom`; ứng dụng không viết lớp Virtual DOM riêng.

## Chạy giao diện cùng backend trên Windows

Từ thư mục gốc `QuanLyMuaHang`, mở Docker Desktop riêng, chuẩn bị `source/.env` rồi chạy:

```powershell
.\CHAY_KHVT.cmd start
```

[CHAY_KHVT.cmd](../../CHAY_KHVT.cmd) gọi `npm ci`/`npm run build`, rồi Maven profile `windows-web` đóng `frontend/dist` vào JAR. Một tiến trình Java phục vụ giao diện và API tại **http://localhost:8080**; các URL giao diện đã biết hỗ trợ reload trực tiếp. Frontend dùng `/api` cùng origin, không cần chạy Vite 5173 cho bản đóng gói. Launcher mặc định bỏ qua test khi đóng gói; kết quả kiểm tra riêng ở phần dưới và [hệ thống hiện tại](../../docs/HE_THONG_HIEN_TAI.md).

`CHAY_KHVT.cmd status`/`stop` quản lý đúng tiến trình launcher ghi nhận; `start -NoBuild` dùng JAR đã đóng gói, không cập nhật source. Hướng dẫn Docker, lựa chọn cổng/heap, profile HTTPS và chuyển máy ở [Chạy Windows và Cloudflare](../../docs/CHAY_WINDOWS_VA_CLOUDFLARE.md).

Ngày **2026-10-10**, launcher đã chạy thật `npm ci`, frontend build/package JAR và Java heap 256 MB thành công; UI/deep link/HTTP auth local đã kiểm tra, MCP render login đã qua. `me` trả 401 khi chưa đăng nhập là trạng thái dự kiến. `start` lần hai không tạo thêm tiến trình, `status`/`stop` đã qua. Lượt `start -NoBuild -ExternalDocker` chạy lại lúc **21:19 +07:00** qua, health `UP`, probe HTTP sau restart lúc **21:19:50** qua 15 kiểm tra; lượt đầu trước đó gặp MySQL EOF lúc container tạo lại. Kiểm tra backend có phạm vi 20 test (11 auth, 3 SPA, 6 proxy production với Tomcat HTTP thật/header tổng hợp) đã qua; không phải toàn bộ bộ test hoặc nghiệm thu MySQL/Cloudflare. Xem [bằng chứng hiện hành](../../docs/HE_THONG_HIEN_TAI.md).

Khi dùng domain HTTPS, đặt `APP_FRONTEND_URL` là origin HTTPS thực tế và chạy `CHAY_KHVT.cmd start -Profile prod`; cookie Secure cần kiểm tra qua HTTPS. Cloudflare Tunnel sau này trỏ `http://127.0.0.1:8080`. Launcher chưa tạo Tunnel/domain hoặc đăng ký Windows Service.

## Chạy Vite khi phát triển

Để chỉnh frontend với HMR, dùng `tools/start-local.ps1` từ root để chạy backend 8080/Vite 5173, hoặc chạy backend bằng VS Code theo [README backend](../README.md) rồi mở terminal frontend riêng:

```powershell
cd source/frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Script dev đã chỉnh preset/heap nhưng chưa kiểm tra lại startup/HMR ở lượt launcher Windows mới; runtime dev lúc 11:30 ngày 2026-10-10 là bằng chứng lịch sử. Bản đóng gói 8080 cần build lại khi đổi frontend, không dùng HMR.

Mở `http://localhost:5173` hoặc `http://127.0.0.1:5173`. Vite proxy `/api` và `/actuator` về backend với `changeOrigin: false`, giữ Host để request cùng origin không bị coi thành request CORS khác origin. Khi frontend gọi API khác origin trực tiếp, cấu hình `APP_FRONTEND_URL` tương ứng. Đăng nhập bằng **email** tài khoản được cấp; email miền nội bộ được chấp nhận và backend kiểm tra tài khoản. Admin bootstrap có mật khẩu tạm được chuyển tới trang đổi mật khẩu, sau đó vào cổng `/modules`. Giữ mật khẩu riêng trong `.env`, không đưa lên chat. Bootstrap chỉ tạo tài khoản khi chưa tồn tại; đổi mật khẩu đăng nhập rồi thì mật khẩu trong `.env` không tự ghi đè lại.

API dùng cookie session HttpOnly, không lưu token đăng nhập trong localStorage. Mutation gửi CSRF; trước login luôn lấy CSRF mới để tránh cache cũ khi tab khác thay cookie, sau login xóa token cũ để lấy lại cho mutation kế tiếp. `CSRF_INVALID` là lỗi mã bảo vệ phiên; `FORBIDDEN` là thiếu quyền nghiệp vụ. Response 403 không có JSON tại Login hướng dẫn tải lại trang, không gán thành thiếu quyền tài khoản. Khi hết phiên, frontend xóa cache dữ liệu nghiệp vụ và chuyển về login. Cổng lấy `modules` từ backend, menu/route/thao tác dùng `permissions` hiệu lực; wildcard `*` mở quyền, không kiểm tra bypass chỉ bằng tên ADMIN. Backend vẫn kiểm tra quyền từng API.

## Phạm vi hiện có

- Đăng nhập, đổi mật khẩu lần đầu, khôi phục phiên, đăng xuất.
- Cổng chọn ứng dụng theo quyền sau đăng nhập: Mua hàng, Nhân sự, Quản trị. Nút Đổi ứng dụng để trở về cổng; không có quyền thì hiển thị thông báo liên hệ admin.
- Layout dùng chung, menu riêng theo ứng dụng, keyboard focus, menu mobile. Palette xanh rêu/xanh ngọc; footer © Bản quyền thuộc về KHVT | Cung cấp bởi [ThuanNgcodelor](https://github.com/ThuanNgcodelor).
- Dashboard Mua hàng lấy dữ liệu thật, có loading/error/retry/empty; không fallback demo. HR không có quyền Mua hàng thì không được vào dashboard và không gửi request tổng quan mua hàng.
- Nhân sự: danh sách/tìm kiếm/lọc trạng thái/phân trang, tạo/sửa hồ sơ, ngừng/kích hoạt; tạo/sửa phòng ban và chức vụ.
- Tài khoản: danh sách/phân trang, tạo/sửa, liên kết nhân viên hoạt động, gán role và trạng thái, reset mật khẩu tạm. Admin cấp Mua hàng qua PLANNER/VIEWER, Nhân sự qua HR_MANAGER; một người có thể có nhiều role. Thay quyền/trạng thái/reset thu hồi phiên theo backend.
- API role trả `permissions` và `moduleCodes` suy ra theo registry để giải thích ứng dụng của từng vai trò. Form lưu `roleCodes`; không gửi grant hoặc moduleCodes. Danh sách ứng dụng của phiên được backend tính lại từ hợp permission.
- Danh mục vật tư/NCC: tìm mã/tên, lọc trạng thái/phân loại, phân trang từ backend, thêm/sửa và xác nhận ngừng/kích hoạt. Danh sách gợi ý đang hoạt động dùng riêng khi lập đơn.
- Giá: lịch sử theo từ khóa/mã/tên, loại tiền và phân loại; xem giá gần nhất đúng mã hoặc tên chính xác và cùng loại tiền; chuyển sang lập đơn và tải XLSX theo bộ lọc. Dữ liệu legacy hiển thị cơ sở giả định VND.
- PO: danh sách/bộ lọc/phân trang, giỏ dòng hàng thủ công hoặc từ danh mục/giá/import, số lượng số hoặc chữ, chọn NCC từng dòng, kiểm tra nhóm NCC rồi lưu từng bản nháp. Sửa có lý do/tăng revision, hủy có xác nhận, phát hành PDF qua POST riêng, tải PDF hiện tại/phiên bản cũ và XLSX.
- Import: file yêu cầu Excel/CSV/PDF text hoặc dán dữ liệu → preview → chọn dòng → lập đơn; workbook cũ → preview số dòng/cảnh báo/checksum → xác nhận đã đối chiếu và database thử/backup → commit. Preview không tự tạo PO hoặc lịch sử giá. PDF scan chưa có OCR.
- Chưa có CRUD role/permission, grant module độc lập, luồng xin/duyệt quyền hay Bán hàng. Xem [mô hình phân quyền và mở rộng](../../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md).

Giỏ chưa lưu chỉ nằm trong bộ nhớ trang, reload có thể mất; bản nháp đã lưu nằm trong danh sách PO. Mỗi đơn tối đa 200 dòng. Khi mất kết nối sau request lưu, kiểm tra danh sách trước khi gửi lại vì backend chưa có idempotency key. Xem [đối chiếu với Index.html/Mã.js](../../docs/DOI_CHIEU_UNG_DUNG_CU.md).

Hồ sơ nhân viên và tài khoản đăng nhập là hai đối tượng riêng. Ngừng nhân viên vô hiệu hóa tài khoản liên kết; kích hoạt nhân viên không tự khôi phục tài khoản. Khi cần mở lại, admin kiểm tra trạng thái tài khoản riêng. Bộ lọc hiện là mã/tên và trạng thái nhân viên; chưa có bộ lọc phòng ban hay search tài khoản vì API danh sách tài khoản hiện chỉ phân trang.

## Kiểm tra

```powershell
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Lượt sửa đăng nhập ngày **2026-10-10 11:27–11:31 +07:00**: build TypeScript/Vite, 10 Vitest tests và 22 Playwright auth desktop/mobile qua, một worker và **API giả lập**. Regression kiểm tra token cache cũ được làm mới trước login, 403 plain text hiện hướng dẫn phiên, form giữ email/xóa password/không gửi lặp và CSRF khác lỗi quyền. Log build/browser: `frontend-login-types.log`, `frontend-login-build.log`, `frontend-login-browser.log` trong `../target/runtime/`. Probe HTTP thật với thông tin tổng hợp đã xác nhận cả hai origin qua proxy tới bước xác thực, thiếu CSRF vẫn bị chặn; chưa xác nhận tài khoản thật của người dùng.

Lượt UI mua hàng trước đó ngày 2026-10-10: build `tsc -b`/Vite và 7 Vitest tests đã qua. Playwright một worker chạy 58 trường hợp: 56 qua lượt đầu, hai selector NCC đã sửa/chạy lại qua; sau chỉnh breadcrumb, 16 trường hợp mua hàng chạy lại đều qua. Có **58 trường hợp browser riêng biệt đã qua tại lượt đó**, gồm 20 auth/cổng, 22 nhân sự/tài khoản, 16 danh mục/giá/PO/import trên desktop/mobile; không cộng lượt chạy lại. Log `frontend-purchasing-final-build.log`, `frontend-purchasing-unit.log`, `frontend-purchasing-browser.log`, `frontend-purchasing-browser-retry.log`, `frontend-purchasing-final-browser.log` trong `../target/runtime/`. Bundle hiện hơn 500 kB nên Vite có cảnh báo kích thước; build không thất bại, chưa tối ưu tách route thành chunk.

Test kiểm tra auth/quyền/cổng, CRUD nhân sự/tài khoản, lỗi/giữ form/phân trang/xác nhận, quyền đọc/ghi danh mục và giá, nhóm NCC tạo PO riêng/số lượng chữ, phát hành PDF tách khỏi tải, operational preview chuyển giỏ, workbook ack/commit, bố cục mobile. Unit regression kiểm tra HTTP 200 body rỗng. Đã xem ảnh `ui-po-form-*` desktop/mobile; bảng giỏ cuộn ngang trong vùng bảng, không làm trang tràn viewport. Ảnh chỉ dùng dữ liệu tổng hợp.

[MCP trình duyệt](../../tools/browser-mcp/README.md) đã chạy initialize/tools/list/browser navigation và luồng mua hàng qua server stdio thật, với API fixture tổng hợp. Đây là kiểm tra MCP/UI khác với driver HTTP/MySQL/Redis.

Playwright dùng server riêng cổng 5190 và **API giả lập**, không xác nhận MySQL/Redis hay nghiệp vụ thật đã được nghiệm thu. Fixture dùng dữ liệu/tài khoản tổng hợp; kiểm tra cả đăng nhập bằng email miền nội bộ. Báo cáo/trace ở `test-results/` đã được ignore; không ghi mật khẩu thật vào test hoặc trace. Test với backend/MySQL/Redis thật phải được thực hiện riêng trên dữ liệu thử và ghi rõ phạm vi.

Runtime chính kiểm tra lại lúc **2026-10-10 11:30 +07:00**: backend 8080 và frontend 5173 đã chạy, MySQL/Redis healthy. Docker/WSL từng crash trong các lượt trước; chưa xác định nguyên nhân. Driver HTTP đã chạy một phần trên schema MySQL/Redis thử riêng (không dùng tài khoản người dùng), chưa hoàn tất browser/workbook trên MySQL. Workbook thật đã preview/commit trên H2 riêng với báo cáo số dòng, không coi đó là MySQL/Flyway/Redis. Xem [bằng chứng hiện hành](../../docs/HE_THONG_HIEN_TAI.md) và [hướng dẫn test local](../../tools/local-test/README.md).

Lịch sử trước UI mua hàng ngày 2026-10-10: backend/frontend health từng UP; một lần thử login bằng cấu hình bootstrap của database ứng dụng nhận 401 và đã dừng, không đổi mật khẩu. Không suy ra người dùng đã đổi mật khẩu hoặc reset tài khoản đó để chạy test. Các log cũ không chứng minh runtime còn hoạt động.

Ngày 2026-10-09 đã kiểm tra riêng qua browser với backend/MySQL/Redis local thật: đăng nhập admin bootstrap, chuyển tới trang đổi mật khẩu, reload vẫn giữ phiên, truy cập dashboard bị chuyển lại trang đổi mật khẩu, rồi logout thành công. Không đổi mật khẩu admin trong smoke test; chưa kiểm tra luồng ghi nghiệp vụ thật hay hoàn tất đổi mật khẩu bằng tài khoản này. Đây là bằng chứng lịch sử trước thay đổi indexed session, không xác nhận auth thật của runtime mới. Backend đã kiểm tra service thu hồi phiên bằng một test Redis thật riêng; xem [README backend](../README.md) để biết phạm vi và cách bật.

Đọc [skill KHVT UI](../../skills/khvt-ui/SKILL.md) khi sửa frontend, kèm [plan 05](../../plan/05-react-vite-tailwind-ui.md) và [plan 12](../../plan/12-react-vite-playwright-openai.md). Skill được tạo riêng trong repository theo yêu cầu người dùng và được AGENTS.md tham chiếu; không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục.
