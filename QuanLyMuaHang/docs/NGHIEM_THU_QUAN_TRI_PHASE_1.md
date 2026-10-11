# Nghiệm thu kỹ thuật Quản trị Phase 1

Ngày **2026-10-11**, báo cáo lượt thật cuối được ghi lúc **18:25:27 +07:00**. **Đạt trong phạm vi Phase 1 dưới đây**: gom Quản trị, tài khoản kèm hồ sơ tùy chọn, tìm kiếm/lọc/phân trang ở server, giới hạn thao tác được ủy quyền và bảo vệ phiên/admin. Driver MySQL/Redis và MCP thật hoàn tất 15 nhóm với `completed: true`, `mainUnchanged: true`. Đây là bước 1 của kế hoạch 15, không phải nghiệm thu toàn bộ quản trị quyền hoặc toàn hệ thống.

## Phạm vi đã triển khai

- Bước 1 của [kế hoạch Quản trị](../plan/15-quan-tri-tai-khoan-va-phan-quyen.md): tài khoản, danh bạ, phòng ban và chức vụ cùng khu vực **Quản trị**; cổng chỉ đăng ký Mua hàng và Quản trị. Giữ dữ liệu và module backend `personnel`.
- Một form tạo/sửa tài khoản, hồ sơ nhân viên cơ bản tùy chọn hoặc liên kết hồ sơ đang hoạt động có sẵn. Tài khoản/hồ sơ/audit cùng transaction; không tạo hồ sơ mới nếu hoàn toàn trống.
- Tìm kiếm email/tên/mã nhân viên, lọc trạng thái/phòng ban và phân trang ở server. API `/options` và `/employees` chỉ cung cấp danh mục/hồ sơ tối thiểu cho form.
- Backend giới hạn vai trò được gán và tài khoản được sửa/reset trong phạm vi quyền actor. `assignable` ở UI phản ánh giới hạn server. Người chỉ có quyền danh bạ không tự có quyền tài khoản.
- Giữ đổi mật khẩu tạm, CSRF/cookie phiên, thu hồi phiên, bảo vệ tự khóa và admin hoạt động cuối cùng.

## Bằng chứng kiểm thử

| Lớp kiểm tra | Kết quả và giới hạn |
|---|---|
| Full suite backend trước các sửa cuối | `mvn -Pwindows-web -DforkCount=0 -Dspring.test.context.cache.maxSize=1 package`: **BUILD SUCCESS lúc 18:01:06 +07:00**, 138 test liệt kê, 136 thực thi qua, 2 opt-in bỏ qua |
| Backend mục tiêu bản cuối | Sau sửa hồ sơ hiện có bị xóa thông tin tùy chọn, giữ danh mục ngừng đúng ID đang liên kết và kiểm tra last-admin chỉ khi target đang hoạt động: `UserAdministrationIntegrationTest,AuthApiIntegrationTest,ApplicationModuleCatalogTest` cùng package **31 qua, 0 lỗi, 0 bỏ qua** (14 + 11 + 6), BUILD SUCCESS lúc **18:24:28**. Chưa chạy lại toàn suite sau các sửa này |
| Frontend | TypeScript/Vite build và **10 unit tests** qua |
| Browser API giả lập | **56 trường hợp riêng biệt** auth/tài khoản/danh bạ trên desktop/mobile qua trong **36,4 giây**. Đây là fixtures, không thay kiểm tra MySQL/Redis |
| HTTP/MySQL/Redis thật | Driver `tools/local-test/admin-phase1.mjs` trên schema `qmh_admin_phase1_c0c3eea29b84`, namespace Redis riêng và backend 8082: **15 nhóm qua**, `completed: true`; đã kiểm tra bỏ vai trò ADMIN của tài khoản đã ngừng vẫn được phép, admin hoạt động cuối cùng vẫn được bảo vệ |
| MCP với API thật | Cổng admin/danh bạ, tạo tài khoản kèm hồ sơ, lọc tại server/reload, viewport mobile và từ chối URL tài khoản cho người chỉ có quyền danh bạ đều qua |
| Hai admin tự bỏ quyền đồng thời | Nhận **200 và 400 `LAST_ADMIN`**, database giữ một admin hoạt động. Phiên của admin bị hạ quyền nhận 401 ở `/me`; admin bị từ chối vẫn đăng nhập và giữ quyền |
| Database chính | Báo cáo trước/sau có `mainUnchanged: true`, không tạo tài khoản/hồ sơ thử ở database chính |

Hai test opt-in bỏ qua trong full suite là Redis thu hồi phiên riêng và workbook legacy H2. Driver thật đã kiểm tra thu hồi phiên ở HTTP/Redis; không coi việc đó là đã chạy test opt-in bị bỏ qua. Không cộng số test của các lượt chạy lại thành tổng mới.

