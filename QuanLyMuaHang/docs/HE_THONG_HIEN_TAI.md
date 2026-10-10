# Hệ thống hiện tại — Quản lý mua hàng KHVT

Cập nhật ngày 2026-10-10. Tài liệu mô tả code và bằng chứng kiểm tra, không phải biên bản nghiệm thu. Nhóm mua hàng đã có UI danh mục/giá/PO/import và mẫu PDF. Backend package mới qua 36 test thực thi, một test Redis opt-in bỏ qua; frontend build, 7 unit tests và 58 trường hợp browser riêng biệt đã qua sau chạy lại. MCP mở trình duyệt và chạy UI với API giả lập đã qua. Workbook thật đã preview/commit trên H2 riêng; **chưa xác nhận import trên MySQL**: lượt driver trước bị ngắt bởi Docker/WSL. Lúc 11:08, MySQL/Redis đã healthy trở lại và backend khởi động qua; sau kiểm tra đã dừng riêng backend để nhường cổng 8080 cho VS Code F5. Frontend 5173 vẫn chạy.

## Bằng chứng mới của nhóm mua hàng

| Kiểm tra | Kết quả và giới hạn |
|---|---|
| Backend | JDK 21/Maven 3.9.11 `mvn package`: BUILD SUCCESS lúc **2026-10-10 09:53:20 +07:00**. 37 test được liệt kê, 36 thực thi qua, một test Redis thật bỏ qua; lần này bật workbook opt-in. Log `source/target/runtime/backend-purchasing-workbook-final.log`. Test mới kiểm tra GET PDF không phát hành, PDF cũ của PO đã hủy, font Unicode/ngắt trang, preview lặp giữ cảnh báo/nhóm PO và workbook thật. Khi không bật cả hai opt-in, có 35 test thực thi/2 bỏ qua như lượt 09:44. |
| Frontend | TypeScript/Vite build và 7 Vitest tests qua. Playwright chạy một worker: 56/58 qua lượt đầu; hai selector tên NCC không khớp cell có thêm địa chỉ, đã sửa selector và chạy lại 2/2 qua. Tổng 58 trường hợp riêng biệt, gồm auth/cổng, nhân sự/tài khoản và 16 trường hợp mua hàng trên desktop/mobile. API **giả lập**. Log `frontend-purchasing-browser.log`, `frontend-purchasing-browser-retry.log`, `frontend-purchasing-unit.log`, `frontend-purchasing-final-build.log`. |
| MCP | Đã cài Playwright MCP, đăng ký `khvt-browser` cho Codex và có cấu hình VS Code. Initialize/tools/list, browser navigation, cổng ứng dụng, nhóm mua hàng, lưu/phát hành và viewport mobile đã qua bằng kết nối stdio thật; **dữ liệu API tổng hợp**. Log `mcp-ui-smoke.log`. Mở phiên Codex mới để nạp tool vừa đăng ký. |
| HTTP/MySQL/Redis | Trong các lượt driver tạo schema `qmh_test_*` riêng trước khi Docker dừng, đã qua login/đổi mật khẩu/CSRF, nhân sự/tài khoản, danh mục, PO/tổng tiền số lượng chữ/phát hành/PDF/XLSX/sửa revision/hủy, cấp số PO đồng thời, tra giá theo currency, thu hồi hai phiên khi đổi role/ngừng nhân viên, tự khóa và last-admin. Đây là **lượt kiểm tra một phần**: driver chưa hoàn tất browser/workbook/report cuối cùng. Không đổi mật khẩu hoặc database ứng dụng. Script tái chạy tại `tools/local-test/run.mjs`. |
| Workbook thật trên H2 | `LegacyWorkbookLocalTest` có trong package cuối đã qua: H2 file UUID riêng, đếm source độc lập, preview không ghi PO/lịch sử; hai commit đồng thời cho một lô → một thành công/một `IMPORT_ALREADY_PROCESSED`; đối chiếu số bản ghi; preview sau commit giữ số nhóm/cảnh báo và không tạo lô trùng. Báo cáo `workbook-h2-report.json` có `completed: true`. Không kiểm chứng MySQL/Redis/Flyway. |

