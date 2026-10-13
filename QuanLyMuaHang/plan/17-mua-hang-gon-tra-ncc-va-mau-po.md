# Mua hàng gọn, tra NCC và mẫu đơn đặt hàng

Ngày lập: **12/10/2026**. Trạng thái: **đang triển khai một phần; chưa nghiệm thu**. Phạm vi chỉ phần **Mua hàng**; giữ cấu trúc navbar/sidebar, cổng ứng dụng và Quản trị. Người dùng đã xác nhận bỏ thành tiền/tổng tiền ở **cả PDF và Excel đơn hàng gửi NCC**, còn tra cứu đơn mua giữ tổng.

### Trạng thái triển khai 12/10/2026

- Đã sửa template PDF/XLSX mới: 6 cột, ĐVT rộng hơn, số căn phải và định dạng theo `vi-VN`, ngày `dd/MM/yyyy`; tổng nội bộ không đổi. Maven portable 3.9.11/JDK 21 hiện chạy được. Nhóm test PO/import 17 trường hợp: 16 qua, 1 bỏ qua; render/so PDF thực tế vẫn cần.
- Đã thêm parser version `source-v3-visible-row-count`, bỏ hàng bị ẩn, trả số hàng ẩn đã loại khỏi từng sheet và reparse cache cũ chỉ khi còn PREVIEW; confirmed snapshot bất biến. Thêm test unit cho hàng ẩn nhưng chưa chạy Maven.
- Đã thêm `POST /api/imports/documents/{id}/lookup-preview` với quyền `IMPORT_OPERATIONAL + PRICE_READ`, CSRF mặc định, chỉ nhận `lineKey` thuộc bản xem trước và tối đa 200 dòng; đã nối nút tra trong preview. Chưa chạy integration/MySQL hoặc browser test.
- Đã thu gọn sidebar desktop và lưu preference trong localStorage cho khu Mua hàng; form PO tách giá/NCC lịch sử khỏi giá hiện tại và chỉ chép khi người lập bấm. Footer khớp nhãn link đã chọn. Build TypeScript/Vite qua, 10 test Vitest qua, Playwright desktop/mobile 104/104 qua sau cập nhật test theo giao diện mới.
- Tiếp tục ngày 12/10: thêm `GET /api/prices/supplier-history` (yêu cầu `PRICE_READ`) có phân trang, lọc tên/mã NCC và loại tiền; trang Tra cứu giá hiển thị mặt hàng, ngày, số lượng, đơn giá và nguồn theo NCC mà không cần mở từng PO. Thêm JPA test cho NCC liên kết/snapshot, loại tiền và phân trang (2/2 qua), cùng API integration test có đăng nhập/quyền `PRICE_READ` (1/1 qua). Sửa test slice SPA để mock `RememberMeService` theo dependency hiện tại của `SecurityConfig`. Frontend build qua; full Playwright 106/106 và Vitest 10/10 qua. Maven toàn bộ suite chạy 150 test, 0 lỗi, 7 bỏ qua.
- Launcher dev đã build và chạy profile `dev` lúc 23:42 +07:00 tại `http://localhost:8080`. Health `UP`, `/login` và JS `200`, API NCC anonymous `401`; MySQL `quanlymuahang` báo schema v10 đã mới nhất, không chạy migration. Launcher status xác nhận PID 1872. Server vẫn đang chạy; dừng bằng `CHAY_KHVT.cmd stop` (không dừng MySQL/Redis). Chưa đăng nhập tài khoản thật, nên chưa xác minh truy vấn NCC qua API live.
- Còn thiếu: rà nghiệm thu tra NCC bằng backend thật và render chứng từ; bulk catalog; server-side workspace draft/version/conflict; chuyển PCVT thành PO với phân bổ và idempotency; kiểm thử backend/MySQL.

### Bổ sung giao diện 13/10/2026

- Thu gọn/mở rộng thanh điều hướng bằng nút có nhãn/icon rõ; chỉ hiện và lưu trạng thái ở khu Mua hàng với khóa `qmh:ui:purchasing-sidebar:v1`. Quản trị giữ chiều rộng bình thường; mobile vẫn dùng drawer và Escape. Browser test desktop/mobile cho nhớ trạng thái, điều hướng Quản trị và drawer qua 4/4.
- Màn PCVT gọn hơn: chứng từ đã lưu thu gọn mặc định, metadata ít dùng nằm trong chi tiết từng dòng, cột nguồn ở cuối và bỏ ô nhập SL bằng chữ. Giá trị SL chữ từ file vẫn được giữ để đối chiếu.
- Build Vite/TypeScript qua; Vitest 10/10; bộ Playwright liên quan chứng từ và thanh điều hướng 24/24 qua sau bản cuối. Launcher đã đóng gói và khởi động lại profile `dev`; health `UP`, `/login` 200 và phục vụ bundle mới. Đây vẫn là kiểm thử UI bằng API fixtures; không thay thế kiểm tra dữ liệu nghiệp vụ thật.

## 1. Kết quả cần đạt

