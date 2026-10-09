# Plan hệ thống Quản lý mua hàng KHVT

## Mục tiêu

Thay Google Apps Script/Google Sheet bằng hệ thống mua hàng có dữ liệu MySQL, backend Java 21/Spring Boot REST, frontend React/Vite/Tailwind. Giữ chức năng đã dùng trong Apps Script/workbook; chỉ chuyển login, phân quyền và danh bạ nhân sự cơ bản từ backend demo, không chuyển full HR và không kết nối hệ thống demo.

## Các quyết định đã chốt

- DDD thực dụng modular monolith, backend là nơi enforce nghiệp vụ/permission.
- Browser auth dùng Spring Security + Spring Session Redis, cookie HttpOnly + CSRF; không dùng JWT localStorage.
- Roles mặc định: ADMIN wildcard, HR_MANAGER, PLANNER, VIEWER; Admin quản lý user/role/personnel toàn quyền.
- React cùng origin Spring Boot; base API cấu hình tập trung tại frontend/src/config/baseApi.ts.
- Compose chỉ có MySQL và Redis. Java chạy native/systemd; cloudflared chạy native system service và trỏ tới app.
- PO PDF có subtotal/VAT/grand total; dòng quantity text không tính vào số tiền.
- Workbook migration có preview/report/idempotency; xử lý đúng các thiếu dữ liệu đã kiểm kê.
- Không có OpenAI API feature trong MVP. Skill UI chuyên biệt không có sẵn trong workspace; giao diện theo ảnh tham chiếu và checklist thiết kế.

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

Kiểm kê 2026-10-09: `source/` đã có code auth/personnel/catalog/pricing/procurement/import/export/dashboard. Compose hiện chỉ chạy MySQL và Redis với port localhost 3307/6380. Đã có log build thành công và 11 test qua, cùng kiểm tra khởi động/CSRF/admin login/logout trong phiên làm việc; đây chưa phải nghiệm thu đầy đủ các nghiệp vụ.

Frontend React/Vite đã có giao diện khung, còn dùng dữ liệu demo và chưa nối đầy đủ API/auth. Cấu trúc backend chưa đồng nhất hoàn toàn theo thiết kế DDD trong plan. Xem [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md) và [AGENTS.md](../AGENTS.md) để biết code thực tế, bằng chứng kiểm thử và phần còn thiếu. Các checklist trong plan cần được xác minh theo acceptance, không tự đánh dấu hoàn thành từ việc đã có code.

## Global Definition of Done

- Login/roles/personnel, mua hàng, search, migration, PDF, audit, backup/restore đạt acceptance ở 09.
- Workbook được import/reconcile không mất giao dịch/snapshot.
- ADMIN và các role giới hạn đúng trên backend.
- Docker Compose chỉ chạy hai dependency MySQL/Redis trong project riêng.
- API/frontend chạy ngoài Docker trên host qua một origin; Cloudflare Tunnel không route database.
- Playwright smoke/full E2E đạt; deploy/rollback runbook rõ ràng.

