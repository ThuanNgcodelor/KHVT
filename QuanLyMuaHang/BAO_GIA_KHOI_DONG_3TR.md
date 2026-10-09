# BÁO GIÁ TRIỂN KHAI PHẦN MỀM

## 1. Giá trọn gói

**Tổng giá: 3.000.000 ₫ (ba triệu đồng)** cho phiên bản cơ bản có đủ các phân hệ nghiệp vụ bên dưới. “Cơ bản” giới hạn độ sâu và các trường hợp đặc biệt, không có nghĩa là chỉ làm giao diện mẫu; từng luồng được nghiệm thu theo tiêu chí tại mục 3.

| Hạng mục | Phạm vi cơ bản | Giá |
|---|---|---:|
| Nền tảng, cấu hình và dữ liệu | Java 21/Spring Boot REST, React/Vite/TypeScript/Tailwind, cấu hình API tập trung; Docker Compose riêng chỉ chạy MySQL và Redis; hướng dẫn chạy và backup cơ bản. | 600.000 ₫ |
| Đăng nhập, phân quyền, nhân sự | Login/logout; ADMIN; tài khoản, gán vai trò cố định; hồ sơ nhân viên, phòng ban, chức vụ; chặn API theo quyền. | 400.000 ₫ |
| Danh mục, lịch sử và tra cứu giá | Danh sách/thêm/sửa nhà cung cấp và vật tư cơ bản; tìm tên vật tư; xem giá/NCC gần nhất và lịch sử theo loại tiền, không quy đổi ngoại tệ. | 500.000 ₫ |
| Import workbook cũ | Nhập một workbook đã thống nhất theo cấu trúc các sheet `LICH_SU`, `DON_HANG`, `NCC`, `CONFIG`; xem trước, báo dòng lỗi/cảnh báo, xác nhận nhập; bỏ qua `ACCESS_CODE`. | 500.000 ₫ |
| Lập đơn mua, nhập yêu cầu và xuất tài liệu | Nhập tay/dán từ Excel; nhận XLSX/CSV và PDF có văn bản; chọn NCC, tiền tệ, VAT; tách PO theo NCC/tiền tệ; sửa và xuất lại PDF; xuất dữ liệu cơ bản. Không OCR PDF scan. | 700.000 ₫ |
| Kiểm thử, bàn giao và hỗ trợ triển khai | Kiểm thử luồng chính; tài liệu chạy hệ thống; hướng dẫn Cloudflare Tunnel và một buổi hỗ trợ xác nhận triển khai trên hạ tầng khách hàng cung cấp. | 300.000 ₫ |
| **Tổng cộng** | **Phiên bản cơ bản bao phủ toàn bộ nhóm chức năng tại mục 2.** | **3.000.000 ₫** |

Giá trên là giá trọn gói theo phạm vi được mô tả. Thuế/hóa đơn (nếu phát sinh) thực hiện theo tư cách bên cung cấp và thỏa thuận hai bên. Tên miền, máy chủ/VPS và dịch vụ bên thứ ba do khách hàng thanh toán.

## 2. Chức năng bao gồm ở mức cơ bản

### 2.1. Tài khoản và nhân sự

- Đăng nhập, đăng xuất, thông tin tài khoản hiện tại và đổi mật khẩu.
- ADMIN quản lý tài khoản, khóa/mở tài khoản và gán các vai trò cố định: `ADMIN`, `HR_MANAGER`, `PLANNER`, `VIEWER`.
- Quản lý hồ sơ nhân viên, phòng ban, chức vụ; liên kết nhân viên với tài khoản.
- Backend kiểm tra quyền cho thao tác quản trị; không chỉ ẩn nút ở giao diện.
- Không chuyển database, mật khẩu hoặc dữ liệu người dùng từ backend demo. Chỉ tham khảo hành vi login và nhân sự cần thiết.

### 2.2. Nhà cung cấp, vật tư và tra cứu giá

