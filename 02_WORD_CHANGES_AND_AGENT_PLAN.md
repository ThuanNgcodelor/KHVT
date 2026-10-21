# AI Agent — Góp ý Word, kế hoạch sửa và đối chiếu ảnh

> Nguồn: `chinh sua 2.docx` (6 trang, 6 nhóm yêu cầu; 7 ảnh gốc trích xuất). Ảnh lưu `assets/word/`. Đọc song song `01_PDF_BUGS_AND_TEST_CASES.md` để không sửa mâu thuẫn.

## Cách bắt đầu với repository
1. Đọc toàn bộ hướng dẫn PDF trước; inspect repo để xác nhận stack thực tế. Nếu là React/TypeScript + Java/Spring Boot + MySQL/Redis thì ghi nhận phiên bản và đường dẫn, **không coi là đã xác minh**.
2. Lập inventory các màn: Đơn mua hàng, Lập đơn, Tra cứu giá, Nhập dữ liệu, Vật tư, Nhà cung cấp, xuất PO. Map mỗi case tới UI, API, DB, test.
3. Gắn link ảnh vào issue, chạy test từng case, chụp before/after. Không sửa hàng loạt khi chưa xác nhận root cause.

## W1 — Thứ tự danh sách PO | Phase 3 | Trang 1
![Ảnh danh sách PO](assets/word/image-01.jpg)
**Yêu cầu:** PO mới nằm sau PO trước theo thứ tự PO, không sắp theo tên NCC. **Điểm cần chốt:** tăng dần theo số PO (mới ở cuối) hay mới nhất lên đầu; tài liệu nghiêng về *mới nằm sau*.
**Test:** tạo 3 PO khác NCC với thứ tự tạo 1,2,3; hiển thị 1→2→3 bất kể tên NCC; sort ổn định qua phân trang/filter; dùng sort key có cấu trúc, không so chuỗi mã PO nếu không bảo đảm.

## W2 — Format PO xuất: cột, số, ngày | Phase 3 | Trang 1–2
![Ảnh mẫu đơn đặt hàng](assets/word/image-02.jpg)
**Yêu cầu:** nới cột ĐVT, cân cột, số căn phải và có phân cách hàng nghìn, ngày `dd/MM/yyyy` (tài liệu gõ `dd/mm/yyyyy`, hiểu là cần xác nhận mẫu hiển thị). Bản **xuất đơn hàng** chỉ cần số lượng và đơn giá, **không cần cột thành tiền**; tra cứu đơn mua vẫn có tổng thành tiền.
**Test:** xuất PDF/Excel theo chức năng thực tế; ĐVT dài không bị vỡ; số 1234567 hiển thị 1.234.567 theo locale; ngày đúng; không có cột Thành tiền và tổng cộng trên mẫu xuất nếu đã chốt; bản tra cứu vẫn tính tổng đúng.
**Xung đột cần giải quyết:** PDF B13 yêu cầu thành tiền/tổng trên màn Lập đơn, không bắt buộc xuất PO; tách `screen totals` và `export template`, không xóa tính toán backend.

## W3 — Tra cứu theo NCC thấy vật tư đã mua | Phase 3 | Trang 2
![Ảnh PO và ghi chú tra cứu](assets/word/image-02.jpg)
**Yêu cầu:** chọn NCC → liệt kê trực tiếp các mặt hàng từng mua, không phải mở từng PO.
**Test:** NCC có 3 PO và vật tư trùng nhau → hiển thị từng lần mua hoặc nhóm vật tư theo thiết kế được chốt, có tên/mã vật tư, ngày, giá, số PO; tìm vật tư + NCC kết hợp đúng như PDF A7; phân trang và permission đúng.

## W4 — Nhập PCVT hiển thị NCC/giá gần nhất, bỏ phần không cần | Phase 2 | Trang 3
![Ảnh màn đơn mua hàng](assets/word/image-03.jpg)
**Yêu cầu:** sau nhập PCVT, hiển thị NCC + giá mua gần nhất; bỏ lượng bằng chữ; thông tin nguồn `2026 - dòng 6...` để sau cùng, giảm độ ưu tiên.
**Test:** import 2 vật tư có lịch sử + 1 chưa có → hiển thị gợi ý phù hợp; không lấy giá của vật tư khác; bỏ lượng bằng chữ ở đúng vị trí giao diện/tài liệu được duyệt, không xóa field DB khi chưa kiểm tra phụ thuộc; nguồn vẫn truy vết được.
**Liên quan:** PDF A1, B10, C14.

