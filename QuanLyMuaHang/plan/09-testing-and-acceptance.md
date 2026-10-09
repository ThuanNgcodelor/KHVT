# Kiểm thử và tiêu chí nghiệm thu

## Phân tầng test

### Unit/domain

- NormalizedText: tiếng Việt có dấu/không dấu, đ, whitespace, null.
- Parser numeric: 690.000, 1,5, 1,000; reject invalid/ambiguous input.
- Quantity numeric/text; text không đóng góp subtotal.
- Currency format/rounding VND và USD; VAT 0/5/8/10.
- Group PO theo supplier + currency.
- PO sequence theo timezone, số vượt 99, unique.
- Edit PO tạo revision; PDF failure không xóa PO/revision.
- Search latest price loại record không ngày khỏi latest; currency không bị trộn.
- Authorization wildcard ADMIN và từng permission.
- Không disable/delete ADMIN cuối; HR_MANAGER không tự có USER_MANAGE.
- Inactive employee/account không được login.

### Auth/personnel integration

Test với MySQL + Redis riêng (Testcontainers hoặc test profile isolated):

- Flyway từ schema trống.
- Login đúng/sai; generic error; cookie HttpOnly/SameSite/Secure ở profile tương ứng.
- CSRF required cho POST/PATCH/PUT/DELETE.
- Session lưu Redis; logout/password reset/role change/disable employee invalidate đúng sessions.
- ADMIN quản lý user, role, permission và nhân sự; HR_MANAGER chỉ quản lý nhân sự; PLANNER không vào trang/admin API nhân sự; VIEWER không mutation.
- Tạo employee + linked account; unique employee/email; đổi department/position; deactivate.
- Không có public registration/Google/OTP endpoint trong MVP.
- Password/temp password/hash/cookie không xuất hiện trong log hoặc audit.

### Import workbook

Tạo fixture đã ẩn tên/giá nhạy cảm; không dùng workbook thật trong CI.

Fixtures kiểm tra:

- Các sheet legacy LICH_SU, DON_HANG, NCC, CONFIG.
- Date serial và 2 record ngày null.
- Cell mã hàng có leading zero/dấu chấm.
- Blank supplier/material codes, currency blank, VAT blank, quantity text.
- 690.000, 1,5, CSV BOM/delimiter, header nằm dưới dòng đầu.
- Sheet thiếu, header sai, formula không có cached value, file quá lớn/zip bomb.
- Same checksum/idempotent replay và workbook thay đổi.

Assertions:

- Preview đếm đúng rows/sheets và phân biệt success/warning/error.
- Raw row, sheet/row number được giữ; không thất lạc record chỉ vì thiếu FK.
- LICH_SU gán VND với ASSUMED_LEGACY; blank LoaiTien gắn warning; missing VAT giữ null; missing date không tham gia latest price.
- ACCESS_CODE không lưu/log/report giá trị; PO_SEQ cũ không dùng.
- Commit chạy lại không tạo duplicate; lỗi batch có thể resume/rollback có kiểm soát.
- Migration report có totals và danh sách warning tải được.

### Procurement/document tests

- Tạo PO một NCC và nhiều NCC; kiểm tra group/result từng PO.
- Hai request song song không cấp trùng PO.
- Issue/update/cancel có authorization/audit.
- PDF có header, dòng hàng, subtotal, VAT, total, currency, font tiếng Việt và page break.
- Số lượng chữ được in, loại khỏi subtotal, ghi chú rõ.
- Legacy VAT null được đánh dấu unknown; không tính thuế giả.
- File download cần quyền; PDF revision mới không ghi đè file revision cũ.
- PDF render fail cho retry mà không mất PO.
- Export XLSX kiểm tra format mã/đơn giá/ngày.

## Playwright E2E

- Login, CSRF, reload trang còn session, logout.
- ADMIN tạo employee/account, gán role, reset password; first login bắt đổi password.
- HR_MANAGER CRUD nhân viên nhưng bị chặn trang/API quản lý user/role.
- PLANNER lập PO, import operational request, tra giá; VIEWER không sửa.
- Search không dấu, filter VND/USD, trạng thái loading/empty/error.
- Legacy import preview -> xác nhận -> report.
- Tạo multi-supplier PO -> tải PDF; edit -> revision tăng.
- Desktop/mobile layout cơ bản và keyboard navigation.
- Failure lưu trace/screenshot/video; không dùng sleep cố định.
- Fixture deterministic, không gọi OpenAI thật.

## Acceptance checklist

- [ ] User seed đầu qua quy trình an toàn, không hard-coded credential.
- [ ] ADMIN có wildcard và mọi mutation vẫn audit.
- [ ] Login/role/session behavior có tests; Redis outage không bypass auth.
- [ ] Employee inactive làm account/session liên kết mất hiệu lực.
- [ ] Workbook baseline được preview/reconcile: 10.563 dòng LICH_SU, 850 dòng DON_HANG thành 262 PO, 279 dòng nguồn NCC.
- [ ] Các warning thiếu data khớp kiểm kê; không bịa VAT/ngày/mã.
- [ ] Giá gần nhất đúng theo date/currency, record date null bị loại.
- [ ] PO number unique, PDF đúng totals, text quantity không tính.
- [ ] Sửa PO giữ revision/file/audit trước đó.
- [ ] Compose chỉ chạy MySQL + Redis trong project riêng, port localhost.
- [ ] Không có database/cache public hoặc Cloudflare ingress.
- [ ] Java chạy native/systemd, frontend same-origin, domain HTTPS qua Tunnel.
- [ ] Backup/restore MySQL và file artifacts đã chạy thử.