| Việc đang vướng | Hành vi sau sửa |
|---|---|
| Đơn xuất có nhiều thông tin tiền, ĐVT hẹp | Mẫu 6 cột như PDF tham chiếu, ĐVT rộng hơn, SL/đơn giá căn phải và có phân nhóm nghìn |
| Gõ NCC chỉ ra các PO, phải mở từng đơn | Tra NCC ra ngay danh sách mặt hàng từng mua, giá/ngày gần nhất và lịch sử ngay tại chỗ |
| Import PCVT rồi phải đi qua nhiều khối để tra | Tên hàng, SL, giá/NCC cũ và giá/NCC hiện tại nằm trên cùng bảng làm việc |
| Sổ CVT chứa nhiều dữ liệu đã ẩn | Mặc định xử lý phần đang hiển thị; người dùng chọn đúng phiếu và dòng cần mua |
| Mỗi dòng có SL bằng chữ, nguồn ở đầu, hàng quá cao | Bỏ ô nhập SL bằng chữ trong luồng mới; nguồn ở cuối/chi tiết; hàng gọn, quy cách mở rộng khi cần |
| Thêm vật tư/NCC từng mục | Nhập danh sách nhiều dòng hoặc dán Excel → kiểm tra lỗi/trùng → lưu một lô |
| Sidebar chiếm vùng làm việc | Có nút thu gọn/mở rộng, nhớ tùy chọn bố cục; giữ nguyên menu, icon và quyền |

Mục tiêu là **ít bước hơn để quyết định mua**, không chỉ giảm font hoặc ẩn các nút. Màn tra cứu, danh mục và đơn đã lưu vẫn có mục đích riêng; không dồn toàn bộ module vào một page.

## 2. Căn cứ đã kiểm tra

Nguồn tham chiếu:

- [PO-261012-02.pdf](../../PO-261012-02.pdf), [PO-261012-06.pdf](../../PO-261012-06.pdf).
- [Copy of CVT 2026 - SCTX.xlsx](<../../CaseTest&OldData/Copy of CVT 2026 - SCTX.xlsx>).
- [Index.html](../../CaseTest&OldData/Index.html), [Mã.js](../../CaseTest&OldData/Mã.js) và các ảnh người dùng gửi trong phiên ngày 12/10/2026.

Đã đọc và xem cả hai PDF: mỗi mẫu một trang A4, **6 cột**, không có thành tiền/tổng. SL/đơn giá căn phải, phân nghìn bằng dấu chấm; ví dụ SL `50,00`, `3.000,00`, giá `72.000`, `2.500.000`. Ngày mẫu có khoảng trắng `12 / 10 / 2026`; yêu cầu mới chuẩn hóa `12/10/2026`. Hai mẫu phân bổ chiều rộng Tên/Quy cách khác nhau, nên cần một bố cục cân đối chung thay vì sao chép hai tỷ lệ khác nhau. Các mẫu chưa chứng minh quy tắc xử lý giá lẻ.

Workbook hiện có một sheet `2026`, **không có cột/sheet ẩn**. Có 2.013 dòng vật tư, trong đó **83 dòng đang hiện**, nguồn dòng 1975–2057, đều thuộc **phiếu 40**, ngày cấp cột N `30/09/2026`. AutoFilter `A4:O2057` lọc cột O theo số phiếu 40. XML có 1.970 dòng ẩn, gồm 1.930 dòng vật tư và 40 dòng khác. “Bỏ dữ liệu hide” của mẫu này được hiểu là bỏ các dòng ẩn khỏi lần làm việc mặc định, giữ bản gốc để đối chiếu.

Phiếu cấp vật tư là nguồn yêu cầu xem xét; không mặc định mọi dòng đã cấp đều phải mua mới. Trong mẫu này ngày cấp của phiếu 40 là ngày hợp lệ mới nhất đã đọc được; về sau chọn phiếu theo phạm vi/ngày và xác nhận của người dùng, không lấy số phiếu lớn nhất hoặc tên file làm quy tắc duy nhất.

| Code hiện tại đã xác nhận | Giới hạn liên quan |
|---|---|
| `PurchaseOrderPdfRenderer` dùng PDFBox | 7 cột, có thành tiền/tổng, ngày ISO và định dạng số chưa theo yêu cầu mới |
| `ExcelExportService.exportPurchaseOrder` | Excel đơn mua có thành tiền/tổng và ngày chưa theo mẫu mới |
| `PricingService.search`, `/api/prices` | Tìm mã/tên vật tư; chưa có NCC → mặt hàng |
| `/api/purchase-orders` | Tìm PO/NCC trả danh sách đơn, không phải danh sách hàng đã mua |
| `DocumentHistoryLookup` | Đã có các ứng viên NCC/giá và quy tắc khớp theo từng dòng |
| `DocumentImportService.lookup`, `/api/imports/documents/{id}/lookup` | Chỉ tra PCVT đã xác nhận; preview đang sửa chưa tra được theo contract này |
| `SourceDocumentParser.readSheet` | Đọc cả dòng/cột ẩn, chưa lưu thông tin hidden cho lựa chọn hiển thị |
| `DocumentWorkbench`, `DocumentPreview`, `HistoryReview` | Các bước/bảng lớn tách rời; nguồn đứng đầu và còn input SL bằng chữ |
| `PurchaseOrderFormPage.copyLatest` | Lấy giá cũ rồi ghi vào giá/NCC hiện tại, chưa có hai nhóm cột rõ ràng |
| `useSidebar`, `catalogApi`, `CatalogController` | Desktop collapse riêng Mua hàng và mobile drawer đã có; API thêm danh mục theo lô chưa có |

