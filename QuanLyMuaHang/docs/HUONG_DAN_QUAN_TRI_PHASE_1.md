# Quản trị tài khoản — Phase 1

Phase 1 gom tài khoản và danh bạ cơ bản vào **Quản trị**. Cổng ứng dụng chỉ đăng ký **Mua hàng** và **Quản trị**; không có ứng dụng Nhân sự riêng. Dữ liệu phòng ban, chức vụ và hồ sơ cũ vẫn được giữ.

## Tạo tài khoản

1. Đăng nhập bằng tài khoản có quyền quản lý tài khoản, vào **Quản trị → Tài khoản → Thêm tài khoản**.
2. Nhập email đăng nhập, tên hiển thị và mật khẩu tạm ít nhất 12 ký tự. Gửi mật khẩu cho người sử dụng qua kênh riêng.
3. Chọn vai trò. `VIEWER` dùng để tra cứu Mua hàng; `PLANNER` có quyền tác nghiệp Mua hàng hiện có; `ADMIN` có toàn quyền. Chức vụ như Phó phòng không tự cấp quyền.
4. Thông tin nhân viên là tùy chọn: có thể tạo tài khoản độc lập, tạo hồ sơ cơ bản ngay trong form, hoặc liên kết một hồ sơ đang hoạt động có sẵn. Chọn phòng ban/chức vụ khi cần quản lý thông tin này.
5. Lưu. Tài khoản mới phải đổi mật khẩu tạm khi đăng nhập lần đầu, rồi vào cổng chọn ứng dụng.

Tạo tài khoản và hồ sơ mới được thực hiện trong cùng transaction. Nếu kiểm tra dữ liệu thất bại, không giữ lại một hồ sơ được tạo dở. Một hồ sơ nhân viên chỉ liên kết một tài khoản.

## Tìm và quản lý tài khoản

Tìm kiếm và bộ lọc được gửi đến server, áp dụng trên toàn danh sách trước khi phân trang. Không phải tìm riêng các dòng trên trang đang mở.

Sửa vai trò/trạng thái hoặc reset mật khẩu có thể thu hồi các phiên của tài khoản đó. Người quản trị cần đọc xác nhận trước khi lưu. Mật khẩu reset là mật khẩu tạm và người dùng phải đổi lại khi đăng nhập.

Backend bảo vệ việc tự khóa và admin hoạt động cuối cùng. Người có quyền quản lý tài khoản được ủy quyền chỉ được gán vai trò và quản lý tài khoản nằm trong phạm vi quyền của mình; không được cấp wildcard hoặc reset tài khoản có quyền cao hơn để chiếm quyền quản trị.

## Danh bạ, phòng ban và chức vụ

Các màn `/admin/employees`, `/admin/departments`, `/admin/positions` là phần phụ của Quản trị. Người chỉ có `PERSONNEL_READ` vào Quản trị từ danh bạ; không được tự có quyền xem hoặc sửa tài khoản. Nhân viên chưa có tài khoản vẫn có thể giữ trong danh bạ.

Quản trị không bao gồm chấm công, lương, nghỉ phép hoặc tuyển dụng. Không tự sinh tài khoản hay mật khẩu cho toàn bộ danh bạ.

## Phần tiếp theo

- Cấp/cấm từng ứng dụng và từng thao tác cho cá nhân, tách quyền phát hành PO: bước 2 của [kế hoạch Quản trị](../plan/15-quan-tri-tai-khoan-va-phan-quyen.md).
- Giữ đăng nhập trên thiết bị và xác nhận lại mật khẩu cho thao tác nhạy cảm: bước 3. Hiện vẫn dùng Redis session idle 30 phút; chưa có access/refresh JWT hoặc cookie ghi nhớ dài hạn.
- OCR PDF scan là công việc import riêng, không thuộc Phase 1 Quản trị.

Phạm vi kiểm thử và giới hạn hiện hành xem [hệ thống hiện tại](HE_THONG_HIEN_TAI.md).
