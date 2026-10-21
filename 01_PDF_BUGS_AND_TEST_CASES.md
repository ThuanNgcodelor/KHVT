# AI Agent — Báo cáo lỗi và kiểm thử theo PDF

> Nguồn: `Gop-y-cai-thien-Web-Mua-hang.pdf` (12 trang, 15 góp ý, ngày 15/10/2026). Đây là **yêu cầu người dùng**, không phải bằng chứng rằng code hiện tại vẫn còn lỗi. Ảnh trang được lưu tại `assets/pdf/`. Agent phải đọc source và tái hiện lỗi trước khi sửa.

## Quy tắc vận hành bắt buộc
1. Khảo sát repo: README, package manifests, React routes/components, Spring Boot controllers/services/DTOs, migration/schema MySQL, Redis, Docker Compose, auth/role, test suite. **Không tự đoán tên file, endpoint hoặc DB**.
2. Tạo bảng đối chiếu `ID → ảnh → route/component → API → DB → trạng thái tái hiện → root cause → patch → test → kết quả`.
3. Trước sửa: chạy ứng dụng và chụp ảnh *before*, lưu log/network payload. Nếu thiếu môi trường hoặc dữ liệu, ghi `BLOCKED`, không ghi PASS.
4. Sửa theo từng case; ưu tiên thay đổi nhỏ, migration có rollback, không phá dữ liệu PO cũ. Chạy unit + integration + E2E và chụp ảnh *after* tại cùng viewport.
5. Các ảnh PDF có **ảnh thực tế** và **minh họa đề xuất**: không được nhầm hình mockup với chức năng đã tồn tại.
6. Kiểm thử tiếng Việt, Unicode, số tiền VND, dấu chấm/phẩy; test API/backend chứ không chỉ kiểm tra giao diện. Không tự sửa giá, NCC hay VAT theo phỏng đoán.

## Thứ tự triển khai
- **Phase 0 — Audit/baseline**: kiểm tra repo, backup DB, dữ liệu mẫu, xác định route và trạng thái thực tế; chạy test nền.
- **Phase 1 — Blocker nhập liệu (A2,A3,A4,A5,A6)**: parser Excel/CSV/PDF/dán, mapping cột, số lượng, lưu nháp, tránh mất dữ liệu.
- **Phase 2 — Đúng nghiệp vụ (A1,A7,B9,B10,B11,B12,B13)**: giá gần nhất, lọc giá, người lập, VAT dòng, số lượng và tính tiền.
- **Phase 3 — UX và hợp nhất (B8,C14)**: tìm NCC một ô, import trong màn lập đơn, chỉ sau khi phase 1/2 ổn.
- **Phase 4 — Quyền & khóa đơn (C15)**: xác nhận quy tắc duyệt/mở khóa trước khi lập trình, test quyền và audit trail.

## 15 case PDF — thực thi từng case

### A1 — Lấy giá/NCC gần nhất hàng loạt | Cao | Phase 2 | Trang 2–3
![Đề xuất giá gần nhất](assets/pdf/page-02.png)
![Ảnh thực tế nút hiện tại](assets/pdf/page-03.png)
- **Given** danh sách 5 vật tư, 4 có lịch sử, 1 chưa có; **When** chọn `Lấy giá gần nhất cho tất cả`; **Then** 4 dòng hiển thị giá/NCC/ngày, 1 dòng `Chưa có lịch sử mua`, không gán giá cũ tự động vào giá đặt hàng khi chưa xác nhận.
- Kiểm tra lookup theo mã vật tư, ngày giao dịch, currency, NCC, phân trang/batch API; tránh N+1 query. Test chạy lại không làm mất giá đã nhập tay.

### A2 — Import PCVT/báo giá Excel, CSV, PDF và paste | Cao | Phase 1 | Trang 3–4
![Lỗi import và mapping](assets/pdf/page-03.png)
![Minh họa chọn cột](assets/pdf/page-04.png)
- Test file Excel/CSV hợp lệ; bảng có/không header; PDF text; PDF scan; paste 4 cột; file rỗng; sai định dạng. Không được báo thành công với `0 dòng` khi có dữ liệu hợp lệ.
- Nếu không nhận diện cột: cho mapping tên hàng/quy cách/ĐVT/SL/giá và preview; lỗi tiếng Việt chỉ rõ dòng/cột. PDF scan **chỉ bắt buộc đính kèm** trước khi chốt có làm OCR hay không.
- Regression: thông báo `PDF_TABLE_NOT_RECOGNIZED`, `PRICE_BASIS_UNKNOWN` phải chuyển thành lỗi người dùng hiểu được; vẫn lưu error code trong log kỹ thuật.

