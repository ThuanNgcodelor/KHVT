# React/Vite, Playwright và hướng dẫn UI liên quan OpenAI

## Tách ba việc riêng

- React/Vite/Tailwind là công nghệ giao diện sản phẩm.
- Playwright là công cụ browser E2E.
- OpenAI API không phải dependency giao diện; không gọi model để thay logic nghiệp vụ.
- Dự án có [skill KHVT UI](../skills/khvt-ui/SKILL.md), được tạo riêng trong repository theo yêu cầu người dùng và được AGENTS.md tham chiếu. Đây không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục.
- Đọc skill trước khi code UI, giữ contract/bảo mật; dùng palette xanh rêu/xanh ngọc theo yêu cầu mới, cổng chọn ứng dụng theo ảnh tham khảo và checklist ở 05.

## Frontend config/API

- frontend/src/config/baseApi.ts là điểm cấu hình duy nhất.
- Local mặc định có thể giữ /api qua Vite proxy; production same-origin /api.
- frontend/src/config/api.ts chỉ alias tương thích.
- apiClient có credentials include, CSRF, trace/request id và xử lý lỗi chung.
- Không lưu session cookie/token trong localStorage; cookie HttpOnly do Spring sở hữu.

## Playwright setup

Trong frontend:

~~~bash
npm install -D @playwright/test
npx playwright install chromium
~~~

playwright.config.ts:

- Cấu hình hiện tại dùng baseURL http://127.0.0.1:5190 và webServer Vite dev riêng có strictPort; không chiếm server người dùng cổng 5173.
- reuseExistingServer chỉ local khi an toàn; CI dùng server process riêng.
- Trace khi fail chỉ cho fixtures tổng hợp. Smoke dùng tài khoản/mật khẩu thật phải tắt trace/video và tránh log request nhạy cảm.
- Chromium là project bắt buộc; browser khác chạy theo nhu cầu.
- Bộ browser tests API giả lập hiện có chỉ xác nhận frontend. Test với backend API và MySQL/Redis thật cần profile/stack cô lập, khởi động riêng và báo cáo riêng.
- Không tái sử dụng session/database production.

## E2E bắt buộc

### Auth và quản trị

- Không login -> route protected chuyển login.
- Login đúng/hoàn tất đổi mật khẩu tạm -> cổng ứng dụng; reload vẫn có phiên, mở đúng module; logout phiên không dùng lại được.
- Sai password generic, không lộ user existence.
- CSRF thiếu thì mutation bị từ chối; request hợp lệ có CSRF pass.
- ADMIN tạo employee/account, gán PLANNER, reset password; user đổi mật khẩu tạm.
- HR_MANAGER quản lý employee nhưng API user/role trả 403.
- Employee inactive hoặc account disabled bị logout/không tạo session.
- UI permission guard và backend authorization cùng đúng.
- Cổng chỉ hiển thị modules server trả về; người không có quyền vào route trực tiếp bị chặn và API trả 403. ADMIN/PLANNER/VIEWER/HR thấy đúng ứng dụng; đọc không có quyền ghi. Không có module thì hiện hướng dẫn liên hệ admin.
- Tạo/sửa/ngừng/kích hoạt nhân viên, phòng ban/chức vụ; lỗi API giữ form để sửa. Ngừng nhân viên ảnh hưởng tài khoản liên kết, kích hoạt nhân viên không tự mở lại tài khoản.
- Tạo/sửa tài khoản, liên kết nhân viên hoạt động, gán role/trạng thái và reset mật khẩu tạm; có xác nhận tác động phiên. Kiểm tra danh sách sau lưu/phân trang và lỗi lookup.

### Mua hàng/tra cứu/import

- Search không dấu, latest price và currency.
- Nhập dòng bằng paste/XLSX; text quantity được giữ.
- Hai NCC -> preview hai PO; issue -> tải PDF.
- PDF có subtotal/VAT/total; text qty được exclude và ghi chú.
- Sửa PO tăng revision; file cũ vẫn tải được.
- Legacy workbook preview warning -> commit -> report; cùng checksum không nhân đôi.
- PDF scan hiện fallback, không báo thành công giả.

## Fixture và privacy

- Dùng workbook/CSV/PDF fixture synthetic hoặc đã mask; không cho giá/NCC/employee data thật vào repo test.
- Seed user/password test chỉ trong profile test, deterministic và không dùng production config.
- API fixtures đặt lại trạng thái theo từng test; test tích hợp reset dữ liệu/phiên theo phạm vi database thử riêng, không reset database local đang dùng để giải quyết lỗi test.
- Assert bằng locator/accessibility roles; không sleep cố định.
- Không gọi OpenAI thật; không yêu cầu API key.

## Prompt giao việc UI

~~~text
Đọc skills/khvt-ui/SKILL.md, plan/05-react-vite-tailwind-ui.md và contract API liên quan. Tạo page React accessible/responsive theo palette xanh rêu/xanh ngọc, bảng nghiệp vụ rõ ràng. Login xong vào cổng chọn module từ quyền backend; không dựng module hoặc control chưa có API. Dùng baseApi.ts/apiClient và TanStack Query; hỗ trợ loading/empty/error/forbidden. Không dùng session localStorage hoặc raw workbook HTML. Thêm Playwright cho luồng thay đổi, permission, lỗi API và mobile; báo rõ API giả lập hay MySQL/Redis thật.
~~~

