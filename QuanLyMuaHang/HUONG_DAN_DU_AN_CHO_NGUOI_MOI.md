# Hướng dẫn dự án Quản lý mua hàng KHVT cho người mới

> Tài liệu này dành cho người chưa biết hoặc mới làm quen với lập trình. Mục tiêu là giúp bạn hiểu dự án dùng để làm gì, thư mục nào có ý nghĩa gì, cách chạy hệ thống và cần tìm ở đâu khi có lỗi.

## 1. Dự án này là gì?

Đây là một hệ thống web quản lý mua hàng. Người dùng mở hệ thống bằng trình duyệt, đăng nhập và sử dụng các chức năng được cấp quyền.

Các nhóm chức năng đang có trên giao diện gồm:

- **Mua hàng:** tạo và theo dõi đơn mua hàng (PO), xem tổng quan, tra cứu giá và nhập dữ liệu.
- **Danh mục:** quản lý vật tư, nhà cung cấp và xem lịch sử mua từ nhà cung cấp.
- **Quản trị:** quản lý tài khoản, danh bạ nhân viên, phòng ban và chức vụ.
- **Chứng từ và dữ liệu:** nhập workbook Excel cũ; nhập, xem trước và xác nhận PCVT/báo giá; xuất một số dữ liệu ra PDF hoặc Excel.

Không phải tài khoản nào cũng thấy tất cả các mục. Hệ thống hiển thị chức năng dựa trên ứng dụng và quyền mà quản trị viên đã cấp.

## 2. Hệ thống gồm những phần nào?

Có thể hình dung hệ thống giống một cửa hàng:

| Thành phần | Ví dụ dễ hiểu | Nằm ở đâu trong dự án? |
|---|---|---|
| **Frontend** | Quầy giao dịch mà người dùng nhìn thấy và bấm vào | `source/frontend/` |
| **Backend** | Nhân viên phía sau tiếp nhận yêu cầu, kiểm tra quyền và xử lý nghiệp vụ | `source/src/main/java/` |
| **Flyway** | Người quản lý mẫu tủ hồ sơ, tự cập nhật cấu trúc database theo phiên bản | `source/src/main/resources/db/migration/` |
| **File storage** | Kho file đã tải lên hoặc file do hệ thống tạo | Đường dẫn do `FILE_STORAGE_ROOT` trong `.env` quy định |

Luồng đơn giản khi người dùng bấm một nút:

```text
Trình duyệt
   ↓
Frontend React (màn hình)
   ↓ gọi API
Backend Spring Boot (kiểm tra đăng nhập, quyền và nghiệp vụ)
   ↓
MySQL / Redis / kho file
   ↓
Kết quả quay lại màn hình
```

Khi chạy bản đóng gói thông thường, frontend và backend cùng được phục vụ tại `http://localhost:8080`. Khi lập trình giao diện, Vite có thể chạy frontend riêng tại `http://localhost:5173`, còn backend vẫn ở cổng `8080`.

## 3. Bản đồ thư mục và file quan trọng

```text
QuanLyMuaHang/
├── CHAY_KHVT.cmd                 Chạy nhanh trên Windows
├── CHAY_KHVT.sh                  Chạy nhanh trên Linux
├── Build.md                      Các lệnh chạy, dừng và kiểm thử
├── HUONG_DAN_DU_AN_CHO_NGUOI_MOI.md  File bạn đang đọc
├── docs/                         Tài liệu vận hành chi tiết
├── plan/                         Kế hoạch và yêu cầu nghiệp vụ
├── tools/                        Các script hỗ trợ chạy/kiểm thử
└── source/                       Mã nguồn chính
    ├── .env                      Cấu hình riêng và bí mật của máy (không chia sẻ)
    ├── .env.example              Mẫu để tạo file .env
    ├── pom.xml                   Danh sách thư viện và cách build backend
    ├── frontend/                 Toàn bộ frontend React/TypeScript
    ├── src/main/java/            Mã backend Java
    ├── src/main/resources/       Cấu hình ứng dụng và migration database
    ├── src/test/                 Kiểm thử backend
    └── target/                   Kết quả build và log được sinh tự động
```

