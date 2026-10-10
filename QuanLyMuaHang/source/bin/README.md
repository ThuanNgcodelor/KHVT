# Backend — Quản lý mua hàng KHVT

Backend là Spring Boot REST API trên Java 21, chia module theo nghiệp vụ theo hướng DDD modular monolith. Cấu trúc hiện còn pha trộn module nghiệp vụ với entity/repository dùng chung; xem [bản đồ hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md) để phân biệt thiết kế và code đang chạy.

Frontend React/Vite ở `frontend/` có login, đổi mật khẩu lần đầu, session/CSRF, cổng chọn ứng dụng, dashboard API và UI nhân sự/tài khoản. Build mới, 7 unit tests và 42 trường hợp browser desktop/mobile trên API giả lập đã qua sau sửa/chạy lại. UI danh mục/giá/PO/import chờ triển khai. Xem [cấu trúc và cách chạy frontend](frontend/README.md). Hướng dẫn cho AI nằm ở [AGENTS.md](../AGENTS.md), kèm [nguyên tắc trung thực](../docs/NGUYEN_TAC_TRUNG_THUC.md) và [skill giao diện KHVT](../skills/khvt-ui/SKILL.md).

## Chạy local

Cần cài JDK 21, Maven 3.9+, Docker Engine và Docker Compose. Docker Compose trong repo chỉ khởi chạy MySQL và Redis; không tạo/đổi tên/xóa container hay volume nào bên ngoài project.

```bash
cd QuanLyMuaHang/source
cp .env.example .env
```

Sửa `.env` và đặt các giá trị riêng, mạnh cho `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `REDIS_PASSWORD`. Đặt `ADMIN_BOOTSTRAP_EMAIL` và `ADMIN_BOOTSTRAP_PASSWORD` để tạo admin đầu tiên; mật khẩu phải dài ít nhất 12 ký tự. Tài khoản này chỉ được tạo khi email chưa tồn tại và bắt buộc đổi mật khẩu lần đầu. Không commit `.env`.

```bash
docker compose up -d mysql redis
docker compose ps
```

Flyway tạo schema khi backend khởi động. Nạp biến môi trường từ `.env` vào shell hiện tại rồi chạy API:

```bash
set -a
. ./.env
set +a
mvn test
mvn spring-boot:run
```

API mặc định ở `http://localhost:8080`. MySQL chỉ bind vào `127.0.0.1:3307`, Redis vào `127.0.0.1:6380`. Khi cần dừng riêng stack local này: `docker compose stop mysql redis`.

## Kiểm tra và đăng nhập API

Chạy `mvn test` để chạy test unit và Spring context với H2 trong bộ nhớ; bộ test thông thường không kết nối MySQL/Redis thật. Test Redis thật được bật riêng như bên dưới. Để chạy API local, MySQL và Redis phải healthy trước.

Kết quả mới: Maven 3.9.11/JDK 21 chạy `mvn package` thành công lúc **2026-10-10 00:29:59 +07:00**, 32 test/0 failure/error/skipped; compile/JAR/repackage đều qua. Log local ở [backend-verified-build.log](target/runtime/backend-verified-build.log). Lần này có 31 unit/mock/H2 tests và một test Redis thật opt-in. Test API kiểm tra modules/permissions/role bằng H2/MockHttpSession; test Redis kiểm tra Boot indexed repository và service thu hồi/giữ phiên với dữ liệu tổng hợp. Chưa xác nhận HTTP hai phiên, last-admin hoặc CRUD MySQL.

Sau build, backend cổng 8080 và Vite cổng 5173 đã khởi động lại; MySQL/Redis Compose healthy tại thời điểm kiểm tra ngày 2026-10-10. Backend `/actuator/health` trả HTTP 200/`UP`, frontend trả 200 và health qua Vite proxy trả `UP`. Smoke login thật thử một lần bằng thông tin bootstrap nạp riêng nhận HTTP 401, đã dừng không retry/đổi mật khẩu. Chưa xác định thông tin đăng nhập hiện hành; không suy ra người dùng đã đổi mật khẩu hoặc báo auth thật mới đã qua. Health không thay thế kiểm thử nghiệp vụ.

