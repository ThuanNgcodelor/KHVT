# Phạm vi, quyết định và giả định đã chốt

## Mục tiêu

Thay Google Apps Script + Google Sheet bằng ứng dụng quản lý mua hàng chạy bằng Java 21/Spring Boot REST, React/Vite/Tailwind, MySQL và Redis. Giữ nghiệp vụ đang dùng trong workbook/Apps Script; từ backend demo chỉ chuyển phạm vi đăng nhập, tài khoản, phân quyền và danh bạ nhân sự cơ bản.

Đây là plan triển khai cho người vibecoding và tự kiểm thử. Nó không yêu cầu đọc/ghi database, container, file hay dịch vụ nào của hệ thống đang chạy riêng.

## Phạm vi MVP

### Mua hàng và tra cứu giá

- Tra cứu tên vật tư không phân biệt hoa thường, dấu tiếng Việt và khoảng trắng.
- Hiển thị giá/NCC gần nhất, lịch sử giá và tiền tệ; không trộn VND với USD.
- Nhập yêu cầu mua từ XLS/XLSX/CSV, PDF có text hoặc dán dữ liệu từ Excel.
- Thêm vật tư thủ công, tự gợi ý quy cách/đơn vị/giá/NCC gần nhất; cho phép sửa trước khi lập PO.
- Số lượng nhận số hoặc mô tả chữ như “Cân thực tế”; dòng chữ được in nhưng không tự cộng vào thành tiền.
- Chọn NCC, địa chỉ, tiền tệ, VAT, người lập và ghi chú.
- Tách các dòng theo NCC và tiền tệ thành PO riêng; cấp số PO không trùng.
- Sinh PDF tiếng Việt có thành tiền trước VAT, VAT và tổng sau VAT; tải PDF trong trình duyệt.
- Gọi lại PO để sửa; giữ số PO, tăng revision, giữ bản PDF/audit cũ.

### Login, tài khoản và nhân sự

- Chỉ tái sử dụng hành vi cần thiết từ backend demo: login, logout, thông tin người dùng hiện tại, đổi mật khẩu, quản trị tài khoản, gán role, khóa/mở khóa, reset mật khẩu và quản lý hồ sơ nhân viên/phòng ban/chức vụ.
- Quản trị tài khoản được giới hạn cho ADMIN. ADMIN có toàn quyền nghiệp vụ và phân quyền.
- Người dùng có thể liên kết với một hồ sơ nhân viên. Nhân viên/người dùng phải ACTIVE để đăng nhập.
- Không chuyển toàn bộ hệ thống HR demo, payroll, chấm công, hợp đồng, OCR, Telegram, push notification, booking hay dữ liệu seed.
- Không kết nối tới database hay API của hệ thống demo/hệ thống công ty. Nhân sự ban đầu được tạo bằng giao diện quản trị; bổ sung import nhân viên khi có file roster được duyệt.

### Import và quản trị

- Import workbook legacy theo bốn sheet: LICH_SU, DON_HANG, NCC, CONFIG; có preview, cảnh báo, xác nhận commit và báo cáo.
- Phân biệt import một lần để di trú database với import báo giá/yêu cầu mua để tạo giỏ hàng.
- Quản trị danh mục vật tư/NCC, audit, import batches và metadata PDF.
- Backup/restore MySQL có hướng dẫn và nghiệm thu thử.

## Quyết định kỹ thuật đã chốt

- Backend: Java 21, Spring Boot REST, modular monolith theo DDD thực dụng, JPA, Flyway.
- Frontend: React, TypeScript, Vite, Tailwind; không dùng Thymeleaf để làm giao diện.
- Auth: Spring Security + Spring Session Redis, cookie HttpOnly/Secure/SameSite; CSRF bật. Đây là cách cài mới an toàn, không bê giao thức JWT demo nguyên trạng.
- Redis chỉ lưu session/cache ngắn hạn; MySQL là nguồn dữ liệu nghiệp vụ duy nhất.
- Docker Compose chỉ khởi động MySQL và Redis. API Java chạy native/systemd trên máy chủ; React build được phục vụ cùng origin bởi Spring Boot. Cloudflare Tunnel chạy ngoài Docker và chỉ đưa ứng dụng ra domain.
- Compose dùng project name, volume và cổng localhost riêng; không đặt container_name, không chạy lệnh ảnh hưởng stack khác.
- PO PDF dùng template server-side FreeMarker + OpenHTMLToPDF hoặc thư viện tương đương, không dùng Thymeleaf.
- Apache POI xử lý XLS/XLSX, Commons CSV xử lý CSV; PDF scan/OCR chưa thuộc MVP.
- UI tham chiếu ảnh hệ thống mua hàng người dùng cung cấp: navy/blue, cam làm màu hành động, nền sáng, bảng nghiệp vụ dễ đọc.
- Không tích hợp OpenAI API trong MVP. Môi trường hiện tại không có skill OpenAI chuyên thiết kế frontend; plan mô tả tiêu chuẩn UI và dùng ảnh tham chiếu. Nếu người triển khai có skill cụ thể, đọc file skill đó trước khi code UI.

