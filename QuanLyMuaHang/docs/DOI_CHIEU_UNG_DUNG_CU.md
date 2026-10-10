# Đối chiếu Index.html / Mã.js với ứng dụng mới

Hai file Google Apps Script và workbook đang ở thư mục cha của `QuanLyMuaHang/`. Chúng là nguồn đối chiếu nghiệp vụ, không phải entrypoint chạy của React/Spring Boot.

## Đoạn return HTML trong Mã.js

`buildPoHtml_(p)` tạo **chuỗi HTML của chứng từ Đơn đặt hàng**. CSS trong `<style>` định dạng bản in; các biểu thức như `esc(p.po)` và `esc(p.ncc)` đưa dữ liệu PO/NCC vào mẫu và escape ký tự HTML. Hàm không tạo trang đăng nhập hay màn mua hàng.

`exportPO(payload)` gọi hàm này, dùng `Utilities.newBlob(...).getAs(MimeType.PDF)` để chuyển HTML thành PDF, lưu Google Drive và ghi các dòng giao dịch vào `DON_HANG`. Luồng sửa cũ xóa các dòng/file cũ rồi ghi lại. Ứng dụng mới lưu snapshot/revision và tệp PDF theo phiên bản.

Backend mới dùng `PurchaseOrderPdfRenderer` và PDFBox để tạo chứng từ Unicode trực tiếp: header công ty theo mẫu cũ, thông tin NCC, bảng quy cách/ĐVT/số lượng/đơn giá/thành tiền, tạm tính/VAT/tổng, ghi chú và ba vị trí ký. Dữ liệu workbook không được thực thi như HTML. Đây là mẫu triển khai cần phòng KHVT duyệt nội dung trước khi dùng chính thức; không tự xác nhận địa chỉ/thông tin công ty trong mã cũ là thông tin pháp lý hiện hành.

## Các luồng đã nối vào giao diện

| Hành vi cũ | Giao diện mới | Luồng API |
|---|---|---|
| Mã truy cập chung | Đăng nhập tài khoản và cổng ứng dụng theo quyền | `/api/auth/*` |
| Gõ tên/gợi ý vật tư | Lập đơn → thêm từ danh mục hoặc nhập dòng thủ công | `/api/catalog/materials` |
| Giá/NCC gần nhất | Tra cứu giá và nút lấy giá/NCC trong giỏ dòng hàng; chọn loại tiền | `/api/prices`, `/api/prices/latest` |
| Excel/CSV/PDF text và dán Excel | Nhập dữ liệu → preview → chọn dòng → chuyển sang lập đơn | `/api/imports/operational/*` |
| SL bằng chữ | Chọn kiểu số lượng chữ; giữ nguyên trên đơn, loại khỏi tổng tiền | DTO `quantityText`, tổng do backend tính |
| Giỏ dòng hàng, checkbox, bỏ dòng | Màn lập đơn và kiểm tra từng nhóm NCC | State React, chưa ghi nghiệp vụ trước thao tác lưu |
| Tách nhiều NCC thành nhiều đơn | NCC mặc định hoặc riêng từng dòng → preview nhóm → lưu từng bản nháp | `POST /api/purchase-orders` mỗi nhóm |
| Số PO tự cấp theo ngày | Backend cấp số khi lưu bản nháp | Sequence MySQL theo ngày |
| Xuất đơn PDF | Lưu nháp → kiểm tra chi tiết → phát hành → tải PDF | `POST /{id}/issue`, sau đó `GET /{id}/pdf` |
| Gọi lại PO để sửa | Tìm danh sách, mở chi tiết, sửa với lý do, tăng revision | `PUT /api/purchase-orders/{id}` |
| Hủy đơn | Nhập lý do và xác nhận hủy | `POST /{id}/cancel` |
| Nhớ địa chỉ NCC | Quản lý địa chỉ ở Danh mục/NCC; PO lưu snapshot địa chỉ khi tạo/sửa | `/api/catalog/suppliers` |
| Xuất file dữ liệu | XLSX PO và lịch sử giá theo bộ lọc | `/api/exports/*` |
| Di chuyển workbook cũ | Tab workbook: preview/checksum/cảnh báo → đối chiếu → xác nhận commit | `/api/imports/legacy/*` |

## Khác biệt và giới hạn

- Giá gần nhất chỉ lấy bản ghi có ngày, cùng loại tiền. Khi có mã, tra đúng mã; không tự thay bằng vật tư gần tên. NCC lấy từ giá được nối vào danh mục khi khớp mã; trường hợp không khớp cần chọn thủ công.
- Lưu bản nháp và phát hành là hai thao tác riêng. Tải PDF là thao tác đọc, không phát hành hoặc ghi giá. Người chỉ xem được tải PDF đã có; chưa có PDF trả `409 PDF_NOT_ISSUED`.
- Giỏ chưa lưu nằm trong bộ nhớ trang. Reload/rời trang có thể mất giỏ; bản nháp đã lưu có trong danh sách PO. Không sao chép cơ chế localStorage của mã truy cập chung cũ.
- Mỗi đơn tối đa 200 dòng. Nhóm đã lưu có liên kết kết quả, không được gửi lại từ cùng hộp kiểm tra. Nếu mất kết nối sau request lưu, kiểm tra danh sách trước khi thử lại; backend chưa có idempotency key cho tạo PO.
- PDF scan chưa có OCR. Operational preview chưa gán NCC tự động và chưa tạo PO/lịch sử. Người dùng rà dữ liệu và chọn NCC trong màn lập đơn.
- Tệp PDF lưu ở `FILE_STORAGE_ROOT`, có phiên bản và cần backup cùng database. Chưa tích hợp Google Drive/Cloudflare Tunnel.
- CRUD role/permission, grant module độc lập, xin/duyệt quyền và Bán hàng vẫn chưa triển khai. Các chức năng nhân sự/tài khoản hiện cấp quyền qua vai trò.

Kết quả kiểm thử hiện hành và import workbook xem `HE_THONG_HIEN_TAI.md`; bảng đối chiếu này mô tả code và luồng, không thay thế nghiệm thu nghiệp vụ.
