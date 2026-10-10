# Backend — Quản lý mua hàng KHVT

Backend là Spring Boot REST API trên Java 21, chia module theo nghiệp vụ theo hướng DDD modular monolith. Cấu trúc hiện còn pha trộn module nghiệp vụ với entity/repository dùng chung; xem [bản đồ hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md) để phân biệt thiết kế và code đang chạy.

Frontend React/Vite ở `frontend/` có auth/session/CSRF, cổng chọn ứng dụng, dashboard, nhân sự/tài khoản, danh mục vật tư/NCC, tra cứu giá, lập/sửa/hủy PO, PDF/XLSX và import preview/commit. Xem [cấu trúc và cách chạy frontend](frontend/README.md), [đối chiếu Index.html/Mã.js](../docs/DOI_CHIEU_UNG_DUNG_CU.md) và [bằng chứng kiểm thử hiện hành](../docs/HE_THONG_HIEN_TAI.md). Hướng dẫn cho AI nằm ở [AGENTS.md](../AGENTS.md), kèm [nguyên tắc trung thực](../docs/NGUYEN_TAC_TRUNG_THUC.md) và [skill giao diện KHVT](../skills/khvt-ui/SKILL.md).

## Chạy trên Windows

Điểm chạy chính ở thư mục gốc `QuanLyMuaHang` là [CHAY_KHVT.cmd](../CHAY_KHVT.cmd). Cần JDK 21, Maven 3.9+, Node/npm và Docker Desktop; tạo `source/.env` riêng từ mẫu nếu chưa có, không ghi đè file đang dùng. **Mở Docker Desktop riêng**, chờ engine sẵn sàng rồi bấm đúp file hoặc chạy từ thư mục gốc:

```powershell
.\CHAY_KHVT.cmd start
```

Launcher gọi Compose để bật MySQL/Redis, chạy `npm ci` và `npm run build` trong `source/frontend`, rồi `mvn -Pwindows-web -DskipTests package` trong `source`. Profile Maven `windows-web` đóng `frontend/dist` vào static resources của JAR. Một tiến trình Java phục vụ **giao diện và API tại http://localhost:8080**, gồm các URL giao diện khi reload trực tiếp; không cần chạy Vite để dùng bản đóng gói.

Lượt start mặc định **bỏ qua bộ test**, nên phải chạy test riêng khi kiểm tra thay đổi. Hướng dẫn tùy chọn, profile HTTPS và chuyển máy tại [Chạy Windows và Cloudflare](../docs/CHAY_WINDOWS_VA_CLOUDFLARE.md). Launcher nạp `source/.env` đúng đường dẫn và ghi log/PID riêng; không thay mật khẩu hoặc dừng Java của VS Code.

```powershell
.\CHAY_KHVT.cmd status
.\CHAY_KHVT.cmd stop
.\CHAY_KHVT.cmd start -NoBuild
```

`-NoBuild` dùng JAR đã chứa frontend, không cập nhật code. `stop` chỉ dừng tiến trình ứng dụng được launcher xác minh; Docker Desktop/MySQL/Redis tiếp tục chạy. Để build lại code mới, dừng đúng ứng dụng rồi chạy `start` không có `-NoBuild`.

Lượt kiểm tra launcher ngày **2026-10-10** đã chạy thật `npm ci`, frontend build và package `windows-web` thành công, rồi Java heap 256 MB phục vụ UI/API trên 8080. HTTP giao diện/deep link đã qua; API chưa đăng nhập trả 401, thiếu CSRF trả 403 và thông tin đăng nhập tổng hợp sai trả 401 ở cả hai origin local. MCP đã render trang login, với 401 từ `me` khi chưa đăng nhập là trạng thái dự kiến. Gọi `start` lần hai không tạo thêm tiến trình; `status` và `stop` đã qua. Lượt `start -NoBuild -ExternalDocker` chạy lại lúc **21:19 +07:00** đã qua, health `UP`; probe HTTP lúc **21:19:50** qua 15 kiểm tra shell/deep link/assets, API 401, asset thiếu 404, thông tin đăng nhập tổng hợp sai 401 ở localhost/127.0.0.1 và thiếu CSRF 403. Lượt `-NoBuild` đầu trước đó gặp MySQL EOF trong lúc container đang được tạo lại. Đây là kiểm tra launcher/HTTP/UI, không xác nhận đăng nhập tài khoản người dùng, toàn bộ nghiệp vụ hoặc production Tunnel. Log build/app trong `source/target/runtime/windows-*.log`; xem [bằng chứng hiện hành](../docs/HE_THONG_HIEN_TAI.md) để biết cập nhật cuối.

