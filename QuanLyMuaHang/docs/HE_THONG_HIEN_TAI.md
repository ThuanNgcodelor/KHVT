# Hệ thống hiện tại — Quản lý mua hàng KHVT

Cập nhật ngày 2026-10-11. Tài liệu mô tả code và bằng chứng kiểm tra, không phải biên bản nghiệm thu. Import workbook đã qua kiểm thử backend và MySQL/Redis riêng: 11.690 dòng nguồn, 91.526 trường, 0 sai lệch; đủ 10.561 lịch sử, 262 PO và 850 dòng hàng. Có CSV cảnh báo đầy đủ, đối chiếu trước commit, rollback khi lệch và bảo vệ nhập đồng thời. JAR mới đã kiểm tra trên 8080, V6 thành công và số bản ghi trước/sau giữ nguyên; server sau đó đã dừng theo yêu cầu người dùng để lập kế hoạch tiếp. Thông tin thiếu hoặc giả định trong nguồn vẫn cần xác minh nghiệp vụ. Chưa kết nối Cloudflare hoặc nghiệm thu production.

## Kế hoạch bổ sung ngày 12/10/2026 — chỉ phần Mua hàng

Đã lập [kế hoạch 17](../plan/17-mua-hang-gon-tra-ncc-va-mau-po.md) sau đọc code, xem hai PDF và kiểm tra workbook CVT. Đã làm PDF/XLSX đơn gửi NCC theo 6 cột và ngày `dd/MM/yyyy`; tổng tra cứu nội bộ giữ nguyên. Parser PCVT bỏ qua hàng ẩn và báo số đã bỏ, lookup giá/NCC có trước khi xác nhận, tra NCC hiển thị mặt hàng và lịch sử trực tiếp. Ngày 2026-10-13, thanh điều hướng Mua hàng có nút thu gọn/mở rộng lưu riêng trong localStorage; Quản trị không bị thu gọn và mobile vẫn dùng drawer. Màn PCVT đã thu gọn danh sách chứng từ cũ, chuyển mã/ghi chú/cảnh báo vào chi tiết dòng, đưa nguồn về cuối và bỏ input SL bằng chữ trong khi vẫn giữ dữ liệu nguồn.

Kiểm tra lượt giao diện cuối: TypeScript/Vite build qua, Vitest 10/10; Playwright nhóm chứng từ và thanh điều hướng 24/24 qua trên Chromium desktop/mobile. Launcher profile `dev` đã build lại và chạy tại localhost:8080; health `UP`, `/login` 200, bundle mới được phục vụ. Backend toàn bộ suite trước lượt UI: 150 test, 0 lỗi, 7 bỏ qua. Các browser test dùng API fixtures. Plan 17 **chưa hoàn tất**: còn bulk catalog, workspace draft/version/conflict, chuyển PCVT sang PO có phân bổ/idempotency, tra NCC trên dữ liệu thật và kiểm tra render PDF/XLSX/MySQL theo đủ phạm vi. Schema ứng dụng đã được ghi nhận ở v10 trước lượt này; không có migration mới.

Ngày 12/10/2026 lúc 23:42 +07:00 đã chạy launcher profile `dev` tại `http://localhost:8080`. Health `UP`; `/login` và bundle JS trả 200; API tra NCC anonymous trả 401 như yêu cầu đăng nhập. Launcher status xác nhận PID 1872. Startup kết nối MySQL cục bộ `quanlymuahang`, Flyway xác nhận schema v10 đã mới nhất và không chạy migration. Server dev vẫn đang chạy; `CHAY_KHVT.cmd stop` chỉ dừng app, không dừng MySQL/Redis. Không đăng nhập tài khoản thật trong lượt smoke test.

Các số liệu runtime/database và kết quả bên dưới là bằng chứng có ngày của các lượt trước, không phải truy vấn lại trạng thái database trừ khi ghi rõ ở trên. Lượt này không ghi dữ liệu nghiệp vụ trực tiếp; ứng dụng đã kết nối database khi khởi động.

## Import PCVT và báo giá đã triển khai ngày 2026-10-11

Theo yêu cầu mới, triển khai **import trước Quản trị**. `/imports` mặc định mở **PCVT / báo giá mới** khi có `IMPORT_OPERATIONAL`. Backend `/api/imports/documents` lưu tệp gốc/checksum, nguồn trích xuất và snapshot xác nhận; chọn từng phiếu từ sổ năm, tra NCC/giá cũ, lưu/xuất XLSX nhóm hỏi giá, nối dòng báo giá và so sánh. Migration V7 lưu chứng từ riêng, không biến báo giá thành giá đã mua.

Đã qua 33 test import backend bản cuối, TypeScript/Vite và 10 unit tests, 46 trường hợp browser desktop/mobile bằng API fixtures. Suite backend trước sửa cuối khóa nhóm giá có 124 test liệt kê/121 thực thi qua/3 opt-in bỏ qua; sau sửa chạy lại 33 test import và package, không cộng các lượt thành một tổng mới. Driver MySQL/Redis riêng hoàn tất **14 nhóm**, gồm MCP/UI thật: Excel CVT **2.013 dòng**, Excel báo giá **43+18 dòng**, PDF text **8/31/11 dòng**, tổng **2.124 dòng/10.671 trường hoặc chi tiết kiểm tra, 0 sai lệch**. PDF scan báo chưa OCR và đã kiểm tra nhập tay riêng. Bằng chứng và phạm vi nằm ở [nghiệm thu kỹ thuật import PCVT/báo giá](NGHIEM_THU_IMPORT_PCVT_VA_BAO_GIA.md); cách dùng ở [hướng dẫn import](HUONG_DAN_IMPORT_PCVT_VA_BAO_GIA.md).

