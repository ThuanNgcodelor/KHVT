# Kiểm thử API/database và workbook local

## MySQL/Redis thật

Từ repository, sau khi Docker `qmh-local` healthy, `.env` riêng đã có cấu hình đúng và JAR/frontend đã build:

```powershell
$env:QMH_TEST_SKIP_BROWSER = 'true'
node tools/local-test/run.mjs
Remove-Item Env:QMH_TEST_SKIP_BROWSER
```

Driver tạo **schema MySQL UUID mới** `qmh_test_*`, namespace Redis UUID, tài khoản tổng hợp với mật khẩu sinh trong bộ nhớ, backend cổng 8081 và thư mục PDF riêng. Không đổi mật khẩu/tài khoản hoặc dữ liệu database ứng dụng. Không xóa volume/flush Redis. Schema test giữ lại để đối chiếu; cần người vận hành quyết định thời điểm dọn dữ liệu test.

Kiểm tra gồm Flyway/health, login/CSRF/đổi mật khẩu, nhân sự/tài khoản, hai phiên Redis và thu hồi, tự khóa/last-admin, danh mục/phân quyền, PO/tổng tiền số lượng chữ/phát hành/phiên bản/hủy, giá theo currency, PDF/XLSX, operational paste, và workbook `QUANLYMUAHANGKHVT.xlsx` ở thư mục cha. Workbook chỉ commit trong schema test sau preview không có lỗi/header conflict; có lỗi thì kiểm tra API từ chối. Kiểm tra chống import lặp, checksum file gốc và audit không chứa mật khẩu test.

`QMH_TEST_SKIP_BROWSER=true` tách lượt HTTP/import khỏi browser để giảm bộ nhớ. Bỏ biến này để chạy thêm MCP trên backend test: cần Chromium/frontend/MCP đã cài, đủ bộ nhớ, cổng 5174 trống. UI smoke fixture của MCP chạy riêng theo [README MCP](../browser-mcp/README.md).

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

Máy hiện tại đã gặp thiếu Windows commit memory khi chạy Java/Maven/browser đồng thời. Chạy lần lượt với một worker Playwright; dùng heap giới hạn cho các tiến trình test. Không dừng Java của IDE hoặc reset database để giải quyết lỗi bộ nhớ.
