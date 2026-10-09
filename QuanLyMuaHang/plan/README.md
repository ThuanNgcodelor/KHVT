# Plan hệ thống Quản lý mua hàng KHVT

## Mục tiêu

Thay Google Apps Script/Google Sheet bằng hệ thống mua hàng có dữ liệu MySQL, backend Java 21/Spring Boot REST, frontend React/Vite/Tailwind. Giữ chức năng đã dùng trong Apps Script/workbook; chỉ chuyển login, phân quyền và danh bạ nhân sự cơ bản từ backend demo, không chuyển full HR và không kết nối hệ thống demo.

## Các quyết định đã chốt

- DDD thực dụng modular monolith, backend là nơi enforce nghiệp vụ/permission.
- Browser auth dùng Spring Security + Spring Session Redis, cookie HttpOnly + CSRF; không dùng JWT localStorage.
- Roles mặc định: ADMIN được seed permission wildcard `*`, HR_MANAGER, PLANNER, VIEWER. Kiểm tra quyền dùng permission hiệu lực, không bypass từ tên role; quản trị CRUD role/permission vẫn là phạm vi cần bổ sung.
- React cùng origin Spring Boot; base API cấu hình tập trung tại frontend/src/config/baseApi.ts.
- Compose chỉ có MySQL và Redis. Java chạy native/systemd; cloudflared chạy native system service và trỏ tới app.
- PO PDF có subtotal/VAT/grand total; dòng quantity text không tính vào số tiền.
- Workbook migration có preview/report/idempotency; xử lý đúng các thiếu dữ liệu đã kiểm kê.
- Không có OpenAI API feature trong MVP. Dùng [skill UI riêng của KHVT](../skills/khvt-ui/SKILL.md), ảnh tham chiếu và checklist; skill repository này không phải skill chính thức của OpenAI.
- Giao diện xanh rêu/xanh ngọc, không dùng màu cam theo yêu cầu mới. Login/đổi mật khẩu tạm dẫn tới cổng chọn ứng dụng theo quyền backend; admin cấp quyền qua role hiện có. [Thiết kế cổng ứng dụng](../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md) tách rõ triển khai hiện tại và hướng grant độc lập/Bán hàng.

## Stack

- Java 21, Spring Boot REST, Spring Security, Spring Session Data Redis.
- JPA + Flyway + MySQL; Redis session/cache.
- React + TypeScript + Vite + Tailwind.
- Apache POI, Commons CSV, PDFBox; FreeMarker + OpenHTMLToPDF cho PDF PO.
- JUnit/Mockito/Spring Boot Test; Testcontainers hoặc môi trường test cô lập MySQL/Redis.
- Playwright cho browser E2E.
- Docker Compose duy nhất cho MySQL/Redis; Cloudflare Tunnel trước ứng dụng host.

## Cách đọc và triển khai

1. 00 — scope, quyết định, workbook inventory.
2. 01 — DDD, bounded contexts, package boundary.
3. 02–03 — schema/migration và business rules.
4. 04 — REST contracts/use cases/repository ports.
5. 05, 12 — UI + Playwright.
6. 06 — workbook/request imports, exports, PDF.
7. 07–08 — security, operations, Docker/Cloudflare.
8. 09–10 — test/acceptance và phase triển khai.
9. 11 — hướng dẫn vibecoding.
10. 13–14 — Identity/Personnel model và code mapping từ demo.

## Danh sách tài liệu

- [00-scope-and-assumptions.md](00-scope-and-assumptions.md)
- [01-architecture-and-packages.md](01-architecture-and-packages.md)
- [02-database-and-migration.md](02-database-and-migration.md)
- [03-business-rules.md](03-business-rules.md)
- [04-controllers-services-and-repositories.md](04-controllers-services-and-repositories.md)
- [05-react-vite-tailwind-ui.md](05-react-vite-tailwind-ui.md)
- [06-import-export-and-pdf.md](06-import-export-and-pdf.md)
- [07-configuration-security-and-operations.md](07-configuration-security-and-operations.md)
- [08-docker-cloudflare.md](08-docker-cloudflare.md)
- [09-testing-and-acceptance.md](09-testing-and-acceptance.md)
- [10-implementation-phases.md](10-implementation-phases.md)
- [11-vibecoding-guide.md](11-vibecoding-guide.md)
- [12-react-vite-playwright-openai.md](12-react-vite-playwright-openai.md)
- [13-ddd-identity-personnel.md](13-ddd-identity-personnel.md)
- [14-demo-auth-personnel-port.md](14-demo-auth-personnel-port.md)

## Current source status

Cập nhật 2026-10-10: `source/` có code auth/personnel/catalog/pricing/procurement/import/export/dashboard và registry ứng dụng theo quyền. Compose chỉ chạy MySQL/Redis ở localhost 3307/6380. Backend `mvn package` bằng Maven 3.9.11/JDK 21 thành công lúc 00:29:59 +07:00, 32 test/0 failure/error/skipped; log `source/target/runtime/backend-verified-build.log`. Có 31 unit/mock/H2 tests và một test Redis thật opt-in với namespace UUID/phiên tổng hợp. Test Redis xác nhận Boot indexed repository và service giữ/thu hồi phiên theo principal; chưa xác nhận HTTP hai phiên, last-admin hoặc CRUD MySQL. Backend/frontend đã khởi động lại, MySQL/Redis healthy, backend/proxy health `UP`, frontend HTTP 200. Một lần login thật bằng thông tin bootstrap nạp riêng nhận 401, đã dừng không retry/đổi mật khẩu; chưa xác định thông tin đăng nhập hiện hành. Smoke MySQL/Redis ngày 2026-10-09 là lịch sử, không xác nhận auth thật mới.

Frontend có login/đổi mật khẩu lần đầu/session/CSRF, dashboard API, cổng chọn ứng dụng, UI nhân sự/tài khoản và layout theo pages/hooks/components. Giao diện đã đổi palette và thêm footer bản quyền; build, 7 Vitest tests và 42 trường hợp Playwright riêng biệt trên API giả lập đã qua sau sửa/chạy lại (20 auth/cổng, 22 nhân sự/tài khoản; desktop/mobile). Đã kiểm tra ảnh và sửa footer/bố cục account mobile; các kết quả này chưa nghiệm thu CRUD MySQL/Redis. UI danh mục/giá/PO/import còn thiếu; chưa có grant module độc lập, xin/duyệt quyền hay Bán hàng. Backend demo đã xóa. Cấu trúc backend chưa đồng nhất hoàn toàn theo DDD trong plan. Xem [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md) và [AGENTS.md](../AGENTS.md) để biết bằng chứng và phần còn thiếu; không đánh dấu acceptance từ việc đã có code.

## Global Definition of Done

- Login/roles/personnel, mua hàng, search, migration, PDF, audit, backup/restore đạt acceptance ở 09.
- Workbook được import/reconcile không mất giao dịch/snapshot.
- ADMIN và các role giới hạn đúng trên backend.
- Docker Compose chỉ chạy hai dependency MySQL/Redis trong project riêng.
- API/frontend chạy ngoài Docker trên host qua một origin; Cloudflare Tunnel không route database.
- Playwright smoke/full E2E đạt; deploy/rollback runbook rõ ràng.