Lượt thật cuối **17:24 +07:00** dùng `qmh_docs_test_34e94b899b19`, `completed: true`, `mainUnchanged: true`. Dữ liệu chính vẫn 10.580 lịch sử/262 PO/850 dòng PO/391 NCC/2.997 vật tư/1 tài khoản và Flyway V6. JAR mới đóng gói UI; V7 chỉ áp dụng trên schema thử, sẽ chạy khi khởi động bản mới. Server chính tiếp tục tắt, MySQL/Redis giữ nguyên. Chưa OCR, lưu alias có duyệt, phân bổ/lập PO trực tiếp từ PCVT, grant/deny riêng hoặc production.

## Quản trị Phase 1 đã triển khai ngày 2026-10-11

Đã có code cho **bước 1 của kế hoạch 15**: gom tài khoản, danh bạ, phòng ban và chức vụ vào **Quản trị**. Cổng ứng dụng chỉ đăng ký `PURCHASING` và `ADMINISTRATION`; không còn ô Nhân sự riêng, nhưng vẫn giữ module backend `personnel` và dữ liệu cũ. Người có `USER_READ` vào trang tài khoản; người chỉ có `PERSONNEL_READ` vào danh bạ. `HR_MANAGER` không được tự thêm quyền quản lý tài khoản.

`/admin/users` dùng một form tạo/sửa tài khoản với hồ sơ nhân viên cơ bản tùy chọn, hoặc liên kết hồ sơ đang hoạt động có sẵn. Tài khoản, hồ sơ và audit lưu cùng transaction. Hồ sơ mới hoàn toàn trống không sinh bản ghi; khi xóa thông tin tùy chọn của hồ sơ đã liên kết, giữ mã nhân viên và bỏ phòng ban/chức vụ. Danh mục đã ngừng chỉ được giữ nếu đúng ID đang liên kết; không được gán mới. `/api/admin/users` tìm kiếm email/tên/mã nhân viên, lọc trạng thái/phòng ban và phân trang tại server; `/options` và `/employees` cung cấp dữ liệu tối thiểu cho form.

Backend kiểm tra phạm vi quyền của người thao tác: không được gán vai trò vượt quyền hiện hành, sửa hoặc reset tài khoản có quyền cao hơn. API vai trò trả `assignable` để UI thể hiện giới hạn này; kiểm tra cuối cùng vẫn ở server. Tiếp tục bảo vệ tự khóa, admin hoạt động cuối cùng và thu hồi các phiên khi đổi vai trò/trạng thái/reset mật khẩu.

**Bằng chứng hiện có:** full package backend lúc **18:01:06 +07:00** liệt kê 138 test, 136 thực thi qua, 2 opt-in bỏ qua. Sau các sửa cuối về hồ sơ trống/danh mục ngừng và điều kiện last-admin cho target đang hoạt động, package mục tiêu lúc **18:24:28** chạy 31 test qua, không bỏ qua (14 quản trị, 11 auth, 6 module); chưa chạy lại toàn bộ suite sau các sửa này. TypeScript/Vite build và 10 unit tests qua; 56 trường hợp auth/tài khoản/danh bạ desktop/mobile với **API giả lập** qua trong 36,4 giây.

Driver MySQL/Redis riêng `tools/local-test/admin-phase1.mjs` hoàn tất **15 nhóm**, báo cáo cuối ghi lúc **18:25:27 +07:00** trên schema `qmh_admin_phase1_c0c3eea29b84`, `completed: true`, `mainUnchanged: true`. Gồm transaction/rollback, tìm kiếm/phân trang, thu hồi hai phiên, phân quyền ủy quyền, restart và **MCP/API thật** tạo tài khoản kèm hồ sơ/lọc/reload/mobile/URL bị cấm. Hai admin tự bỏ quyền đồng thời nhận 200 và 400 `LAST_ADMIN`, database giữ một admin hoạt động; phiên bị hạ quyền nhận 401, admin bị từ chối vẫn đăng nhập. Bỏ vai trò ADMIN của tài khoản đã ngừng được phép, không làm giảm số admin hoạt động. Lượt MCP đầu lỗi selector email strict đã sửa trong driver kiểm thử rồi chạy lại; không đổi app để làm test qua. **Đạt nghiệm thu kỹ thuật trong phạm vi Phase 1**; server chính giữ tắt, dữ liệu/metadata/role links/Flyway chính giữ nguyên, MySQL/Redis healthy. Xem [biên bản Phase 1](NGHIEM_THU_QUAN_TRI_PHASE_1.md), [báo cáo lượt thật](../source/target/runtime/admin-phase1-real-integration-report.json) và [cách sử dụng](HUONG_DAN_QUAN_TRI_PHASE_1.md).

Grant/deny cho từng ứng dụng/hành động và giữ đăng nhập dài hạn thuộc bước 2–3, chưa triển khai. Phiên bản quyền để chặn race giữa đăng nhập và đổi quyền cũng chưa có; không coi kiểm tra thu hồi phiên tuần tự là bằng chứng cho mọi thao tác đồng thời.

## Hướng tiếp theo được người dùng xác nhận ngày 2026-10-11

Đã dừng ứng dụng bằng `CHAY_KHVT.cmd stop`; tại lần kiểm tra, cổng 8080/5173/5174 không có listener. Hai container `qmh-local` MySQL/Redis vẫn healthy, không thay đổi database hoặc volume.

Người dùng cần **Quản trị** để tạo tài khoản, quản lý phòng ban/chức vụ cơ bản và cấp/cấm ứng dụng/hành động, không cần ứng dụng Nhân sự riêng. Phase 1 đã gom giao diện và bổ sung form tài khoản kèm hồ sơ tùy chọn; grant/deny cá nhân và giữ đăng nhập chưa triển khai. Cấu hình Redis session idle 30 phút, không có access/refresh JWT. Xem [kế hoạch Quản trị và phiên đăng nhập](../plan/15-quan-tri-tai-khoan-va-phan-quyen.md).

