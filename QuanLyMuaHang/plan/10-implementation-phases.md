# Thứ tự triển khai theo vertical slice

## Phase 0 — Đồng bộ khung và môi trường

- Rà source skeleton hiện có, chốt API contract, giữ baseApi.ts làm cổng API duy nhất.
- Kiểm tra flyway_schema_history; không sửa migration đã chạy, mọi thay đổi schema thêm migration version mới.
- Chuyển Compose của project thành đúng hai service MySQL/Redis, project name/volume riêng, loopback ports; không thao tác Docker stack khác.
- Thêm Security, Spring Session Redis, Flyway, POI/CSV/PDF deps theo kế hoạch; bỏ secret fallback.
- Vite dev proxy /api; production React build được đóng trong Spring static.
- Health/readiness kiểm tra MySQL/Redis; lệnh local có -p project name.
- Exit: Compose config hợp lệ và chỉ liệt kê mysql/redis; app/frontend chạy ngoài Docker; test baseline qua.

## Phase 1 — Identity, login và Quản trị tài khoản

Theo yêu cầu mới ngày 2026-10-11, phần nhân sự cơ bản được gom vào Quản trị. Lượt triển khai này hoàn thiện bước 1 của [kế hoạch Quản trị](15-quan-tri-tai-khoan-va-phan-quyen.md): tạo tài khoản và hồ sơ tùy chọn trong một form, tìm kiếm/lọc ở server, bảo vệ phạm vi gán vai trò. Login/CSRF/Redis và CRUD danh bạ đã có trước đó. Cấp/cấm quyền riêng, quản lý mẫu quyền và giữ đăng nhập thuộc các bước 2–3 của kế hoạch 15; chưa coi là đã hoàn tất.

- Port theo hành vi cần thiết từ demo, reimplement sạch theo DDD: account/password, login/logout/me/change-password, user admin, session revoke.
- Spring Session Redis cookie + CSRF; không public registration, Google hoặc OTP.
- Role/permission tables, ADMIN wildcard, seed roles idempotent.
- Admin bootstrap an toàn; CRUD user, link account/employee, reset password tạm + force change.
- Employee/department/position CRUD cơ bản, deactivate và đồng bộ trạng thái account.
- React login, dashboard shell, Quản trị tài khoản/danh bạ/phòng ban/chức vụ, đọc và gán vai trò hiện có; không có ô Nhân sự riêng.
- Một form tạo tài khoản kèm hồ sơ cơ bản tùy chọn; tìm kiếm/lọc toàn bộ tài khoản phía server, giới hạn trang, trả DTO không có mật khẩu/hash.
- Chặn người quản lý tài khoản được ủy quyền gán vai trò hoặc sửa/reset tài khoản vượt phạm vi quyền của mình.
- Exit của lượt Phase 1: JUnit/MVC/Playwright auth/tài khoản qua; API/MySQL/Redis kiểm tra rollback, thu hồi hai phiên và cổng ứng dụng trên dữ liệu thử riêng. Không suy ra đã kiểm chứng race login/đổi quyền hoặc token giữ đăng nhập của bước sau. Bằng chứng hiện hành ở [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md).

## Phase 2 — Di trú workbook cũ

- Legacy importer POI/CSV theo 4 named sheets, staging, preview, idempotent commit/report.
- Dry-run trên workbook đã review; xác nhận counts/warnings; không import ACCESS_CODE.
- Backup database/files trước commit; commit có transaction/chunk/resume.
- Exit: reconciliation totals pass; search được historical prices và 262 legacy POs; report warning không còn sai lệch chưa giải thích.

## Phase 3 — Catalog và tra cứu giá

- Supplier/material master + aliases, code nullable, snapshot preserved.
- Vietnamese normalize/token search; API paginated history/latest by currency.
- React catalog pages và price search/autocomplete.
- Exit: không trộn currency; fuzzy suggestions không tự ghi master.

## Phase 4 — Draft mua hàng và phát hành PO

- Nhập tay/dán/operational request upload.
- Cart, checkbox selection, latest-price fill, supplier/address/currency/VAT.
- Backend preview group by supplier/currency; sequence per date; transaction, revision and audit.
- PDF template with subtotal/VAT/grand total.
- Exit: single/multi supplier, concurrent sequence, edit/retry PDF tests pass.

## Phase 5 — Operational imports và exports

- XLS/XLSX/CSV/PDF text parser tạo draft.
- Preview, mapping/sửa field, warning/confidence; scanned PDF manual fallback.
- Export PO/history/import report XLSX/CSV.
- Exit: malformed file không crash; không âm thầm bỏ dòng.

## Phase 6 — UI polish và hardening

- Polish system-style interface, permission-aware navigation, responsive tables, keyboard/accessibility.
- Playwright full workflows; API security and upload review.
- Performance pagination/search; structured logging, metrics, backups/restore.
- Exit: acceptance checklist Phase 09 đạt.

## Phase 7 — Production deploy

- Build versioned JAR + frontend static; OS service account/systemd.
- MySQL/Redis only in project-specific Compose; app binds 127.0.0.1:8080.
- Cloudflare Tunnel host service -> app only; TLS, cookie, CSRF, PDF download smoke tests.
- Seed first admin via secret environment/one-time command; do not use demo seed data.
- Exit: backup/restore, domain smoke test, rollback runbook and credentials custody handed to owner.

## AI/OpenAI

Không có AI API feature trong MVP. Không đưa OpenAI key, SDK hoặc model call vào frontend/backend. Nếu sau này cần AI mapping/import, mở phase riêng, cần duyệt privacy/security trước.

