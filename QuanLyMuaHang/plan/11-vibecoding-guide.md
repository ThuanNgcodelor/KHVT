# Hướng dẫn vibecoding và tự kiểm thử

## Trước mỗi task

1. Đọc README plan và 00-scope.
2. Đọc plan chuyên biệt: architecture + Identity/Personnel cho login; DB + Import/PDF cho workbook; Docker/Cloudflare trước deploy.
3. Xem source hiện hữu và giữ các thay đổi người dùng.
4. Chỉ triển khai một vertical slice có migration, API, UI và test liên quan.
5. Nêu giả định; không tự mở rộng thành full HR, Google login, OCR hoặc AI.

## Prompt mẫu cho Claude/Codex

~~~text
Đọc QuanLyMuaHang/plan/README.md, 00, 01, 04 và 13. Chỉ triển khai use case được giao trong bounded context. Giữ controller mỏng; application service kiểm tra permission và transaction; repository interface/adapter đúng layer; thêm Flyway migration, unit/integration test và Playwright test. Không gọi repository từ controller, không copy nguyên package từ backend demo, không tạo seed mật khẩu/dữ liệu nhân viên. Chạy test phù hợp, báo file đổi, giả định và kết quả.
~~~

## Thứ tự giao task

1. Compose MySQL/Redis riêng và app chạy ngoài Docker.
2. Identity/session/CSRF + seed ADMIN.
3. Role/permission + account admin + personnel CRUD/link account.
4. Legacy workbook staging/preview/commit/report.
5. Catalog và price search.
6. Draft import/parser/autocomplete.
7. PO sequence/issue/edit/revision/audit.
8. PDF/export.
9. UI polish, accessibility, full Playwright.
10. Deploy/Tunnel, backup/restore.

## Quy tắc bắt buộc

- Không truy cập database/API/secrets của backend demo hoặc hệ thống công ty.
- Không copy EmployeeDataSeeder, DataSeeder, user rows, password mẫu, JWT secret, mail/Google credentials.
- Chỉ lấy hành vi login/account/personnel cần dùng; tái cấu trúc theo DDD và cookie session đã chọn.
- Không kết nối workbook production/test thật trong CI; dùng fixture đã mask.
- Không commit .env, app.env, Cloudflare credentials, password hay session cookie.
- Mọi schema change dùng Flyway; Hibernate ddl-auto validate.
- Không double cho tiền; không dùng findAll cho price history.
- Mutation account/permission/personnel/PO/import phải authorize + audit.
- Deactivate thay hard delete; giữ PO/revision/file history.
- Lỗi import phải nêu sheet/row/field; không catch rồi bỏ qua.
- React chỉ dùng baseApi.ts/apiClient; không URL fetch rải rác, không localStorage session/token.
- ADMIN wildcard không bỏ qua audit/transaction; không cho mất ADMIN cuối.
- Docker command luôn project-scoped. Ví dụ:
  - docker compose -p qlmh-dev config
  - docker compose -p qlmh-dev up -d mysql redis
  - docker compose -p qlmh-dev logs mysql redis
- Không dùng docker compose down -v, docker system prune, hoặc stop stack không thuộc project này.
- Không chạy test/command nào có thể seed thật hay migrate production nếu chưa có backup.

## Lệnh kiểm tra

~~~bash
docker compose -p qlmh-dev config
docker compose -p qlmh-dev up -d mysql redis
mvn test
mvn verify
cd frontend && npm ci && npm run build && npm test
cd frontend && npx playwright test
~~~

Kiểm tra unit/API trước, E2E sau; nêu rõ test nào cần Docker. Dọn chỉ container thuộc test run/project riêng.

