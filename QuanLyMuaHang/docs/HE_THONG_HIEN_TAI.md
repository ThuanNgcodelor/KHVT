# Hệ thống hiện tại — Quản lý mua hàng KHVT

Cập nhật ngày 2026-10-10. Tài liệu mô tả code đã quan sát và kết quả đã có; không phải biên bản nghiệm thu. Backend đã qua package/32 test, gồm một test Redis thật được bật riêng. Frontend đã qua build, 7 unit tests và 42 trường hợp browser riêng biệt trên API giả lập sau các lượt sửa/chạy lại. Backend/frontend đã khởi động lại và health/HTTP đã qua; đăng nhập thật mới chưa xác nhận thành công. Các kết quả ngày 2026-10-09 bên dưới là lịch sử.

## Các thư mục ở gốc

| Thư mục/file | Vai trò |
|---|---|
| `AGENTS.md` | Điểm vào cho AI: quy tắc làm việc, bản đồ code, liên kết tài liệu |
| `docs/NGUYEN_TAC_TRUNG_THUC.md` | Cam kết về trung thực và minh bạch bằng chứng |
| `docs/HE_THONG_HIEN_TAI.md` | Bản đồ hiện trạng và các phần còn thiếu |
| `docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md` | Cổng chọn ứng dụng, quyền hiện có và hướng mở rộng Bán hàng/grant độc lập |
| `skills/khvt-ui/SKILL.md` | Skill giao diện riêng của repository, palette và quy tắc UI theo yêu cầu người dùng |
| `source/` | Backend và frontend của hệ thống mua hàng đang triển khai |
| `plan/` | Thiết kế và tiêu chí cần đạt; không mặc nhiên là trạng thái đã hoàn thành |
| `backend(Demo để lấy phần login)/` | Đã xóa theo yêu cầu người dùng; không thuộc build ứng dụng chính |
| `.codegraph/` | Chỉ mục để tra cứu code; không phải module chạy ứng dụng |

## Backend đã tới đâu

Backend có code cho các nhóm API sau:

| Nhóm | Chức năng có trong code | Giới hạn bằng chứng |
|---|---|---|
| Identity | CSRF, login/logout/me, đổi mật khẩu, bootstrap admin, tạo/sửa/reset tài khoản, gán vai trò, session revocation, audit | MockMvc kiểm tra login/CSRF/đổi mật khẩu/quyền/logout; Redis thật kiểm tra service thu hồi/giữ phiên theo principal. Chưa kiểm thử HTTP với hai phiên, đầy đủ khóa tài khoản hoặc last-admin/MySQL |
| Roles và cổng ứng dụng | Vai trò/quyền mặc định, đọc roles có permissions/moduleCodes, gán role; registry trả modules từ hợp permission hiệu lực trong phiên | MockMvc kiểm tra module/quyền hiệu lực, custom role và role ngừng; `/api/admin/roles` chỉ đọc, chưa có CRUD role/permission, grant riêng hoặc xin/duyệt quyền |
| Personnel | Danh sách/chi tiết/tạo/sửa/kích hoạt/ngừng nhân viên; phòng ban và chức vụ | Chưa kiểm thử đầy đủ các luồng API với MySQL/Redis và tất cả vai trò |
| Catalog và Pricing | Danh mục vật tư/NCC, tra cứu lịch sử/giá | Có controller/service; chưa nghiệm thu với dữ liệu nghiệp vụ |
| Procurement | Danh sách/chi tiết/tạo/sửa/hủy PO, revision và PDF | Có code và một số test tổng tiền/model; chưa thử đầy đủ luồng tạo/sửa/hủy/xuất tài liệu |
| Export | XLSX giá và PO | Có API; chưa kiểm tra file thực tế theo acceptance |
| Import | Legacy workbook preview/commit; yêu cầu mua từ spreadsheet/CSV/PDF text hoặc paste thành draft | Chưa nghiệm thu bằng fixtures và đối chiếu workbook; PDF scan chưa có OCR |
| Dashboard | `/api/dashboard` và service tổng hợp, yêu cầu quyền PO_READ + CATALOG_READ hoặc wildcard | Frontend gọi API thật; backend test kiểm tra ADMIN/VIEWER được đọc, HR không được đọc |

