# Hệ thống hiện tại — Quản lý mua hàng KHVT

Kiểm kê ngày 2026-10-09. Tài liệu mô tả code đã quan sát và kết quả đã có; không phải biên bản nghiệm thu. Khi code thay đổi, kiểm tra lại trước khi dùng các thông tin này làm kết luận.

## Các thư mục ở gốc

| Thư mục/file | Vai trò |
|---|---|
| `AGENTS.md` | Điểm vào cho AI: quy tắc làm việc, bản đồ code, liên kết tài liệu |
| `docs/NGUYEN_TAC_TRUNG_THUC.md` | Cam kết về trung thực và minh bạch bằng chứng |
| `docs/HE_THONG_HIEN_TAI.md` | Bản đồ hiện trạng và các phần còn thiếu |
| `source/` | Backend và frontend của hệ thống mua hàng đang triển khai |
| `plan/` | Thiết kế và tiêu chí cần đạt; không mặc nhiên là trạng thái đã hoàn thành |
| `backend(Demo để lấy phần login)/` | Đã xóa theo yêu cầu người dùng; không thuộc build ứng dụng chính |
| `.codegraph/` | Chỉ mục để tra cứu code; không phải module chạy ứng dụng |

## Backend đã tới đâu

Backend có code cho các nhóm API sau:

| Nhóm | Chức năng có trong code | Giới hạn bằng chứng |
|---|---|---|
| Identity | CSRF, login/logout/me, đổi mật khẩu, bootstrap admin, tạo/sửa/reset tài khoản, gán vai trò, session revocation, audit | Có test MockMvc cho login/CSRF/đổi mật khẩu/quyền/logout; chưa kiểm thử đầy đủ khóa tài khoản, last-admin và revoke Redis |
| Roles | Vai trò/quyền mặc định trong migration, đọc danh sách roles, gán role cho tài khoản | `/api/admin/roles` hiện chỉ đọc; chưa có API tạo/sửa role và ma trận permission đầy đủ theo plan |
| Personnel | Danh sách/chi tiết/tạo/sửa/kích hoạt/ngừng nhân viên; phòng ban và chức vụ | Chưa kiểm thử đầy đủ các luồng API với MySQL/Redis và tất cả vai trò |
| Catalog và Pricing | Danh mục vật tư/NCC, tra cứu lịch sử/giá | Có controller/service; chưa nghiệm thu với dữ liệu nghiệp vụ |
| Procurement | Danh sách/chi tiết/tạo/sửa/hủy PO, revision và PDF | Có code và một số test tổng tiền/model; chưa thử đầy đủ luồng tạo/sửa/hủy/xuất tài liệu |
| Export | XLSX giá và PO | Có API; chưa kiểm tra file thực tế theo acceptance |
| Import | Legacy workbook preview/commit; yêu cầu mua từ spreadsheet/CSV/PDF text hoặc paste thành draft | Chưa nghiệm thu bằng fixtures và đối chiếu workbook; PDF scan chưa có OCR |
| Dashboard | `/api/dashboard` và service tổng hợp, yêu cầu quyền PO_READ + CATALOG_READ hoặc wildcard | Frontend gọi API thật; backend test kiểm tra ADMIN/VIEWER được đọc, HR không được đọc |

Kết quả đã có:

- Lần build cũ 2026-10-09 22:45:02 có 11 test qua. Lần kiểm tra mới lúc 23:25 có 17 test, 0 failure/error/skipped: thêm 6 test `AuthApiIntegrationTest`. Context/test API dùng H2 và servlet session; không kiểm chứng MySQL/Redis thật hay session revocation qua Redis.
- `mvn package` lần mới chạy test thành công rồi lỗi repackage vì tiến trình backend cũ giữ JAR trên Windows. Sau khi dừng đúng tiến trình, `mvn -DskipTests package` đã `BUILD SUCCESS` lúc 23:27:30; kết quả ở `source/target/runtime/package.log`. `build.log` lưu lần có 17 test qua và lỗi khóa file; không gọi log đó là build thành công.
- Trong lần chạy tiếp theo đã xác nhận health `UP`, frontend HTTP 200, proxy health `UP`, CSRF/admin login/me/logout thành công. Tài khoản bootstrap vẫn bắt buộc đổi mật khẩu.
- Schema local ban đầu rỗng gặp lỗi migration V2 do tên `row_number`. Sau khi sửa trích dẫn identifier và sao lưu schema rỗng, database local được khởi tạo lại và ứng dụng đã khởi động qua Flyway.

Những kết quả trên là lịch sử kiểm tra của phiên làm việc, không chứng minh tiến trình hiện vẫn chạy hoặc mọi thay đổi code sau đó đã được build. Không quy đổi số test thành tỷ lệ hoàn thành. Acceptance đầy đủ nằm ở `plan/09-testing-and-acceptance.md`.

## Kiến trúc thực tế và lý do nhìn rối

Thiết kế trong plan là **DDD thực dụng theo modular monolith**: một backend Spring Boot, chia theo nghiệp vụ, một MySQL và Redis cho session. Chưa tách microservice.

Code hiện chưa thống nhất hoàn toàn theo thiết kế đó. Có hai cách tổ chức cùng tồn tại:

```text
source/src/main/java/com/example/quanlymuahang/
  identity/, personnel/       chia domain/application/infrastructure/web
  catalog/, pricing/,
  procurement/, importing/,
  exporting/, dashboard/     chủ yếu application và web
  domain/                    entity dùng chung cho vật tư, NCC, lịch sử, PO, import
  repository/                repository JPA dùng chung của các entity trên
  service/                   tiện ích TextNormalizer
  web/                       DashboardController đang nằm ngoài module dashboard
  sharedkernel/, config/     thành phần dùng chung và cấu hình
```

