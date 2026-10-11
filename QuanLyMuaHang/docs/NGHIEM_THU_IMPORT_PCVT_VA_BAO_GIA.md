# Nghiệm thu kỹ thuật import PCVT và báo giá

Ngày **2026-10-11**, lượt kiểm thử thật hoàn tất lúc **17:24 +07:00**. **Đạt trong phạm vi kiểm tra dưới đây**: nhập/lưu PCVT và báo giá → mở lại → tra NCC/giá cũ → lưu/xuất nhóm hỏi giá → nối dòng báo giá và so sánh. Đây không phải nghiệm thu toàn bộ kế hoạch 16, chứng nhận nghiệp vụ chứng từ hoặc cam kết tự đọc đúng mọi Excel/PDF.

## Bằng chứng kiểm thử

| Lớp kiểm tra | Kết quả |
|---|---|
| Toàn bộ suite backend | `mvn -Pwindows-web -DforkCount=0 -Dspring.test.context.cache.maxSize=1 package`: BUILD SUCCESS lúc 17:18; 124 test liệt kê, 121 thực thi qua, 3 opt-in bỏ qua |
| Backend import bản cuối | Sau sửa việc giữ giá cùng NCC nhưng khác cơ sở, chạy riêng `SourceDocumentParserTest,SourceDocumentWorkflowIntegrationTest` và package: **33 qua, 0 lỗi, 0 bỏ qua**, gồm 19 parser và 14 workflow; BUILD SUCCESS lúc 17:20 |
| Frontend | TypeScript và Vite build qua; 10 unit tests API client qua. Vite còn cảnh báo bundle lớn hơn 500 kB |
| Browser API giả lập | **46 trường hợp riêng biệt** desktop/mobile qua: 28 import/mua hàng và 18 chứng từ. Lượt đầu có 2 lỗi tràn trang; đã sửa grid và chạy lại toàn bộ 18 trường hợp chứng từ, gồm 2 trường hợp mới cho ID PCVT cũ. Không cộng lượt chạy lại vào tổng |
| HTTP/MySQL/Redis thật | Driver `tools/local-test/document-run.mjs` hoàn tất **14 nhóm kiểm tra**, `completed: true`, trên schema UUID `qmh_docs_test_34e94b899b19`, namespace Redis riêng, backend 8082 |
| MCP với API thật | Mở preview sổ năm 2.013 dòng/40 phiếu, mở lại PCVT đã lưu qua reload, tra NCC/giá, xem so sánh có nguồn giá đối chiếu và kiểm tra viewport 390 px; đã qua |

Ba test opt-in của suite đầy đủ chưa chạy trong lượt đó: Redis thu hồi phiên, workbook legacy H2 và PDF PO Unicode. Hai phần đầu đã có kiểm chứng riêng trước đó; driver mới kiểm tra Redis runtime nhưng không thay test thu hồi phiên. Không coi build/package thay cho các test đã bỏ qua.

Báo cáo local: `source/target/runtime/document-real-integration-report.json`, bản UUID cùng thư mục, `document-backend-full-package.log`, `document-backend-final-package.log`, `document-browser.log`, `document-browser-final.log`. Ảnh MCP thực tế: `qmh_docs_test_34e94b899b19-real-import-mobile.png`. Đây là artifact sinh ra, không đưa log/cookie/tệp gốc vào Git.

## Sáu file thật

| Nguồn | Kết quả đối chiếu |
|---|---|
| `Copy of CVT 2026 - SCTX.xlsx` | **2.013/2.013 dòng**, 40 số phiếu; đối chiếu 10.065 trường tên/quy cách/ĐVT/SL/số phiếu, 0 sai lệch. 39 dòng STT phân cách không bị nhập như vật tư; dòng 1416 chưa có phiếu vẫn được giữ để xử lý |
| Báo giá Thiên Phú Excel, sheet `BG` | **43/43 dòng**, 258 trường đối chiếu, 0 sai lệch; ngày nội dung 05/05/2026, không lấy ngày tên file; giữ dòng phương án không STT và dòng thiếu giá |
| Cùng workbook, sheet `Sheet1` | **18/18 dòng**, 108 trường, 0 sai lệch; ngày nội dung 08/09/2026. Chưa chọn sheet trả 0 dòng kèm yêu cầu chọn, không tự lấy sheet đầu |
| PDF Vạn Phúc | **8/8 dòng**, 40 giá trị/số lượng/thành tiền và chi tiết model kiểm tra, 0 sai lệch |
| PDF Phượng Hoàng Á Châu | **31/31 dòng**, 150 trường/chi tiết kiểm tra, 0 sai lệch; không suy 34 dòng từ STT cuối; ngày nội dung 02/10/2026 |
| PDF Trung Đông | **11/11 dòng**, 50 trường/chi tiết kiểm tra, 0 sai lệch; giữ chi phí bản/trục in và số lượng chào riêng, không tự sửa thành tiền nguồn |
| PDF scan Nam Phát | Parser báo `PDF_SCAN`, `OCR_NOT_SUPPORTED`, 0 dòng. Lưu rỗng bị từ chối; đã nhập tay theo ảnh 1 dòng lưới inox 304, 60 mét, đơn giá 592.000, thành tiền 35.520.000, VAT 10% và kiểm tra lưu/mở lại. VND là lựa chọn của người kiểm tra; ảnh không ghi ký hiệu tiền tệ |