`QUANLYMUAHANGKHVT.xlsx` là **dữ liệu lịch sử của Apps Script**. Báo giá mới chưa phải giao dịch đã mua. [Kế hoạch PCVT, tra lịch sử và báo giá](../plan/16-pcvt-tra-gia-cu-va-bao-gia.md) đã được triển khai phần import/tra/gom/so sánh như bằng chứng ở trên; sáu mẫu mới chỉ nhập vào schema thử, chưa nhập database chính. Theo yêu cầu tiếp theo, Quản trị Phase 1 đã triển khai và qua kiểm thử thật trong phạm vi ghi trên.

## Bổ sung kiểm tra độ chính xác khi nhập workbook

Code đã bổ sung kiểm tra header/mapping, ô lỗi Excel, trường quá dài, ngày không đọc được, loại tiền chưa hỗ trợ, VAT không hợp lệ và số không biểu diễn chính xác trong cột SQL. Giá trị số dạng text có dấu phân cách mơ hồ bị chặn; số lượng chữ vẫn giữ nguyên. Ô số Excel được đọc từ token gốc bằng BigDecimal, giữ provenance `__numeric`; mã có định dạng số 0 đầu được giữ theo định dạng. Công thức chỉ được đánh giá trong phạm vi cho phép; tham chiếu CONFIG, named range hoặc nguồn ngoài bị từ chối. Đây là các điều kiện đọc/ghi, không phải chứng nhận chứng từ đúng nghiệp vụ.

Validator mới thêm cảnh báo cho giá/số lượng bằng 0 hoặc âm, thiếu đơn vị/ngày, loại tiền giả định và biến thể mã/tên/đơn vị. Commit kiểm tra lại staging, ghi liên kết dòng NCC → master, flush dữ liệu rồi đối chiếu từng dòng với dữ liệu nghiệp vụ trong cùng transaction. Sai lệch trả `IMPORT_RECONCILIATION_FAILED` và rollback lượt nhập. Response thành công bổ sung `verification` gồm dòng nguồn/đã đối chiếu, lịch sử, PO, dòng PO và dòng NCC. Đối chiếu này so staging với database; driver riêng còn so OpenXML gốc với staging để kiểm tra parser.

Migration `V6__preserve_excel_decimal_precision.sql` đã áp dụng thành công trên schema chính: bốn cột số lượng/đơn giá của lịch sử và dòng PO đều là `DECIMAL(38,18)`. Migration không phục hồi các chữ số từng bị làm tròn ở dữ liệu cũ. Đối chiếu chính xác workbook gốc với database chính đã kiểm tra **12.834 giá trị số, 0 chênh lệch**; không có bằng chứng cần tự sửa các giá trị này. Chưa backfill liên kết NCC của lô 1 hoặc chạy lại import vào database chính trong lượt bổ sung code này.

UI hiện mặc định mở PCVT/báo giá mới khi có quyền operational; tài khoản chỉ có legacy mở workbook. Parser chứng từ mới từ chối workbook ba sheet bằng `LEGACY_WORKBOOK_DETECTED` để chọn đúng tab; công cụ lập đơn nhanh cũ vẫn chuyển sang legacy cùng tệp nếu có quyền, hoặc hướng dẫn liên hệ admin. Có tải **toàn bộ cảnh báo/lỗi CSV** qua `GET /api/imports/legacy/{id}/issues.csv`, kiểm tra lại trạng thái qua `GET /api/imports/legacy/{id}`, cập nhật trạng thái sau commit và chặn gửi lặp khi đang xử lý. Preview trên màn hình chỉ hiển thị tối đa 30 dòng vấn đề; CSV xuất từng điều kiện, nên số dòng CSV không nhất thiết bằng số dòng nguồn có cảnh báo.

**Kiểm thử backend bản cuối:** `mvn -DforkCount=0 -Dspring.test.context.cache.maxSize=1 test` đã BUILD SUCCESS lúc **2026-10-10 23:57:35 +07:00**: 92 test được liệt kê, **91 thực thi qua**, một test Redis opt-in riêng bỏ qua. Đã bật workbook gốc và font Arial Windows; H2 đối chiếu đủ 11.690 dòng, 10.561 lịch sử, 262 PO, 850 dòng PO và 279 dòng NCC; hai commit đồng thời cho một thành công/một từ chối. Có 0 lỗi đọc/ghi và 11.433 dòng cảnh báo theo validator mới. JVM dùng heap 384 MB cùng Mockito agent; các lượt thiếu bộ nhớ trước đó không tính là qua. Log `source/target/runtime/import-backend-final.log`, báo cáo `workbook-h2-report.json`. Bằng chứng database chính bên dưới dùng validator cũ; số 4.803 không đại diện cho tổng cảnh báo mới.

**Kiểm thử giao diện import:** build TypeScript/Vite và 10 unit tests qua; 28 trường hợp import/mua hàng trên desktop/mobile với API giả lập đã qua sau chạy lại hai trường hợp bị worker thiếu bộ nhớ. Log `import-browser-retry.log` xác nhận hai trường hợp qua; không cộng lượt chạy lại vào số trường hợp. Bản đóng gói `windows-web` BUILD SUCCESS lúc 23:57:52; package bỏ qua test và dùng kết quả test riêng ở trên.

**MySQL/Redis bản cuối:** driver `tools/local-test/run.mjs` hoàn tất với `completed: true`, 19 nhóm kiểm tra trên schema riêng `qmh_test_b54edb7456f2` và namespace Redis riêng. OpenXML → MySQL staging khớp **11.690 dòng, 91.526 trường và 23.670 cờ numeric**, 0 sai lệch; guard trong transaction xác nhận đủ các dòng nghiệp vụ. Hai commit đồng thời nhận một 200 và một 409 `IMPORT_IN_PROGRESS`; gửi lại sau hoàn tất nhận `IMPORT_ALREADY_PROCESSED`. CSV đủ issues, preview lại không tăng dữ liệu và checksum file gốc không đổi. HTTP auth/CSRF, thu hồi phiên Redis, PO/PDF/XLSX và bảo vệ admin cũng qua. Browser của driver tách riêng vì giới hạn RAM; đây không phải MCP browser với API thật. Báo cáo `source/target/runtime/real-integration-report.json`, log `import-final-mysql-verified.log`.

