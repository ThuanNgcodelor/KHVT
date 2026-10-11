# Quản trị tài khoản, quyền ứng dụng và giữ đăng nhập

Ngày lập/cập nhật: **2026-10-11**. **Bước 1 gom Quản trị đã có code** trong lượt Phase 1; bước 2 cấp/cấm quyền riêng và bước 3 giữ đăng nhập chưa triển khai. Phạm vi do người dùng xác nhận: tạo tài khoản cho nhân viên và cấp/cấm chức năng; không cần một ứng dụng Nhân sự riêng. Cách dùng ở [hướng dẫn Quản trị Phase 1](../docs/HUONG_DAN_QUAN_TRI_PHASE_1.md), kết quả kiểm thử ở [hệ thống hiện tại](../docs/HE_THONG_HIEN_TAI.md). Phần mua hàng nằm ở [kế hoạch PCVT và báo giá](16-pcvt-tra-gia-cu-va-bao-gia.md).

## 1. Hiện tại đã có gì?

| Khu vực | Đang có trong code | Chưa có |
|---|---|---|
| `/admin/users` | Tạo/sửa tài khoản kèm hồ sơ cơ bản tùy chọn trong một form; liên kết hồ sơ có sẵn; gán vai trò trong phạm vi actor; khóa/ngừng/reset mật khẩu; tìm email/tên/mã nhân viên, lọc trạng thái/phòng ban và phân trang ở server | Cấp/cấm module trực tiếp cho từng người; sửa ma trận quyền |
| `/admin/employees` | Tạo/sửa hồ sơ; phòng ban/chức vụ; kích hoạt/ngừng | Không tự tạo nhân viên từ phòng ban hoặc workbook mua hàng |
| `/modules` | Mua hàng và Quản trị từ permission backend; danh bạ nằm trong Quản trị, người chỉ có quyền danh bạ không được mở tài khoản | Chưa có grant/deny riêng hoặc ứng dụng Bán hàng |
| Đăng nhập | Cookie phiên HttpOnly, Redis và CSRF; đổi mật khẩu tạm lần đầu | Chưa có lựa chọn giữ đăng nhập lâu dài |

Danh sách phòng ban có dữ liệu không làm phát sinh hồ sơ nhân viên. Người dùng báo hiện chưa có nhân viên; lượt lập kế hoạch này không truy vấn số hồ sơ trong database. Nhập nhân viên từ danh sách chưa được duyệt cũng không nằm trong phạm vi.

Cách dùng hiện tại: **Quản trị → Tài khoản → Thêm tài khoản → email đăng nhập, tên hiển thị, mật khẩu tạm tối thiểu 12 ký tự, vai trò → lưu**. API hiện yêu cầu định dạng email, không phải username tự do. Có thể bỏ liên kết nhân viên. Người đó đăng nhập và đổi mật khẩu tạm, sau đó chọn ứng dụng được cấp. `PLANNER` mở Mua hàng với quyền tác nghiệp hiện có; `VIEWER` chỉ đọc; `ADMIN` toàn quyền. Chức vụ “Phó phòng” hiện không tự cấp quyền.

Đổi vai trò/trạng thái/reset mật khẩu có thu hồi phiên. Backend bảo vệ tài khoản admin hoạt động cuối cùng và thao tác tự khóa. Các kiểm thử đã chạy được ghi trong [hiện trạng](../docs/HE_THONG_HIEN_TAI.md), không đồng nghĩa toàn bộ ứng dụng đã nghiệm thu.

## 2. Trải nghiệm cần triển khai

Giữ hai ô ứng dụng có nghiệp vụ: **Mua hàng** và **Quản trị**. Không đưa “Nhân sự” lên cổng ứng dụng. Giữ dữ liệu/phòng ban đang có, không xóa bảng vì đổi menu.

Trong Quản trị:

- **Tài khoản** là màn chính: họ tên, tên đăng nhập, phòng ban, chức vụ, trạng thái, ứng dụng được cấp, quyền bên trong. Một form tạo tài khoản kèm thông tin nhân viên cơ bản; người quản trị không phải tạo qua hai màn.
- **Phòng ban và chức vụ** là danh mục phụ. Chức vụ dùng mô tả người làm việc, không phải quy tắc cấp quyền.
- **Quyền truy cập** trong chi tiết tài khoản: chọn ứng dụng, mẫu quyền và ngoại lệ cho cá nhân; hiển thị quyền cuối cùng cùng nguồn cấp/cấm.
- `/admin/employees` chuyển thành danh bạ phụ trong Quản trị hoặc điều hướng tới màn tài khoản tương ứng. Hồ sơ chưa có tài khoản vẫn có thể giữ; không tự sinh tài khoản/mật khẩu hàng loạt.
- Không bổ sung chấm công, lương, nghỉ phép, tuyển dụng. Không dùng `HR_MANAGER` để cho nhân viên quyền quản trị tài khoản.

Ví dụ đề xuất:

| Người dùng | Chức vụ | Ứng dụng | Quyền bên trong |
|---|---|---|---|
| A | Phó phòng | Mua hàng | Tra lịch sử, xử lý PCVT, chọn NCC, lập PO; phát hành chỉ khi được cấp |
| B | Nhân viên | Mua hàng | Xử lý PCVT/báo giá, lưu bản nháp; cấm phát hành/hủy PO |
| C | Người xem | Mua hàng | Tra cứu; không sửa, import, phát hành |
| D | Nhân viên | Bán hàng khi module này đã triển khai | Có quyền Bán hàng riêng; không tự được Mua hàng |

“Phê duyệt quyền truy cập ứng dụng” là admin cấp quyền trực tiếp. “Phê duyệt PCVT/PO” là quy trình nghiệp vụ khác, chỉ xây khi đã xác định ai duyệt và trạng thái; không tự suy ra từ chức vụ.

## 3. Cấp quyền ở backend

Đề xuất dùng **quyền vào ứng dụng + quyền thao tác**, trên cùng backend hiện tại:

1. Danh mục ứng dụng do code đăng ký; admin chỉ cấp ứng dụng đã triển khai. Chưa cần màn tự tạo module bằng tên/URL.
2. Grant theo tài khoản/module: `ALLOW`, `DENY` hoặc chưa cấp. Chưa cấp là không được vào. `DENY` thắng cấp từ vai trò.
3. Vai trò là mẫu permission tái sử dụng; có thể có ngoại lệ `ALLOW`/`DENY` cho từng hành động của cá nhân. Cấm hành động thắng quyền được cấp từ bất kỳ vai trò nào.
4. Backend tính permission hiệu lực, loại toàn bộ permission thuộc module bị cấm, rồi trả `modules` và `permissions` cho frontend. Từng API kiểm tra **cả module và hành động**; ẩn menu chỉ hỗ trợ giao diện.
5. Thay quyền/khóa/reset mật khẩu thu hồi phiên và quyền giữ đăng nhập. Phiên mới tải lại quyền hiện hành; có phiên bản quyền để tránh dùng snapshot quyền cũ trong race.

Mẫu dữ liệu bổ sung: `user_module_access`, `user_permission_overrides`; tận dụng bảng role/permission hiện có. Có audit người cấp, người nhận, thay đổi, thời điểm. Bộ tính quyền phải được dùng thống nhất cho session, module catalog và API, kể cả API dùng chung catalog/import/export/dashboard. Permission chưa ánh xạ module phải được rà soát, không mặc định cho qua.