Một số quy tắc dễ nhớ:

- Muốn sửa **màn hình, nút, bảng, màu sắc**: thường tìm trong `source/frontend/src/`.
- Muốn sửa **nghiệp vụ, phân quyền, API, đọc/ghi database**: thường tìm trong `source/src/main/java/`.
- Muốn thay đổi **cấu trúc database**: thêm migration mới trong `source/src/main/resources/db/migration/`; không sửa tùy tiện migration đã chạy trên database đang dùng.
- Không sửa file trong `source/target/` hoặc `source/frontend/dist/`, vì đó là kết quả được tạo lại khi build.
- `plan/` là kế hoạch mong muốn; mã nguồn đang chạy mới cho biết chức năng thực tế đã được làm đến đâu.

### Các khu vực chính của backend

| Tên thư mục | Phụ trách |
|---|---|
| `identity` | Đăng nhập, tài khoản, vai trò và phiên đăng nhập |
| `personnel` | Nhân viên, phòng ban và chức vụ |
| `catalog` | Danh mục vật tư và nhà cung cấp |
| `pricing` | Tra cứu giá |
| `procurement` | Đơn mua hàng (PO) và PDF |
| `importing` | Nhập Excel, PCVT và báo giá |
| `exporting` | Xuất Excel |
| `dashboard` | Dữ liệu tổng quan |
| `sharedkernel`, `config` | Thành phần dùng chung, bảo mật và cấu hình |

### Các khu vực chính của frontend

Trong `source/frontend/src/features/`, mỗi thư mục thường tương ứng một nhóm màn hình:

- `auth`: đăng nhập, đổi/quên/đặt lại mật khẩu.
- `portal`: màn hình chọn ứng dụng sau khi đăng nhập.
- `procurement`: lập và quản lý đơn mua hàng.
- `catalog`: vật tư và nhà cung cấp.
- `pricing`: tra cứu giá.
- `imports`: nhập dữ liệu.
- `identity-admin`: quản trị tài khoản.
- `personnel`: nhân viên, phòng ban và chức vụ.
- `dashboard`: trang tổng quan.

## 4. `.env` là gì?

`.env` là file cấu hình riêng cho từng máy. Nó giống một tờ giấy cài đặt cho biết ứng dụng phải kết nối database nào, dùng cổng nào, lưu file ở đâu và dùng tài khoản khởi tạo nào.

File thật nằm tại:

```text
source/.env
```

File mẫu nằm tại:

```text
source/.env.example
```

Nếu chưa có `.env`, sao chép file mẫu rồi sửa bản sao:

**Windows PowerShell:**

```powershell
Copy-Item .\source\.env.example .\source\.env
```

**Linux:**

```bash
cp source/.env.example source/.env
```

Các nhóm biến quan trọng:

| Biến | Ý nghĩa |
|---|---|
| `SPRING_DATASOURCE_URL` | Địa chỉ MySQL |
| `SPRING_DATASOURCE_USERNAME`, `DB_PASSWORD` | Tài khoản ứng dụng dùng để vào MySQL |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Cách kết nối Redis |
| `SERVER_PORT` | Cổng của ứng dụng, mặc định thường là `8080` |
| `APP_FRONTEND_URL` | Địa chỉ frontend được backend chấp nhận |
| `ADMIN_BOOTSTRAP_*` | Thông tin để tạo quản trị viên đầu tiên nếu tài khoản chưa tồn tại |
| `FILE_STORAGE_ROOT` | Thư mục lưu file nghiệp vụ |
| `APP_PDF_FONT_PATH` | Font Unicode dùng khi tạo PDF tiếng Việt |
| `MAIL_*` | Cấu hình gửi email quên mật khẩu |
| `APP_RESET_PASSWORD_URL` | Đường dẫn người dùng mở để đặt lại mật khẩu |