Rà soát này chỉ đọc source/file mẫu. Chưa test runtime, chưa sửa code chức năng hoặc import vào database trong lượt lập kế hoạch.

## 3. Mẫu PDF và Excel đơn hàng

### Bố cục xuất cho NCC

Giữ tiêu đề công ty, đường phân cách xanh theo mẫu, tên **ĐƠN ĐẶT HÀNG**, số PO, ngày, Bên bán, Địa chỉ, Ghi chú và ba vị trí ký **Người lập / Trưởng P. KHVT / Tổng giám đốc**. Dữ liệu lấy từ snapshot đúng revision; tên người lập vẫn hiện nếu có.

```text
STT | TÊN HÀNG HÓA / VẬT TƯ | QUY CÁCH / MÔ TẢ | ĐVT | SỐ LƯỢNG | ĐƠN GIÁ CHƯA VAT (loại tiền)
```

- Bỏ cột **Thành tiền** và các khối **Tạm tính / Tiền VAT / Tổng cộng** khỏi PDF và Excel đơn hàng gửi NCC. Loại tiền/cơ sở đơn giá vẫn phải rõ; đây là thông tin giá, không phải tổng tiền.
- Tỷ lệ cột khởi đầu **đề xuất để duyệt bằng ảnh**: STT 5%, Tên 30%, Quy cách 29%, ĐVT 10%, SL 12%, Đơn giá 14%. ĐVT rộng hơn mẫu tham chiếu; tên/quy cách tự xuống dòng, không ép nhỏ font để nhét dữ liệu.
- STT/ĐVT căn giữa; tên/quy cách căn trái; SL/đơn giá căn phải. Header rõ, border nhẹ, chiều cao theo nội dung.
- PDF nhiều trang lặp header bảng, không mất dòng hoặc cắt giá. Vùng ký đặt sau dòng cuối và không đè lên bảng. Không bắt đơn dài phải nằm trong một trang.
- Excel đồng bộ 6 cột và bố cục in; ô số/ngày có kiểu số/ngày thật, mã/PO có kiểu text. Không chỉ ghi số đã format thành chuỗi.

### Quy ước số và ngày

Ngày hiển thị toàn bộ phần Mua hàng là **`dd/MM/yyyy`**, ví dụ `05/09/2026`, `12/10/2026`. Chuỗi người dùng ghi `dd/mm/yyyyy` được hiểu là năm 4 chữ số. API/database vẫn giữ định dạng chuẩn; không thay đổi ngày nghiệp vụ hoặc chuyển ngày thuần qua UTC làm lệch ngày.

Hiển thị Việt Nam: dấu chấm phân nghìn, dấu phẩy thập phân. PDF mẫu dùng SL tối thiểu hai số lẻ (`3.000,00`); SL có thêm chữ số có ý nghĩa phải được giữ. Đơn giá nguyên hiển thị `72.000`; giá lẻ được hiển thị đủ theo cơ sở đã xác nhận, không cắt thành số nguyên chỉ vì hai mẫu là số nguyên. Bảng thao tác có thể bỏ phần `,00` để gọn khi SL nguyên. Chốt formatter chung và kiểm tra dữ liệu decimal lớn/nhỏ trước khi áp dụng.

Trong trường nhập: có hướng dẫn quy ước số; giá trị API vẫn là decimal string chuẩn. Không suy đoán `1.234` đồng thời là 1,234 và 1.234 nghìn. Số nhập/dán theo locale đã chọn được preview thành giá trị chuẩn trước lưu. Không dùng `Number`/`parseFloat` để thay tính toán chính xác của backend.

### Tổng nội bộ và chứng từ cũ

`subtotal`, `taxAmount`, `grandTotal` và tính tiền backend được giữ cho tra cứu/chi tiết đơn mua. Excel **PO nội bộ** giữ tổng đang có trong `exportPurchaseOrder`; Excel **đơn gửi NCC** theo mẫu 6 cột. Cần mode/endpoint và nhãn tải phân biệt hai mục đích. `exportPriceHistory` hiện chưa có thành tiền/tổng; không mô tả nó như một báo cáo tổng PO hoặc tự cộng các tiền tệ/cơ sở khác nhau.

Mẫu mới áp dụng cho PO/revision phát hành sau thay đổi. PDF revision đã lưu giữ nguyên bytes/checksum; tải GET chỉ đọc. Nếu chuyển đơn cũ sang mẫu mới, dùng thao tác sửa/tạo revision có lý do và phát hành đúng quyền. Không ghi đè chứng từ cũ hoặc ghi thêm lịch sử chỉ vì tải PDF.