- Xem/thêm/sửa/ngừng sử dụng nhà cung cấp và vật tư ở các trường thông tin cơ bản.
- Tìm vật tư theo tên; hỗ trợ tìm không phân biệt hoa thường và dấu tiếng Việt ở mức cơ bản.
- Hiển thị đơn vị tính, giá và nhà cung cấp gần nhất; xem các giao dịch giá lịch sử theo loại tiền.
- Không tự quy đổi VND/USD hoặc các tiền tệ khác; không lấy tỷ giá bên ngoài.

### 2.3. Nhập workbook lịch sử

- Hỗ trợ **một workbook nguồn đã chốt** với các sheet `LICH_SU`, `DON_HANG`, `NCC`, `CONFIG` theo file mẫu khách hàng cung cấp.
- Có bước xem trước, tổng số dòng, dòng hợp lệ, cảnh báo/lỗi trước khi xác nhận nhập; có báo cáo sau khi nhập.
- Giữ lại thông tin tên hàng/NCC gốc khi thiếu mã liên kết; không tự bịa ngày, VAT hoặc mã còn thiếu.
- Không nhập giá trị `ACCESS_CODE`; dữ liệu thiếu tiền tệ được cảnh báo và chỉ gán mặc định theo xác nhận của khách hàng.
- Cấu trúc sheet/cột khác file mẫu, nhiều workbook nguồn hoặc cần làm sạch dữ liệu thủ công sẽ được xem là phát sinh.

### 2.4. Yêu cầu mua và đơn đặt hàng

- Thêm dòng mua thủ công hoặc dán các dòng sao chép từ Excel; đọc XLSX/CSV và PDF có lớp văn bản để tạo danh sách nháp cơ bản.
- Tra cứu/gợi ý vật tư và điền giá/NCC gần nhất; người dùng được sửa trước khi tạo đơn.
- Chọn NCC, địa chỉ, tiền tệ, VAT, ghi chú và người lập; nhóm dòng thành PO riêng theo NCC và tiền tệ.
- Cấp số PO tăng dần, tránh trùng; cho phép sửa PO và tạo lại PDF. Lưu trạng thái/revision cơ bản, không có luồng duyệt nhiều cấp.
- PDF tiếng Việt gồm thành tiền trước VAT, VAT và tổng sau VAT. Số lượng dạng chữ vẫn in trên PO nhưng không tính thành tiền; số lượng số được tính bình thường.
- Không bao gồm OCR tài liệu scan, gửi email cho NCC, hóa đơn điện tử, đối chiếu ERP/kế toán hoặc chữ ký số.

### 2.5. Giao diện, xuất dữ liệu và vận hành

- Giao diện quản trị sáng, ưu tiên desktop; có màn hình login, dashboard cơ bản, mua hàng, tra cứu giá, nhà cung cấp/vật tư, nhân sự và tài khoản.
- Xuất PDF PO và file CSV/XLSX cho danh sách/lịch sử ở các màn hình đã thống nhất.
- Compose của dự án chỉ chạy MySQL và Redis; API/frontend chạy theo hướng dẫn bàn giao, không đụng tới Docker project khác.
- Cung cấp cấu hình mẫu, hướng dẫn khởi động/dừng, backup cơ bản và một buổi hướng dẫn xác nhận deploy qua Cloudflare Tunnel trên hạ tầng khách hàng cung cấp.

## 3. Tiêu chí nghiệm thu cơ bản

- Tạo được tài khoản ADMIN; đăng nhập/logout; quyền sai bị backend từ chối; ADMIN quản lý được tài khoản và nhân sự theo phạm vi.
- Import được workbook mẫu đã chốt; xem được thống kê dòng và cảnh báo; dữ liệu nguồn thiếu được giữ kèm cảnh báo, không tự suy đoán âm thầm.
- Tìm được vật tư, xem giá/NCC gần nhất và lịch sử mà không gộp các loại tiền khác nhau.
- Lập được một PO hoặc nhiều PO khi chọn nhiều NCC; số PO không trùng trong các thao tác kiểm thử; PDF có subtotal, VAT và tổng đúng với dữ liệu đã nhập.
- Nhập được danh sách nháp từ các định dạng/phương thức đã nêu; xuất được PDF/CSV/XLSX theo phạm vi cơ bản.
- Khởi động được MySQL và Redis bằng Compose riêng; tài liệu bàn giao có hướng dẫn chạy và triển khai cơ bản.