Lưu ý rất quan trọng:

- Không gửi nội dung `.env` qua chat, email hoặc ảnh chụp màn hình.
- Không đưa `.env` lên Git. Dự án chỉ nên lưu `.env.example` với giá trị mẫu.
- Mật khẩu trong `.env` phải được thay bằng mật khẩu riêng đủ mạnh.
- Đổi mật khẩu MySQL trong `.env` không tự đổi mật khẩu bên trong database đã được tạo trước đó.
- Không chép giá trị bí mật từ tài liệu hoặc máy khác nếu chưa hiểu mục đích.

## 5. Cách chạy dễ nhất trên Windows

### 5.1. Cài trước một lần

Máy cần có:

- JDK 21 (Java).
- Maven 3.9 trở lên.
- Node.js và npm.
- Laragon có MySQL và Redis.

Tạo `source/.env` từ file mẫu và điền cấu hình riêng như phần trên. Với Laragon mặc định, MySQL thường ở cổng `3306` và Redis ở cổng `6379`; cấu hình thực tế trên máy phải khớp với `.env`.

### 5.2. Bật MySQL và Redis trong Laragon

1. Mở **Laragon** trên Windows.
2. Nhấn **Start All**.
3. Kiểm tra MySQL đã ở trạng thái chạy.
4. Kiểm tra Redis đã ở trạng thái chạy. Tùy phiên bản Laragon, Redis có thể xuất hiện ngay trên cửa sổ chính hoặc trong **Menu → Redis**. Nếu Redis chưa được cài trong Laragon thì cần thêm Redis trước khi chạy dự án.
5. Giữ Laragon đang chạy trong lúc sử dụng hệ thống.

Cấu hình `.env` cần trỏ đến dịch vụ Laragon, ví dụ về địa chỉ và cổng:

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/quanlymuahang?useUnicode=true&characterEncoding=utf8
REDIS_HOST=localhost
REDIS_PORT=6379
```

Tên tài khoản, mật khẩu MySQL và mật khẩu Redis phải điền theo cấu hình thực tế trên máy. Không sao chép nguyên mật khẩu ví dụ và không gửi nội dung `.env` cho người khác.

### 5.3. Khởi động ứng dụng

Mở PowerShell tại thư mục `QuanLyMuaHang`, chạy:

```powershell
.\CHAY_KHVT.cmd start
```

Hoặc bấm đúp `CHAY_KHVT.cmd`. Launcher sẽ:

1. Đọc `source/.env`.
2. Kiểm tra MySQL và Redis của Laragon đang mở ở đúng cổng.
3. Cài/build frontend.
4. Build backend và gói frontend vào file JAR.
5. Chạy một tiến trình Java phục vụ cả giao diện và API.

Khi phát hiện đủ MySQL và Redis đang chạy cục bộ, launcher sẽ dùng trực tiếp hai dịch vụ của Laragon.

Sau khi thấy khởi động thành công, mở:

```text
http://localhost:8080
```

### 5.4. Xem trạng thái và dừng

```powershell
.\CHAY_KHVT.cmd status
.\CHAY_KHVT.cmd stop
```

Lệnh `stop` chỉ dừng ứng dụng Java do launcher quản lý; MySQL và Redis trong Laragon vẫn tiếp tục chạy. Khi không còn ứng dụng nào khác cần chúng, bạn có thể nhấn **Stop All** trong Laragon.

Nếu chắc chắn JAR hiện có là bản cần dùng và không muốn build lại:

```powershell
.\CHAY_KHVT.cmd start -NoBuild
```

`-NoBuild` không lấy thay đổi code mới. Khi vừa sửa code, hãy chạy lại không có tùy chọn này.

## 6. Chạy khi đang phát triển hoặc sửa code

Người chỉ sử dụng hệ thống không cần phần này.

### Chạy cả hệ thống ở chế độ phát triển trên Windows

Mở Laragon và nhấn **Start All** trước, sau đó chạy từ thư mục gốc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\start-local.ps1
```

