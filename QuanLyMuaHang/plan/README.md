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
- Phase 1 gom danh bạ/phòng ban/chức vụ vào Quản trị; chỉ đăng ký hai ứng dụng Mua hàng và Quản trị. Quyền danh bạ không tự cấp quyền quản lý tài khoản. Tài khoản có thể có hồ sơ cơ bản tùy chọn trong cùng form/transaction.

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
11. 15–16 — hướng ưu tiên ngày 2026-10-11: Quản trị tài khoản/quyền và PCVT → tra giá/NCC cũ → gom hỏi giá → so sánh báo giá.

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
- [15-quan-tri-tai-khoan-va-phan-quyen.md](15-quan-tri-tai-khoan-va-phan-quyen.md)
- [16-pcvt-tra-gia-cu-va-bao-gia.md](16-pcvt-tra-gia-cu-va-bao-gia.md)

## Ưu tiên được người dùng làm rõ ngày 2026-10-11

Không cần ứng dụng Nhân sự riêng: Quản trị phục vụ tạo tài khoản, thông tin phòng ban/chức vụ cơ bản và cấp/cấm module/hành động cho từng người. Chức vụ không tự sinh quyền. Giữ đăng nhập là bổ sung đề xuất; hiện hệ thống dùng Redis session idle 30 phút, không có cặp access token/refresh token.

`QUANLYMUAHANGKHVT.xlsx` chỉ là dữ liệu cũ của Apps Script. File PCVT mới dùng để tra từng món từng mua NCC nào/giá nào, gom hỏi giá theo NCC; file báo giá mới dùng so sánh. Người dùng đổi ưu tiên sang import trước: kế hoạch 16 đã triển khai phần nhập/lưu/tra/gom/so sánh và qua [nghiệm thu kỹ thuật theo phạm vi](../docs/NGHIEM_THU_IMPORT_PCVT_VA_BAO_GIA.md); phần OCR/alias/phân bổ PO còn làm tiếp.

Theo yêu cầu tiếp theo, **bước 1 của kế hoạch 15 đã triển khai và nghiệm thu kỹ thuật theo phạm vi**: Quản trị tài khoản kèm hồ sơ tùy chọn, tìm kiếm/lọc/phân trang phía server, gom danh bạ và giới hạn thao tác của người được ủy quyền. Driver MySQL/Redis/MCP thật hoàn tất 15 nhóm, báo cáo cuối ghi lúc 18:25:27 +07:00, gồm tạo tài khoản qua UI/reload/mobile và bảo vệ hai admin tự bỏ quyền đồng thời. Xem [biên bản Phase 1](../docs/NGHIEM_THU_QUAN_TRI_PHASE_1.md) và [hướng dẫn dùng](../docs/HUONG_DAN_QUAN_TRI_PHASE_1.md). Bước 2 grant/deny cá nhân và bước 3 giữ đăng nhập chưa triển khai. Không coi route/API đề xuất ở phần còn lại là tính năng đã có.

## Current source status

Cập nhật 2026-10-11: `source/` có auth/cổng ứng dụng, Quản trị tài khoản/danh bạ/phòng ban/chức vụ, danh mục/giá/PO/PDF/XLSX/import/dashboard và chứng từ PCVT–tra giá/NCC–gom hỏi–so sánh báo giá. Cổng chỉ có Mua hàng và Quản trị; backend `personnel` và dữ liệu vẫn giữ. Quyền hiện tính từ vai trò; chưa có grant/deny từng tài khoản. UI/code tồn tại không đồng nghĩa nghiệm thu đủ nghiệp vụ; xem biên bản kiểm tra thực tế và giới hạn theo từng phần.

Phase 1: full package backend lúc 18:01:06 có 138 test liệt kê/136 thực thi qua/2 opt-in bỏ qua; sau các sửa cuối hồ sơ và điều kiện last-admin đã chạy package mục tiêu 31 test qua lúc 18:24:28, chưa chạy lại toàn suite. Frontend build/10 unit tests/56 browser cases desktop/mobile qua; browser dùng API giả lập. Driver thật hoàn tất trên schema `qmh_admin_phase1_c0c3eea29b84`, [báo cáo cuối](../source/target/runtime/admin-phase1-real-integration-report.json) xác nhận `completed: true`, `mainUnchanged: true`; không cộng lượt test chạy lại thành tổng mới.

Luồng workbook cũ đã qua 91 test backend thực thi và driver MySQL/Redis riêng: đối chiếu 11.690 dòng nguồn/91.526 trường, 0 sai lệch theo mapping; đủ 10.561 lịch sử, 262 PO, 850 dòng PO. Cảnh báo nguồn vẫn cần xác minh. Kết quả frontend và giới hạn kiểm thử xem [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md), không dùng các con số của lượt build cũ để mô tả bản hiện tại.

Server chính tiếp tục tắt theo yêu cầu người dùng. Driver Quản trị dùng backend 8082, schema/namespace UUID riêng, backend/browser helper đã tự dừng; không chạy lại ứng dụng chính để chứng minh nghiệm thu. Báo cáo Phase 1 xác nhận dữ liệu chính, metadata tài khoản và liên kết vai trò giữ nguyên; Flyway chính vẫn V6. JAR/UI mới đóng gói; V7 chỉ áp dụng trên schema thử. Backend demo đã xóa; Cloudflare/production chưa nghiệm thu.

## Global Definition of Done

- Login/roles/personnel, mua hàng, search, migration, PDF, audit, backup/restore đạt acceptance ở 09.
- Workbook được import/reconcile không mất giao dịch/snapshot.
- ADMIN và các role giới hạn đúng trên backend.
- Docker Compose chỉ chạy hai dependency MySQL/Redis trong project riêng.
- API/frontend chạy ngoài Docker trên host qua một origin; Cloudflare Tunnel không route database.
- Playwright smoke/full E2E đạt; deploy/rollback runbook rõ ràng.