Nếu muốn tự bật container từ **thư mục gốc** trước khi chạy ứng dụng:

```powershell
docker compose --env-file .\source\.env -f .\source\docker-compose.yml --project-name qmh-local up -d mysql redis
docker compose --env-file .\source\.env -f .\source\docker-compose.yml --project-name qmh-local ps
.\CHAY_KHVT.cmd start -NoBuild -ExternalDocker
```

Root không chứa file Compose; vì vậy `docker compose up` tại root cần `-f` như trên. `-ExternalDocker` không gọi `compose up`, vẫn kiểm tra engine và hai container của dự án. Chưa có JAR đóng gói thì bỏ `-NoBuild` để build trước.

### Giới hạn RAM local

Compose chỉ có hai dependency, với giới hạn sau:

| Dịch vụ | Giới hạn RAM container | CPU | Cấu hình chính |
|---|---|---|---|
| MySQL | 1536 MiB | 2 CPU | InnoDB buffer pool 512 MiB, tối đa 80 kết nối |
| Redis | 256 MiB | 1 CPU | `maxmemory` 128 MiB, `noeviction`, giữ AOF |

Đây là mức trần, không phải lượng RAM được đặt trước hoặc tổng RAM Docker Desktop/WSL. Sau người dùng đổi Compose sang cấu hình trong bảng và tạo lại container, số đo `docker stats` lúc **2026-10-10 21:17 +07:00** là MySQL 375.9 MiB/giới hạn 1.5 GiB và Redis 5.02 MiB/giới hạn 256 MiB. Volume, schema và số tài khoản khớp trước/sau. Redis có 15 key sau smoke CSRF, so với 6 key trước smoke; không coi thay đổi key phiên này là mất dữ liệu.

Lịch sử cấu hình 768 MiB MySQL/128 MiB Redis ngày 2026-10-10: trước chỉnh lúc 21:03, MySQL 466.8 MiB/Redis 8.574 MiB; sau áp dụng lúc 21:12, MySQL 316.5 MiB/Redis 7.547 MiB, cả hai healthy và vẫn dùng volume cũ. Các số đo là ảnh chụp tại thời điểm tương ứng, không bảo đảm mức RAM dưới tải workbook hoặc nhiều người dùng, và không phải kết quả của cấu hình hiện tại.

Launcher mới dùng Java heap tối đa 256 MB mặc định; có thể đổi bằng `-HeapMB`. Heap không phải RSS toàn tiến trình: Java còn dùng bộ nhớ cho class, thread và bộ nhớ native. Cấu hình Java của VS Code vẫn riêng, không bị đổi bởi launcher. Không dùng `docker compose down -v` để giảm RAM hoặc sửa startup vì lệnh đó xóa volume.

## Chạy backend thủ công và chế độ phát triển

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

