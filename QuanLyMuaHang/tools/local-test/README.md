# Kiểm thử API/database và workbook local

## Quản trị Phase 1: tài khoản và hồ sơ cơ bản

Build frontend và JAR `windows-web` mới trước khi chạy. MySQL/Redis `qmh-local` phải healthy; cổng 8082 phải trống. Driver từ chối cổng đang có listener và không dừng ứng dụng của người sử dụng.

```powershell
$env:QMH_PHASE1_BROWSER = 'true'
node --max-old-space-size=128 --max-semi-space-size=2 tools/local-test/admin-phase1.mjs
Remove-Item Env:QMH_PHASE1_BROWSER
```

Driver đọc `.env` riêng trong bộ nhớ, tạo schema `qmh_admin_phase1_*`, namespace Redis riêng, mật khẩu ngẫu nhiên và backend tạm cổng 8082. Kiểm tra tạo tài khoản kèm hồ sơ trong một request, tự sinh mã hoặc bỏ hồ sơ, rollback khi lỗi, cập nhật thông tin, tìm kiếm/lọc/phân trang phía server, quyền danh bạ của `HR_MANAGER`, chống cấp quyền vượt phạm vi, tự khóa/admin cuối cùng và thu hồi hai phiên Redis khi thay quyền/trạng thái/mật khẩu. Restart chỉ tiến trình backend do driver tạo để kiểm tra lưu dữ liệu và phiên Redis.

Đặt `QMH_PHASE1_BROWSER=true` để chạy MCP với UI/API/MySQL/Redis thật: cổng ứng dụng Quản trị, tạo hồ sơ trong form tài khoản, tìm kiếm, reload, tài khoản chỉ quản lý danh bạ và layout mobile. Bỏ biến để chỉ chạy API; không coi lượt bỏ biến là kiểm thử browser. Driver không kiểm thử grant/deny cá nhân hoặc giữ đăng nhập lâu dài của các phase sau.

Báo cáo tại `source/target/runtime/admin-phase1-real-integration-report.json` và tên schema tương ứng: chỉ xem là qua khi `completed: true`, `mainUnchanged: true` và browser đã chạy nếu đặt biến. Đối chiếu database chính gồm số bản ghi, Flyway và metadata tài khoản/liên kết vai trò; không đọc hash để đối chiếu. Schema thử giữ lại; backend/browser tự dừng, không xóa volume hay flush Redis. `QMH_TEST_JAVA` và `QMH_TEST_JAR` cho phép chỉ định JDK 21/JAR khác. Mật khẩu, hash, cookie và CSRF không được ghi vào báo cáo.

## PCVT và báo giá mới: sáu file thật

Sau khi frontend/JAR `windows-web` đã build và Docker healthy, chạy từ root. Driver đọc `.env` riêng trong bộ nhớ; không log mật khẩu/cookie. Không dùng tài khoản của người sử dụng để test.

```powershell
$env:QMH_DOCUMENT_SAMPLE_ROOT = (Resolve-Path -LiteralPath '..').Path
$env:QMH_DOCUMENT_BROWSER = 'true'
node --max-old-space-size=128 --max-semi-space-size=2 tools/local-test/document-run.mjs
Remove-Item Env:QMH_DOCUMENT_SAMPLE_ROOT, Env:QMH_DOCUMENT_BROWSER
```

Mẫu nằm ở thư mục cha KHVT; nếu đặt nơi khác thì sửa `QMH_DOCUMENT_SAMPLE_ROOT`. `QMH_TEST_JAVA` và `QMH_TEST_JAR` cho phép chỉ định JDK 21/JAR khác; mặc định driver dùng JDK 21 của máy này. Driver tạo `qmh_docs_test_*`, namespace UUID, backend cổng 8082 và tệp private riêng, không import vào database chính. MCP whitelist có origin `http://127.0.0.1:8082`. Bỏ `QMH_DOCUMENT_BROWSER` để chỉ chạy API; không báo browser đã qua khi bỏ biến.

Lượt cuối 2026-10-11 **17:24 +07:00** đã hoàn tất 14 nhóm, gồm MCP thật; đối chiếu **2.124 dòng/10.671 trường hoặc chi tiết**, 0 sai lệch. Scan báo chưa OCR và test nhập tay riêng. Báo cáo `source/target/runtime/document-real-integration-report.json` có `completed: true`, `mainUnchanged: true`; source hash giữ nguyên. Schema test giữ lại phục vụ đối chiếu; backend/browser tự dừng, không xóa volume hay flush Redis. Xem [biên bản](../../docs/NGHIEM_THU_IMPORT_PCVT_VA_BAO_GIA.md).