Tách **quản lý thông tin tài khoản** (`USER_MANAGE`) với **cấp quyền** (`ACCESS_MANAGE`, đề xuất ở bước 2). Phase 1 đã kiểm tra actor hiện hành trong database: người chỉ có `USER_MANAGE` không được gán wildcard hoặc vai trò có permission vượt phạm vi actor, và không được sửa/reset tài khoản có quyền cao hơn. API vai trò trả `assignable` để UI thể hiện cùng giới hạn. Khi thêm grant/override và quản lý mẫu quyền ở bước 2, tiếp tục áp dụng quy tắc này; không mở đường nâng quyền bằng form tạo tài khoản.

Wildcard `*` của admin vẫn phải qua quy tắc cấm: mở rộng thành permission cụ thể rồi áp dụng deny; principal không giữ wildcard có thể bypass `@PreAuthorize` sau khi đã tính ngoại lệ. Rà soát cả điều kiện `hasAuthority('*')` đang có trong controller, không chỉ sửa cổng ứng dụng. Không cho cấm quyền Quản trị/cấp quyền của admin hoạt động cuối cùng hoặc tự khóa đường khôi phục của chính mình. Tài khoản có quyền quản trị vẫn có thể bị cấm Mua hàng. Vai trò chức vụ không được cấp wildcard. Quyền quản trị mẫu quyền phải có ràng buộc chống tự nâng quyền và chống xóa bảo vệ last-admin.

Khi triển khai migration, chuyển **quyền hiệu lực hiện có** thành grant tương ứng để tránh mất quyền của tài khoản đang dùng; chuyển quản lý hồ sơ vào Quản trị. Rà soát tài khoản chỉ có `HR_MANAGER`, giữ quyền danh bạ phù hợp nhưng không tự cấp `USER_READ/USER_MANAGE`; cổng Quản trị phải có trang vào phù hợp cho người chỉ quản lý danh bạ. Quyền phát hành hiện dùng `PO_CREATE` hoặc `PO_EDIT`; cần thêm quyền riêng như `PO_ISSUE` và cập nhật vai trò cũ trước khi hỗ trợ checkbox “chỉ lập nháp/cấm phát hành”.

## 4. Mật khẩu và phiên đăng nhập

**Cấu hình đang có:** `spring.session.timeout: 30m` trong `source/src/main/resources/application.yml`. Đây là thời gian không có hoạt động phiên, không phải 30 phút kể từ lúc login. Cookie tên `QMHSESSION`, HttpOnly, SameSite=Lax; Secure ở production. Không cấu hình Max-Age cho cookie phiên; chưa có giới hạn tuyệt đối riêng trong luồng auth. Thời gian thực tế có thể bị biến môi trường/profile ghi đè; lượt này kiểm tra code/config, không đo TTL một phiên đang chạy.

Không có access token hoặc refresh token JWT để nêu thời hạn. Form hiện chỉ có Hiện mật khẩu, đã đặt autocomplete cho tên đăng nhập/mật khẩu. **Lưu mật khẩu** do trình quản lý mật khẩu của trình duyệt; ứng dụng không lưu mật khẩu vào localStorage/cookie.

Đề xuất thêm **“Giữ đăng nhập trên máy này”**, mặc định không chọn:

| Chính sách đề xuất | Thời hạn |
|---|---|
| Phiên làm việc | Giữ idle 30 phút; bổ sung tối đa 12 giờ cho một phiên |
| Thiết bị được ghi nhớ | Tối đa 30 ngày từ lần đăng nhập bằng mật khẩu; hết hạn yêu cầu login lại, không tự kéo dài vô hạn |
| Thao tác quản trị quyền/reset mật khẩu | Yêu cầu xác nhận lại mật khẩu nếu chỉ có đăng nhập khôi phục hoặc xác thực mật khẩu quá 15 phút |

Các con số trên là **đề xuất chính sách**, chưa phải cấu hình đã áp dụng. Máy dùng chung không chọn giữ đăng nhập. Khi phiên ngắn hết hạn, cookie thiết bị hợp lệ có thể tạo phiên mới mà không nhập mật khẩu, nên phải coi thiết bị đó đang được ủy quyền trong thời hạn đã chọn.