Nếu metadata PDF cũ còn nhưng file bị mất, báo `PDF_FILE_MISSING` và phục hồi backup hoặc tạo revision mới đúng luồng. Không dùng renderer mới sinh lại cùng revision rồi giữ checksum metadata cũ. Bổ sung phiên bản mẫu trong artifact khi cần để quản lý thay đổi này.

## 4. Bàn làm việc Mua hàng gọn như Apps Script

Giữ logo, palette xanh rêu/xanh ngọc, header, footer bản quyền và các mục sidebar hiện có. Đổi bố cục **bên trong trang Nhập dữ liệu** thành ba vùng chính:

1. **Nhận danh sách:** tải file/dán Excel, hoặc gõ thêm vật tư. Chọn sheet/phiếu và loại PCVT/báo giá; chứng từ đã lưu mở qua nút/khung riêng, không luôn chiếm một bảng lớn.
2. **Bảng chuẩn bị mua:** chọn dòng, sửa thông tin cần thiết, thấy giá/NCC cũ, điền giá/NCC hiện tại trên cùng dòng.
3. **Thông tin đơn và hành động:** ngày, loại tiền, VAT, người lập, ghi chú; lưu PCVT, gom hỏi giá, kiểm tra nhóm NCC/lập nháp, phát hành/tải theo quyền và trạng thái.

```text
KHVT / Nhập dữ liệu                              [Thu gọn menu]

[PCVT cần mua] [Báo giá NCC]       [Tải file] [Dán Excel] [Mở bản đã lưu]
Sheet: 2026 | Phiếu: 40 | Chỉ dòng đang hiển thị    [Thêm vật tư]

✓ | Tên vật tư | Quy cách | ĐVT | SL | Giá cũ | NCC cũ | Giá hiện tại | NCC hiện tại | ... | Nguồn
✓ | Thép...    | 6m/cây   | cây |  1 | ...    | ...    | [           ] | [           ] | ... | Chi tiết

[Dùng giá/NCC cũ cho dòng chọn] [Gom hỏi giá] [Lưu PCVT] [Kiểm tra và lập đơn]
Ngày | Loại tiền | VAT | Người lập | Ghi chú
```

Giữ trang **Tra cứu giá** riêng với hai chế độ **Theo vật tư / Theo NCC**. Trang **Đơn mua hàng** tiếp tục tìm/xem đơn và tổng. Không thêm một dãy tab toàn module lặp lại sidebar. Tab con/phiếu/chứng từ phản ánh URL phù hợp; các đường `/imports?document=...`, `/price-search`, `/purchase-orders/...`, `/catalog/...` vẫn mở được và Back/Forward/reload giữ đúng ngữ cảnh.

### Lưu bản làm việc

URL giúp mở đúng chứng từ nhưng không tự khôi phục giá/NCC đang nhập. Cần **bản làm việc lưu server**, tách khỏi file gốc và snapshot PCVT đã xác nhận: gồm dòng chọn, giá/NCC hiện tại, thông tin đơn và phiên bản cập nhật. Lưu theo quyền, có kiểm soát phiên bản khi hai tab cùng sửa; xung đột giữ dữ liệu đang nhập và cho xem lại, không ghi đè mù.

Có nút **Lưu bản làm việc** và nhãn đã lưu/còn thay đổi. Reload/mở lại khôi phục bản đã lưu; khi chưa lưu, đổi phiếu/tab/rời trang cần nhắc còn thay đổi. Không đưa giá/chứng từ vào localStorage theo yêu cầu nhớ navbar. Bản làm việc chưa xác nhận/phát hành không tạo lịch sử mua.

### Bảng chuẩn bị mua

- Cột mặc định: chọn, tên/mã vật tư, quy cách, **ĐVT**, **SL**, **giá cũ + ngày**, **NCC cũ**, **giá hiện tại**, **NCC hiện tại**, thao tác, **nguồn cuối cùng**. Cho cuộn trong bảng khi rộng; không làm tràn toàn trang.
- Giảm khoảng đệm/hàng thừa; dùng input một dòng cho giá trị ngắn. Quy cách dài mở rộng khi cần. Mã nội bộ và cảnh báo chi tiết nằm ở phần sửa/chi tiết, không luôn tạo thêm input dưới từng tên.
- Bỏ điều khiển **SL bằng chữ** khỏi nhập mới. Giá trị `quantityText` cũ/raw được giữ để đối chiếu; dòng chỉ có chữ và chưa có SL số hiển thị cần xử lý, không biến thành 0/1 hoặc tự bỏ khỏi PCVT. PO cũ vẫn đọc đúng snapshot của nó.
- Giữ checkbox, chọn theo phiếu/trang/toàn bộ tập đã lọc. Chuyển trang không mất lựa chọn; dùng `lineKey`, không dùng vị trí hiển thị làm ID.
- Một trạng thái gửi/lỗi rõ; lưu lỗi giữ nội dung đang nhập. Dòng lỗi mạng khi tra không được ghi “chưa mua lần nào”.