Hồi quy backend: trong `source/`, `mvn.cmd '-Dtest=SourceDocumentParserTest,SourceDocumentWorkflowIntegrationTest' test`; đặt `QMH_DOCUMENT_SAMPLE_ROOT` để bật hai test Excel thật. Lượt bản cuối có **33 test qua**, không bỏ qua. Khi dùng `-DforkCount=0`, phải gắn Mockito agent vào JVM Maven; Surefire argLine không gắn agent cho JVM đang chạy. Chạy browser fixtures từ frontend bằng `node node_modules/@playwright/test/cli.js test tests/e2e/documents.spec.ts --workers=1`; `QMH_E2E_BUILT_UI=true` dùng dist trên cổng 5190 để giảm RAM.

## MySQL/Redis thật cho workbook legacy

Từ repository, sau khi Docker `qmh-local` healthy, `.env` riêng đã có cấu hình đúng và JAR/frontend đã build:

```powershell
$env:QMH_TEST_SKIP_BROWSER = 'true'
node --max-old-space-size=128 --max-semi-space-size=2 --expose-gc tools/local-test/run.mjs
Remove-Item Env:QMH_TEST_SKIP_BROWSER
```

Driver tạo **schema MySQL UUID mới** `qmh_test_*`, namespace Redis UUID, tài khoản tổng hợp với mật khẩu sinh trong bộ nhớ, backend cổng 8081 và thư mục PDF riêng. Không đổi mật khẩu/tài khoản hoặc dữ liệu database ứng dụng. Không xóa volume/flush Redis. Schema test giữ lại để đối chiếu; cần người vận hành quyết định thời điểm dọn dữ liệu test.

Kiểm tra gồm Flyway/health, login/CSRF/đổi mật khẩu, nhân sự/tài khoản, hai phiên Redis và thu hồi, tự khóa/last-admin, danh mục/phân quyền, PO/tổng tiền số lượng chữ/phát hành/phiên bản/hủy, giá theo currency, PDF/XLSX, operational paste, và workbook `QUANLYMUAHANGKHVT.xlsx` ở thư mục cha. Workbook chỉ commit trong schema test sau preview không có lỗi/header conflict; có lỗi thì kiểm tra API từ chối. Kiểm tra chống import lặp, checksum file gốc và audit không chứa mật khẩu test.

[workbook-source.mjs](workbook-source.mjs) đọc OpenXML độc lập để driver so workbook gốc → staging theo từng sheet/dòng/trường, không chỉ đếm bản ghi hoặc dựa vào parser Java. Chỉ mở NCC/LICH_SU/DON_HANG, không mở CONFIG; shared strings chỉ giải mã các mục được ba sheet dùng. Số so bằng mantissa/exponent BigInt, không chuyển giá trị thập phân sang Number; giữ mã định dạng có số 0 đầu và ngày theo Excel date window. So cả provenance `__numeric`. Kết quả chỉ xuất số lượng và tọa độ/trường sai lệch, không xuất giá trị giao dịch. Hàm là module được run.mjs gọi, không phải lệnh import trực tiếp.

Bộ đọc độc lập hiện hỗ trợ workbook `.xlsx` không có công thức trong các sheet nghiệp vụ; gặp công thức, ô lỗi, header/cell chưa hỗ trợ thì dừng rõ ràng. Không coi dừng kiểm tra là đã đối chiếu thành công, và không dùng module này để tuyên bố mọi workbook `.xls`/công thức được xác minh độc lập. Backend và driver phải kiểm tra lần lượt source → staging → dữ liệu nghiệp vụ; đối chiếu chỉ từ staging không đủ chứng minh parser đọc đúng nguồn.

Guard mới của backend đối chiếu dữ liệu đã flush trong transaction trước khi commit; mismatch trả `IMPORT_RECONCILIATION_FAILED` và rollback. Báo cáo commit có số nguồn/đã đối chiếu, lịch sử, PO, dòng PO và NCC. Driver thử cần đối chiếu các số này, staging, checksum gốc, preview lại không nhập trùng và CSV đủ issues. V6 tăng độ chính xác quantity/unit_price thành DECIMAL(38,18); Flyway trên schema thử mới phải được kiểm chứng riêng, không lấy test H2 thay cho migration MySQL.