## W5 — Sidebar thu gọn/hiện khi hover | Phase 3 | Trang 4–5
![Ảnh layout rộng và sidebar](assets/word/image-04.jpg)
![Ảnh sidebar và dữ liệu](assets/word/image-05.jpg)
**Yêu cầu:** ẩn/thu gọn sidebar để tăng chiều ngang bảng; hover để mở trên desktop.
**Test:** desktop hover/focus keyboard, nút pin/collapse; tablet/mobile phải có nút mở vì không có hover; không che dữ liệu khi nhập bảng; nhớ lựa chọn nếu phù hợp; kiểm tra accessibility và không thay đổi chức năng menu.

## W6 — Thêm vật tư/NCC hàng loạt | Phase 3 | Trang 5–6
![Ảnh danh sách nhập vật tư](assets/word/image-06.jpg)
![Ảnh form thêm vật tư](assets/word/image-07.jpg)
**Yêu cầu:** hỗ trợ thêm nhiều vật tư hoặc NCC một lần, không chỉ form từng cái.
**Test:** nhập 10 dòng từ Excel/template → preview, validate bắt buộc, duplicate mã, duplicate NCC, trim Unicode, lỗi từng dòng, chọn bỏ dòng lỗi/rollback toàn bộ theo chính sách đã chốt; không sinh duplicate khi retry. Phân quyền tạo danh mục phải được kiểm tra ở API.

## Phụ thuộc, thứ tự và quyết định
| Giai đoạn | Nội dung | Phụ thuộc |
|---|---|---|
| 0 | Audit repo, backup DB, fixture, test baseline | Không |
| 1 | PDF A2/A3/A4/A5/A6 | Audit |
| 2 | PDF A1/A7/B9/B10/B11/B12/B13; Word W4 | Import ổn định |
| 3 | Word W1/W2/W3/W5/W6; PDF B8/C14 | Nghiệp vụ ổn định |
| 4 | PDF C15, full regression, UAT, rollout | Chốt quy tắc phê duyệt |

## Các điểm cần hỏi người phụ trách trước khi code
- PO sắp tăng dần hay giảm dần? Nếu tăng dần, PO mới ở cuối đúng theo Word W1.
- Mẫu xuất PO bỏ `Thành tiền` và phần tổng, hay chỉ bỏ cột thành tiền? Word nói xuất chỉ cần SL và đơn giá: **chưa tự ý chốt phần tổng**.
- Ngày xuất định dạng `dd/MM/yyyy`? Kiểu phân cách tiền tệ và decimal dùng quy ước nào?
- Tra cứu NCC hiển thị mọi giao dịch vật tư hay mỗi vật tư một dòng kèm lần mua gần nhất?
- VAT default theo danh mục có sẵn chưa? Quy tắc rounding và đơn vị tiền?
- Hàng cân cập nhật khi nhận hàng hay khi hóa đơn, ai được sửa?
- Sau phát hành khóa ngay hay 3 ngày, ai được mở lại? Có bước duyệt hiện hữu không?
- PDF scan chỉ lưu đính kèm hay cần OCR? Mẫu PCVT chuẩn lấy từ đâu?

## Tiêu chuẩn hoàn thành
Mỗi issue phải có: ảnh nguồn, bước tái hiện, kết quả trước, nguyên nhân có dẫn file/dòng code, diff nhỏ, test tự động, ảnh sau, đánh giá ảnh hưởng chức năng cũ, migration/rollback nếu cần. Không đổi nghiệp vụ chưa được chốt. Không ghi PASS nếu chưa chạy test trên repo thật.

## Prompt thực thi dành cho coding agent
> Hãy đọc cả `01_PDF_BUGS_AND_TEST_CASES.md` và `02_WORD_CHANGES_AND_AGENT_PLAN.md` cùng các ảnh `assets/`. Trước tiên phân tích toàn bộ repo, xác nhận kiến trúc, lập ma trận 21 yêu cầu (15 PDF + 6 Word), phân loại lỗi còn tồn tại/đã sửa/chưa đủ thông tin. Tạo báo cáo audit và test baseline. Sau đó thực hiện theo Phase 1→4, **mỗi lần chỉ một case hoặc nhóm phụ thuộc nhỏ**, viết failing test trước khi sửa, chạy lại unit/integration/E2E, lưu screenshot before/after, ghi kết quả. Không giả lập kết quả test, không tự quyết chính sách chưa được phê duyệt. Khi vướng quyết định nghiệp vụ thì báo BLOCKED và tiếp tục case độc lập khác.
