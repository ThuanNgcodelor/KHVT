# Cấu hình, bảo mật và vận hành

## Profiles và secret

- application.yml: defaults không nhạy cảm; không có password/secret fallback.
- application-dev.yml: local host ports riêng, log phù hợp.
- application-test.yml: database/Redis test riêng hoặc Testcontainers.
- production lấy credentials và file paths từ environment/systemd EnvironmentFile bên ngoài Git.
- .env.example chỉ có tên biến và placeholder giả; .env, app.env, Cloudflare credentials nằm ngoài Git, permission file tối thiểu.
- Khi env bắt buộc thiếu, app phải fail startup thay vì dùng password mặc định.

Biến cần có: SPRING_PROFILES_ACTIVE, SPRING_DATASOURCE_URL/USERNAME/PASSWORD, SPRING_DATA_REDIS_HOST/PORT/PASSWORD, APP_BASE_URL, APP_TIMEZONE, FILE_STORAGE_ROOT, ADMIN_BOOTSTRAP_EMAIL/PASSWORD, cookie/session settings.

## Authentication và session

- Spring Security + Spring Session Data Redis; không viết JWT refresh rotation mới trong MVP.
- Login email/password, BCrypt password hashing; không public registration, Google OAuth hoặc email OTP.
- Cookie session HttpOnly, Secure production, SameSite=Lax/Strict theo kiểm thử; Path=/, TTL idle/absolute có giới hạn.
- React không đọc hoặc lưu session cookie/token ở localStorage.
- CSRF bật; cung cấp CSRF token endpoint/cookie theo Spring Security SPA pattern; apiClient gửi header token với mutation.
- Production React và API cùng origin; local Vite proxy /api về Spring, tránh CORS rộng.
- Thông báo login generic; không cho biết email có tồn tại hay không.
- Rate limit login và lock tạm sau nhiều lần sai; lock admin phải có quy trình bootstrap/khôi phục.
- Login chỉ thành công nếu account active và employee liên kết (nếu có) active.
- Password reset của admin đặt mật khẩu tạm một lần, bắt buộc đổi khi login; không gửi email trong MVP.
- Disable user, role change, reset password, employee deactivation revoke mọi Spring Session Redis đang hoạt động.
- Redis outage: login/session mutations fail closed, không cho qua auth; health/readiness báo dependency lỗi.
- Spring Session namespace dùng prefix riêng cho ứng dụng, tránh key đụng Redis service khác.

## Authorization

- Permission check ở request route và application service.
- Role mặc định: ADMIN wildcard; HR_MANAGER personnel CRUD; PLANNER procurement/import/price workflows; VIEWER read-only theo policy.
- ADMIN quản lý mọi user, role, permission, employee, catalog, import, PO, audit; ADMIN không bypass audit.
- Không cho xóa audit hoặc disable ADMIN cuối cùng; thao tác self-lock/self-demote bị chặn nếu khiến mất quản trị.
- UI guard không được xem là security boundary.
- Tất cả mutation log actor/action/target/before-after/requestId; password/hash/session id không được audit.
- Upload, PDF download, audit, account admin đều enforce permission.

## Database và file

- App DB user chỉ có quyền trên schema quanlymuahang; không dùng root.
- Hạn chế MySQL/Redis bind cổng host trên loopback; firewall chặn truy cập ngoài.
- MySQL là durable business store; backup mysqldump --single-transaction định kỳ ra thư mục riêng và mã hóa.
- Redis chứa session có TTL; không phải database nghiệp vụ. Có thể persistence cho restart continuity, nhưng có thể xóa sessions mà không mất PO/history.
- FILE_STORAGE_ROOT nằm ngoài repository, quyền OS chỉ cho service user; backup cùng database metadata.
- Retention và access policy cho workbook/pdf gốc; không ghi file contents trong logs.

## Cloudflare/proxy

- Tunnel trỏ tới 127.0.0.1:8080 của Spring Boot; không route MySQL/Redis.
- Chỉ tin forwarded headers từ local cloudflared; cấu hình secure cookie đúng khi TLS dừng tại Cloudflare.
- Không public port 8080 nếu chỉ dùng tunnel; inbound firewall đóng mặc định.
- Cloudflare Access có thể bật cho admin như lớp bổ sung, không thay Spring authorization.
- Không đưa Cloudflare tunnel token vào source/plan/example.

## Backup/restore và observability

- Trước mỗi release/migration workbook: backup DB và file artifacts.
- Có lệnh kiểm tra restore sang database test; ghi thời điểm/checksum.
- /actuator/health chỉ expose health cần thiết; không để env/config secrets ra actuator.
- Request ID và structured logs; không log password, cookie, CSRF/session tokens, raw file.
- Metrics: auth failures, session invalidation, import duration/errors, PO issue failure, PDF generation failure, DB/Redis health.