**Bản chạy chính sau cập nhật:** health `UP`, `/imports` và JS asset trả 200, báo cáo API khi chưa đăng nhập trả 401. V6 thành công; trước/sau đều có 10.580 lịch sử (10.561 thuộc lô workbook 1, 19 ngoài lô), 262 PO, 850 dòng PO, 391 NCC, 2.997 vật tư và một lô import. 19 lịch sử khác giữ nguyên số bản ghi; chưa xác minh nguồn tạo trong lượt này. 279 dòng NCC của lô cũ vẫn chưa backfill liên kết; code mới áp dụng cho lô mới. Bằng chứng `import-main-before-update.json`, `import-main-after-update.json`. Backup trước cập nhật ở `source/target/runtime/backups/before-workbook-2026-10-10T16-23-41-743Z/`, gzip đã kiểm tra, chưa thử restore.

## Import workbook thật qua MCP vào MySQL

Ngày **2026-10-10, 22:11–22:22 +07:00**, dùng browser MCP tại `http://localhost:8080/imports` với backend/MySQL thật, theo yêu cầu người dùng. Đây là lượt dùng validator trước các bổ sung độ chính xác ở trên. Đăng nhập trả HTTP 200, tài khoản có quyền import legacy và không phải đổi mật khẩu tạm. Trước import, các bảng NCC, vật tư, lịch sử, PO và dòng PO của schema `quanlymuahang` đều có 0 bản ghi; database đã có schema và tài khoản.

Đã tạo bản sao lưu database bằng `mysqldump`, sao chép workbook giữ nguyên checksum và kiểm tra thư mục lưu tài liệu trước khi commit. Bản sao lưu nằm ở `source/target/runtime/backups/before-workbook-2026-10-10T15-11-14-409Z/`; gzip đã giải nén kiểm tra và có dấu kết thúc dump, **chưa thử restore**. Đây là bản sao lưu riêng, không đưa nội dung SQL hoặc bí mật vào tài liệu.

| Đối chiếu | Kết quả thực tế |
|---|---|
| Workbook | 682.929 byte; SHA-256 `2ea5e68728de5ac68277612c25a47cc4859725c572a69884e04b2929b6eb9608`; file gốc không đổi |
| Preview | Lô 1: NCC 279, LICH_SU 10.561, DON_HANG 850; tổng 11.690 dòng, 262 nhóm PO, 4.803 dòng cảnh báo, 0 lỗi |
| Commit | HTTP 200, lô 1 `COMMITTED`; 11.690 dòng staging đã xử lý |
| MySQL sau commit | 10.561 lịch sử, 262 PO, 850 dòng PO; toàn bộ thuộc lô 1. Danh mục có 391 NCC và 2.997 vật tư |
| Preview lại cùng tệp | HTTP 200, cùng lô 1 `COMMITTED`, `duplicate=true`; vẫn giữ 262 nhóm PO và 4.803 dòng cảnh báo, không có nút commit; số bản ghi MySQL không tăng |
| Giao diện thật | Danh sách PO hiện 262, lịch sử giá 10.561, NCC 391, vật tư 2.997; mở PO 262 có 1 dòng hàng, các trang này không hiện alert lỗi |

Các trường thiếu vẫn giữ NULL/snapshot hoặc mặc định currency có dấu nguồn theo chính sách import; không sửa workbook để bỏ 4.803 dòng cảnh báo. Số dòng sheet NCC không đồng nghĩa số NCC cuối cùng sau xử lý cả workbook. Bằng chứng local: `source/target/runtime/mcp-workbook-before.json`, `mcp-workbook-backup.json`, `mcp-workbook-after.json`, `mcp-workbook-result.json`. Lượt này kiểm chứng import và đọc dữ liệu; không chạy lại toàn bộ test, thử commit đồng thời trên MySQL, nghiệm thu chứng từ hoặc production.

Lượt đối chiếu sâu sau đó đọc MySQL nhất quán lúc **22:29:33 +07:00**, so sánh toàn bộ 11.690 dòng Excel → staging → dữ liệu nghiệp vụ, không phát hiện lệch trường theo mapping hoặc liên kết trỏ tới bản ghi không tồn tại. **Nguồn chưa được xác nhận chuẩn nghiệp vụ**: 234 lịch sử có giá/số lượng bằng 0 hoặc âm, 1.038 lịch sử thiếu đơn vị; 6 mã NCC và 75 mã hàng có nhiều tên chuẩn hóa, 36 mã hàng có nhiều đơn vị chuẩn hóa. LICH_SU không ghi loại tiền, cả 10.561 lịch sử dùng VND theo `ASSUMED_LEGACY`; có 3.637 lịch sử/9 PO chưa liên kết NCC và 624 dòng PO chưa liên kết vật tư. Đã tạo [báo cáo đối chiếu và danh sách dòng cần rà soát](DOI_CHIEU_CSDL_VA_WORKBOOK.md), không sửa database hoặc workbook. 0 lỗi import không đồng nghĩa đã chuẩn hóa dữ liệu.

Màn hình **“Kiểm tra 850 dòng”** thuộc tab **Yêu cầu mua / báo giá**: lấy các dòng yêu cầu để chuyển sang lập đơn, tối đa 200 dòng mỗi lượt. Để nạp workbook lịch sử nhiều PO, chọn **Workbook dữ liệu cũ**. 850 dòng DON_HANG trong workbook này thuộc 262 PO, không phải một PO có 850 dòng.

## Chạy Windows, dev và chuẩn bị production

[CHAY_KHVT.cmd](../CHAY_KHVT.cmd) gọi `tools/run-windows.ps1`: nạp cấu hình riêng, chọn JDK 21/Maven, kiểm tra Docker Desktop đã mở, bật riêng MySQL/Redis, `npm ci`/`npm run build`, rồi `mvn -Pwindows-web -DskipTests package`. Profile Maven đưa `frontend/dist` vào static resources của JAR. Một tiến trình Java phục vụ UI và API; không cần Vite để chạy bản đóng gói. `SpaController` phục vụ các URL giao diện cụ thể và deep link; API vẫn kiểm tra session/CSRF/permission. Không forward API hoặc asset bị thiếu thành HTML.

