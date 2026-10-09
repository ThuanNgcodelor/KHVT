# Import dữ liệu, xuất file và PDF

## Hai luồng import tách biệt

### A. Legacy workbook migration

Mục đích: đưa dữ liệu database cũ từ 4 sheet vào MySQL một lần, có preview và report. Không dùng luồng này để tạo draft mua hàng.

Pipeline:

~~~text
Upload workbook -> size/type/checksum validation
 -> parse named sheets -> staging rows/raw snapshot
 -> validate/map/match -> preview counts + warnings/errors
 -> ADMIN confirm -> transactional/chunk commit
 -> reconcile report + mark batch committed
~~~

- Chỉ ADMIN được preview/commit.
- Nhận .xlsx/.xls; dùng POI WorkbookFactory + DataFormatter + DateUtil.
- Không giả định cột theo vị trí nếu header nhận diện được; sheet legacy phải map theo tên/header đã kiểm kê.
- Mã vật tư/NCC đọc text-safe; không để 02.1352/3.2419 thành date.
- Import idempotent theo checksum + mode + source row; lặp cùng file không tạo thêm giao dịch.
- Nếu sửa workbook sau baseline, coi là batch mới, preview delta và cần admin xác nhận append/update; không tự replace dữ liệu cũ.
- Staging giữ sheet, row number, raw JSON, warning/error, mapped IDs và kết quả commit.
- Không log/hiển thị value của ACCESS_CODE; không lưu nó vào cấu hình hệ thống.
- Bỏ PO_SEQ cũ, dựng lại sequence từ số PO hợp lệ; bảo toàn chuỗi PO legacy.
- Báo cáo tải CSV/XLSX có sheet/row/field, raw safe value, lý do và hướng xử lý; mask dữ liệu nhạy cảm phù hợp.

Mapping:

| Sheet | Mapping |
|---|---|
| LICH_SU | Ngay, MaNCC, NhaCungCap, MaHang, TenHang, DVT, SoLuong, DonGia -> historical price snapshots |
| DON_HANG | Group theo SoPO; Ngay/header fields -> purchase_orders; hàng -> purchase_order_items |
| NCC | MaNCC, TenNCC, DiaChi -> supplier staging/master |
| CONFIG | ACCESS_CODE bỏ qua; PO_SEQ không dùng làm nghiệp vụ |

Chính sách các dữ liệu thiếu trong workbook đã kiểm kê:

- 10.563 LICH_SU không có currency column: gán VND theo cách app cũ hiển thị và gắn ASSUMED_LEGACY.
- 38 DON_HANG thiếu LoaiTien: gán VND kèm warning.
- 2 DON_HANG thiếu VAT: lưu null/unknown, warning; không mặc định VAT.
- Khi group 850 dòng theo SoPO, các header fields của cùng PO phải nhất quán; nếu khác nhau, giữ raw row và yêu cầu admin chọn snapshot đúng trước commit.
- 2 LICH_SU thiếu ngày: giữ record với date null/warning; không được chọn là latest price.
- 137 DON_HANG thiếu MaNCC, 624 thiếu MaHang: giữ snapshot, nullable relation; không bỏ giao dịch.
- 10 địa chỉ NCC và 5 người lập thiếu: để null; không tự điền dữ liệu giả.
- Số lượng chữ có một dòng đã biết: lưu quantity_text, không biến thành số.
- Tên/mã lặp hoặc xung đột NCC cần preview; không merge dựa trên fuzzy match đơn thuần.

Acceptance baseline cho file đang có: 10.563 dòng LICH_SU, 850 dòng DON_HANG thành 262 PO, 279 dòng nguồn NCC; số supplier master sau deduplicate/review có thể khác số dòng sheet. Report phải phân loại đủ warning ở trên. Nếu file người dùng chọn khác checksum/số dòng, hiển thị thực tế và yêu cầu xác nhận, không hard-fail theo con số baseline.

### B. Import operational request/quotation

Mục đích: lấy dòng vật tư từ phiếu yêu cầu/báo giá vào giỏ draft; chưa commit thành lịch sử/PO.

- .xlsx/.xls: tìm header trong 15 dòng đầu, match TenHang/Tên vật tư/Tên hàng, Quy cách, DVT, Số lượng, Đơn giá; preview cho sửa mapping.
- CSV: UTF-8/BOM, detect comma/semicolon/tab; hiện preview trước parse.
- Paste Excel: tab-separated; hỗ trợ STT, tên, quy cách, đơn vị, số lượng.
- PDF có text: PDFBox extract text, parser theo cột/header; báo confidence, cho người dùng kiểm tra/sửa.
- PDF scan: báo OCR_NOT_SUPPORTED và cho nhập tay/dán; không giả vờ đọc đúng.
- Đơn giá/NCC từ file là giá tham khảo; user xác nhận NCC, currency và giá hiện tại.
- Không tự dùng file báo giá làm master history cho tới khi PO được phát hành.

## Parser số và cell

- Parser phải có test locale: 690.000 -> 690000, 1,5 -> 1.5, 1,000 cần theo format được xác nhận.
- Không dùng parseFloat trên string thô; preserve raw original.
- Dùng DataFormatter; formula cell có thể đọc cached value nhưng nếu thiếu cached value phải warning.
- Ngày Excel dùng DateUtil, timezone nghiệp vụ Asia/Ho_Chi_Minh.
- Với mã có leading zero/dấu chấm, raw display text là nguồn; không numeric-convert.
- Size limit, max rows/sheets, zip bomb protection, MIME/extension check, random temp filename, antivirus hook nếu môi trường hỗ trợ.
- Temp upload được xóa sau commit/expiry; file gốc migration chỉ giữ tại vùng private có retention policy.

## PDF PO

- Template FreeMarker/HTML server-side + OpenHTMLToPDF; React không tự tính/generate PDF nghiệp vụ.
- Include logo/tên công ty, PO, ngày, supplier/address/note, item lines, DVT/spec/qty/unit price, subtotal, VAT, grand total, currency và vùng ký.
- VND hiển thị 0 decimal; USD tối đa 2; dùng BigDecimal, rounding policy cố định trong domain tests.
- Dòng quantity_text in nguyên văn, đánh dấu không bao gồm trong subtotal; tổng cho biết có dòng chữ không tính.
- Legacy VAT null render “Chưa xác định VAT”; không tự phát hành PDF tính thuế.
- Font có glyph tiếng Việt và font license hợp lệ; test dấu, xuống trang, bảng nhiều dòng, text dài.
- Render từ immutable revision snapshot, không query lại danh mục sau khi PO issue.
- Sau commit PO, sinh file; lỗi generation lưu trạng thái FILE_FAILED, cho retry; giữ PDF revision cũ.
- File key có PO + revision; MySQL chỉ lưu metadata/checksum; tải file phải authorize.

## Export XLSX/CSV

- Export PO/filter, lịch sử giá và import report.
- POI SXSSFWorkbook khi dữ liệu lớn; freeze header, autofilter, format số/ngày.
- Mã hàng, mã NCC, PO luôn là text.
- Export chỉ các cột user có quyền xem; không export password hash/session/audit secret.