Hai workbook đối chiếu độc lập từ OpenXML, không dùng parser Java để tạo đáp án. Xuống dòng literal CRLF/CR được chuẩn hóa thành LF ở bước đọc XML theo quy tắc XML; không bỏ khoảng trắng/ký tự tùy ý để làm cho kết quả khớp. PDF đối chiếu số dòng, số lượng/giá/thành tiền, chi tiết tên/model đã lập trước và provenance trang; không tuyên bố đã chứng nhận từng ký tự của toàn bộ PDF.

Tổng phần đọc tự động: **2.124 dòng, 10.671 trường/chi tiết đã kiểm tra, 0 sai lệch**. PDF scan có kiểm tra nhập tay riêng, không tính thành tự động đọc thành công. File gốc của cả sáu nguồn giữ nguyên SHA-256 sau kiểm thử.

## Các hành vi đã kiểm chứng

- Chọn phiếu 1 và phiếu 2 từ cùng sổ năm, lưu hai snapshot riêng, nguồn vẫn mở lại để chọn phiếu khác. Retry cùng phiếu/tệp không tạo trùng; không ghi đè dữ liệu khác lên bản đã xác nhận.
- PCVT chưa có giá/NCC vẫn lưu được. Báo giá phải kiểm tra NCC/ngày/tiền tệ và nối dòng đúng PCVT. Giá trị decimal truyền bằng chuỗi và lưu BigDecimal; dữ liệu nguồn/raw được giữ riêng khỏi giá trị người dùng sửa.
- Tra nhiều NCC snapshot, kể cả không có FK. Giá PO hiện hành vẫn thấy khi không có bản lịch sử; loại PO hủy/revision cũ và lineage trùng. Dữ liệu tra cứu tổng hợp trong driver là fixture nghiệp vụ trên MySQL thật, không chứng minh mọi tên nguồn thực tế đã khớp master.
- Giá đối chiếu phải dương, có ngày, cùng ĐVT/quy cách/tiền tệ/cơ sở VAT. Giá tham khảo chưa xác minh vẫn hiện, nhưng không sinh phần trăm chắc chắn. Giá phù hợp cũ không bị mất chỉ vì cùng NCC có giá legacy mới hơn chưa xác minh.
- Lưu nhóm hỏi giá qua reload; XLSX có một sheet/NCC, chỉ có nhu cầu đã chọn, không có giá cũ/giá đối thủ. Tên gần giống vẫn báo cần đối chiếu, không tự duyệt tương đương.
- API/file trực tiếp kiểm tra quyền; anonymous 401, người VIEWER 403, request thiếu CSRF 403. Không tự gửi chứng từ ra ngoài.
- Import/confirm tất cả chứng từ mới để lịch sử/PO/dòng PO ở **0/0/0** trong schema trống. Các bản ghi mua xuất hiện cuối driver là dữ liệu tổng hợp được chủ động seed để kiểm tra tra giá, không do import tạo.

## Dữ liệu chính và trạng thái chạy

Trước/sau: **10.580 lịch sử, 262 PO, 850 dòng PO, 391 NCC, 2.997 vật tư, 1 tài khoản; Flyway vẫn V6**. Driver xác nhận `mainUnchanged: true`. Không import sáu mẫu vào database chính, không xóa volume, không reset hay flush Redis.

JAR đã đóng gói frontend mới. Server chính 8080/Vite5173 tiếp tục tắt theo yêu cầu trước đó; backend/browser test tự dừng khi hoàn tất, MySQL/Redis vẫn chạy. V7 đã qua trên schema MySQL test; database chính sẽ áp dụng V7 khi khởi động bản mới, chưa chạy migration này trên dữ liệu chính trong lượt nghiệm thu.

## Còn ngoài phạm vi

OCR tự động, alias vật tư đã duyệt/lưu quyết định sau xác nhận, xác minh VAT/tiền tệ lịch sử, lập và phân bổ PO trực tiếp từ PCVT/báo giá, Quản trị grant/deny và production/Cloudflare còn làm tiếp. Parser PDF hiện phục vụ bảng có cấu trúc/đường kẻ của các mẫu đã thử; file bố cục mới phải preview và đối chiếu.

Người sử dụng vẫn kiểm tra chứng từ trước khi xác nhận, nhất là giá thiếu, VAT theo dòng, quy cách gần giống, số lượng tối thiểu và thành tiền nguồn bất thường. Xem [hướng dẫn thao tác](HUONG_DAN_IMPORT_PCVT_VA_BAO_GIA.md).