Dev khi sửa code vẫn dùng `tools/start-local.ps1` hoặc backend VS Code/Maven và Vite 5173. Script dev đặt profile `dev`, origin local và cookie HTTP; hai launcher mặc định heap 256 MB, Serial GC, Hikari tối đa 5 kết nối/minimum idle 1. Heap không phải RAM toàn bộ tiến trình. Production dùng profile `prod`, origin HTTPS thật và cookie Secure; Tomcat native forwarded headers chỉ tin proxy loopback, bỏ override host/port, ứng dụng bind `127.0.0.1`. Hướng dẫn đầy đủ tại [chạy Windows và Cloudflare](CHAY_WINDOWS_VA_CLOUDFLARE.md).

| Kiểm tra ngày 2026-10-10 | Kết quả và giới hạn |
|---|---|
| Test mục tiêu | `mvn -Dtest=AuthApiIntegrationTest,SpaWebIntegrationTest,ProductionProxyIntegrationTest test`: BUILD SUCCESS, 20/20 qua, không bỏ qua. Gồm 11 auth, 3 SPA/asset/API và 6 proxy/Secure cookie qua Tomcat HTTP thật. Dùng H2/MockMvc hoặc Tomcat test riêng; không chạy lại toàn bộ bộ test hay kiểm chứng Cloudflare. Log `source/target/runtime/windows-focused-tests.log`. |
| Launcher/build | `CHAY_KHVT.cmd start` thực sự chạy npm ci, TypeScript/Vite build và Maven `windows-web` package thành công lúc 21:15. Build mặc định bỏ qua test. Gọi start lần hai giữ cùng PID, status UP, stop chỉ dừng Java được theo dõi. `start -NoBuild -ExternalDocker` chạy lại thành công; runtime cuối lúc 21:20 dùng PID 5448, cổng 8080. Không đăng ký Windows Service/tự chạy sau reboot. |
| HTTP/UI thực | `windows-web-probe.json` lúc 21:19:50: root/login/modules/PO deep link/admin shell trả HTML 200, JS/CSS 200, API anonymous trả JSON 401, asset thiếu 404. Login bằng tài khoản tổng hợp không tồn tại và CSRF hợp lệ trả 401/INVALID_CREDENTIALS cho cả localhost/127.0.0.1; thiếu CSRF trả 403/CSRF_INVALID. MCP mở login của JAR, React render form/footer đúng; console có 401 `/api/auth/me` dự kiến khi chưa đăng nhập. Không đăng nhập tài khoản thật hoặc kiểm thử CRUD nghiệp vụ trong lượt này. |
| Docker/dữ liệu | Người dùng xóa container cũ nhưng hai volume còn; container mới dùng nguyên volume. Lượt giới hạn 768/128 MiB lúc 21:12: Docker báo MySQL 316,5 MiB/Redis 7,547 MiB, so với 466,8/8,574 MiB lúc 21:03. Sau đó người dùng chỉnh Compose thành MySQL 1536 MiB/2 CPU/buffer pool 512 MiB, Redis 256 MiB/1 CPU/maxmemory 128 MiB/noeviction; lúc 21:17 báo 375,9 MiB/5,02 MiB, cả hai healthy. Volume, danh sách bảng, số migration thành công và số tài khoản khớp trước/sau; không đối chiếu toàn bộ dữ liệu nghiệp vụ. Redis keys 6→15 sau HTTP CSRF smoke; không xóa session. Log `docker-before.json`, `docker-after.json`. |
| Java/WSL | Java JAR heap 256 MB dùng khoảng 336,5 MB Windows working set lúc 21:15; số đo tức thời, chưa đo tải/import lớn. Java của IDE không bị dừng hoặc đổi cấu hình bởi launcher. WSL VM còn bộ nhớ hệ thống/cache ngoài container; giới hạn Compose không phải giới hạn RAM của toàn bộ Docker Desktop. |

Một lần `-NoBuild` startup nhận lỗi MySQL Communications/EOF đúng thời gian container được tạo lại với cấu hình mới; chờ cả hai healthy và chạy lại đã thành công. Không có bằng chứng lỗi này do giới hạn RAM. Không reset database, volume hoặc đổi mật khẩu để xử lý. Cloudflare Tunnel/domain và việc chuyển sang máy Windows khác chưa được kiểm chứng.

## Sửa thông báo không có quyền khi đăng nhập

Kiểm tra HTTP thực tế lúc **2026-10-10 11:24–11:30 +07:00** bằng tài khoản tổng hợp không tồn tại: qua Vite, Origin `http://127.0.0.1:5173` với cookie/CSRF hợp lệ bị HTTP 403/plain text `Invalid CORS request`, còn Origin `http://localhost:5173` tới được bước xác thực và trả 401/`INVALID_CREDENTIALS` như dự kiến. Vite cấu hình proxy bằng chuỗi URL làm Host chuyển sang backend; frontend gán thông báo thiếu quyền cho response 403 không có JSON.

Đã sửa proxy `/api` và `/actuator` thành cấu hình `changeOrigin: false` để giữ Host của trình duyệt. Frontend làm mới CSRF trước mỗi lần login để tránh cache cũ khi tab khác đăng nhập/đăng xuất; 403 không có JSON trên Login hiện hướng dẫn tải lại phiên. Backend phân biệt lỗi CSRF bằng `CSRF_INVALID`, còn lỗi permission thực vẫn là `FORBIDDEN`; CSRF vẫn được kiểm tra. Không thay mật khẩu hoặc quyền tài khoản.