`QMH_TEST_SKIP_BROWSER=true` tách lượt HTTP/import khỏi browser để giảm bộ nhớ. Bỏ biến này để chạy thêm MCP trên backend test: cần Chromium/frontend/MCP đã cài, đủ bộ nhớ, cổng 5174 trống. UI smoke fixture của MCP chạy riêng theo [README MCP](../browser-mcp/README.md).

Driver chạy Java lần lượt với heap 128 MB cho API tổng hợp, 384 MB cho đọc workbook và 256 MB cho commit/đối chiếu. Sau kiểm tra OpenXML, dữ liệu tạm được giải phóng; phiên HTTP phải tiếp tục hoạt động qua các lần restart nhờ Redis. Hai commit đồng thời phải có đúng một HTTP 200 và một HTTP 409 (`IMPORT_ALREADY_PROCESSED` hoặc `IMPORT_IN_PROGRESS` khi hết thời gian chờ khóa). Gửi lại sau khi hoàn tất phải nhận `IMPORT_ALREADY_PROCESSED`.

Các báo cáo JSON ở `source/target/runtime/real-integration-report.json` và `qmh_test_*-report.json` chỉ chứa kết quả, số dòng và mã lỗi. Log backend vẫn là dữ liệu local riêng; không gửi nguyên log/bí mật lên chat. Luôn xem `completed` và `failure`; có báo cáo không đồng nghĩa lượt chạy đã qua. Cấu hình máy hiện tại dùng JDK 21 tại đường dẫn trong script; nếu đổi máy, chỉnh đường dẫn launcher.

## H2 bổ sung khi Docker không hoạt động

Trong `source/`, với JDK 21/Maven:

```powershell
$env:QMH_TEST_WORKBOOK = (Resolve-Path -LiteralPath '../../QUANLYMUAHANGKHVT.xlsx').Path
mvn.cmd '-Dtest=LegacyWorkbookLocalTest' test
Remove-Item Env:QMH_TEST_WORKBOOK
```

Test opt-in dùng H2 file UUID riêng trong `target/runtime/`, tạo schema thử theo entities và bảng JDBC tối thiểu, preview/đếm source độc lập/commit khi hợp lệ, kiểm tra không tạo nghiệp vụ trong preview và không commit lặp. Không nạp `.env`, không đọc CONFIG, không kết nối MySQL/Redis. Database H2 và `workbook-h2-report.json` giữ lại; test không thay thế kiểm chứng Flyway/MySQL/Redis hoặc nghiệm thu kế toán.

`LegacyPreviewReplayTest` là regression tự động cho số cảnh báo, nhóm PO và staging không lặp khi tải lại file; dùng fixture tổng hợp. Test workbook thật không chạy khi chưa đặt `QMH_TEST_WORKBOOK`.

Lượt cuối đã qua **91 test backend thực thi**, một test Redis opt-in riêng bỏ qua; workbook H2 và PDF Unicode đã bật. Maven chạy trong một JVM (`-DforkCount=0 -Dspring.test.context.cache.maxSize=1`) có Mockito agent, heap 384 MB, metaspace 192 MB, compressed class space 32 MB và code cache 48 MB. Driver HTTP/MySQL/Redis cũng hoàn tất 19 nhóm kiểm tra trên `qmh_test_b54edb7456f2`: nguồn/staging 11.690 dòng, 91.526 trường, 0 sai lệch; commit đủ 10.561 lịch sử/262 PO/850 dòng PO, xử lý khóa và chống nhập lại qua. Log `import-backend-final.log`, `import-final-mysql-verified.log`; báo cáo `real-integration-report.json` có `completed: true`. Browser dùng fixtures chạy riêng, không phải browser thật của lượt driver này. Xem [hệ thống hiện tại](../../docs/HE_THONG_HIEN_TAI.md).

Máy hiện tại đã gặp thiếu Windows commit memory khi chạy Java/Maven/browser đồng thời. Chạy lần lượt với một worker Playwright; dùng heap giới hạn cho các tiến trình test. Không dừng Java của IDE hoặc reset database để giải quyết lỗi bộ nhớ.