Kết quả mới đã xác nhận:

- `mvn package` dùng Maven 3.9.11/JDK 21 đã `BUILD SUCCESS` lúc **2026-10-10 00:29:59 +07:00**: 32 test, 0 failure/error/skipped; compile, tạo JAR và Spring Boot repackage đều qua. Log: `source/target/runtime/backend-verified-build.log`. Lần này đã bật test Redis bằng biến môi trường riêng.
- 31 test thông thường dùng unit/mock/H2; test API kiểm tra danh sách modules/permissions, tài khoản không có quyền, role ngừng, custom role và metadata moduleCodes trong API roles. Có test binding cấu hình indexed session và 5 unit tests cho service thu hồi phiên. Bộ test thông thường không kết nối MySQL/Redis; test Redis opt-in bị bỏ qua nếu không bật.
- Một test `RedisSessionRevocationIntegrationTest` chạy với Redis thật, tạo namespace UUID riêng và phiên tổng hợp. Test xác nhận Boot dùng `RedisIndexedSessionRepository`, giữ phiên chỉ định khi thu hồi các phiên cùng principal, thu hồi toàn bộ phiên của principal và giữ nguyên phiên của principal khác. Chỉ dọn key trong namespace test; không dùng `FLUSHDB` hoặc dữ liệu tài khoản thật. Đây là kiểm tra repository/service, chưa xác nhận luồng HTTP hai phiên trình duyệt, last-admin hoặc CRUD MySQL.
- Frontend `tsc -b`/Vite build cuối cùng và 7 Vitest tests đã qua. Auth có 20 trường hợp browser, nhân sự/tài khoản có 22, chạy Chromium desktop/mobile bằng API giả lập. Lượt auth đầu có 2 lỗi khoảng trắng footer; đã sửa và 2 trường hợp đó chạy lại qua. Đã xem ảnh desktop/mobile, sửa panel tài khoản bị hẹp trên mobile và chạy lại 2 trường hợp tạo tài khoản qua; sau chỉnh heading/checkbox, 2 trường hợp bố cục cổng/nhân sự chạy lại cũng qua. Tổng cộng 42 trường hợp riêng biệt đã qua, không cộng lượt chạy lại thành trường hợp mới. Log: `frontend-build.log`, `frontend-unit.log`, `frontend-auth-e2e-new.log`, `frontend-auth-e2e-retry.log`, `frontend-crud-e2e.log`, `frontend-account-layout-retry.log`, `frontend-personnel-layout-retry.log` trong `source/target/runtime/`. Ảnh `ui-*` ở cùng thư mục dùng dữ liệu tổng hợp.
- Runtime đã khởi động lại backend cổng 8080 và Vite cổng 5173; MySQL/Redis Compose healthy tại thời điểm kiểm tra. HTTP `/actuator/health` backend trả 200/`UP`, frontend trả 200 và health qua Vite proxy trả `UP`. Đây là kiểm tra khả năng khởi động/kết nối, chưa nghiệm thu API nghiệp vụ.
- Smoke đăng nhập thật mới thử một lần bằng thông tin bootstrap từ môi trường riêng và nhận HTTP 401; đã dừng, không retry hoặc đổi mật khẩu. Chưa xác định thông tin đăng nhập hiện hành nên không báo luồng login thật mới đã qua và không suy ra người dùng đã đổi mật khẩu. Auth JSON/module/permission đã được kiểm tra bằng H2/MockMvc; service session đã được kiểm tra riêng bằng Redis thật.

Cấu hình phiên Redis đã sửa sang `spring.session.redis.repository-type: indexed` để `SessionRevocationService` tra các phiên theo principal; repository mặc định trước đó không cung cấp index cần cho service này. Namespace mới là `qmh:session:indexed`, tách dữ liệu indexed khỏi key phiên cũ. Sau khi khởi động bản mới, người dùng phải đăng nhập lại; key cũ được để hết TTL, không xóa Redis hoặc database. Profile test H2 loại `SessionAutoConfiguration`; test Redis opt-in nạp cấu hình chính và chạy riêng. Cách bật test ở [README backend](../source/README.md).