### A3 — Import lại Excel do chính web xuất | Cao | Phase 1 | Trang 4
![File xuất không nhập lại được](assets/pdf/page-04.png)
- Export PO mẫu `don-hang-301-r1.xlsx` (nếu có fixture) → import lại → preview đúng số dòng, tên, ĐVT, SL, giá. Chấp nhận header khác tên và phần tiêu đề dài hơn 15 dòng; không nhầm dòng tổng thành vật tư.
- Bắt buộc có round-trip automated test `export → import → compare normalized lines`.

### A4 — Paste Excel lệch cột/giá/xuống dòng | Cao | Phase 1 | Trang 5
![So sánh dữ liệu dán và kết quả mong muốn](assets/pdf/page-05.png)
- TSV `Xích máy nghiền\tCái\t312\t6.795` phải ra ĐVT=Cái, SL=312, giá=6795 (theo ngữ cảnh VND); không đưa SL sang ĐVT.
- Ô tên hàng có Alt+Enter `Thép ống mạ kẽm Φ42x3\nCT3, Φ42x3mm,\nL=6000mm` phải giữ **1 dòng vật tư**, SL=25, giá=60000.
- Kiểm thử chuỗi `6.795`, `60,000`, `152,5`, `1.234,56`, `1,234.56` với chính sách phân giải số rõ ràng; định dạng mơ hồ phải yêu cầu xác nhận, không âm thầm biến đổi.
- Preview sửa từng ô trước khi commit; không tạo duplicate nếu bấm import hai lần.

### A5 — Hàng mua theo cân | Cao | Phase 1/2 | Trang 6
![Lỗi số lượng hàng cân](assets/pdf/page-06.png)
- Nhập SL dự kiến 150 kg, flag `chốt theo cân`; lưu nháp; cập nhật thực tế 152,5 kg tại bước nghiệp vụ được phê duyệt. Kiểm tra dấu phẩy/chấm và lỗi hiển thị đúng ô.
- Chưa chốt ai cập nhật và ở bước nào: chỉ thiết kế có thể cấu hình, không tự cho phép sửa PO đã duyệt.

### A6 — Mất dữ liệu khi đổi trang | Cao | Phase 1 | Trang 6
![Hai màn hình gây mất dữ liệu](assets/pdf/page-06.png)
- Chọn NCC, VAT, người lập, 3 dòng vật tư; chuyển Lập đơn → Nhập dữ liệu → quay lại, refresh/tab back; dữ liệu nháp còn đủ. Test logout/login, nhiều tab, lỗi mạng và conflict version; không ghi đè nháp người khác.

### A7 — Lọc kết hợp NCC + vật tư | Vừa | Phase 2 | Trang 7
![Bộ lọc kết hợp](assets/pdf/page-07.png)
- NCC=N.A.T và từ khóa=`dây cáp` → chỉ kết quả thỏa **cả hai**; thêm currency/time filter, xóa từng chip và xóa tất cả. Test query backend với điều kiện AND, phân trang và dấu tiếng Việt.

### B8 — Autocomplete NCC | Vừa | Phase 3 | Trang 8–9
![Giao diện tìm NCC](assets/pdf/page-08.png)
![Gợi ý nhà cung cấp](assets/pdf/page-09.png)
- Gõ `ngũ hi` → gợi ý `VTU0012 — CÔNG TY TNHH NGŨ HIỆP` nếu dữ liệu tồn tại; chọn bằng chuột, mũi tên, Enter; hiện địa chỉ. Test debounce, không chọn nhầm NCC khi có tên giống nhau.

### B9 — Người lập theo tài khoản | Vừa | Phase 2 | Trang 9
![Ô người lập hiện tại](assets/pdf/page-09.png)
- Login A → người lập A; không thể giả mạo người lập bằng sửa request. Nếu lập thay, chỉ chọn user được phép; audit `createdBy` tách biệt `onBehalfOf`.

