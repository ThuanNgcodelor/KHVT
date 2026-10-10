# Đối chiếu CSDL và workbook KHVT

Kiểm tra ngày **2026-10-10**. Bản đọc MySQL nhất quán được lấy lúc **22:29:33 +07:00**, từ schema local `quanlymuahang`. Lượt này chỉ đọc dữ liệu nghiệp vụ, không sửa database hoặc workbook, không đọc bảng tài khoản và không mở nội dung sheet CONFIG.

**Kết luận: dữ liệu đã chuyển đúng theo quy tắc import hiện tại; chưa thể xác nhận nguồn đã chuẩn nghiệp vụ.** Không phát hiện mất dòng, lệch trường đã nhập hoặc liên kết trỏ tới bản ghi không tồn tại. Các trường thiếu, giá/số lượng bất thường và biến thể mã/tên/đơn vị vẫn cần đối chiếu chứng từ.

## Phạm vi đối chiếu đã qua

| Kiểm tra | Kết quả |
|---|---|
| File gốc | 682.929 byte; SHA-256 `2ea5e68728de5ac68277612c25a47cc4859725c572a69884e04b2929b6eb9608`; không đổi sau kiểm tra |
| Excel → staging | Toàn bộ 11.690 dòng, 91.526 trường được đối chiếu độc lập từ OpenXML; 0 chênh lệch giá trị sau quy đổi biểu diễn số |
| Staging → MySQL | Đối chiếu ngày, mã/tên nguồn, số lượng/số lượng chữ, đơn giá, currency/VAT, quy cách/ghi chú, nhóm PO/thứ tự dòng, liên kết và cờ nguồn theo mapping hiện tại; 0 chênh lệch |
| LICH_SU | 10.561 dòng nguồn → 10.561 bản ghi lịch sử |
| DON_HANG | 850 dòng nguồn → 262 PO và đủ 850 dòng PO; mỗi PO tối đa 23 dòng |
| NCC/vật tư | 391 bản ghi NCC, 2.997 vật tư; toàn bộ khớp kết quả xử lý các sheet theo chính sách hiện tại |
| Toàn vẹn liên kết | 7 nhóm kiểm tra bản ghi được tham chiếu không tồn tại đều bằng 0; NULL có chủ đích được thống kê riêng bên dưới |
| Ngày/số thập phân | Không phát hiện lệch ngày hoặc làm tròn do scale cột SQL trong lô này |
| Workbook | Không có công thức hoặc ô lỗi Excel; đầy đủ các header được importer nhận diện trong 3 sheet nghiệp vụ |

Có 14 khác biệt chỉ về biểu diễn ô số thành chuỗi, như `14.0` → `14`, không thay đổi giá trị số. Trường hợp tên NCC ở `LICH_SU!C16` vẫn cần rà soát nghiệp vụ dù phép chuyển biểu diễn đúng.

279 là **số dòng sheet NCC**, không phải số bản ghi NCC cuối cùng. Mô phỏng lại chính sách import từ các bảng nghiệp vụ rỗng trước commit cho thấy 279 dòng này tương ứng 271 NCC; các dòng có mã ở sheet khác bổ sung 120 NCC, tổng 391, khớp toàn bộ master thực tế. Đây là kết quả tái dựng từ nguồn và code; database hiện không lưu ID NCC đích trên từng dòng staging NCC.

Ngày nguồn LICH_SU: **2025-01-02–2026-06-20**; DON_HANG: **2026-07-09–2026-10-06**. Hai dòng cuối sheet LICH_SU không có trường nghiệp vụ, nên không tính thành bản ghi dữ liệu.

## Những điểm chưa chuẩn hóa