Sau sửa: cả hai Origin qua frontend đều trả 401/`INVALID_CREDENTIALS` với thông tin tổng hợp; thiếu CSRF trả 403/`CSRF_INVALID`. Gọi trực tiếp backend từ Origin 127.0.0.1 khác cấu hình vẫn bị CORS từ chối. Báo cáo `source/target/runtime/login-csrf-probe.json` (trước) và `login-csrf-probe-after-fix.json` (sau) chỉ ghi trạng thái/code, không ghi cookie/token/mật khẩu. Lượt sửa lúc 11:30 chưa xác nhận tài khoản thật; lượt MCP import buổi tối đã đăng nhập thật HTTP 200 như phần bằng chứng mới ở trên.

`mvn -Dtest=AuthApiIntegrationTest package` dùng JDK 21/Maven 3.9.11 đã BUILD SUCCESS, 11/11 test H2: CSRF thiếu/sai/không khớp cookie không tăng failed-login counter, sai mật khẩu với CSRF hợp lệ trả 401, quyền đọc/ghi và mật khẩu tạm vẫn được kiểm tra. Frontend TypeScript/Vite build và 10 unit tests qua; Playwright `auth.spec.ts --workers=1` có 22/22 desktop/mobile qua, **API giả lập**. Log `backend-login-fix.log`, `frontend-login-types.log`, `frontend-login-build.log`, `frontend-login-browser.log` trong `source/target/runtime/`. Đây là lượt kiểm tra auth có phạm vi cụ thể, không phải chạy lại toàn bộ bộ test hay nghiệm thu nghiệp vụ.

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

Docker Desktop/WSL trước đó từng báo bootstrap `0xc00000fd` hoặc mất pipe `dockerDesktopLinuxEngine`; Windows Application Event 1000 xác nhận `wslservice.exe`/`wslhost.exe` crash. Nguyên nhân gốc chưa xác định; công cụ trên máy cũng có lúc không tạo được PowerShell vì Windows báo paging file quá nhỏ (`1455`). Không reset database, xóa volume hoặc dừng Java của IDE. Các driver workbook, PCVT/báo giá và Phase 1 đã có lượt hoàn tất sau đó như bằng chứng mới ở đầu tài liệu; lỗi lịch sử không dùng để mô tả lượt cuối. Xem [kiểm thử local](../tools/local-test/README.md).

Đã sửa hai vấn đề tìm thấy khi test: Hibernate chèn dòng PO mới trước khi xóa dòng cũ gây trùng `(purchase_order_id,line_no)` trên MySQL (nay flush xóa trong cùng transaction); preview file trùng checksum mất số cảnh báo/nhóm PO (nay tính lại từ staging). Sửa/hủy/phát hành khóa bản ghi PO; commit khóa lô import trong transaction để không commit đồng thời hai lần. Phát hành chuyển sang POST có CSRF/quyền ghi; GET PDF chỉ tải tài liệu đã có, tránh người chỉ đọc làm thay đổi trạng thái/giá.

## Các thư mục ở gốc

| Thư mục/file | Vai trò |
|---|---|
| `AGENTS.md` | Điểm vào cho AI: quy tắc làm việc, bản đồ code, liên kết tài liệu |
| `docs/NGUYEN_TAC_TRUNG_THUC.md` | Cam kết về trung thực và minh bạch bằng chứng |
| `docs/HE_THONG_HIEN_TAI.md` | Bản đồ hiện trạng và các phần còn thiếu |
| `docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md` | Cổng chọn ứng dụng, quyền hiện có và hướng mở rộng Bán hàng/grant độc lập |
| `docs/HUONG_DAN_QUAN_TRI_PHASE_1.md`, `docs/NGHIEM_THU_QUAN_TRI_PHASE_1.md` | Cách dùng Quản trị Phase 1 và bằng chứng kiểm thử theo phạm vi |
| `skills/khvt-ui/SKILL.md` | Skill giao diện riêng của repository, palette và quy tắc UI theo yêu cầu người dùng |
| `source/` | Backend và frontend của hệ thống mua hàng đang triển khai |
| `CHAY_KHVT.cmd`, `tools/run-windows.ps1` | Build và chạy bản Windows, giao diện/API trong cùng JAR; Docker Desktop mở riêng |
| `docs/CHAY_WINDOWS_VA_CLOUDFLARE.md` | Lệnh dev/production, giới hạn RAM, chuyển máy và chuẩn bị HTTPS/Tunnel |
| `plan/` | Thiết kế và tiêu chí cần đạt; không mặc nhiên là trạng thái đã hoàn thành |
| `backend(Demo để lấy phần login)/` | Đã xóa theo yêu cầu người dùng; không thuộc build ứng dụng chính |
| `.codegraph/` | Chỉ mục để tra cứu code; không phải module chạy ứng dụng |

## Backend đã tới đâu

Backend có code cho các nhóm API sau:

