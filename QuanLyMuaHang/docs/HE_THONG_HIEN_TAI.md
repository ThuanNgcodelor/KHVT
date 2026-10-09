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
| `backend(Demo để lấy phần login)/` | Dự án tham khảo riêng; không nằm trong build của `source/` |
| `.codegraph/` | Chỉ mục để tra cứu code; không phải module chạy ứng dụng |

## Backend đã tới đâu

Backend có code cho các nhóm API sau:

| Nhóm | Chức năng có trong code | Giới hạn bằng chứng |
|---|---|---|
| Identity | CSRF, login/logout/me, đổi mật khẩu, bootstrap admin, tạo/sửa/reset tài khoản, gán vai trò, session revocation, audit | Đã thử CSRF/login/me/logout trong phiên làm việc; chưa có suite integration đầy đủ cho mọi quyền, khóa tài khoản và revoke |
| Roles | Vai trò/quyền mặc định trong migration, đọc danh sách roles, gán role cho tài khoản | `/api/admin/roles` hiện chỉ đọc; chưa có API tạo/sửa role và ma trận permission đầy đủ theo plan |
| Personnel | Danh sách/chi tiết/tạo/sửa/kích hoạt/ngừng nhân viên; phòng ban và chức vụ | Chưa kiểm thử đầy đủ các luồng API với MySQL/Redis và tất cả vai trò |
| Catalog và Pricing | Danh mục vật tư/NCC, tra cứu lịch sử/giá | Có controller/service; chưa nghiệm thu với dữ liệu nghiệp vụ |
| Procurement | Danh sách/chi tiết/tạo/sửa/hủy PO, revision và PDF | Có code và một số test tổng tiền/model; chưa thử đầy đủ luồng tạo/sửa/hủy/xuất tài liệu |
| Export | XLSX giá và PO | Có API; chưa kiểm tra file thực tế theo acceptance |
| Import | Legacy workbook preview/commit; yêu cầu mua từ spreadsheet/CSV/PDF text hoặc paste thành draft | Chưa nghiệm thu bằng fixtures và đối chiếu workbook; PDF scan chưa có OCR |
| Dashboard | `/api/dashboard` và service tổng hợp | Có code; chưa kiểm thử tích hợp giao diện |

Kết quả đã có:

- Log `source/target/runtime/build.log` ghi `BUILD SUCCESS`, 11 test, 0 failure, 0 error, 0 skipped; thời điểm kết thúc là 2026-10-09 22:45:02, múi giờ Asia/Saigon.
- Sáu class test: model PO, quyền domain UserAccount, parser số, tổng tiền PO, Spring context và chuẩn hóa text. Spring context dùng H2, không phải MySQL/Redis thật.
- Trong lần chạy tiếp theo đã xác nhận health `UP`, frontend HTTP 200, proxy health `UP`, CSRF/admin login/me/logout thành công. Tài khoản bootstrap vẫn bắt buộc đổi mật khẩu.
- Schema local ban đầu rỗng gặp lỗi migration V2 do tên `row_number`. Sau khi sửa trích dẫn identifier và sao lưu schema rỗng, database local được khởi tạo lại và ứng dụng đã khởi động qua Flyway.

Những kết quả trên là lịch sử kiểm tra của phiên làm việc, không chứng minh tiến trình hiện vẫn chạy hoặc mọi thay đổi code sau đó đã được build. Không quy đổi 11 test thành tỷ lệ hoàn thành. Acceptance đầy đủ nằm ở `plan/09-testing-and-acceptance.md`.

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

- Đã có React/TypeScript/Vite/Tailwind và khung các màn tổng quan, mua hàng, giá, import, nhân sự.
- `App.tsx` đang chứa nhiều màn trong một file. Có dữ liệu mẫu, nhãn API cố định và nhiều nút chưa có xử lý nghiệp vụ.
- Dashboard thử gọi API rồi fallback về demo. Điều này không xác nhận toàn bộ giao diện đã nối backend.
- Chưa có màn login/đổi mật khẩu, bootstrap auth, CSRF cho mutation và đầy đủ CRUD/PO/import theo kế hoạch.
- Build frontend đã thành công trong phiên chạy trước. Chưa có bằng chứng đã chạy Playwright/E2E.
- Chưa tìm thấy `SKILL.md` frontend chuyên biệt trong repository hoặc skill catalog đang được cung cấp. Chưa tạo/cài skill frontend trong lần kiểm kê này.
- Hướng dẫn UI hiện nằm ở `plan/05-react-vite-tailwind-ui.md` và `plan/12-react-vite-playwright-openai.md`. Đây là tài liệu thiết kế, không phải skill đã cài. Nếu bổ sung skill sau này, ghi rõ tên, path và nguồn; skill riêng của dự án không được gọi là skill chính thức của OpenAI.

## Thư mục backend demo có thể bỏ không

Theo kiểm tra hiện tại, `source/pom.xml`, Dockerfile, Compose, frontend package và Java imports không tham chiếu dự án `backend(Demo để lấy phần login)` hoặc package `com.booking.system`. Build ứng dụng chính đã chạy bằng POM của `source/`.

Vì vậy thư mục demo không cần để build/chạy ứng dụng chính hiện tại. Đây là kết luận về phụ thuộc kỹ thuật, không khẳng định đã chuyển hết mọi hành vi trong demo hoặc đã nghiệm thu auth/personnel.

Nếu bỏ thư mục demo, lưu một bản tham khảo khi chưa đối chiếu xong `plan/14-demo-auth-personnel-port.md`. Chỉ thao tác đúng thư mục được yêu cầu; không tác động database, container hoặc volume của dự án demo. Chỉ mục CodeGraph có thể cần cập nhật sau khi code tham khảo được bỏ; không tự tạo lại index nếu người dùng chưa yêu cầu.

## Công việc còn lại theo ưu tiên

1. Bổ sung test integration cho auth/CSRF/permission/session revocation/last-admin; kiểm tra thay đổi mật khẩu đầu tiên.
2. Kiểm thử PO, import, pricing, PDF/XLSX với dữ liệu thử và tiêu chí ở plan 09; không commit workbook thật để test.
3. Triển khai login/đổi mật khẩu và nối từng feature frontend tới API; bỏ việc hiển thị thành công giả hoặc fallback demo không rõ ràng.
4. Quyết định phạm vi quản trị role/permission vì hiện chưa đủ các API được mô tả trong plan.
5. Thống nhất cấu trúc backend từng module và tách frontend theo feature; cập nhật tài liệu cùng code.
6. Kiểm thử backup/restore, HTTPS, triển khai host và Cloudflare Tunnel trước khi dùng production.