Luồng thực tế phổ biến:

```text
React -> REST controller (web) -> service (application) -> repository JPA -> MySQL
                |
                +-> Spring Security + session cookie + Redis
```

Ý nghĩa tên tầng:

| Tầng | Cách hiểu |
|---|---|
| `web` | Nhận request HTTP, kiểm tra DTO/quyền và trả kết quả |
| `application` | Điều phối thao tác nghiệp vụ và transaction |
| `domain` | Mô hình/quy tắc nghiệp vụ; nhiều entity hiện còn có annotation JPA |
| `infrastructure` / `repository` | Lưu dữ liệu, tích hợp Spring Security/Redis và các dịch vụ kỹ thuật |

Ví dụ: `UserAdministrationService` gọi trực tiếp `UserAccountJpaRepository` và thao tác `UserAccountEntity`. Interface domain `UserAccountRepository` và model thuần `UserAccount` tồn tại riêng nhưng chưa được nối vào luồng service này. Tình trạng tương tự có ở Personnel. Vì vậy người đọc thấy hai model gần cùng tên mà không thấy một đường chạy thống nhất.

Hướng dọn cấu trúc hợp lý là thống nhất dần theo từng module, gom entity/repository đang dùng về module sở hữu và xử lý model/port không được dùng sau khi kiểm tra callers. Không xóa hàng loạt `domain/` hay `repository/`: các service hiện phụ thuộc vào chúng. Không cần thêm nhiều tầng để làm một CRUD đơn giản khó đọc hơn.

## Frontend và skill giao diện

- Đã triển khai login, đổi mật khẩu lần đầu, khôi phục phiên, logout và CSRF cho mutation; session qua cookie HttpOnly, không lưu token localStorage.
- Tách `features/auth/pages`, `features/auth/hooks`, `features/auth/components` và `features/dashboard/pages`, `features/dashboard/hooks`. Layout có Header/Navigation/Sidebar/Footer riêng; `AppShell` ghép bố cục, `App.tsx` ghép providers/routes. Chi tiết ở `source/frontend/README.md`.
- Dashboard lấy số liệu thật; bỏ fallback demo và nhãn thành công cố định. Có loading/error/retry/empty, hết phiên chuyển về login và xóa cache nghiệp vụ. HR không gửi request tổng quan mua hàng.
- UI CRUD nhân sự/tài khoản, danh mục/giá, PO và import chưa triển khai. Các route này hiện thông báo đang triển khai; chưa có thao tác ghi dữ liệu.
- Đã chạy `npm.cmd run build`, 6 test Vitest và 16 lượt Playwright thành công (8 tình huống trên Chromium desktop/mobile). Playwright dùng **API giả lập**, không xác nhận tích hợp thật MySQL/Redis; kiểm tra login, đổi mật khẩu, reload, logout, quyền route/menu, lỗi API, hết phiên và menu mobile/Escape.
- Chưa tìm thấy `SKILL.md` frontend chuyên biệt trong repository hoặc skill catalog đang được cung cấp. Chưa tạo/cài skill frontend trong lần kiểm kê này.
- Hướng dẫn UI hiện nằm ở `plan/05-react-vite-tailwind-ui.md` và `plan/12-react-vite-playwright-openai.md`. Đây là tài liệu thiết kế, không phải skill đã cài. Nếu bổ sung skill sau này, ghi rõ tên, path và nguồn; skill riêng của dự án không được gọi là skill chính thức của OpenAI.

## Thư mục backend demo đã xóa

Theo kiểm tra hiện tại, `source/pom.xml`, Dockerfile, Compose, frontend package và Java imports không tham chiếu dự án `backend(Demo để lấy phần login)` hoặc package `com.booking.system`. Build ứng dụng chính đã chạy bằng POM của `source/`.

Vì vậy thư mục demo không cần để build/chạy ứng dụng chính hiện tại. Đây là kết luận về phụ thuộc kỹ thuật, không khẳng định đã chuyển hết mọi hành vi trong demo hoặc đã nghiệm thu auth/personnel.

Đã xóa đúng thư mục theo yêu cầu người dùng. Không thao tác database, container hoặc volume demo. Chỉ mục CodeGraph có thể còn tham chiếu lịch sử; không tự tạo lại index khi chưa được yêu cầu. `plan/14-demo-auth-personnel-port.md` vẫn là tài liệu mapping lịch sử, không phải phụ thuộc runtime.

## Công việc còn lại theo ưu tiên

1. Bổ sung kiểm tra Redis session revocation/last-admin/khóa tài khoản và integration MySQL/Redis cô lập; các test auth mới chưa bao phủ các trường hợp này.
2. Kiểm thử PO, import, pricing, PDF/XLSX với dữ liệu thử và tiêu chí ở plan 09; không commit workbook thật để test.
3. Triển khai UI nhân sự/tài khoản rồi các feature danh mục/giá/PO/import; nối API và kiểm thử từng luồng.
4. Quyết định phạm vi quản trị role/permission vì hiện chưa đủ các API được mô tả trong plan.
5. Thống nhất cấu trúc backend từng module; tiếp tục giữ frontend theo pages/hooks/components khi bổ sung tính năng.
6. Kiểm thử backup/restore, HTTPS, triển khai host và Cloudflare Tunnel trước khi dùng production.