Workbook `QUANLYMUAHANGKHVT.xlsx` ở thư mục cha đã xác nhận **NCC 279 dòng, LICH_SU 10.561 dòng, DON_HANG 850 dòng / 262 nhóm PO**; tổng 11.690 dòng preview, 0 lỗi, 4.803 dòng cảnh báo. Commit H2 tạo 10.561 bản ghi lịch sử và 262 PO. Có 4.275 cảnh báo thiếu mã NCC, 624 thiếu mã hàng, 38 thiếu currency và 2 VAT chưa xác định; một dòng có thể có nhiều cảnh báo nên không cộng số cảnh báo thành số dòng. VAT chưa biết giữ NULL; không sửa nguồn để ép commit. CONFIG không đọc/import; file gốc kiểm tra checksum trước/sau không đổi. Báo cáo `source/target/runtime/workbook-h2-report.json` ghi đường dẫn database H2 giữ lại để đối chiếu. Số liệu này thay ước lượng LICH_SU 10.563 trong tài liệu khảo sát cũ.

**Vận hành kiểm tra lại lúc 2026-10-10 11:08 +07:00:** Compose MySQL/Redis healthy. Log VS Code 11:01 có MySQL `1045` (`Access denied`); cấu hình ở thư mục cha `KHVT/.vscode/launch.json` trỏ `.env` vào `KHVT/.env` không tồn tại. Đã sửa `cwd`/`envFile` tới `QuanLyMuaHang/source`, thêm JDK 21/heap 384 MB/font Windows, đồng thời thêm profile tương ứng ở [launch.json của repository](../.vscode/launch.json) khi mở riêng thư mục dự án. Không sao chép hoặc đổi mật khẩu. Launcher `tools/start-local.ps1 -SkipBuild` khởi động JAR thành công: backend và health qua Vite proxy đều HTTP 200/`UP`; frontend HTTP 200. Báo cáo `source/target/runtime/vscode-startup-check.json`. Sau xác nhận đã dừng đúng backend kiểm tra, giữ frontend/MySQL/Redis; cổng 8080 trống cho F5. Đây là kiểm tra khởi động bằng cùng `.env`, chưa thao tác F5 trực tiếp hay kiểm thử lại nghiệp vụ/workbook MySQL.

Docker Desktop/WSL trước đó từng báo bootstrap `0xc00000fd` hoặc mất pipe `dockerDesktopLinuxEngine`; Windows Application Event 1000 xác nhận `wslservice.exe`/`wslhost.exe` crash. Nguyên nhân gốc chưa xác định; công cụ trên máy cũng có lúc không tạo được PowerShell vì Windows báo paging file quá nhỏ (`1455`). Không reset database, xóa volume hoặc dừng Java của IDE. Driver MySQL/workbook vẫn cần chạy lại riêng; xem [kiểm thử local](../tools/local-test/README.md).

Đã sửa hai vấn đề tìm thấy khi test: Hibernate chèn dòng PO mới trước khi xóa dòng cũ gây trùng `(purchase_order_id,line_no)` trên MySQL (nay flush xóa trong cùng transaction); preview file trùng checksum mất số cảnh báo/nhóm PO (nay tính lại từ staging). Sửa/hủy/phát hành khóa bản ghi PO; commit khóa lô import trong transaction để không commit đồng thời hai lần. Phát hành chuyển sang POST có CSRF/quyền ghi; GET PDF chỉ tải tài liệu đã có, tránh người chỉ đọc làm thay đổi trạng thái/giá.

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
| Identity | CSRF, login/logout/me, đổi mật khẩu, bootstrap admin, tạo/sửa/reset tài khoản, gán vai trò, session revocation, audit | MockMvc và Redis opt-in; driver MySQL/Redis đã kiểm tra HTTP hai phiên và last-admin trong lượt chạy một phần. Chưa nghiệm thu toàn bộ vai trò hoặc production. |
| Roles và cổng ứng dụng | Vai trò/quyền mặc định, đọc roles có permissions/moduleCodes, gán role; registry trả modules từ hợp permission hiệu lực trong phiên | MockMvc kiểm tra module/quyền hiệu lực, custom role và role ngừng; `/api/admin/roles` chỉ đọc, chưa có CRUD role/permission, grant riêng hoặc xin/duyệt quyền |
| Personnel | Danh sách/chi tiết/tạo/sửa/kích hoạt/ngừng nhân viên; phòng ban và chức vụ | Chưa kiểm thử đầy đủ các luồng API với MySQL/Redis và tất cả vai trò |
| Catalog và Pricing | Danh mục vật tư/NCC, tra cứu lịch sử/giá | Có controller/service; chưa nghiệm thu với dữ liệu nghiệp vụ |
| Procurement | Danh sách/chi tiết/tạo/sửa/hủy/phát hành PO, revision và PDF | Test document/model và driver MySQL một phần đã qua luồng chính; cần nghiệm thu mẫu chứng từ/nghiệp vụ và các trường hợp tranh chấp đồng thời. |
| Export | XLSX giá và PO | Có API; chưa kiểm tra file thực tế theo acceptance |
| Import | Legacy workbook preview/commit; yêu cầu mua từ spreadsheet/CSV/PDF text hoặc paste thành draft | Workbook thật đã đối chiếu/commit H2; MySQL còn chặn Docker. PDF scan chưa có OCR. |
| Dashboard | `/api/dashboard` và service tổng hợp, yêu cầu quyền PO_READ + CATALOG_READ hoặc wildcard | Frontend gọi API thật; backend test kiểm tra ADMIN/VIEWER được đọc, HR không được đọc |