Log local: `source/target/runtime/admin-phase1-backend-package.log`, `admin-phase1-backend-final.log`, `admin-phase1-browser.log` và [log lượt thật](../source/target/runtime/admin-phase1-real-run.log). Báo cáo driver: [báo cáo cuối](../source/target/runtime/admin-phase1-real-integration-report.json) và [bản UUID](../source/target/runtime/qmh_admin_phase1_c0c3eea29b84-report.json). Đây là artifact sinh ra, không đưa cookie, mật khẩu, hash hoặc nội dung môi trường riêng vào tài liệu/Git.

Lượt MCP đầu bị lỗi selector ô email khớp nhiều phần tử. Đã sửa selector strict trong **driver kiểm thử**, rồi chạy lại đến hết. Không sửa API/giao diện để làm test qua và không cộng các nhóm của lượt lỗi vào 15 nhóm cuối.

## Các nhóm thật đã qua

1. Schema MySQL thử rỗng, Flyway V7 và health Redis runtime.
2. Bắt buộc đổi mật khẩu tạm, cookie HttpOnly, anonymous/CSRF và hai ứng dụng theo quyền.
3. Chặn tự vô hiệu hóa và bỏ vai trò admin hoạt động cuối cùng; cho phép bỏ vai trò ADMIN của tài khoản đã ngừng vì không làm giảm số admin đang hoạt động.
4. Tạo tài khoản/hồ sơ trong một request, sinh mã khi cần, bỏ hồ sơ mới trống, liên kết hồ sơ có sẵn không tạo bản sao.
5. Trùng email/mã, vai trò sai hoặc liên kết hồ sơ đã dùng đều rollback tài khoản/hồ sơ/audit.
6. Hồ sơ đã liên kết xóa được phòng ban/chức vụ và đồng bộ tên; giữ tham chiếu danh mục ngừng đúng ID, chặn gán mới và không để hồ sơ dở.
7. Sửa hồ sơ không tạo trùng; tìm kiếm email/tên/mã và lọc phòng ban/phân trang tại server.
8. Đổi vai trò/trạng thái/reset mật khẩu thu hồi cả hai phiên Redis; khóa/ngừng và vượt bước đổi mật khẩu tạm bị chặn.
9. `HR_MANAGER` vào danh bạ trong Quản trị và bị từ chối API tài khoản.
10. Người quản trị được ủy quyền không gán `ADMIN`, sửa/reset admin; `assignable` đúng với kiểm tra API.
11. Restart giữ tài khoản/hồ sơ đã lưu và phiên Redis còn hợp lệ.
12. MCP thật kiểm tra cổng admin/danh bạ, tạo tài khoản kèm hồ sơ, lọc/reload, URL bị cấm và không tràn trang trên mobile.
13. Hai admin tự bỏ quyền đồng thời vẫn giữ một admin hoạt động; phiên bị hạ quyền được thu hồi, admin có request bị từ chối vẫn đăng nhập.
14. Audit chứa metadata tài khoản/hồ sơ phù hợp, không chứa mật khẩu/hash/CSRF/session cookie.
15. Metadata tài khoản, liên kết vai trò, số bản ghi nghiệp vụ/danh bạ và phiên bản Flyway của database chính giữ nguyên.

## Dữ liệu chính và trạng thái chạy

Main server 8080/Vite5173 tiếp tục tắt theo yêu cầu người dùng. Driver chỉ dùng schema/namespace UUID và cổng 8082; backend/browser helper của lượt thật đã tự dừng. Kiểm tra sau lượt cuối không có listener 8080/8082/5173/5190, MySQL/Redis vẫn healthy. Dữ liệu chính trước/sau giữ **10.580 lịch sử, 262 PO, 850 dòng PO, 391 NCC, 2.997 vật tư, 1 tài khoản, 0 hồ sơ nhân viên và Flyway V6**. Metadata tài khoản và liên kết vai trò cũng khớp, `mainUnchanged: true`. Tài khoản/hồ sơ thử chỉ nằm trong schema kiểm thử; không tự sinh nhân viên trong database chính.

## Còn ngoài phạm vi

Grant/deny riêng cho ứng dụng/hành động, ma trận sửa quyền, tách quyền phát hành PO và phiên bản quyền chống race đăng nhập/đổi quyền thuộc bước 2; giữ đăng nhập theo thiết bị và xác nhận lại mật khẩu thuộc bước 3. Hiện vẫn dùng Redis session idle 30 phút, chưa có access/refresh JWT hoặc cookie giữ đăng nhập dài hạn. Kiểm tra thu hồi hai phiên và tự hạ quyền đồng thời không chứng minh đã loại hết race giữa login mới và thay quyền.

Không triển khai chấm công/lương/nghỉ phép/tuyển dụng, không OCR PDF scan trong Phase 1, không nhập sáu chứng từ mẫu vào database chính và chưa nghiệm thu production/Cloudflare. Cách dùng hiện tại ở [hướng dẫn Quản trị Phase 1](HUONG_DAN_QUAN_TRI_PHASE_1.md).