### B10 — Hiện giá/NCC/ngày gần nhất | Cao | Phase 2 | Trang 8–9
![Giao diện bảng và giá gần nhất](assets/pdf/page-08.png)
![Bảng hiện tại thiếu giá](assets/pdf/page-09.png)
- Mỗi vật tư có giá/NCC/ngày gần nhất, hoặc không có lịch sử. Nút `Dùng giá này` mới copy giá sang ô đơn giá. Cảnh báo giá chênh >10% là **ngưỡng ví dụ**, cần xác nhận cấu hình trước.

### B11 — VAT theo dòng | Cao | Phase 2 | Trang 8,10
![Ví dụ VAT theo dòng](assets/pdf/page-08.png)
![Lỗi VAT toàn đơn](assets/pdf/page-10.png)
- Hai dòng VAT 8% và 10% → VAT = tổng `(SL × đơn giá × thuế suất)` từng dòng; kiểm tra rounding và currency theo quy tắc hệ thống. Test API, DB, export và các đơn cũ dùng VAT toàn đơn; cần kế hoạch migration.

### B12 — Không mặc định SL=1 | Vừa | Phase 2 | Trang 9–10
![Số lượng đang mặc định](assets/pdf/page-09.png)
![Đề xuất số lượng bắt buộc](assets/pdf/page-10.png)
- Dòng mới SL trống; chặn phát hành khi SL trống/<=0; báo ngay tại ô. Lưu nháp có thể chứa dòng chưa hoàn thiện nếu nghiệp vụ cho phép; trường hợp hàng cân áp dụng A5.

### B13 — Thành tiền và tổng trước lưu | Vừa | Phase 2 | Trang 8,10
![Minh họa tổng đơn](assets/pdf/page-08.png)
![Yêu cầu tổng tiền](assets/pdf/page-10.png)
- Thành tiền dòng = SL × đơn giá; tổng tạm tính = tiền hàng + VAT dòng; cập nhật realtime; dòng thiếu SL không được tính vào tổng và phải cảnh báo. Test rounding, số lớn, đổi tiền tệ.
- **Phân biệt** tổng tạm tính trên màn hình/tra cứu với **mẫu PO xuất cho NCC**: tài liệu Word yêu cầu bản xuất không có cột thành tiền.

### C14 — Nhập PCVT, dò giá, lập đơn một màn | Vừa | Phase 3 | Trang 11
![Quy trình một màn](assets/pdf/page-11.png)
- Từ Lập đơn bấm `Dán / tải PCVT` → preview → mapping → import vào danh sách → gợi ý giá/NCC → người dùng duyệt → lưu nháp/phát hành. Test không mất dữ liệu khi đóng popup, hủy import, import lỗi một phần.

### C15 — Khóa sửa PO sau phát hành/duyệt | Cao | Phase 4 | Trang 11–12
![Quy trình trạng thái đơn](assets/pdf/page-11.png)
![Câu hỏi cần chốt](assets/pdf/page-12.png)
- Test state transitions `DRAFT → ISSUED → APPROVED` theo state machine thực tế; API phải từ chối sửa khi khóa (không chỉ disable UI). Mở lại yêu cầu quyền, lý do, timestamp, before/after audit.
- **BLOCKED nghiệp vụ**: khóa ngay hay cho sửa 3 ngày, ai có quyền mở, quy trình duyệt thực tế chưa được xác nhận.

## Quy trình chạy cho MỖI case
`RED (reproduce + screenshot + failing test) → FIX (minimal patch) → GREEN (unit/integration/E2E) → REGRESSION (cases liên quan) → REVIEW (diff + migration + bảo mật) → DONE`.

## Kết quả Agent phải xuất
- `reports/01-audit.md`: kiến trúc thật, routes/API/DB, các giả định đã kiểm chứng.
- `reports/cases/A1.md ... C15.md`: nguyên nhân, file sửa, test input/expected/actual, ảnh before/after, trạng thái PASS/FAIL/BLOCKED.
- `reports/phase-summary.md`: test command, coverage, lỗi còn lại, rủi ro, cách rollback.
- Không tự tuyên bố đã test nếu chưa chạy app thực tế.
