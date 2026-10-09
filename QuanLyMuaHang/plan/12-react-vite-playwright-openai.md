# React/Vite, Playwright và hướng dẫn UI liên quan OpenAI

## Tách ba việc riêng

- React/Vite/Tailwind là công nghệ giao diện sản phẩm.
- Playwright là công cụ browser E2E.
- OpenAI API không phải dependency giao diện; không gọi model để thay logic nghiệp vụ.
- Người dùng muốn dùng skill OpenAI frontend/UI, nhưng trong skill catalog của workspace hiện không có skill chuyên thiết kế frontend. Không được ghi rằng đã dùng skill nếu chưa có file hướng dẫn. Giao diện MVP được thiết kế theo screenshot hệ thống mua hàng đã cung cấp và checklist ở 05.
- Nếu người dùng/maintainer đặt skill UI cụ thể trong Agent/ hoặc cung cấp path, agent triển khai phải đọc toàn bộ SKILL.md trước khi code UI, đồng thời giữ contract và bảo mật của plan này.

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

- baseURL http://127.0.0.1:4173.
- webServer chạy Vite preview host localhost.
- reuseExistingServer chỉ local khi an toàn; CI dùng server process riêng.
- trace/video/screenshot khi fail.
- Chromium là project bắt buộc; browser khác chạy theo nhu cầu.
- Backend API và MySQL/Redis test profile được khởi động ngoài webServer hoặc qua script test cô lập.
- Không tái sử dụng session/database production.

## E2E bắt buộc

### Auth và quản trị

- Không login -> route protected chuyển login.
- Login đúng -> dashboard; reload vẫn có session cookie; logout session không dùng lại được.
- Sai password generic, không lộ user existence.
- CSRF thiếu thì mutation bị từ chối; request hợp lệ có CSRF pass.
- ADMIN tạo employee/account, gán PLANNER, reset password; user đổi mật khẩu tạm.
- HR_MANAGER quản lý employee nhưng API user/role trả 403.
- Employee inactive hoặc account disabled bị logout/không tạo session.
- UI permission guard và backend authorization cùng đúng.

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
- Mỗi test reset database/session theo scope riêng.
- Assert bằng locator/accessibility roles; không sleep cố định.
- Không gọi OpenAI thật; không yêu cầu API key.

## Prompt giao việc UI

~~~text
Đọc 05-react-vite-tailwind-ui.md và contract API liên quan. Tạo page React accessible, responsive, theo màu navy/blue/orange của ảnh tham chiếu. Dùng baseApi.ts/apiClient, TanStack Query cho server state; hỗ trợ loading/empty/error/forbidden. Không dùng session localStorage, không render raw workbook HTML. Thêm Playwright test cho permission, lỗi API và viewport mobile. Nếu có frontend skill được cung cấp thì đọc skill trước và báo skill/path đã dùng.
~~~

