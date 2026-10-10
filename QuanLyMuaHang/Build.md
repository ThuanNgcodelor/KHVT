# Lệnh chạy, tắt server và nhập Excel trên Windows

Các lệnh dưới đây dùng PowerShell đang đứng ở **thư mục `KHVT`**, giống terminal hiện tại của bạn. File này nằm trong `KHVT/QuanLyMuaHang/Build.md`; launcher nằm trong `KHVT/QuanLyMuaHang/CHAY_KHVT.cmd`.

Mở **Docker Desktop riêng** trước khi chạy ứng dụng. Máy cần JDK 21, Maven 3.9+ và Node.js 22+ để build. Giữ cấu hình/mật khẩu riêng trong `QuanLyMuaHang/source/.env`.

## 1. Chạy local để test giao diện và chức năng

```powershell
.\QuanLyMuaHang\CHAY_KHVT.cmd start -Profile dev
```

Mở **http://localhost:8080**. Launcher bật MySQL/Redis, build frontend/backend và chạy một Java phục vụ cả giao diện lẫn API. Lệnh `start` mặc định bỏ qua bộ test tự động. Nếu ứng dụng do launcher quản lý đã healthy, gọi lại `start` sẽ dùng tiến trình đó; muốn build code mới, dùng `stop` rồi `start`.

Đã có JAR đóng gói thì có thể chạy nhanh bằng:

```powershell
.\QuanLyMuaHang\CHAY_KHVT.cmd start -Profile dev -NoBuild
```

`-NoBuild` không cập nhật code mới. Nếu MySQL/Redis đã được bạn bật riêng, thêm `-ExternalDocker` để launcher chỉ kiểm tra hai dịch vụ, không gọi `compose up`.

## 2. Tắt riêng server, giữ Docker và database chạy

```powershell
.\QuanLyMuaHang\CHAY_KHVT.cmd stop
```

Lệnh này chỉ dừng Java của ứng dụng được **CHAY_KHVT.cmd** tạo và theo dõi. Docker Desktop, container MySQL/Redis và volume dữ liệu vẫn giữ nguyên.

Kiểm tra server đang chạy hay đã dừng:

```powershell
.\QuanLyMuaHang\CHAY_KHVT.cmd status
```

Java chạy nền; đóng cửa sổ launcher không phải lệnh tắt server. Nếu backend chạy bằng **F5 trong VS Code**, bấm **Stop** ở phiên debug đó. Nếu chạy bằng `mvn spring-boot:run` trong terminal, dùng **Ctrl+C** tại terminal đó. Launcher không tự dừng backend của IDE hoặc launcher khác.

## 3. Chạy test tự động

Các lệnh test cần JDK 21/Maven/npm đã cấu hình trong `PATH`; `JAVA_HOME` cần trỏ tới JDK 21 nếu Maven dùng nó.

Test backend:

```powershell
mvn.cmd -f .\QuanLyMuaHang\source\pom.xml test
```

Test frontend và kiểm tra build:

```powershell
npm.cmd --prefix .\QuanLyMuaHang\source\frontend test
npm.cmd --prefix .\QuanLyMuaHang\source\frontend run build
```

Backend test thông thường dùng H2/mock; test Redis và workbook thật là opt-in. Frontend `test` chạy Vitest, không phải browser E2E. Các lệnh này không chứng minh toàn bộ nghiệp vụ hoặc import MySQL đã qua. Đọc [README backend](source/README.md) và [README frontend](source/frontend/README.md) nếu cần các lượt kiểm thử bổ sung.

## 4. Chạy production với domain HTTPS

Trong `QuanLyMuaHang/source/.env`, đặt `APP_FRONTEND_URL` bằng origin HTTPS thực tế, ví dụ `https://mua-hang.example.com` (thay bằng domain của bạn). Giữ credentials riêng; không đưa mật khẩu vào file hướng dẫn này.

Dừng bản dev do launcher quản lý rồi build/chạy production:

```powershell
.\QuanLyMuaHang\CHAY_KHVT.cmd stop
.\QuanLyMuaHang\CHAY_KHVT.cmd start -Profile prod
```

Profile `prod` bật session cookie Secure. Cloudflare Tunnel cần trỏ tới **`http://127.0.0.1:8080`**; dùng **domain HTTPS** để đăng nhập production. Nếu đã có JAR chứa frontend, có thể thêm `-NoBuild` vào lệnh `start`.

