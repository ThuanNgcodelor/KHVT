---
name: khvt-ui
description: Thiết kế và triển khai giao diện React quản lý nội bộ cho dự án KHVT, đặc biệt bảng nhân sự, tài khoản và mua hàng. Áp dụng khi chỉnh giao diện trong source/frontend; không thay thế ứng dụng bằng landing page hoặc sản phẩm khác.
---

# Giao diện nghiệp vụ KHVT

Skill riêng của repository, được tạo theo yêu cầu người dùng; không phải skill chính thức từ OpenAI. Dùng cùng AGENTS.md ở gốc dự án. Đọc contract controller/service của tính năng trước khi nối UI; plan mô tả mục tiêu, không chứng minh API đã tồn tại.

## Hướng thiết kế đã chọn

- Công cụ làm việc hằng ngày của phòng KHVT. Ưu tiên bảng, bộ lọc, phân trang, form rõ label và thao tác có ý nghĩa.
- Người dùng đã yêu cầu bỏ màu cam. Palette hiện tại: xanh rêu đậm `#173f35`, xanh ngọc `#176b57`, nền trắng ngà `#f4f6f3`, chữ than `#23352e`. Nếu người dùng đổi màu, cập nhật lựa chọn này thay vì giữ palette cũ trong plan.
- Dùng Segoe UI/system font hỗ trợ tiếng Việt, cỡ chữ nội dung 14px, bảng dễ đọc, số liệu thẳng hàng. Viền nhẹ, góc bo nhỏ; dành khoảng trắng cho nội dung.
- Đầu trang có tiêu đề, mô tả ngắn và thao tác chính; một bảng là trọng tâm của trang danh sách. Dùng SVG nét thống nhất cho menu, không dùng ký tự emoji hoặc icon ngẫu nhiên.
- Bỏ slogan quảng cáo, gradient, glow, KPI/trend giả và những khối trang trí không giúp thực hiện nghiệp vụ. Dashboard chỉ có số liệu API cung cấp.
- Footer tại trang auth và khu vực làm việc: `© Bản quyền thuộc về KHVT | Cung cấp bởi` và link `https://github.com/ThuanNgcodelor` với nhãn `ThuanNgcodelor`.

## Triển khai

- Giữ pages/components/hooks/services theo feature. Tái sử dụng PageHeader, field, dialog và pagination khi dùng cho nhiều màn.
- Request qua apiClient với cookie/CSRF; không thêm token localStorage. Xóa cache nhạy cảm khi logout/hết phiên.
- Loading, không có dữ liệu, không có kết quả lọc, lỗi có retry và 403 cần phân biệt. Không thay API lỗi bằng dữ liệu demo; không hiển thị thành công trước khi server xác nhận.
- Form có validation phù hợp contract; giữ nội dung khi server lỗi để người dùng sửa. Mật khẩu tạm không xuất vào toast, log, URL, fixture thật hay browser trace.
- Xác nhận thao tác ngừng nhân viên, khóa tài khoản, thay quyền và reset mật khẩu; nêu hậu quả về phiên đăng nhập khi backend thực hiện. Không thêm nút xóa khi API chỉ hỗ trợ ngừng/kích hoạt.
- UI ẩn thao tác theo vai trò mặc định nhưng backend luôn kiểm tra quyền. Không dựng màn sửa role/permission nếu backend mới có API đọc.
- Dialog dùng semantics đúng, focus vào dialog, giữ Tab bên trong, Escape đóng khi không gửi, trả focus về nút mở. Bảng cuộn trong vùng bảng ở mobile; không làm tràn toàn trang. Menu mobile có đóng và hỗ trợ Escape.

## Kiểm chứng

Chạy build, test request/validation cần thiết và Playwright cho luồng vừa thay đổi. Kiểm tra ảnh desktop/mobile của dữ liệu tổng hợp và bàn phím. Test giả lập xác nhận frontend, không được báo là đã nghiệm thu MySQL/Redis. Cập nhật README và docs/HE_THONG_HIEN_TAI.md với phạm vi đã làm, kết quả thực tế và phần còn thiếu.