| Điều kiện cần rà soát | Số lượng xác nhận | Ý nghĩa |
|---|---:|---|
| Thiếu mã NCC | 4.275 dòng | NCC 58, LICH_SU 4.080, DON_HANG 137; không đủ mã để luôn liên kết chính xác |
| Thiếu mã hàng | 624 dòng DON_HANG | Giữ tên/quy cách nguồn; chưa liên kết vật tư danh mục |
| Thiếu đơn vị tính | 1.038 dòng LICH_SU | Cần xác minh để so sánh giá theo cùng đơn vị |
| Số lượng lịch sử bằng 0 / âm | 184 / 8 dòng | Có thể là dữ liệu thiếu, điều chỉnh hoặc trả hàng; cần chứng từ để kết luận |
| Đơn giá lịch sử bằng 0 / âm | 225 / 2 dòng | Không đủ căn cứ coi là giá mua bình thường hoặc tự sửa thành giá dương |
| Một mã NCC có nhiều tên chuẩn hóa | 6 mã, 345 dòng liên quan | Cần kiểm tra mã dùng lại, tên cũ/tên mới hoặc nhập sai; không tự gộp |
| Một mã hàng có nhiều tên chuẩn hóa | 75 mã, 574 dòng liên quan | Cần kiểm tra đồng nhất vật tư/quy cách |
| Một mã hàng có nhiều đơn vị chuẩn hóa | 36 mã, 220 dòng liên quan | Cần đối chiếu đơn vị và hệ số quy đổi; không chỉ sửa cách viết |
| Trùng toàn bộ trường nguồn lịch sử | 286 nhóm, gồm 699 dòng | Tương ứng 413 lần xuất hiện thêm; chưa chứng minh giao dịch trùng vì thiếu mã chứng từ |

Các điều kiện có thể chồng nhau. **234 dòng lịch sử khác nhau** có ít nhất một điều kiện giá/số lượng bằng 0 hoặc âm; không cộng 234 vào 4.803 dòng cảnh báo để suy ra tổng lỗi.

Một số tọa độ cụ thể để rà soát trong Excel:

- `LICH_SU!C16`: tên NCC là ô số, được đọc thành `3`.
- `LICH_SU` dòng **8934, 10017**: đơn giá âm.
- `LICH_SU` dòng **2617, 8838, 8934, 9137, 9395, 9494, 9711, 9831**: số lượng âm.
- `DON_HANG` dòng **444**: số lượng bằng 0.
- `DON_HANG` dòng **578**: số lượng chữ; được giữ trong `quantity_text`, không coi là số lượng số để nhân đơn giá.
- `DON_HANG` dòng **2, 3**: không có VAT; database giữ NULL, không tự đổi thành 0%.

## Loại tiền và VAT cần xác minh

LICH_SU **không có cột loại tiền**. Importer lưu cả 10.561 bản ghi với currency VND và `currency_basis=ASSUMED_LEGACY`. Có thể xác nhận đây là cách hệ thống lưu, **không thể xác nhận tất cả giá nguồn thực sự là VND chỉ từ workbook**. Cần đối chiếu nguồn/chứng từ trước khi dùng làm giá chuẩn hoặc quy đổi tiền tệ.

Theo từng PO, DON_HANG có **227 PO ghi VND, 24 PO ghi USD, 11 PO không ghi loại tiền**. 11 PO thiếu loại tiền gồm 38 dòng và được mặc định VND theo chính sách hiện tại, với dấu nguồn trong staging.

Có 2 PO chưa biết VAT, được giữ NULL. Các VAT khác trong nguồn là dữ liệu nguồn, không phải kết luận về thuế suất phải áp dụng. Lượt kiểm tra này không xác minh quy định thuế hoặc tính đúng của chứng từ kế toán.

## Liên kết danh mục và giới hạn của hệ thống

| Trường chưa liên kết danh mục | Số bản ghi | Cách lưu hiện tại |
|---|---:|---|
| NCC trong lịch sử | 3.637 | `supplier_id=NULL`, còn tên/mã nguồn |
| NCC của PO | 9 | `supplier_id=NULL`, còn thông tin NCC nguồn |
| Vật tư của dòng PO | 624 | `material_id=NULL`, còn tên/quy cách/đơn vị nguồn |

Đây là các liên kết NULL theo chính sách giữ nguồn, không phải liên kết trỏ tới ID đã mất. Khi không có mã và tên không khớp chính xác, duy nhất, importer không tự đoán để gắn danh mục.

Theo quy tắc khớp mã hiện tại, có 339 lịch sử và 62 dòng PO giữ tên vật tư nguồn khác tên master sau chuẩn hóa; 308 lịch sử giữ tên NCC nguồn khác master. Snapshot không bị ghi đè. Tuy nhiên, cần rà soát các trường hợp dùng chung mã trước khi coi chúng là cùng thực thể nghiệp vụ.