Launcher không tự tạo Tunnel/domain hoặc tự chạy sau reboot. Hướng dẫn chuẩn bị và chuyển máy: [Chạy Windows và Cloudflare](docs/CHAY_WINDOWS_VA_CLOUDFLARE.md).

## 5. Nhập CSDL từ QUANLYMUAHANGKHVT.xlsx

File nguồn nằm ở **`KHVT/QUANLYMUAHANGKHVT.xlsx`**, cùng cấp với thư mục `QuanLyMuaHang`.

Thao tác trên giao diện:

1. Đăng nhập bằng tài khoản có quyền **`IMPORT_LEGACY`** hoặc wildcard **`*`**. Vai trò ADMIN mặc định có quyền này; PLANNER mặc định chỉ nhập yêu cầu mua/báo giá.
2. Tại cổng ứng dụng, chọn **Mua hàng**.
3. Trong menu, chọn **Nhập dữ liệu**; có thể mở trực tiếp **http://localhost:8080/imports** khi đang chạy local.
4. Chọn tab **Workbook dữ liệu cũ**.
5. Chọn file **QUANLYMUAHANGKHVT.xlsx**, bấm **Preview tệp**.
6. Đối chiếu số dòng từng sheet, số nhóm PO, lỗi và cảnh báo. Workbook cần các sheet `NCC`, `LICH_SU`, `DON_HANG`; sheet `CONFIG` được bỏ qua. Giao diện hiện chỉ hiển thị chi tiết tối đa 30 dòng cảnh báo/lỗi.
7. Sau khi đã đối chiếu, dùng database thử hoặc có bản sao lưu phù hợp: tích ô xác nhận, bấm **Commit dữ liệu cũ**, rồi **Commit dữ liệu** trong hộp xác nhận.

Nếu thấy **“Kiểm tra 850 dòng”** và nút chuyển sang lập đơn bị khóa, bạn đang ở tab **Yêu cầu mua / báo giá**. Tab này chuyển tối đa 200 dòng mỗi lượt sang lập đơn. Với workbook lịch sử nhiều PO, chọn **Workbook dữ liệu cũ**; 850 dòng DON_HANG của file hiện tại thuộc **262 PO**, không phải một đơn 850 dòng.

**Preview** lưu lô nhập, dòng staging và audit; chưa ghi NCC/vật tư/lịch sử/PO. **Commit** ghi dữ liệu nghiệp vụ vào database backend đang kết nối. Tab workbook chỉ hiện khi tài khoản có quyền tương ứng; đăng nhập lại sau khi admin thay đổi quyền.

Ngày **2026-10-10**, đã sao lưu rồi import file này qua MCP vào MySQL local: lô 1 **COMMITTED**, 10.561 lịch sử, 262 PO, 850 dòng PO, 0 lỗi và 4.803 dòng cảnh báo. Preview lại nhận diện tệp đã nhập, không tạo thêm dữ liệu. Có thể xem dữ liệu tại **Đơn mua hàng**, **Tra cứu giá** và **Danh mục**; không cần commit lại. Vị trí backup và bằng chứng chi tiết ở [hệ thống hiện tại](docs/HE_THONG_HIEN_TAI.md#import-workbook-thật-qua-mcp-vào-mysql).

**`-Profile dev` không tự tạo database thử.** Kiểm tra kết nối database trong `.env` riêng trước khi commit, tránh thử import trên dữ liệu cần giữ. Chỉ preview khi chưa đáp ứng điều kiện backup/đối chiếu; không xóa volume để nhập lại.

## 6. Nếu tự bật MySQL/Redis từ thư mục KHVT

Không chạy `docker compose up` tại `KHVT` mà thiếu đường dẫn file. Dùng:

```powershell
docker compose --env-file .\QuanLyMuaHang\source\.env -f .\QuanLyMuaHang\source\docker-compose.yml up -d mysql redis
docker compose --env-file .\QuanLyMuaHang\source\.env -f .\QuanLyMuaHang\source\docker-compose.yml ps
```

Muốn tắt **chỉ ứng dụng**, dùng `CHAY_KHVT.cmd stop` ở mục 2. Không dùng `docker compose down -v`: lệnh đó xóa volume dữ liệu.

Nếu terminal đã chuyển vào **`KHVT/QuanLyMuaHang`**, bỏ tiền tố `QuanLyMuaHang\` ở các lệnh trên; ví dụ `.\CHAY_KHVT.cmd stop`.