### Dữ liệu ẩn trong Excel

- PCVT Excel mặc định **Chỉ dòng đang hiển thị**. Với mẫu đã kiểm tra: preview đúng **83 dòng phiếu 40**, không đưa 1.930 dòng vật tư ẩn vào danh sách mua của lần này.
- Thống kê rõ tổng vật tư phát hiện / vật tư ẩn bỏ khỏi phạm vi / vật tư hiển thị / dòng chọn. Có tùy chọn đọc cả dòng ẩn hoặc chọn phiếu khác khi người dùng chủ động cần.
- File gốc/checksum/raw nguồn giữ nguyên. Không xóa dòng/cột của workbook hay dữ liệu lịch sử trong database.
- Với tệp **đã upload bằng parser cũ**, preview hiện có thể trả parsed cache theo checksum trước khi đọc lại file. Bổ sung phiên bản parser/chính sách hiển thị và reparse/projection từ nguồn private khi thiếu hidden metadata. Giữ checksum/ID nguồn, raw lịch sử, child/scope và liên kết đã lưu; không tạo lô trùng hoặc ghi đè snapshot đã xác nhận. Chế độ phần đang hiển thị áp dụng phạm vi preview mới; mở PCVT đã xác nhận giữ đúng tập dòng đã chốt.
- Ẩn do AutoFilter hay ẩn thủ công đều được nhận diện. Nếu metadata lọc không đủ giải thích phạm vi, hiển thị cho người dùng chọn lại; không tự tái tính công thức/filter Excel để đoán.
- Cột nghiệp vụ phụ không xuất ra bảng chính. Metadata nhóm như số phiếu/ngày/địa chỉ nguồn vẫn được giữ để xác định đúng phạm vi, kể cả workbook sau này ẩn cột đó. Không bỏ khóa nhóm rồi áp số phiếu trên header cho cả năm.
- Chính sách này dành cho PCVT/sổ công việc, không tự áp vào di trú `QUANLYMUAHANGKHVT.xlsx`. Không đổi checksum/idempotency legacy hoặc làm mất lịch sử ẩn.

### Tra giá/NCC cũ tự động

Thêm API tra **tham khảo từ preview đang sửa**, nhận snapshot dòng hiện tại và tái dùng `DocumentHistoryLookup`. Endpoint này mới là đề xuất; không tự gọi confirm để vượt điều kiện `REQUEST_NOT_CONFIRMED` của API lookup hiện tại.

API cần đồng thời `IMPORT_OPERATIONAL` + `PRICE_READ`, quyền đọc chứng từ và CSRF cho POST. Chỉ nhận request PCVT hợp lệ, giới hạn lô, kiểm tra `lineKey` trùng/phạm vi và decimal. Response mang `lineKey` + fingerprint/version; không log payload giá hoặc chứng từ. Đây là tra cứu nghiệp vụ chỉ đọc: không tạo child xác nhận, PO, phân bổ hoặc lịch sử mua.

- Sau chọn sheet/phiếu, tra theo lô các dòng đang nhìn thấy/được chọn; những dòng khác tra khi mở hoặc chọn tất cả. Cho tra lại. Không gọi một request/SQL cho từng dòng hoặc tải mọi lịch sử vào browser.
- Kết quả từng dòng có giá, NCC snapshot, ngày, ĐVT/quy cách, tiền tệ/VAT, nguồn và mức khớp. Ưu tiên giá mới nhất của **cùng vật tư và cơ sở phù hợp**, không lấy giá thấp nhất mọi thời điểm.
- Khớp mã đã xác nhận → ưu tiên đúng mã; tên gần giống → **Cần đối chiếu**. Không tự đổi mét/cây, thùng/cái, USD/VND hoặc coi hai quy cách gần nhau là một.
- Giá cũ thiếu cơ sở VAT/tiền tệ xác nhận vẫn xem được, ghi “tham khảo/chưa đủ cơ sở so sánh”. Các nhóm ĐVT/quy cách/tiền tệ khác vẫn mở xem được, không gộp làm một giá.
- Cùng ngày có nhiều NCC: hiện các ứng viên; không tuyên bố một NCC là duy nhất gần nhất. Bản ghi thiếu ngày không được dùng làm “gần nhất”.
- Đổi tên/mã/ĐVT/quy cách/phiếu làm kết quả cũ hết hiệu lực. Fingerprint theo chứng từ, `lineKey` và các thuộc tính tra; hủy/bỏ qua response đến muộn của phiếu hoặc dòng đã đổi.
- **Giá/NCC cũ chỉ để đọc; giá/NCC hiện tại do người lập chọn.** Nút dùng giá cũ áp dụng cho dòng chọn và nêu rõ nguồn; không ghi đè giá hiện tại đã nhập khi refetch. NCC cũ chưa nối danh mục/đã ngừng vẫn xem được nhưng cần chọn NCC hợp lệ trước lập PO.
- Lưu PCVT/xác nhận vẫn là hành động riêng. Import, tra và nhập giá hiện tại chưa tự ghi lịch sử mua hoặc phát hành PO.