Hiện field `authenticatedAt` trong response được tạo bằng thời điểm trả response, kể cả `/me`; không thể dùng nó làm bằng chứng lần xác thực mật khẩu gần nhất. Bổ sung `passwordAuthenticatedAt` và `sessionStartedAt` lưu phía server, cùng kiểu đăng nhập bằng mật khẩu/khôi phục, rồi mới áp dụng giới hạn 15 phút/12 giờ. Không lấy đồng hồ client hoặc thời điểm gọi `/me` để kéo dài xác thực.

Thực hiện bằng token ngẫu nhiên trong cookie HttpOnly/SameSite, Secure ở HTTPS production; HTTP loopback dev dùng cấu hình riêng. Lưu bản băm và vòng đời theo thiết bị phía server; có xoay token, xử lý request đồng thời và thu hồi. Khôi phục phải kiểm tra tài khoản/nhân viên liên kết, thời hạn tuyệt đối và quyền mới nhất, tạo lại session/CSRF; không giữ token phiên trong localStorage. Phiên khôi phục không được kéo thời gian đã ghi nhớ vượt 30 ngày; hết hạn thiết bị phải xác thực mật khẩu lại kể cả khi đang có hoạt động. Logout thu hồi thiết bị hiện tại; “Đăng xuất mọi thiết bị”, đổi/reset mật khẩu, khóa tài khoản hoặc đổi quyền thu hồi cả phiên lẫn token ghi nhớ. Không cấp token ghi nhớ khi còn mật khẩu tạm.

Luồng login JSON đang do ứng dụng tự xử lý, nên không chỉ thêm checkbox hoặc bật cấu hình remember-me rồi coi là xong; phải tích hợp login/restore/logout và `AccountPrincipal` đúng với service thu hồi hiện tại. Tài liệu [Spring Security về remember-me](https://docs.spring.io/spring-security/reference/servlet/authentication/rememberme.html) mô tả đăng nhập bằng cookie qua các phiên; tài liệu hiện tại không phải lệnh yêu cầu nâng phiên bản dự án. [OWASP về session](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) là nguồn tham khảo cho idle/absolute timeout và vô hiệu hóa phiên phía server; thời hạn nêu trong bảng là lựa chọn của kế hoạch này.

## 5. Thứ tự làm và điều kiện nghiệm thu

1. **Gom Quản trị — Phase 1:** đã có form tạo tài khoản kèm hồ sơ tùy chọn, danh mục và chọn hồ sơ trong API Quản trị; bỏ ô Nhân sự; tìm kiếm/lọc tài khoản phía server; giữ dữ liệu cũ. Kiểm thử và giới hạn ghi riêng ở hiện trạng.
2. **Cấp/cấm module và thao tác:** migration, bộ tính quyền, API và UI xem quyền cuối cùng; tách quyền PO; audit/thu hồi phiên; kiểm thử trước khi đổi tài khoản thật.
3. **Giữ đăng nhập:** vòng đời token theo thiết bị, checkbox, re-auth thao tác nhạy cảm; kiểm tra cookie qua HTTPS thật khi Cloudflare được cấu hình.
4. **Kiểm tra API/MySQL/Redis riêng và browser:** tài khoản có/không có module, nhiều vai trò, deny thắng wildcard/role, URL trực tiếp và API 403, thu hồi hai phiên/thiết bị, admin cuối cùng, CSRF, mật khẩu tạm, restart server, hết hạn/đăng xuất/đổi quyền. Test browser giả lập được ghi riêng, không thay test backend thật.

Kết quả mong muốn: admin tạo tài khoản, cấp “Mua hàng → chỉ xem” hoặc “lập nháp”; nhân viên login thấy đúng ứng dụng, không thể gọi API bị cấm. Khi có Bán hàng sau này, đăng ký module/quyền mới và cấp cho người được chọn, không sửa toàn bộ cơ chế login.