Các giới hạn được xác nhận từ code và dữ liệu:

- Legacy validator hiện kiểm tra khả năng đọc giá/trường bắt buộc; **chưa tạo cảnh báo riêng cho giá/số lượng bằng 0 hoặc âm, thiếu đơn vị, biến thể mã/tên/đơn vị và giao dịch có trường giống nhau**. Vì thế 4.803 cảnh báo hiện có chưa bao phủ mọi vấn đề nghiệp vụ.
- Cả 279 dòng staging NCC đã COMMITTED nhưng chưa ghi ID/type của NCC đích. Có thể đối chiếu master bằng tái dựng quy tắc; chưa có truy vết trực tiếp dòng NCC → bản ghi master.
- Lịch sử được gán category MATERIAL; nguồn không có phân loại. Không coi đó là phân loại nghiệp vụ đã được duyệt.
- PO cũ được gán trạng thái EXPORTED theo chính sách import; workbook không có cột trạng thái để xác minh quy trình duyệt/phát hành. Lượt này không nghiệm thu PDF hoặc quy trình phê duyệt.

**“0 lỗi import” không đồng nghĩa “0 vấn đề dữ liệu”.** Nó nói về khả năng đọc/ghi và các điều kiện chặn được triển khai hiện tại.

## Vì sao ban đầu màn hình 850 dòng không cho nhập

Heading **“Kiểm tra 850 dòng”** chỉ được render cho tab **Yêu cầu mua / báo giá**. Sau preview, frontend chọn tất cả 850 dòng; nút chuyển sang lập một đơn mới bị khóa nếu chọn quá 200 dòng. Operational preview có thể đọc nhiều hơn 200 dòng; giới hạn 200 là số dòng chuyển sang lập đơn mỗi lượt, không phải giới hạn toàn bộ workbook lịch sử.

Workbook này chứa nhiều PO cũ, nên cần tab **Workbook dữ liệu cũ**, xử lý NCC/LICH_SU/DON_HANG và nhóm theo số PO. 850 dòng đã được nhóm thành 262 PO; mỗi PO lớn nhất chỉ 23 dòng. Lô 1 đã COMMITTED và preview cùng checksum nhận diện tệp đã nhập.

Đây là nguyên nhân xác nhận được cho màn hình “Kiểm tra 850 dòng”. Không suy ra nguyên nhân của các lỗi khác nếu không có log/request tương ứng. File đọc được, không có bằng chứng workbook bị hỏng trong lượt này.

Code đối chiếu: `ImportsPage.tsx:26,46-47`; `LegacyWorkbookImportService.java:214,280-302,415-446,540-560`; `PurchaseOrderService.java:251-267`.

## Tệp bằng chứng và danh sách cần rà soát

- [Danh sách dòng cần rà soát](../source/target/runtime/workbook-rows-to-review.csv): sheet, số dòng Excel và mã điều kiện; không chứa toàn bộ nội dung giao dịch. Gồm **6.744 dòng** theo bộ kiểm tra bổ sung, không phải 6.744 lỗi import. Có cả điều kiện cảnh báo, thiếu thông tin và ứng viên cần xác minh.
- `source/target/runtime/audit-workbook-source-staging-report.json`: Excel → staging.
- `source/target/runtime/audit-workbook-db-reconciliation.json`: staging → MySQL, liên kết và tái dựng danh mục.
- `source/target/runtime/workbook-quality-audit.json`, `workbook-review-summary.json`: số liệu chất lượng nguồn.
- Các bản trích xuất riêng `workbook-normalized.json`, `audit-workbook-db-snapshot.json` chỉ phục vụ kiểm tra local trong thư mục target được Git bỏ qua, không đưa dữ liệu thô vào báo cáo này.

Ưu tiên tiếp theo là đối chiếu mã NCC/vật tư và đơn vị, xác minh currency của lịch sử, rồi duyệt các dòng số lượng/giá bất thường và các nhóm giống nhau bằng chứng từ. Không tự xóa các dòng giống nhau hoặc sửa số âm/bằng 0 khi chưa biết bản chất giao dịch. Lượt này chưa đánh giá toàn bộ dữ liệu ngoài phạm vi workbook, tất cả vai trò, tải đồng thời, khôi phục backup hoặc production.