Lịch sử kiểm tra trước khi thêm UI mua hàng (các kết luận chưa kiểm thử ở đây chỉ nói về thời điểm đó):

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
- Tách page/component/hook/service theo feature: `auth`, `portal`, `dashboard`, `personnel`, `identity-admin`, `catalog`, `pricing`, `procurement`, `imports`. Layout có Header/Navigation/Sidebar/Footer riêng; `AppShell` ghép bố cục, `App.tsx` ghép providers/routes. Chi tiết ở `source/frontend/README.md`.
- Sau login/đổi mật khẩu tạm, mở `/modules` để chọn Mua hàng, Nhân sự hoặc Quản trị theo danh sách backend trả về. Mỗi ứng dụng có khu vực làm việc riêng và đường quay về cổng ứng dụng. Quyền được cấp qua role; wildcard `*` trong permission hiệu lực mở tất cả module, không bypass bằng tên role. Chưa có grant module độc lập, xin/duyệt quyền hay Bán hàng. Xem [thiết kế cổng ứng dụng và phân quyền](CONG_UNG_DUNG_VA_PHAN_QUYEN.md).
- Giao diện chuyển sang xanh rêu/xanh ngọc, nền trắng ngà và bảng nghiệp vụ gọn theo `skills/khvt-ui/SKILL.md`; bỏ màu cam. Footer auth/cổng ứng dụng/khu vực làm việc có © Bản quyền thuộc về KHVT | Cung cấp bởi link ThuanNgcodelor.
- Dashboard lấy số liệu thật; bỏ fallback demo và nhãn thành công cố định. Có loading/error/retry/empty, hết phiên chuyển về login và xóa cache nghiệp vụ. HR không gửi request tổng quan mua hàng.
- UI nhân sự có danh sách, tìm kiếm mã/tên, lọc trạng thái, phân trang, thêm/sửa, ngừng/kích hoạt nhân viên; thêm/sửa phòng ban và chức vụ qua API. Form phân biệt hồ sơ nhân viên với tài khoản đăng nhập. Kích hoạt nhân viên không tự mở lại tài khoản đã bị vô hiệu hóa.
- UI quản trị tài khoản có danh sách/phân trang, tạo/sửa tài khoản, liên kết nhân viên đang hoạt động, gán vai trò, thay trạng thái và đặt lại mật khẩu tạm. Role và danh sách permission dùng API đọc; chưa có ma trận chỉnh quyền. Thao tác thay quyền/trạng thái/reset có xác nhận và thông báo thu hồi phiên theo API. Không có nút xóa nhân viên/tài khoản.
- UI danh mục vật tư/NCC có tìm kiếm/lọc/phân trang/thêm/sửa/ngừng/kích hoạt. UI giá có lịch sử/giá gần nhất theo currency, chuyển sang lập đơn và XLSX. UI PO có giỏ từ nhiều nguồn, số lượng chữ, tách nhóm NCC/lưu từng nháp, sửa có lý do/revision, hủy có xác nhận, phát hành/tải PDF phiên bản và XLSX. Import có preview file/dán dữ liệu → chọn dòng → lập đơn; workbook → số dòng/cảnh báo/checksum → xác nhận commit. Giỏ chưa lưu nằm trong bộ nhớ; giới hạn 200 dòng/đơn, chưa có idempotency key cho tạo PO. Xem [đối chiếu ứng dụng cũ](DOI_CHIEU_UNG_DUNG_CU.md).
- Build cuối cùng, 7 Vitest tests và 58 trường hợp Playwright desktop/mobile đã qua sau sửa/chạy lại ngày 2026-10-10. Browser tests dùng **API giả lập**; nhóm mua hàng bổ sung 16 trường hợp ngoài 42 auth/nhân sự/tài khoản. Sau chỉnh breadcrumb, 16 trường hợp mua hàng đã chạy lại qua. Không coi các kết quả này là nghiệm thu CRUD MySQL/Redis. Lịch sử 2026-10-09 có 6 Vitest và 16 lượt Playwright qua, không dùng số cũ để xác nhận code mới.
- Smoke browser với MySQL/Redis thật ngày 2026-10-09 đã qua: admin bootstrap login, trang bắt buộc đổi mật khẩu, reload giữ phiên, bảo vệ dashboard và logout. Không đổi mật khẩu admin hay ghi dữ liệu nghiệp vụ trong smoke này. Phát hiện và sửa validation email frontend trước đó từ chối miền nội bộ được backend chấp nhận. Đây là bằng chứng lịch sử trước thay đổi indexed session; lần thử login mới ngày 2026-10-10 nhận 401 và đã dừng, chưa xác nhận auth thật của runtime mới.
- Một lần chạy browser song song gặp lỗi thiếu bộ nhớ của Node/Windows; cấu hình Playwright chạy một worker để giảm bộ nhớ.
- Đã có [skill KHVT UI](../skills/khvt-ui/SKILL.md) trong repository, tạo theo yêu cầu người dùng và được AGENTS.md tham chiếu. Đây là skill riêng của dự án, không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục. Plan 05/12 là tài liệu bổ trợ và đã cập nhật palette cùng cổng ứng dụng.