Test Redis thật sử dụng namespace UUID `qmh:test:session:<uuid>`, chỉ dọn key do test tạo, không dùng tài khoản thật hoặc `FLUSHDB`. Bật khi Redis của dự án đã chạy và `REDIS_PASSWORD` đã được nạp riêng vào môi trường. Trên Bash:

```bash
export QMH_RUN_REDIS_TESTS=true
export QMH_TEST_REDIS_HOST=127.0.0.1
export QMH_TEST_REDIS_PORT=6380
export QMH_TEST_REDIS_PASSWORD="$REDIS_PASSWORD"
mvn -Dtest=RedisSessionRevocationIntegrationTest test
unset QMH_RUN_REDIS_TESTS QMH_TEST_REDIS_HOST QMH_TEST_REDIS_PORT QMH_TEST_REDIS_PASSWORD
```

Trên PowerShell, với `REDIS_PASSWORD` đã nạp vào môi trường:

```powershell
$env:QMH_RUN_REDIS_TESTS = 'true'
$env:QMH_TEST_REDIS_HOST = '127.0.0.1'
$env:QMH_TEST_REDIS_PORT = '6380'
$env:QMH_TEST_REDIS_PASSWORD = $env:REDIS_PASSWORD
mvn.cmd '-Dtest=RedisSessionRevocationIntegrationTest' test
Remove-Item Env:QMH_RUN_REDIS_TESTS, Env:QMH_TEST_REDIS_HOST, Env:QMH_TEST_REDIS_PORT, Env:QMH_TEST_REDIS_PASSWORD
```

Để chạy cả bộ test và đóng gói có Redis thật, thay lệnh test trong khối trên bằng `mvn package` (PowerShell: `mvn.cmd package`). Không bật biến `QMH_RUN_REDIS_TESTS` thì test Redis được bỏ qua. Profile H2 loại `SessionAutoConfiguration`; test opt-in nạp cấu hình chính để kiểm tra đúng repository được cấu hình cho runtime.

Đăng nhập dùng session cookie `QMHSESSION` và CSRF, không dùng JWT:

1. `GET /api/auth/csrf`; lưu cookie `XSRF-TOKEN` và gửi token trong header có tên trả về (mặc định `X-XSRF-TOKEN`).
2. `POST /api/auth/login` với JSON `{ "email": "...", "password": "..." }`, đồng thời gửi CSRF header và bật credentials/cookie.
3. Sau đăng nhập, gọi lại `GET /api/auth/csrf` để lấy token mới; tiếp tục dùng session cookie cho API. Tài khoản mật khẩu tạm chỉ được gọi `me`, `csrf`, `change-password`, `logout` cho tới khi đổi mật khẩu.
4. `GET /api/auth/me`, `POST /api/auth/change-password`, `POST /api/auth/logout`.

Frontend và API khác origin phải dùng credentials; cấu hình `APP_FRONTEND_URL` đúng origin. Profile `prod` bật cờ Secure cho session cookie, nên chỉ dùng khi request đi qua HTTPS.

Redis session hiện cấu hình `repository-type: indexed` và namespace `qmh:session:indexed` để tìm/thu hồi các phiên theo principal. Cấu hình trước dùng repository không có index nên service thu hồi không tìm được repository cần dùng. Namespace mới tách key indexed khỏi phiên cũ: sau khi chạy bản mới, người dùng phải đăng nhập lại; key cũ để hết TTL, không xóa Redis/database để chuyển cấu hình.

## API đã có