Khi phát triển cần Vite HMR, trên Windows PowerShell từ thư mục repository vẫn có thể dùng launcher dev:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\start-local.ps1
```

Script nạp `.env` riêng, kiểm tra Docker và chờ MySQL/Redis healthy, chọn JDK 21/Maven, build JAR rồi chạy Java 8080/Vite 5173 cửa sổ ẩn với heap giới hạn. Đây là chế độ phát triển; bản chạy chung bằng `CHAY_KHVT.cmd` ở trên dùng một cổng 8080. `-SkipBuild` dùng JAR đã build; không thay cho chạy test. Nếu cổng đang có tiến trình chưa xác nhận, script dừng với thông báo; frontend do launcher tạo có PID/command đúng có thể được dùng lại ở API 8080. PID và log riêng trong `source/target/runtime/`. Lịch sử bản script trước chỉnh preset: đã qua kiểm tra cú pháp PowerShell 5.1, nhánh Docker offline và khởi động thành công với `-SkipBuild` lúc **2026-10-10 11:08 +07:00**, backend/proxy health 200/`UP`, frontend 200; lúc **11:30** đã chạy lại backend/frontend bằng bản sửa đăng nhập. Script dev sau chỉnh preset/heap chưa kiểm tra khởi động lại hoặc HMR; các lượt lịch sử không xác nhận bản script mới.

### Chạy backend bằng VS Code

Mở **Run and Debug** (`Ctrl+Shift+D`), chọn profile Java rồi nhấn **F5**. Khi mở thư mục `QuanLyMuaHang`, chọn **KHVT Backend (source/.env)** trong [launch.json](../.vscode/launch.json). Khi mở thư mục cha `KHVT`, profile Spring Boot hiện có trong `KHVT/.vscode/launch.json` đã được sửa trên máy local. Cả hai dùng JDK 21 đã cài ở `C:/Program Files/Java/jdk-21.0.12`; trên máy khác cần chỉnh `javaExec` theo nơi cài JDK 21.

Đường dẫn phụ thuộc thư mục thực tế đang mở trong VS Code:

| Thư mục mở | `cwd` | `envFile` |
|---|---|---|
| `QuanLyMuaHang` | `${workspaceFolder}/source` | `${workspaceFolder}/source/.env` |
| `KHVT` | `${workspaceFolder}/QuanLyMuaHang/source` | `${workspaceFolder}/QuanLyMuaHang/source/.env` |

`envFile` nạp biến môi trường cho tiến trình Java, còn `cwd` giữ đường dẫn lưu file đúng trong `source/`; xem [tài liệu Java debugging của VS Code](https://code.visualstudio.com/docs/java/java-debugging). Không chép mật khẩu vào `launch.json`. Profile local có giới hạn heap 384 MB và font Arial cho PDF trên Windows. Backend đang chạy bằng launcher sẽ chiếm cổng 8080, nên dừng bản đó trước khi chạy thêm bằng F5.

Log `Access denied for user 'app' ...` / MySQL `1045` là lỗi xác thực database khi Spring Boot khởi động, không phải lỗi biên dịch Java. Lượt VS Code lúc 11:01 ngày 2026-10-10 trỏ `envFile` tới `KHVT/.env` không tồn tại; file đúng là `QuanLyMuaHang/source/.env`. Sau sửa, khởi động JAR bằng cấu hình này đã qua MySQL/Flyway/Redis và health `UP`; chưa thao tác F5 trực tiếp trong VS Code. Nếu lỗi 1045 còn xuất hiện, kiểm tra cấu hình được chọn và thông tin MySQL thực tế; không xóa volume để xử lý lỗi mật khẩu.

## Kiểm tra và đăng nhập API

Chạy `mvn test` để chạy test unit và Spring context với H2 trong bộ nhớ; bộ test thông thường không kết nối MySQL/Redis thật. Test Redis thật được bật riêng như bên dưới. Để chạy API local, MySQL và Redis phải healthy trước.

Lượt đóng gói UI/profile proxy Windows lúc **2026-10-10 21:13:05 +07:00** đã qua 20 test có phạm vi: 11 auth, 3 SPA và 6 proxy production với Tomcat HTTP thật/header tổng hợp; 0 failure/error/skipped. Log `source/target/runtime/windows-focused-tests.log`. Kiểm tra này không phải lượt chạy lại toàn bộ bộ test, workbook/MySQL hoặc HTTPS qua Cloudflare thật. Launcher sau đó đã build frontend/JAR và chạy HTTP local như phần Windows ở trên; start mặc định vẫn dùng `-DskipTests`.

Lịch sử lượt mua hàng: Maven 3.9.11/JDK 21 `mvn package` **BUILD SUCCESS lúc 2026-10-10 09:53:20 +07:00**; 37 test liệt kê, 36 thực thi qua, một test Redis opt-in bỏ qua. Lần này bật `QMH_TEST_WORKBOOK`: preview/commit H2 file UUID, đối chiếu 10.561 dòng lịch sử/262 PO, hai commit đồng thời chỉ một thành công, preview sau commit giữ cảnh báo/nhóm PO; file gốc không đổi. Driver HTTP/MySQL/Redis riêng đã qua một phần gồm hai phiên/last-admin/CRUD/PO nhưng chưa hoàn tất lượt workbook/MCP vì Docker crash. Log hiện hành và giới hạn xem [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md); cách tái chạy tại [tools/local-test](../tools/local-test/README.md).

Runtime local kiểm tra lại lúc **2026-10-10 11:30 +07:00**: MySQL/Redis healthy, backend và frontend đang chạy. Qua Vite proxy, cả Origin localhost/127.0.0.1 với cookie/CSRF đúng đã tới bước xác thực; tài khoản tổng hợp không tồn tại trả 401 như dự kiến, thiếu CSRF vẫn trả 403/`CSRF_INVALID`. Đã sửa proxy giữ Host, làm mới CSRF trước login và tách thông báo lỗi phiên với thiếu quyền. Package auth mới qua 11 test H2; không kiểm thử lại toàn bộ bộ test hay xác nhận mật khẩu hiện hành của người dùng. Docker/WSL từng crash trong các lượt trước, chưa xác định nguyên nhân gốc. Lịch sử trước UI mua hàng: thử login bằng cấu hình bootstrap nhận 401 và đã dừng, không reset mật khẩu. Health và test không thay nghiệm thu nghiệp vụ.

Nếu trang Đăng nhập báo 403, phân biệt lỗi phiên/CSRF hoặc origin với quyền nghiệp vụ: `/api/auth/login` là `permitAll`. Bản đóng gói dùng giao diện và `/api` cùng origin 8080; bản dev dùng Vite 5173 và proxy giữ Host của trình duyệt. API khác origin cần `APP_FRONTEND_URL` khớp địa chỉ frontend thực tế. Ô đăng nhập nhận **email tài khoản**; mật khẩu MySQL/Redis là cấu hình hạ tầng, không phải mật khẩu người dùng.

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
- Danh mục: `/api/catalog/materials`, `/api/catalog/suppliers` (gợi ý active); thêm `/page` cho bộ lọc/phân trang và `/{id}` cho chi tiết. Tra cứu giá: `/api/prices`, `/api/prices/latest`; lịch sử/XLSX hỗ trợ `category`.
- Đơn mua: `/api/purchase-orders` (tạo, sửa/revision, hủy, danh sách). Phát hành qua `POST /api/purchase-orders/{id}/issue`, cần PO_CREATE hoặc PO_EDIT và CSRF; `GET /{id}/pdf`/`GET /{id}/revisions/{revision}/pdf` chỉ đọc tài liệu đã có, không phát hành/ghi giá. Chưa có PDF trả 409. Excel export: `/api/exports/prices.xlsx`, `/api/exports/purchase-orders/{id}.xlsx`.
- Import workbook legacy: `POST /api/imports/legacy/preview` rồi `POST /api/imports/legacy/{batchId}/commit`.
- Import yêu cầu mua/báo giá thành draft để người dùng rà lại: `/api/imports/operational/preview` và `/api/imports/operational/paste`. Luồng draft không ghi lịch sử giá hay tạo PO; PDF scan không có OCR.
- Health check: `/actuator/health`.

Quyền mặc định: `ADMIN` được seed permission wildcard `*` nên có toàn quyền; `HR_MANAGER` quản lý nhân sự; `PLANNER` làm danh mục/giá/đơn mua/import yêu cầu; `VIEWER` chỉ tra cứu. Tên role riêng lẻ không thay permission trong kiểm tra quyền module/action. Mọi thay đổi đi qua API được audit, request có `X-Request-ID`.

Thông tin login/me/đổi mật khẩu gồm `permissions` hiệu lực và `modules` được truy cập; không có mật khẩu/hash. Registry `ApplicationModuleCatalog` cấp Mua hàng khi có `PO_READ` + `CATALOG_READ`, Nhân sự khi có `PERSONNEL_READ`, Quản trị khi có `USER_READ`; wildcard mở tất cả ứng dụng đã đăng ký. Admin cấp quyền bằng cách gán role trong trang Tài khoản; cập nhật tài khoản thu hồi phiên hiện có. Cổng `/modules` không thay thế kiểm tra quyền từng API. Chưa có CRUD role/permission, grant module riêng, luồng xin/duyệt quyền hay nghiệp vụ Bán hàng; xem [cổng ứng dụng và hướng mở rộng](../docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md).

`GET /api/admin/roles` trả `permissions` và `moduleCodes` do registry suy ra cho từng role để giải thích phạm vi trong form. `moduleCodes` không phải grant độc lập; tạo/sửa tài khoản vẫn gửi `roleCodes`. Quyền phiên là hợp permission từ các vai trò rồi tính lại modules.

## Import dữ liệu Excel cũ

Legacy import chấp nhận `.xls`/`.xlsx` và yêu cầu các sheet `NCC`, `LICH_SU`, `DON_HANG`. Luồng preview tạo staging và checksum để chống import lặp; commit chỉ diễn ra khi lô không có dòng lỗi hoặc xung đột header PO. Sheet `CONFIG` và các khóa trong đó không được đọc/import. Mã nguồn, snapshot tên và số lượng dạng chữ được giữ lại; không ghép mơ hồ theo tên. Số lượng chữ không được tính thành tiền. Lịch sử giá cũ được đánh dấu currency là giả định `VND` cho tới khi dữ liệu nguồn bổ sung được currency.

Hãy sao lưu workbook và database trước lần commit đầu tiên trên dữ liệu thật; kiểm tra preview/warnings và đối chiếu số dòng/PO trước khi commit.

Trong giao diện `/imports`, chọn **Workbook dữ liệu cũ** để nhập workbook có NCC/LICH_SU/DON_HANG. Màn hình **Kiểm tra 850 dòng** thuộc tab **Yêu cầu mua / báo giá**, dùng để chọn tối đa 200 dòng mỗi lượt chuyển sang lập đơn; không nạp toàn bộ lịch sử workbook.

Lượt MCP với backend/MySQL thật ngày **2026-10-10, 22:11–22:22 +07:00** đã đăng nhập HTTP 200, sao lưu và commit workbook vào schema local `quanlymuahang`: lô 1 `COMMITTED`, 10.561 lịch sử, 262 PO và 850 dòng hàng; 391 NCC, 2.997 vật tư sau xử lý. Có 4.803 dòng cảnh báo, 0 lỗi; dữ liệu nguồn thiếu không bị sửa để ép commit. Preview lại giữ số nhóm/cảnh báo, nhận `duplicate=true` và không tăng số bản ghi. File gốc không đổi. Bằng chứng/giới hạn và vị trí bản sao lưu ở [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md#import-workbook-thật-qua-mcp-vào-mysql); đây là lượt import/đọc dữ liệu thật, không phải chạy lại toàn bộ driver hoặc nghiệm thu production.

## PDF và file sinh ra

PDF PO nhúng font Unicode, có thông tin công ty/NCC, bảng hàng hóa, tổng tiền/VAT, ghi chú và ba vị trí ký; hàng dài tự xuống dòng/ngắt trang. Thông tin công ty được chuyển từ mẫu legacy, cần bộ phận nghiệp vụ rà lại trước dùng chính thức. Mặc định dùng DejaVu Sans tại `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`; đổi `APP_PDF_FONT_PATH` nếu hệ điều hành đặt font ở nơi khác (Windows đã thử `C:/Windows/Fonts/arial.ttf`). File PDF lưu trong `FILE_STORAGE_ROOT` (mặc định `./data/files`); cần backup thư mục này cùng database.

## Triển khai sau này

Với domain HTTPS thực tế, đặt `APP_FRONTEND_URL=https://<domain-cua-ban>` trong `.env` riêng rồi chạy `CHAY_KHVT.cmd start -Profile prod`. Profile này dùng cookie Secure; kiểm tra login qua domain HTTPS, không qua HTTP local. Cloudflare Tunnel cần trỏ tới **`http://127.0.0.1:8080`**, nơi JAR phục vụ cả React và API; không trỏ vào Vite hoặc public port MySQL/Redis. Hướng dẫn cấu hình, backup/chuyển máy và giới hạn tự khởi động ở [Chạy Windows và Cloudflare](../docs/CHAY_WINDOWS_VA_CLOUDFLARE.md).

Launcher chưa cài Windows Service, tự chạy sau reboot hay tạo Tunnel/domain. Trước khi dùng cho người thật cần kiểm tra HTTPS, backup/restore, quyền ghi file và vận hành máy chủ; không suy ra production đã triển khai từ việc build hoặc health local qua.