### Từ PCVT sang đơn mua

Đây là phần nối còn thiếu của code, cần triển khai thật để bảng gọn có tác dụng: các dòng được chọn cùng giá/NCC hiện tại → kiểm tra → nhóm theo **NCC + tiền tệ + VAT/cơ sở tương thích** → lưu PO nháp → phát hành theo quyền. Không ép các dòng VAT 8%/10% về một mức.

Giữ liên kết `sourceDocumentId`/`lineKey` và phân bổ SL vào dòng PO. Có trạng thái đã tạo nháp/đã phát hành; gửi lại sau timeout phải tra kết quả hoặc dùng idempotency key, không sinh PO đôi. Sổ nguồn chọn nhiều phiếu vẫn giữ PREVIEW; từng phạm vi đã xác nhận có child/scope riêng. Không tự xác nhận cả sổ năm. Giới hạn dòng một PO không được làm cắt bớt dòng PCVT; tách nhóm/đơn hợp lệ và báo đầy đủ kết quả.

Phân bổ là số lượng đang đặt từ PCVT, không phải số đã nhận kho: tổng lượng ở các nháp còn hiệu lực và PO đã phát hành không vượt SL cần mua đã xác nhận. Khóa/kiểm tra cùng transaction khi nhiều request; sửa/hủy PO cập nhật hoặc giải phóng phần phân bổ tương ứng, giữ audit/revision. Báo kết quả từng nhóm; retry chỉ nhóm chưa lưu, không tạo lại nhóm thành công. Nếu nhu cầu đổi, sửa phạm vi yêu cầu theo luồng có lý do trước khi đặt thêm.

## 5. Tra NCC ra mặt hàng đã mua

Trang Tra cứu giá có **Theo vật tư** và **Theo NCC**. Gõ “Văn Anh” hoặc mã NCC → danh sách NCC khớp → chọn NCC nếu có nhiều ứng viên → ra bảng mặt hàng từng mua:

```text
Tên / mã vật tư | Quy cách | ĐVT | Ngày gần nhất | Giá gần nhất | Loại tiền / cơ sở | Số lần | Xem lịch sử
```

Mở lịch sử ngay dưới mặt hàng hoặc trong khung chi tiết: ngày, SL, đơn giá, thành tiền nội bộ, tiền tệ/cơ sở và liên kết PO nguồn nếu có. Người dùng không phải mở từng PO để biết NCC bán những hàng gì. Vật tư chưa có mã vẫn giữ tên/ĐVT snapshot và nhóm theo khóa an toàn, không tự gộp chỉ vì gần tên.

Tái dùng lịch sử Apps Script và PO **đã phát hành** hiện hành; loại PO hủy/revision thay thế và không đếm đôi dòng đã nối lineage. Nhãn “PO đã phát hành” không đồng nghĩa đã nhận hàng/thanh toán. Tên NCC đã sửa vẫn giữ được snapshot lúc giao dịch; tìm theo master ID/mã và tên lịch sử phù hợp. Không gộp hai NCC chỉ vì tên giống hoặc MST thiếu.

Phân trang nhóm mặt hàng và lịch sử của từng nhóm ở server. Tra theo NCC không phụ thuộc một trang PO đã tải. Tối ưu truy vấn theo tập vật tư/NCC cần tra; bổ sung index sau khi xem truy vấn MySQL thật, vì `DocumentHistoryLookup.load()` hiện còn đọc toàn bộ kho lịch sử.

## 6. Thu gọn menu và thêm danh mục theo danh sách

### Thu gọn sidebar

Giữ menu, thứ tự, icon, active indicator, palette và header. Desktop có nút thu gọn thành thanh icon rồi mở lại; tooltip/nhãn truy cập vẫn đọc được tên đầy đủ. Scope chỉ Mua hàng vì shell đang dùng chung cả Quản trị.

Lưu boolean UI có phiên bản, đề xuất `qmh:ui:purchasing-sidebar:v1`, vào localStorage. Chỉ là tùy chọn bố cục; không lưu session/token, quyền, chứng từ hoặc giá. localStorage bị chặn thì vẫn hoạt động trong phiên. Mobile drawer mở/đóng tách với desktop collapse; đổi breakpoint không ghi đè lựa chọn desktop. Keyboard, focus và Escape vẫn dùng được.

### Thêm vật tư / NCC theo lô

Mỗi danh mục có **Thêm một mục** và **Thêm danh sách**. Danh sách nhận bảng nhập nhiều dòng, dán từ Excel hoặc tải file theo mẫu cột. Có thêm/xóa dòng và preview trước lưu; không bắt người dùng mở dialog từng mục.

| Danh mục | Cột cơ bản |
|---|---|
| Vật tư | Mã vật tư, Tên vật tư bắt buộc, Phân loại, ĐVT mặc định |
| NCC | Mã NCC, Tên NCC bắt buộc, Địa chỉ, MST/thông tin liên hệ nếu có |

Backend mới cần preview/commit lô, kết quả theo dòng và idempotency. UI không vòng lặp gọi create từng mục để giả là một lô.