- Danh tính: `/api/auth/*`; quản trị tài khoản/vai trò: `/api/admin/users`, `/api/admin/roles`, `/api/admin/audit`.
- Nhân sự: `/api/personnel/employees`, `/api/personnel/departments`, `/api/personnel/positions`; de/activate nhân viên sẽ vô hiệu hóa phiên đăng nhập liên kết.
- Danh mục: `/api/catalog/materials`, `/api/catalog/suppliers`; tra cứu giá: `/api/prices`.
- Đơn mua: `/api/purchase-orders` (tạo, sửa/revision, hủy, danh sách, PDF và PDF revision); Excel export: `/api/exports/prices.xlsx`, `/api/exports/purchase-orders/{id}.xlsx`.
- Import workbook legacy: `POST /api/imports/legacy/preview` rồi `POST /api/imports/legacy/{batchId}/commit`.
- Import yêu cầu mua/báo giá thành draft để người dùng rà lại: `/api/imports/operational/preview` và `/api/imports/operational/paste`. Luồng draft không ghi lịch sử giá hay tạo PO; PDF scan không có OCR.
- Health check: `/actuator/health`.

Quyền mặc định: `ADMIN` được seed permission wildcard `*` nên có toàn quyền; `HR_MANAGER` quản lý nhân sự; `PLANNER` làm danh mục/giá/đơn mua/import yêu cầu; `VIEWER` chỉ tra cứu. Tên role riêng lẻ không thay permission trong kiểm tra quyền module/action. Mọi thay đổi đi qua API được audit, request có `X-Request-ID`.

Thông tin login/me/đổi mật khẩu gồm `permissions` hiệu lực và `modules` được truy cập; không có mật khẩu/hash. Registry `ApplicationModuleCatalog` cấp Mua hàng khi có `PO_READ` + `CATALOG_READ`, Nhân sự khi có `PERSONNEL_READ`, Quản trị khi có `USER_READ`; wildcard mở tất cả ứng dụng đã đăng ký. Admin cấp quyền bằng cách gán role trong trang Tài khoản; cập nhật tài khoản thu hồi phiên hiện có. Cổng `/modules` không thay thế kiểm tra quyền từng API. Chưa có CRUD role/permission, grant module riêng, luồng xin/duyệt quyền hay nghiệp vụ Bán hàng; xem [cổng ứng dụng và hướng mở rộng](../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md).

`GET /api/admin/roles` trả `permissions` và `moduleCodes` do registry suy ra cho từng role để giải thích phạm vi trong form. `moduleCodes` không phải grant độc lập; tạo/sửa tài khoản vẫn gửi `roleCodes`. Quyền phiên là hợp permission từ các vai trò rồi tính lại modules.

## Import dữ liệu Excel cũ

Legacy import chấp nhận `.xls`/`.xlsx` và yêu cầu các sheet `NCC`, `LICH_SU`, `DON_HANG`. Luồng preview tạo staging và checksum để chống import lặp; commit chỉ diễn ra khi lô không có dòng lỗi hoặc xung đột header PO. Sheet `CONFIG` và các khóa trong đó không được đọc/import. Mã nguồn, snapshot tên và số lượng dạng chữ được giữ lại; không ghép mơ hồ theo tên. Số lượng chữ không được tính thành tiền. Lịch sử giá cũ được đánh dấu currency là giả định `VND` cho tới khi dữ liệu nguồn bổ sung được currency.

Hãy sao lưu workbook và database trước lần commit đầu tiên trên dữ liệu thật; kiểm tra preview/warnings và đối chiếu số dòng/PO trước khi commit.

## PDF và file sinh ra

PDF PO nhúng font Unicode. Mặc định dùng DejaVu Sans tại `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`; đổi `APP_PDF_FONT_PATH` nếu hệ điều hành đặt font ở nơi khác. File PDF lưu trong `FILE_STORAGE_ROOT` (mặc định `./data/files`); cần backup thư mục này cùng database.

## Triển khai sau này

Cloudflare Tunnel trỏ tới dịch vụ web/API đang chạy tại máy chủ, ví dụ `http://localhost:8080`; không public port MySQL/Redis. Trước khi mở cho người dùng thật cần đặt secrets riêng, bật profile `prod`, HTTPS, backup/restore thử nghiệm, giám sát dung lượng và chiến lược lưu file bền vững. Chưa cấu hình Tunnel hoặc domain trong source này.