Trong chế độ này:

- Frontend Vite thường ở `http://localhost:5173` và tự cập nhật khi sửa giao diện.
- Backend thường ở `http://localhost:8080`.
- Không chạy đồng thời launcher đóng gói, script dev và VS Code backend trên cùng cổng.

### Chạy backend thủ công

Từ `source/`, nạp biến môi trường và chạy:

```bash
set -a
. ./.env
set +a
mvn spring-boot:run
```

MySQL và Redis trong Laragon phải chạy trước và phải khớp với địa chỉ, cổng cùng thông tin đăng nhập trong `source/.env`.

### Chạy riêng frontend

Từ `source/frontend/`:

```bash
npm ci
npm run dev
```

Frontend vẫn cần backend để dùng dữ liệu thật. Việc chỉ thấy trang web mở được chưa chứng minh database hoặc toàn bộ nghiệp vụ đang hoạt động.

## 7. Cách sử dụng hệ thống

### Đăng nhập

1. Mở địa chỉ hệ thống.
2. Nhập tài khoản và mật khẩu do quản trị viên cấp.
3. Nếu là mật khẩu tạm, hệ thống sẽ yêu cầu đổi mật khẩu trước khi đi tiếp.
4. Sau khi đăng nhập, màn hình **Cổng ứng dụng** chỉ hiện các ứng dụng mà tài khoản được cấp.

### Mua hàng

- **Mua hàng:** bắt đầu lập một đơn mua hàng mới nếu có quyền tạo PO.
- **Đơn mua hàng:** xem danh sách, mở chi tiết, sửa hoặc thực hiện thao tác theo quyền.
- **Tra cứu giá:** tìm dữ liệu giá cũ để tham khảo.
- **Nhập dữ liệu:** nhập workbook cũ hoặc làm việc với PCVT/báo giá mới. Nên luôn xem phần preview và cảnh báo trước khi xác nhận.
- **Vật tư / Nhà cung cấp:** quản lý danh mục dùng khi lập đơn và tra cứu lịch sử.

### Quản trị

- **Tài khoản:** tạo/sửa tài khoản, cấp vai trò hoặc đặt lại mật khẩu trong phạm vi quyền được phép.
- **Danh bạ nhân viên:** quản lý thông tin nhân viên.
- **Phòng ban / Chức vụ:** quản lý danh mục tổ chức.

Nếu một mục không xuất hiện hoặc báo “không có quyền”, đây có thể là hành vi đúng theo phân quyền. Hãy liên hệ quản trị viên thay vì sửa frontend để bỏ kiểm tra quyền; backend vẫn kiểm tra quyền độc lập.

## 8. Dữ liệu được lưu ở đâu và cần sao lưu gì?

Ba nơi quan trọng:

1. **MySQL:** dữ liệu nghiệp vụ chính.
2. **`FILE_STORAGE_ROOT`:** file nguồn, PDF, Excel hoặc artifact nghiệp vụ.
3. **Redis:** chủ yếu lưu phiên đăng nhập; mất Redis thường khiến người dùng phải đăng nhập lại, nhưng Redis không thay thế backup MySQL.

Copy thư mục mã nguồn hoặc file JAR **không có nghĩa là đã copy database**. Khi chuyển máy, phải xuất và khôi phục database MySQL của Laragon, đồng thời sao lưu thư mục `FILE_STORAGE_ROOT` phù hợp cùng thời điểm. Không xóa thư mục dữ liệu MySQL của Laragon để chữa lỗi khởi động.

## 9. Kiểm thử dành cho người sửa code

### Backend

Từ `source/`:

```bash
mvn test
```

### Frontend

Từ `source/frontend/`:

```bash
npm test
npm run build
npm run test:e2e -- --workers=1
```

Ý nghĩa:

- `npm test`: chạy kiểm thử frontend bằng Vitest.
- `npm run build`: kiểm tra TypeScript và tạo bản frontend đóng gói.
- `npm run test:e2e`: chạy kiểm thử trình duyệt Playwright.
- `mvn test`: chạy kiểm thử backend thông thường.

Build thành công chỉ có nghĩa là mã đã đóng gói được. Nó chưa đồng nghĩa với việc toàn bộ nghiệp vụ, database thật, quyền người dùng và file thật đã được nghiệm thu.

## 10. Lỗi thường gặp

### Không mở được `localhost:8080`

- Chạy `CHAY_KHVT.cmd status` hoặc `./CHAY_KHVT.sh status`.
- Kiểm tra cổng `8080` có bị chương trình khác chiếm không.
- Xem log trong `source/target/runtime/`.

### MySQL hoặc Redis trong Laragon chưa sẵn sàng

- Mở Laragon và nhấn **Start All**.
- Kiểm tra MySQL đang nghe ở cổng ghi trong `SPRING_DATASOURCE_URL`, thường là `3306`.
- Kiểm tra Redis đang nghe ở `REDIS_PORT`, thường là `6379`.
- Nếu launcher báo chưa phát hiện đủ dịch vụ, hãy kiểm tra cả MySQL và Redis trong Laragon có đang chạy đúng cổng ghi trong `.env` hay không.

### Lỗi MySQL “Access denied” hoặc mã 1045

Thông tin trong `.env` không khớp tài khoản MySQL thực tế. Kiểm tra URL, username và password. Nếu database đã được tạo từ trước, chỉ sửa `.env` không tự đổi mật khẩu đã lưu trong MySQL.

### Sửa giao diện nhưng không thấy thay đổi

- Nếu dùng `-NoBuild`, dừng ứng dụng và chạy lại không có `-NoBuild`.
- Nếu đang phát triển bằng Vite, kiểm tra đúng địa chỉ `localhost:5173`.
- Đảm bảo không có hai bản backend/frontend khác nhau đang chạy đồng thời.

### Đăng nhập được nhưng không thấy chức năng

Kiểm tra ứng dụng, vai trò và quyền của tài khoản. Đây thường là vấn đề phân quyền, không phải lỗi hiển thị.

### PDF lỗi font tiếng Việt

Kiểm tra `APP_PDF_FONT_PATH` trong `.env` có trỏ tới file font Unicode `.ttf` thực sự tồn tại trên máy.

## 11. Thuật ngữ nhanh

| Thuật ngữ | Giải thích ngắn |
|---|---|
| API | Cách frontend gửi yêu cầu tới backend |
| Build | Biến mã nguồn thành bản có thể chạy/phát hành |
| JAR | File đóng gói ứng dụng Java |
| PO | Purchase Order, đơn mua hàng |
| PCVT | Phiếu/yêu cầu cung cấp vật tư theo nghiệp vụ của dự án |
| Migration | File thay đổi cấu trúc database theo từng phiên bản |
| Session | Phiên đăng nhập của người dùng |
| CSRF | Cơ chế bảo vệ thao tác ghi khỏi yêu cầu giả mạo |
| Laragon | Bộ công cụ trên Windows dùng để chạy MySQL, Redis và các dịch vụ phát triển cục bộ |
| Cổng (port) | “Số cửa” để truy cập dịch vụ, ví dụ `8080` |
| Log | Nhật ký kỹ thuật giúp tìm nguyên nhân lỗi |

---

**Tóm tắt một câu:** hãy tạo đúng `source/.env`, mở Laragon và nhấn **Start All**, chạy `CHAY_KHVT.cmd start`, rồi mở `http://localhost:8080`.