## Thư mục backend demo đã xóa

Theo kiểm tra hiện tại, `source/pom.xml`, Dockerfile, Compose, frontend package và Java imports không tham chiếu dự án `backend(Demo để lấy phần login)` hoặc package `com.booking.system`. Build ứng dụng chính đã chạy bằng POM của `source/`.

Vì vậy thư mục demo không cần để build/chạy ứng dụng chính hiện tại. Đây là kết luận về phụ thuộc kỹ thuật, không khẳng định đã chuyển hết mọi hành vi trong demo hoặc đã nghiệm thu auth/personnel.

Đã xóa đúng thư mục theo yêu cầu người dùng. Không thao tác database, container hoặc volume demo. Chỉ mục CodeGraph có thể còn tham chiếu lịch sử; không tự tạo lại index khi chưa được yêu cầu. `plan/14-demo-auth-personnel-port.md` vẫn là tài liệu mapping lịch sử, không phải phụ thuộc runtime.

## Công việc còn lại theo ưu tiên

1. Phục hồi Docker/WSL và khởi động runtime chính; chạy driver MySQL/Redis đến hết lượt, gồm workbook trên schema thử riêng theo yêu cầu người dùng. H2 workbook đã qua, không thay kiểm chứng MySQL/Flyway.
2. Đối chiếu 4.803 dòng cảnh báo nguồn, mẫu PDF/VAT/currency với nghiệp vụ và tiêu chí plan 09. Không commit vào database ứng dụng nếu chưa backup/đối chiếu.
3. Kiểm thử browser với backend thật trên dữ liệu thử, các role và file yêu cầu Excel/CSV/PDF text; bổ sung ràng buộc chống PO trùng khi mất kết nối và cải thiện trải nghiệm giỏ chưa lưu. Browser fixture/MCP không thay nghiệm thu nghiệp vụ.
4. Quyết định phạm vi CRUD role/permission và có cần grant module/xin duyệt quyền độc lập không; hiện admin cấp ứng dụng thông qua vai trò. Bán hàng mới là hướng mở rộng, chưa có code nghiệp vụ.
5. Thống nhất cấu trúc backend từng module; tiếp tục giữ frontend theo pages/hooks/components khi bổ sung tính năng.
6. Kiểm thử backup/restore, HTTPS, triển khai host và Cloudflare Tunnel trước khi dùng production.