| Nhóm | Chức năng có trong code | Giới hạn bằng chứng |
|---|---|---|
| Identity | CSRF, login/logout/me, đổi mật khẩu, bootstrap admin, tài khoản kèm hồ sơ tùy chọn trong cùng transaction, tìm kiếm/lọc/phân trang server, gán vai trò có giới hạn actor/target, session revocation, audit | 31 test mục tiêu bản cuối qua; driver Phase 1 MySQL/Redis/MCP thật hoàn tất 15 nhóm gồm thu hồi phiên và bảo vệ hai admin tự bỏ quyền đồng thời. Nghiệm thu theo phạm vi Phase 1, chưa bao gồm grant/deny/giữ đăng nhập/production. |
| Roles và cổng ứng dụng | Vai trò/quyền mặc định, roles có permissions/moduleCodes/assignable; registry trả Mua hàng và Quản trị từ permission hiệu lực | MockMvc kiểm tra module/quyền hiệu lực, custom role và role ngừng; HR chỉ vào danh bạ trong Quản trị. Chưa có CRUD role/permission, grant/deny riêng hoặc xin/duyệt quyền |
| Personnel | Danh bạ/chi tiết/tạo/sửa/kích hoạt/ngừng nhân viên; phòng ban và chức vụ thuộc khu vực Quản trị | Giữ dữ liệu/backend cũ. Đã kiểm tra tạo/cập nhật/link hồ sơ cùng tài khoản trên MySQL test; chưa nghiệm thu đầy đủ mọi API danh bạ và vai trò |
| Catalog và Pricing | Danh mục vật tư/NCC, tra cứu lịch sử/giá | MCP đọc danh mục và lịch sử giá sau import workbook thật trên MySQL đã qua; chưa nghiệm thu đầy đủ nghiệp vụ |
| Procurement | Danh sách/chi tiết/tạo/sửa/hủy/phát hành PO, revision và PDF | Test document/model và driver MySQL một phần đã qua luồng chính; cần nghiệm thu mẫu chứng từ/nghiệp vụ và các trường hợp tranh chấp đồng thời. |
| Export | XLSX giá và PO | Có API; chưa kiểm tra file thực tế theo acceptance |
| Import | Legacy workbook preview/commit; yêu cầu mua từ spreadsheet/CSV/PDF text hoặc paste thành draft | Workbook thật đã preview/commit H2 riêng và MySQL chính qua MCP; MySQL đối chiếu 10.561 lịch sử/262 PO/850 dòng, preview lại không nhập trùng. PDF scan chưa có OCR. |
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
- Sau login/đổi mật khẩu tạm, mở `/modules` để chọn Mua hàng hoặc Quản trị theo danh sách backend trả về. Danh bạ/phòng ban/chức vụ nằm trong Quản trị; người chỉ có quyền danh bạ có trang vào riêng và không được xem tài khoản. Mỗi ứng dụng có khu vực làm việc riêng và đường quay về cổng ứng dụng. Quyền được cấp qua role; wildcard `*` trong permission hiệu lực mở tất cả module, không bypass bằng tên role. Chưa có grant/deny module độc lập, xin/duyệt quyền hay Bán hàng. Xem [thiết kế cổng ứng dụng và phân quyền](CONG_UNG_DUNG_VA_PHAN_QUYEN.md).
- Giao diện chuyển sang xanh rêu/xanh ngọc, nền trắng ngà và bảng nghiệp vụ gọn theo `skills/khvt-ui/SKILL.md`; bỏ màu cam. Footer auth/cổng ứng dụng/khu vực làm việc có © Bản quyền thuộc về KHVT | Cung cấp bởi link ThuanNgcodelor.
- Dashboard lấy số liệu thật; bỏ fallback demo và nhãn thành công cố định. Có loading/error/retry/empty, hết phiên chuyển về login và xóa cache nghiệp vụ. HR không gửi request tổng quan mua hàng.
- Danh bạ nhân viên trong Quản trị có tìm kiếm mã/tên, lọc trạng thái, phân trang, thêm/sửa, ngừng/kích hoạt; thêm/sửa phòng ban và chức vụ qua API. Hồ sơ nhân viên và tài khoản đăng nhập vẫn là hai dữ liệu riêng. Kích hoạt nhân viên không tự mở lại tài khoản đã bị vô hiệu hóa.
- UI tài khoản có tìm kiếm/lọc trạng thái/phòng ban/phân trang ở server, một form tạo/sửa tài khoản kèm hồ sơ cơ bản tùy chọn hoặc liên kết hồ sơ có sẵn qua danh sách phân trang. Vai trò ngoài phạm vi actor và thao tác với tài khoản có quyền cao hơn được giới hạn ở UI và backend. Role/permission dùng API đọc; chưa có ma trận chỉnh quyền. Thay quyền/trạng thái/reset có xác nhận và thông báo thu hồi phiên theo API. Không có nút xóa nhân viên/tài khoản.
- UI danh mục vật tư/NCC có tìm kiếm/lọc/phân trang/thêm/sửa/ngừng/kích hoạt. UI giá có lịch sử/giá gần nhất theo currency, chuyển sang lập đơn và XLSX. UI PO có giỏ từ nhiều nguồn, số lượng chữ, tách nhóm NCC/lưu từng nháp, sửa có lý do/revision, hủy có xác nhận, phát hành/tải PDF phiên bản và XLSX. Import có preview file/dán dữ liệu → chọn dòng → lập đơn; workbook → số dòng/cảnh báo/checksum → xác nhận commit. Giỏ chưa lưu nằm trong bộ nhớ; giới hạn 200 dòng/đơn, chưa có idempotency key cho tạo PO. Xem [đối chiếu ứng dụng cũ](DOI_CHIEU_UNG_DUNG_CU.md).
- Trong lượt Phase 1 ngày 2026-10-11: TypeScript/Vite build và 10 unit tests qua; 56 trường hợp auth/tài khoản/danh bạ desktop/mobile bằng **API giả lập** qua. Nhóm import/chứng từ có kết quả riêng ở đầu tài liệu; không cộng các lượt và không coi fixture là nghiệm thu CRUD MySQL/Redis. Lịch sử 2026-10-10 có 7 Vitest và 58 browser cases auth/danh bạ/tài khoản/mua hàng qua; số cũ không xác nhận các thay đổi mới.
- Smoke browser với MySQL/Redis thật ngày 2026-10-09 đã qua: admin bootstrap login, trang bắt buộc đổi mật khẩu, reload giữ phiên, bảo vệ dashboard và logout. Không đổi mật khẩu admin hay ghi dữ liệu nghiệp vụ trong smoke này. Phát hiện và sửa validation email frontend trước đó từ chối miền nội bộ được backend chấp nhận. Đây là bằng chứng lịch sử trước thay đổi indexed session; lần thử cấu hình bootstrap sau đó nhận 401 và đã dừng. Lượt MCP import buổi tối ngày 2026-10-10 đã đăng nhập tài khoản thật thành công, không đổi mật khẩu.
- Một lần chạy browser song song gặp lỗi thiếu bộ nhớ của Node/Windows; cấu hình Playwright chạy một worker để giảm bộ nhớ.
- Đã có [skill KHVT UI](../skills/khvt-ui/SKILL.md) trong repository, tạo theo yêu cầu người dùng và được AGENTS.md tham chiếu. Đây là skill riêng của dự án, không phải skill chính thức của OpenAI hay skill đã cài vào catalog toàn cục. Plan 05/12 là tài liệu bổ trợ và đã cập nhật palette cùng cổng ứng dụng.