## Kiểm kê workbook hiện tại

Workbook có 4 sheet nhìn thấy:

| Sheet | Số dòng dữ liệu | Ghi chú |
|---|---:|---|
| LICH_SU | 10.563 | 2025-01-02 đến 2026-06-20; không có cột tiền tệ; 2 dòng thiếu ngày |
| DON_HANG | 850 | 262 số PO; 2026-07-09 đến 2026-10-06 |
| NCC | 279 | Sổ nhà cung cấp |
| CONFIG | 2 mục cấu hình | ACCESS_CODE cũ và PO_SEQ |

Các bất thường đã kiểm tra trong workbook:

- DON_HANG có 137 dòng thiếu MaNCC, 624 dòng thiếu MaHang, 38 dòng thiếu LoaiTien, 2 dòng thiếu VAT, 10 dòng thiếu địa chỉ và 5 dòng thiếu người lập.
- Một dòng DON_HANG có số lượng dạng chữ; các trường tên NCC, tên hàng, DVT, số lượng và đơn giá đều có dữ liệu.
- Không có công thức trong các sheet.
- Không được đưa giá trị ACCESS_CODE vào database mới, log, báo cáo hoặc seed. PO_SEQ cũ không phải nguồn cấp số đáng tin; đối chiếu số PO thực tế rồi xây lại sequence.
- Plan cũ ghi 10.561 dòng LICH_SU; con số đã được sửa theo file đang có là 10.563.

## Chính sách di trú được chọn

- 10.563 dòng LICH_SU được gán VND vì ứng dụng cũ hiển thị lịch sử không có tiền tệ như VND; đánh dấu nguồn tiền tệ là giả định legacy.
- 38 dòng DON_HANG thiếu LoaiTien cũng được gán VND nhưng phải có warning trong báo cáo.
- VAT thiếu để null/unknown, không tự đoán; PO đó không được tự tái xuất PDF cho tới khi người dùng xác nhận VAT.
- Ngày thiếu được giữ trong staging/legacy record với ngày null và warning; không bịa ngày, không dùng làm giá gần nhất.
- Mã NCC/MaHang thiếu: giữ nguyên snapshot tên, để liên kết catalog null; không loại bỏ giao dịch.
- ACCESS_CODE bị bỏ qua. Số PO legacy được giữ nguyên chuỗi; sequence mới được dựng từ PO theo ngày.
- Import có thể chạy lại an toàn: cùng checksum/batch được cảnh báo/không nhân đôi; file khác phải qua preview, đối chiếu và xác nhận policy append/update.

## Ngoài phạm vi MVP

- Chuyển toàn bộ HR demo hoặc kết nối hệ thống HR hiện hữu.
- Đăng ký công khai, đăng nhập Google, OTP email/quên mật khẩu qua email.
- Tích hợp ERP/kế toán, gửi email NCC, tỷ giá tự động.
- OCR PDF scan, workflow duyệt nhiều cấp, microservices.
- Tính năng AI dùng OpenAI API.
- Docker hóa API/frontend hoặc chạy Cloudflare Tunnel trong container.

## Definition of Done

- Đăng nhập, logout, đổi mật khẩu, ADMIN quản trị user/role; nhân sự có CRUD và liên kết account.
- ADMIN có wildcard permission; user thiếu quyền bị backend chặn dù gọi API trực tiếp.
- Import dry-run workbook hiển thị đủ sheet, số lượng dòng, cảnh báo dữ liệu; commit không mất snapshot hoặc tạo duplicate.
- Tìm vật tư không dấu, xem đúng giá mới nhất theo currency.
- Lập PO theo NCC, tạo PDF có subtotal/VAT/tổng; sửa PO tăng revision, không mất revision/PDF cũ nếu lỗi.
- Frontend có trang login, dashboard, mua hàng, tra cứu giá, nhân sự, tài khoản/phân quyền.
- Docker Compose chỉ có mysql và redis; port không public ra ngoài localhost.
- Hệ thống chạy native trên máy chủ qua Cloudflare Tunnel HTTPS; MySQL/Redis không được tunnel public.
- Unit, integration, Playwright và backup/restore checks đạt.