Lịch sử 2026-10-09:

- Lúc 22:45:02 có 11 test qua; lúc 23:25 có 17 test, 0 failure/error/skipped, thêm 6 test `AuthApiIntegrationTest` trên H2/servlet session.
- `mvn package` lúc đó qua test nhưng lỗi repackage vì backend cũ giữ JAR trên Windows. Sau khi dừng đúng tiến trình, `mvn -DskipTests package` thành công lúc 23:27:30; `package.log` lưu package thành công, `build.log` lưu test qua kèm lỗi khóa file.
- Trong lần chạy tiếp theo đã xác nhận health `UP`, frontend HTTP 200, proxy health `UP`, CSRF/admin login/me/logout thành công. Tài khoản bootstrap vẫn bắt buộc đổi mật khẩu.
- Schema local ban đầu rỗng gặp lỗi migration V2 do tên `row_number`. Sau khi sửa trích dẫn identifier và sao lưu schema rỗng, database local được khởi tạo lại và ứng dụng đã khởi động qua Flyway.

Log build/test không chứng minh tiến trình hiện vẫn chạy hoặc thay đổi code sau thời điểm log đã được kiểm tra. Không quy đổi số test thành tỷ lệ hoàn thành. Acceptance đầy đủ nằm ở `plan/09-testing-and-acceptance.md`.

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
- Tách page/component/hook/service theo feature: `auth`, `portal`, `dashboard`, `personnel`, `identity-admin`. Layout có Header/Navigation/Sidebar/Footer riêng; `AppShell` ghép bố cục, `App.tsx` ghép providers/routes. Chi tiết ở `source/frontend/README.md`.
- Sau login/đổi mật khẩu tạm, mở `/modules` để chọn Mua hàng, Nhân sự hoặc Quản trị theo danh sách backend trả về. Mỗi ứng dụng có khu vực làm việc riêng và đường quay về cổng ứng dụng. Quyền được cấp qua role; wildcard `*` trong permission hiệu lực mở tất cả module, không bypass bằng tên role. Chưa có grant module độc lập, xin/duyệt quyền hay Bán hàng. Xem [thiết kế cổng ứng dụng và phân quyền](CONG_UNG_DUNG_VA_PHAN_QUYEN.md).
- Giao diện chuyển sang xanh rêu/xanh ngọc, nền trắng ngà và bảng nghiệp vụ gọn theo `skills/khvt-ui/SKILL.md`; bỏ màu cam. Footer auth/cổng ứng dụng/khu vực làm việc có © Bản quyền thuộc về KHVT | Cung cấp bởi link ThuanNgcodelor.
- Dashboard lấy số liệu thật; bỏ fallback demo và nhãn thành công cố định. Có loading/error/retry/empty, hết phiên chuyển về login và xóa cache nghiệp vụ. HR không gửi request tổng quan mua hàng.
- UI nhân sự có danh sách, tìm kiếm mã/tên, lọc trạng thái, phân trang, thêm/sửa, ngừng/kích hoạt nhân viên; thêm/sửa phòng ban và chức vụ qua API. Form phân biệt hồ sơ nhân viên với tài khoản đăng nhập. Kích hoạt nhân viên không tự mở lại tài khoản đã bị vô hiệu hóa.
- UI quản trị tài khoản có danh sách/phân trang, tạo/sửa tài khoản, liên kết nhân viên đang hoạt động, gán vai trò, thay trạng thái và đặt lại mật khẩu tạm. Role và danh sách permission dùng API đọc; chưa có ma trận chỉnh quyền. Thao tác thay quyền/trạng thái/reset có xác nhận và thông báo thu hồi phiên theo API. Không có nút xóa nhân viên/tài khoản.
- UI danh mục/giá, PO và import chưa triển khai; route hiện thông báo đang triển khai và chưa có thao tác ghi dữ liệu trên các màn đó.
- Build cuối cùng, 7 Vitest tests và 42 trường hợp Playwright desktop/mobile đã qua sau sửa/chạy lại ngày 2026-10-10. Browser tests dùng **API giả lập**, kiểm tra auth/cổng/quyền/menu, nhân viên/phòng ban/chức vụ/tài khoản, lỗi API, xác nhận thao tác, reset mật khẩu với HTTP 200 rỗng và phân trang. Kiểm tra ảnh đã dẫn tới sửa khoảng trắng footer, panel tài khoản mobile và heading/checkbox; các trường hợp liên quan chạy lại qua. Không coi các kết quả này là nghiệm thu CRUD MySQL/Redis. Lịch sử 2026-10-09 có 6 Vitest và 16 lượt Playwright qua, không dùng số cũ để xác nhận code mới.
- Smoke browser với MySQL/Redis thật ngày 2026-10-09 đã qua: admin bootstrap login, trang bắt buộc đổi mật khẩu, reload giữ phiên, bảo vệ dashboard và logout. Không đổi mật khẩu admin hay ghi dữ liệu nghiệp vụ trong smoke này. Phát hiện và sửa validation email frontend trước đó từ chối miền nội bộ được backend chấp nhận. Đây là bằng chứng lịch sử trước thay đổi indexed session; lần thử login mới ngày 2026-10-10 nhận 401 và đã dừng, chưa xác nhận auth thật của runtime mới.
- Một lần chạy browser song song gặp lỗi thiếu bộ nhớ của Node/Windows; cấu hình Playwright chạy một worker để giảm bộ nhớ.
- Đã có [skill KHVT UI](../skills/khvt-ui/SKILL.md) trong repository, tạo theo yêu cầu người dùng và được AGENTS.md tham chiếu. Đây là skill riêng của dự án, không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục. Plan 05/12 là tài liệu bổ trợ và đã cập nhật palette cùng cổng ứng dụng.

