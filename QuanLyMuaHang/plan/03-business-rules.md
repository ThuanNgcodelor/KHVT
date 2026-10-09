# Quy tắc nghiệp vụ chuẩn

## Chuẩn hóa và tìm kiếm

normalize(text): lowercase, Unicode NFD bỏ dấu, đ -> d, gom whitespace, trim. Dùng normalized value để tìm, không thay tên snapshot gốc.

Ưu tiên kết quả:

1. Match material ID/code chính xác.
2. Alias chính xác sau normalize.
3. Token search không dấu.
4. Fuzzy suggestion có điểm tin cậy và hiển thị “gần đúng”; không tự gán MaHang/NCC hoặc tự sửa master bằng fuzzy match.

Autocomplete hiển thị giá, ngày, NCC, đơn vị và currency gần nhất. Lịch sử được sort theo ngày; bản ghi thiếu ngày chỉ nằm trong nhóm “chưa xác định ngày”, không thể thắng bản ghi có ngày.

## Giá và currency

- VND và USD là chuỗi truy vấn/tổng độc lập; không tự quy đổi.
- LICH_SU gán VND với cờ ASSUMED_LEGACY do file không có currency và ứng dụng cũ hiển thị mặc định VND.
- DON_HANG có currency nguồn thì giữ; blank gán VND nhưng warning.
- Mỗi PO chỉ dùng một currency; nếu input có nhiều loại tiền, phải tách group theo supplier + currency hoặc yêu cầu người lập xử lý trước khi preview.
- VND hiển thị không có phần lẻ; USD tối đa hai số lẻ. Tính bằng BigDecimal và rounding HALF_UP theo currency ở tổng header.

## Draft mua hàng và import nghiệp vụ

- Import từ yêu cầu mua/báo giá chỉ tạo draft lines, chưa ghi lịch sử/PO cho tới khi người dùng xác nhận phát hành.
- Nhận file XLS/XLSX/CSV và PDF text; PDF scan báo rõ không đọc được, cho nhập/dán thủ công.
- Header parser hỗ trợ tên hàng/tên vật tư/diễn giải, quy cách, DVT/đơn vị, số lượng, đơn giá; preview cho phép sửa trước khi dùng.
- Paste từ Excel nhận tab, nhiều khoảng trắng, có thể bỏ cột STT.
- Số dạng Việt Nam như 690.000 và 1,5 phải parse nhất quán. Trường rỗng/không hợp lệ không được âm thầm biến thành số 0.
- Số lượng numeric > 0 được tính toán; quantity_text được in nguyên văn và không tính tổng tự động.
- Giá hiện tại phải >= 0; PO thường yêu cầu > 0 khi phát hành, ngoại lệ miễn phí cần policy riêng.
- Giá/NCC gần nhất chỉ là gợi ý; người dùng phải xác nhận/chỉnh giá hiện tại trước khi phát hành.

## Nhà cung cấp

- Một PO thuộc một NCC; master supplier được match bằng code hoặc alias exact.
- Snapshot NCC/địa chỉ tại thời điểm phát hành không đổi khi master cập nhật.
- Địa chỉ có thể điền từ supplier master; khi match không chắc chắn phải yêu cầu xác nhận, không tự lấy địa chỉ NCC khác.
- Thiếu supplier code trong legacy không làm mất giao dịch; supplier_id null và giữ snapshot tên.

## PO và sequence

- Số mới: PO-yyMMdd-NN theo ngày Asia/Ho_Chi_Minh; NN tối thiểu hai chữ số và tăng không cắt số.
- Cấp sequence bằng DB lock/atomic operation; unique constraint bắt mọi race.
- Một lần issue nhiều NCC tạo mỗi NCC/currency một PO riêng; trả trạng thái rõ theo từng PO.
- Legacy PO giữ nguyên format cũ và number; không đổi hàng loạt để phù hợp format mới.
- PO đã phát hành không hard-delete. Sửa tạo revision mới, audit before/after, file PDF mới; PDF/revision cũ giữ nguyên.
- Trước khi render PDF, lưu PO và revision thành công. Lỗi render chỉ đánh dấu file generation failed và cho retry, không rollback/xóa PO.
- Hủy PO dùng trạng thái CANCELLED và lý do; không xóa dòng/history.

## Tiền, VAT và PDF

- Header VAT cho PO mới bắt buộc là 0%, 5%, 8% hoặc 10%; giá dòng là giá chưa VAT.
- Tổng trước VAT chỉ cộng dòng quantity numeric.
- Dòng quantity_text vẫn hiển thị, đánh dấu không bao gồm trong subtotal; PDF ghi rõ tổng không bao gồm các dòng số lượng dạng chữ.
- vat_amount = subtotal * vat_percent; grand_total = subtotal + vat_amount; rounding theo currency được ghi cố định trong test.
- Legacy PO có VAT null được hiển thị là “Chưa xác định VAT”; không tự phát hành lại PDF hoặc tính tổng sau VAT cho đến khi người có quyền bổ sung.
- PDF chứa PO/date, bên bán/địa chỉ, ghi chú, dòng hàng, spec, DVT, quantity, đơn giá, subtotal, VAT, tổng sau VAT, currency và vùng chữ ký theo mẫu phòng KHVT.
- Font PDF phải hỗ trợ tiếng Việt; kiểm thử dấu tiếng Việt và page break.

## Accounts, employees và permissions

- Chỉ ADMIN tạo account, đổi role, reset password, disable/enable, revoke sessions.
- HR_MANAGER quản lý employee/departments/positions; không mặc nhiên quản lý user account hoặc permission.
- PLANNER có quyền mua hàng/import nghiệp vụ theo matrix; VIEWER đọc dữ liệu được phép.
- Bất cứ account ACTIVE nào có quyền phù hợp đều có thể login; EMPLOYEE không bị chặn cứng như demo cũ.
- Account disabled/locked hoặc employee linked ở INACTIVE không được login. Khi disable/đổi role/reset password phải revoke mọi session.
- Account không liên kết employee được phép cho bootstrap/admin hệ thống; employee không có password/role.
- Không xóa vật lý account/employee/role đã tham chiếu. Không cho thao tác xóa/disable ADMIN cuối cùng.
- Mọi thay đổi hồ sơ, quyền, mật khẩu, trạng thái ghi audit; audit không chứa password hoặc hash.

## Quyền di trú

- Chỉ dùng user/employee records do người quản trị tạo hoặc file roster đã duyệt; không import nhân viên/tài khoản/password seed từ demo.
- Không tự ánh xạ MANAGER/EMPLOYEE cũ sang role mới. ADMIN tạo/duyệt role gán tại hệ thống mới.

