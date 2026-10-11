# Nhập PCVT và báo giá mới

Luồng này ở `/imports` → **PCVT / báo giá mới**. Tài khoản cần quyền `IMPORT_OPERATIONAL`; tra lịch sử, gom hỏi giá và so sánh cần thêm `PRICE_READ`. Quản trị hiện cấp quyền qua vai trò, chưa có grant/deny riêng theo tài khoản.

## Nhập danh sách cần mua

1. Chọn loại **PCVT / yêu cầu cần mua**, chọn Excel/CSV và bấm **Kiểm tra tệp chứng từ**.
2. Nếu có nhiều sheet, chọn đúng sheet. Với `Copy of CVT 2026 - SCTX.xlsx`, chọn một số phiếu rồi chọn các dòng thực sự cần mua. Sổ năm giữ nguyên để nhập tiếp phiếu khác; không gộp cả năm thành một PCVT.
3. Đối chiếu tên, mã nội bộ, quy cách, ĐVT, số lượng và vị trí dòng nguồn. Có thể sửa giá trị trong preview hoặc thêm dòng thủ công; dữ liệu trích xuất gốc được giữ riêng. Dòng chưa có số phiếu phải được kiểm tra và gán đúng phiếu trước khi chọn.
4. Xác nhận đã kiểm tra rồi lưu. PCVT lưu trên server, có ID trong URL và mở lại được sau reload; chưa tạo PO hay ghi giá đã mua.
5. Bấm **Tra NCC và giá cũ**. Mỗi món có thể có nhiều NCC. Kết quả giữ ngày, snapshot NCC/vật tư, ĐVT, quy cách, giá và cảnh báo nguồn. Tên gần giống là gợi ý cần đối chiếu, không tự coi là cùng vật tư.
6. Chọn NCC cho từng món, **Lưu nhóm hỏi giá** hoặc tải XLSX. Mỗi NCC có một sheet chứa các món được chọn và số lượng cần mua. File không chứa giá cũ/giá đối thủ; hệ thống chưa tự gửi email.

Nguồn tham khảo gồm dữ liệu lịch sử Apps Script và PO đã phát hành hiện hành. PO hủy và revision cũ bị loại; một dòng PO đã có trong lịch sử không được đếm thêm khi lineage trùng. “PO đã phát hành” chưa có nghĩa hàng đã nhận hoặc đã thanh toán.

## Nhập báo giá

1. Chọn **Báo giá nhà cung cấp**, tải Excel/PDF có text. Chọn riêng sheet nếu một workbook có nhiều bản báo giá.
2. Kiểm tra NCC, ngày trong nội dung, tiền tệ, cơ sở VAT, các dòng giá/số lượng/ĐVT/quy cách. Mã hàng của NCC không tự thành mã vật tư nội bộ. Giá thiếu hoặc điều kiện số lượng tối thiểu vẫn có cảnh báo; không thay nhu cầu PCVT bằng số lượng chào.
3. Chọn PCVT đã lưu và nối từng dòng báo giá với đúng dòng yêu cầu sau khi đối chiếu. Không nối theo thứ tự dòng một cách tự động.
4. Xác nhận và lưu. Mở PCVT → **Xem so sánh báo giá**. Chênh lệch chỉ tính với giá cũ dương, có ngày và cùng cơ sở so sánh. Hệ thống hiển thị nguồn giá cũ được dùng; trường hợp không tương thích ghi rõ lý do.

Giá Apps Script thiếu cơ sở VAT hoặc tiền tệ giả định vẫn xem được, nhưng chưa dùng để tính phần trăm chắc chắn. Không tự đổi USD↔VND, thùng↔cái hoặc coi model gần giống là tương đương.

## PDF scan và dữ liệu cũ

PDF dạng ảnh được đánh dấu **chưa hỗ trợ OCR**; tải nguồn để xem, thêm dòng thủ công, kiểm tra rồi lưu. Không coi 0 dòng trích xuất là một báo giá rỗng đã nhập thành công.

`QUANLYMUAHANGKHVT.xlsx` là dữ liệu cũ của Apps Script: dùng tab **Dữ liệu lịch sử cũ**, không dùng làm mẫu cho PCVT/báo giá mới. Luồng này có preview/đối chiếu/commit riêng. Không commit lại trên database đang có lô đó.

Tệp gốc lưu private, có SHA-256; API tải lại kiểm tra checksum. Chứng từ xác nhận giữ bản nguồn và snapshot đã kiểm tra. Tải lại cùng tệp/sheet hoặc gửi lại cùng xác nhận không tạo thêm bản trùng. Sửa bản đã xác nhận cần chứng từ mới; chưa có thao tác sửa liên kết vật tư/alias độc lập sau xác nhận.

## Phạm vi hiện tại

Giới hạn: 10 MB/tệp, tối đa 20 sheet, 10.000 dòng nguồn/sheet, 100 cột, 5.000 dòng vật tư tổng cộng hoặc 30 trang PDF. Vượt giới hạn trả lỗi, không âm thầm cắt bớt. Danh sách hiển thị 50 chứng từ gần nhất; ID URL vẫn mở lại bản cũ.

Đợt này tập trung import → lưu → tra → gom hỏi → so sánh. OCR tự động, alias đã duyệt, đổi cơ sở giá lịch sử và lập PO trực tiếp từ phân bổ PCVT/báo giá còn ở bước tiếp theo. Kết quả kiểm thử thực tế được ghi riêng trong biên bản nghiệm thu; hướng dẫn này không thay bằng chứng kiểm thử.