- Phát hiện trùng mã trong lô/với database; tên/MST giống là ứng viên cần đối chiếu, không tự merge hoặc ghi đè mục có sẵn. Giữ mã có số 0 đầu và dấu chấm.
- Cho chọn **Tạo mới / Bỏ qua / Sửa dòng đầu vào** sau xem lỗi. Mọi dòng đã chọn phải hợp lệ trước commit; lưu tập đó trong cùng transaction. Muốn lưu các dòng tốt phải chủ động loại dòng lỗi và xác nhận lại phạm vi.
- Preview và commit đều cần `CATALOG_MANAGE`, CSRF, audit phù hợp; commit là kiểm tra cuối. Giới hạn lô theo cấu hình và báo rõ vượt hạn, không cắt dòng âm thầm.
- Khi database thay đổi sau preview, kiểm tra lại xung đột; rollback lô được chọn khi lỗi. Khóa idempotency gắn người thao tác + loại thao tác + payload: retry cùng nội dung trả kết quả cũ, dùng lại khóa với nội dung khác trả xung đột. Không tự cập nhật dữ liệu đang có trong lượt thêm danh sách này.

## 7. Thứ tự triển khai và nơi sửa

| Lượt | Kết quả bàn giao | Vùng code chính |
|---|---|---|
| 1 | Mẫu PDF/XLSX 6 cột, số/ngày; tổng nội bộ giữ; ảnh 2 mẫu cân đối | `PurchaseOrderPdfRenderer`, `ExcelExportService`, formatter chung, test file/PO |
| 2 | PCVT phần đang hiện, bảng gọn, tra giá/NCC ngay từ preview, không ghi đè giá hiện tại | `SourceDocumentParser`, DTO/snapshot, `DocumentHistoryLookup`, `DocumentImportService`, `DocumentWorkbench`/`DocumentPreview`/`HistoryReview` |
| 3 | Gõ NCC ra mặt hàng/lịch sử trực tiếp, tối ưu tra theo lô | `PricingService`/repository/controller, `PriceSearchPage`/`pricingApi` |
| 4 | Nối bảng chuẩn bị mua sang nhóm PO nháp với nguồn/idempotency, trạng thái rõ | Procurement/importing service, DTO/phân bổ, `PurchaseOrderFormPage`, hooks theo feature |
| 5 | Thêm danh mục theo danh sách; thu gọn menu; rà soát toàn bộ UI Mua hàng | `CatalogController`/`CatalogService`, API lô, pages/forms danh mục, `Sidebar`/`useSidebar`/shell |

Thu gọn menu có thể thực hiện sớm sau lượt 1; đây là thứ tự ưu tiên nghiệp vụ, không phải thời gian ước tính. Sau mỗi lượt cập nhật code/API thật và bằng chứng kiểm tra. Không viết lại `App.tsx` thành page khổng lồ hoặc thay engine PDF chỉ để khớp mẫu.

Nếu cần schema mới cho hidden metadata, phân bổ hoặc lô danh mục, bổ sung migration Flyway mới sau rà soát; không sửa V6/V7 đã áp dụng. API đề xuất và UI mới chỉ được báo có khi đã triển khai và test.

## 8. Nghiệm thu từng yêu cầu

| Kịch bản | Điều kiện đạt |
|---|---|
| PDF hai PO mẫu, đơn dài, tên/quy cách/ĐVT dài, Unicode | Đủ 6 cột; số căn phải/phân nghìn; ngày đủ 2 chữ số; ĐVT rộng; không có thành tiền/tổng; không mất dòng, đè chữ hoặc vùng ký |
| Excel đơn gửi NCC và Excel tra nội bộ | Bản NCC cùng 6 cột, số/ngày thật và in được; bản tra nội bộ/tổng API vẫn chính xác |
| PDF revision cũ | Bytes/checksum cũ giữ nguyên; file mất báo rõ và không tái sinh cùng revision bằng mẫu mới; GET không phát hành/ghi lịch sử; phát hành mới cần quyền ghi và CSRF |
| CVT mẫu đang lọc phiếu 40 | Preview 83 dòng hiển thị, số bỏ/phạm vi rõ; đủ tên/quy cách/ĐVT/SL/số phiếu/nguồn; checksum file gốc không đổi; chọn nhiều trang không mất dòng |
| File Excel khác và tệp đã upload | Test ẩn thủ công, filter, hidden cột nhóm, nhiều phiếu, thiếu số phiếu; bản cache cũ reparse ra phạm vi đúng; snapshot đã xác nhận giữ nguyên; không gán phiếu header cho mọi dòng hoặc đổi luồng legacy |
| Tra preview tự động | Kết quả giá/NCC ở cùng dòng trước xác nhận; khớp/không khớp/cần đối chiếu/lỗi mạng đúng; đổi phiếu nhanh không dính response cũ; dữ liệu hiện tại không bị ghi đè |
| Nhiều NCC/cơ sở khác/thiếu ngày | Giữ nhóm khác ĐVT/currency/quy cách/VAT, không tạo so sánh chắc chắn hoặc NCC hợp lệ giả; đồng ngày cho chọn ứng viên |
| Bản làm việc | Lưu/mở lại/reload giữ dòng chọn và giá/NCC hiện tại; chưa lưu có cảnh báo; hai tab xung đột không ghi đè; không sửa snapshot xác nhận |
| Tra NCC | Tìm ra mặt hàng trực tiếp dù nằm ở PO/trang khác; mở lịch sử tại chỗ; snapshot nguồn giữ; không đếm đôi PO/hủy/revision |
| Từ PCVT sang PO | Nhóm nhiều NCC/VAT/currency đúng, SL nguồn còn đủ; đồng thời không đặt quá nhu cầu; sửa/hủy cập nhật phân bổ; retry từng nhóm không sinh trùng; lịch sử chỉ đổi khi phát hành đúng luồng |
| Danh mục theo lô | Lô hợp lệ lưu đủ; một dòng lỗi/trùng không để nửa lô; sửa lại/retry không nhập đôi; không merge ngầm; thiếu quyền API bị chặn |
| Sidebar và UI | Thu gọn/reload/chuyển trang nhớ đúng; Quản trị/menu không đổi; mobile/keyboard dùng được, không tràn trang |