## Thư mục backend demo đã xóa

Theo kiểm tra hiện tại, `source/pom.xml`, Dockerfile, Compose, frontend package và Java imports không tham chiếu dự án `backend(Demo để lấy phần login)` hoặc package `com.booking.system`. Build ứng dụng chính đã chạy bằng POM của `source/`.

Vì vậy thư mục demo không cần để build/chạy ứng dụng chính hiện tại. Đây là kết luận về phụ thuộc kỹ thuật, không khẳng định đã chuyển hết mọi hành vi trong demo hoặc đã nghiệm thu auth/personnel.

Đã xóa đúng thư mục theo yêu cầu người dùng. Không thao tác database, container hoặc volume demo. Chỉ mục CodeGraph có thể còn tham chiếu lịch sử; không tự tạo lại index khi chưa được yêu cầu. `plan/14-demo-auth-personnel-port.md` vẫn là tài liệu mapping lịch sử, không phải phụ thuộc runtime.

## Công việc còn lại theo ưu tiên

1. Triển khai bước 2 của kế hoạch 15: cấp/cấm module và hành động cho từng tài khoản, bộ tính quyền thống nhất, tách quyền phát hành PO, audit/thu hồi phiên và phiên bản quyền chống race. Phase 1 đã nghiệm thu riêng; không nhập lại dữ liệu chính để chứng minh API.
2. Bước 3 của kế hoạch 15: giữ đăng nhập theo thiết bị, xác nhận lại mật khẩu và thu hồi token thiết bị; chưa có hiện tại.
3. Đối chiếu cảnh báo nguồn, mẫu PDF/VAT/currency với nghiệp vụ và tiêu chí plan 09. Số 4.803 thuộc validator cũ; validator mới đã có CSV đầy đủ và kết quả riêng. Chưa xác nhận nghiệp vụ thì giữ cảnh báo, không tự sửa nguồn hoặc commit lại database chính.
4. OCR PDF scan, alias vật tư có duyệt và phân bổ/lập PO từ PCVT là phần còn lại của kế hoạch 16. Bổ sung chống tạo PO trùng khi mất kết nối và cải thiện giỏ chưa lưu; nghiệm thu nghiệp vụ ngoài sáu mẫu đã thử.
5. Thống nhất cấu trúc backend từng module; tiếp tục giữ frontend theo pages/hooks/components. Bán hàng chưa có code nghiệp vụ.
6. Kiểm thử backup/restore, HTTPS, triển khai host và Cloudflare Tunnel trước khi dùng production.

## C?p nh?t giao di?n Mua h�ng � 2026-10-13

B?ng preview PCVT c� th? tra l?ch s? NCC/gi� cho c�c d�ng d� ch?n v� hi?n gi� cu, nh� cung c?p, don v? c�ng ng�y mua ngay trong c�ng b?ng. Ngu?i d�ng nh?p gi� hi?n t?i v� ch?n nh� cung c?p l?ch s? cho t?ng d�ng; n�t L?p PO chuy?n c�c d�ng du?c ch?n c�ng s? lu?ng, gi� hi?n t?i, m�/t�n NCC v� th�ng tin ngu?n v�o form t?o PO. T?o PO v?n l� b?n nh�p ? trang k? ti?p; kh�ng x�c nh?n PCVT, ghi l?ch s? mua hay ph�t h�nh don t? d?ng. N�t tra l?ch s? ch? hi?n khi c� quy?n PRICE_READ; quy?n t?o PO v?n do route/API hi?n c� ki?m tra.

Ki?m tra frontend trong lu?t n�y: npm.cmd run build th�nh c�ng; npm.cmd test -- --run 10/10; Playwright tests/e2e/documents.spec.ts 20/20 tru?c b? sung lu?ng t?o PO; ki?m th? m?i ri�ng desktop/mobile 2/2. C�c browser tests d�ng API fixtures; chua ki?m tra d? li?u MySQL/Redis, ph�n b? nhi?u VAT/ti?n t?, b?n nh�p b?n qua reload ho?c import workbook legacy th?t. Plan 17 c�n c�c ph?n backend/bulk danh m?c v� nghi?m thu th?t du?c ghi ? tr�n v?n chua ho�n t?t.

# Cập nhật UI Mua hàng — 2026-10-13

Đã tích hợp nhập PCVT/báo giá vào trang `/purchase-orders/new`, giữ sidebar bên trái. Từ trang này có thể nhập tệp hoặc dán Excel, duyệt preview, tự tải lịch sử giá/NCC cho trang dòng đang xem, sửa giá/NCC hiện tại rồi chuyển dòng được chọn vào bảng PO. Đơn theo nhiều NCC tiếp tục được chia thành các nhóm bản nháp; phát hành vẫn là thao tác riêng. Menu chỉ tô sáng đúng “Mua hàng” trên trang lập đơn.

Xác minh UI Mua hàng ngày 2026-10-13: một xác nhận tạo PO theo từng NCC, phát hành và gửi tải từng PDF; lỗi một NCC được báo riêng, luồng tiếp tục NCC kế tiếp. `npm.cmd run build` qua; `npm.cmd test -- --run` 10/10; Playwright `tests/e2e/documents.spec.ts tests/e2e/purchasing.spec.ts` 50/50 trên Chromium desktop/mobile; `git diff --check` qua. Browser tests dùng API fixtures, không kết nối database hay xác minh backend thật. Không truy cập production. Vì API chưa có giao dịch/idempotency theo lô, lỗi mạng khi tạo PO cần kiểm tra danh sách đơn trước khi thử lại; Plan 17 chưa nghiệm thu đầy đủ.