## Thư mục backend demo đã xóa

Theo kiểm tra hiện tại, `source/pom.xml`, Dockerfile, Compose, frontend package và Java imports không tham chiếu dự án `backend(Demo để lấy phần login)` hoặc package `com.booking.system`. Build ứng dụng chính đã chạy bằng POM của `source/`.

Vì vậy thư mục demo không cần để build/chạy ứng dụng chính hiện tại. Đây là kết luận về phụ thuộc kỹ thuật, không khẳng định đã chuyển hết mọi hành vi trong demo hoặc đã nghiệm thu auth/personnel.

Đã xóa đúng thư mục theo yêu cầu người dùng. Không thao tác database, container hoặc volume demo. Chỉ mục CodeGraph có thể còn tham chiếu lịch sử; không tự tạo lại index khi chưa được yêu cầu. `plan/14-demo-auth-personnel-port.md` vẫn là tài liệu mapping lịch sử, không phải phụ thuộc runtime.

## Công việc còn lại theo ưu tiên

1. Kiểm tra HTTP với hai phiên đăng nhập để xác nhận thu hồi quyền xuyên suốt controller/service/Redis; bổ sung last-admin/khóa tài khoản và integration MySQL cô lập. Test Redis repository/service đã qua nhưng chưa bao phủ các luồng này.
2. Kiểm thử PO, import, pricing, PDF/XLSX với dữ liệu thử và tiêu chí ở plan 09; không commit workbook thật để test.
3. Kiểm thử CRUD UI nhân sự/tài khoản với backend/MySQL/Redis trên dữ liệu thử; browser API giả lập đã qua. Tiếp tục triển khai danh mục/giá/PO/import theo từng luồng API.
4. Quyết định phạm vi CRUD role/permission và có cần grant module/xin duyệt quyền độc lập không; hiện admin cấp ứng dụng thông qua vai trò. Bán hàng mới là hướng mở rộng, chưa có code nghiệp vụ.
5. Thống nhất cấu trúc backend từng module; tiếp tục giữ frontend theo pages/hooks/components khi bổ sung tính năng.
6. Kiểm thử backup/restore, HTTPS, triển khai host và Cloudflare Tunnel trước khi dùng production.