Kiểm tra backend/formatter và TypeScript/Vite theo phần thay đổi; Playwright desktop/mobile bằng fixtures tách khỏi test thật. Nghiệm thu thật dùng schema MySQL/Redis namespace riêng, MCP/UI và các mẫu đã xác minh; không commit thử vào database người dùng. Giữ source/checksum, báo cáo dòng/trường và ảnh PDF/Excel trước khi kết luận. Không báo đạt chỉ vì build qua hoặc có controller.

Kế hoạch này thay yêu cầu **thành tiền/tổng trong PDF/XLSX đơn gửi NCC** ở các plan cũ 03/06/09/10/12. Tính tiền và tra cứu nội bộ vẫn theo nghiệp vụ cũ. Grant/deny tài khoản, giữ đăng nhập dài hạn, OCR và triển khai Cloudflare là các phạm vi riêng.

## C?p nh?t giao di?n Mua h�ng � 2026-10-13

B?ng preview PCVT c� th? tra l?ch s? NCC/gi� cho c�c d�ng d� ch?n v� hi?n gi� cu, nh� cung c?p, don v? c�ng ng�y mua ngay trong c�ng b?ng. Ngu?i d�ng nh?p gi� hi?n t?i v� ch?n nh� cung c?p l?ch s? cho t?ng d�ng; n�t L?p PO chuy?n c�c d�ng du?c ch?n c�ng s? lu?ng, gi� hi?n t?i, m�/t�n NCC v� th�ng tin ngu?n v�o form t?o PO. T?o PO v?n l� b?n nh�p ? trang k? ti?p; kh�ng x�c nh?n PCVT, ghi l?ch s? mua hay ph�t h�nh don t? d?ng. N�t tra l?ch s? ch? hi?n khi c� quy?n PRICE_READ; quy?n t?o PO v?n do route/API hi?n c� ki?m tra.

Ki?m tra frontend trong lu?t n�y: npm.cmd run build th�nh c�ng; npm.cmd test -- --run 10/10; Playwright tests/e2e/documents.spec.ts 20/20 tru?c b? sung lu?ng t?o PO; ki?m th? m?i ri�ng desktop/mobile 2/2. C�c browser tests d�ng API fixtures; chua ki?m tra d? li?u MySQL/Redis, ph�n b? nhi?u VAT/ti?n t?, b?n nh�p b?n qua reload ho?c import workbook legacy th?t. Plan 17 c�n c�c ph?n backend/bulk danh m?c v� nghi?m thu th?t du?c ghi ? tr�n v?n chua ho�n t?t.
# Tiến độ cập nhật 2026-10-13

Đã đưa intake PCVT/báo giá vào cùng trang Mua hàng với bảng chuẩn bị PO, giữ sidebar trái. Nhập file và dán Excel đi qua preview; lịch sử giá/NCC nạp tự động theo trang preview, có thể sửa giá/NCC hiện tại trước khi chuyển dòng được chọn vào form. Mua thủ công và tìm vật tư vẫn có sẵn. Nhóm nhiều NCC tiếp tục tạo bản nháp tách riêng; phát hành không tự động.

Build frontend, 10 unit tests và 50 browser cases desktop/mobile với API fixtures qua. Nút xuất hiện tạo PO từng NCC, phát hành từng PDF và gửi tải sau một xác nhận; xử lý trạng thái lỗi theo từng NCC. Đây là tiến độ UI, chưa nghiệm thu toàn bộ plan: thiếu xác minh browser/database thật cho luồng tích hợp, đảm bảo bản làm việc còn nguyên qua reload, idempotency/phân bổ PCVT → PO và các mục backend/bulk catalog liệt kê phía dưới. Vì chưa có transaction/idempotency theo lô ở backend, lỗi mạng khi tạo PO cần đối chiếu danh sách trước khi chạy lại.
