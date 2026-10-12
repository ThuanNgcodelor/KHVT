# Hướng dẫn cho AI làm việc với QuanLyMuaHang

## Đọc trước khi làm việc

1. Đọc [nguyên tắc trung thực](docs/NGUYEN_TAC_TRUNG_THUC.md) và tuân thủ khi trả lời, lập kế hoạch, sửa code hoặc báo cáo kết quả.
2. Đọc [hệ thống hiện tại](docs/HE_THONG_HIEN_TAI.md) để biết cấu trúc, phần đã triển khai và giới hạn kiểm thử.
3. Đọc `source/README.md` khi chạy backend; đọc [chạy Windows và Cloudflare](docs/CHAY_WINDOWS_VA_CLOUDFLARE.md) khi dùng launcher, đóng gói giao diện hoặc chuẩn bị domain HTTPS. Đọc các tài liệu liên quan trong `plan/` khi thay đổi nghiệp vụ.

`plan/` mô tả thiết kế và tiêu chí mong muốn. Code hiện tại và kết quả kiểm tra thực tế mới xác nhận phần đã làm. Không tự đánh dấu nghiệm thu chỉ vì đã có controller, file hoặc test chạy qua.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Bản đồ nhanh

- `source/`: ứng dụng chính, backend Spring Boot Java 21; `source/pom.xml` là điểm build.
- `CHAY_KHVT.cmd` → `tools/run-windows.ps1`: điểm chạy Windows chính, đóng frontend vào JAR và chạy Java phục vụ UI/API cùng cổng 8080; Docker Desktop mở riêng. `tools/start-local.ps1` vẫn là launcher dev Java/Vite.
- `source/frontend/`: React/TypeScript/Vite/Tailwind; auth/CSRF, cổng ứng dụng, dashboard, nhân sự/tài khoản, danh mục/giá/PO/import theo pages/hooks/components. Đọc `source/frontend/README.md` để biết phần UI đã có và giới hạn kiểm thử.
- `source/src/main/resources/db/migration/`: schema MySQL do Flyway quản lý.
- `source/src/test/`: test backend. Kết quả build và runtime local có thể nằm trong `source/target/runtime/`; thư mục `target/` là đầu ra sinh ra, không phải source.
- `plan/`: phạm vi, nghiệp vụ, thiết kế UI, triển khai và acceptance.
- Thư mục `backend(Demo để lấy phần login)/` đã xóa theo yêu cầu người dùng. Không thêm lại phụ thuộc vào dự án demo.

## Cách sửa backend

- Một ứng dụng Spring Boot; không tự tách microservice hoặc thêm các tầng chỉ để khớp sơ đồ.
- Các module nghiệp vụ hiện có: `identity`, `personnel`, `catalog`, `pricing`, `procurement`, `importing`, `exporting`, `dashboard`; phần dùng chung nằm ở `sharedkernel` và `config`.
- Code đang pha trộn module nghiệp vụ với các package dùng chung `domain`, `repository`, `service`, `web`. Những package này vẫn chứa code đang được dùng, không phải toàn bộ là code thừa.
- Luồng chạy phổ biến là controller trong `web` -> service trong `application` -> repository JPA -> MySQL. Một số domain model/port ở Identity và Personnel chưa được nối vào luồng này.
- Sửa theo luồng thực tế. Nếu thay đổi cấu trúc, thực hiện từng module, cập nhật imports và test; không di chuyển toàn bộ code cùng lúc khi chỉ sửa một tính năng.
- DTO trả ra API không chứa mật khẩu/hash. Quyền phải được kiểm tra ở backend.
- Flyway quản lý schema. Không sửa migration đã áp dụng thành công trên database đang dùng; bổ sung migration mới. Khi migration thất bại, kiểm tra trạng thái và dữ liệu trước khi sửa hoặc phục hồi.
- Chạy `mvn test` hoặc `mvn package` trong `source/` bằng JDK 21 và Maven 3.9+. POM có cấu hình Mockito agent cho test. Test context thường dùng H2/servlet session, không coi đó là kiểm chứng MySQL/Redis thật. `RedisSessionRevocationIntegrationTest` chỉ chạy khi `QMH_RUN_REDIS_TESTS=true`, dùng biến `QMH_TEST_REDIS_*` và namespace UUID riêng; không flush Redis.
- Profile Maven `windows-web` đưa `frontend/dist` vào static resources của JAR; launcher chạy `npm ci`, `npm run build`, rồi `mvn -Pwindows-web -DskipTests package`. Bước này bỏ qua test, không báo nghiệm thu từ build/start. Chỉ các URL UI/assets GET được phục vụ công khai; session/CSRF/permission của `/api` vẫn phải được kiểm tra.

## Cách sửa frontend

- Đọc `plan/05-react-vite-tailwind-ui.md` và `plan/12-react-vite-playwright-openai.md` trước khi sửa UI.
- Đọc và áp dụng [skill giao diện KHVT](skills/khvt-ui/SKILL.md) khi sửa frontend. Đây là skill riêng của repository, được tạo theo yêu cầu người dùng, không phải skill chính thức từ OpenAI. Người dùng đã yêu cầu bỏ màu cam; dùng palette xanh rêu/xanh ngọc của skill trừ khi có chỉ dẫn mới.
- Khi sửa Mua hàng, đọc [plan 17](plan/17-mua-hang-gon-tra-ncc-va-mau-po.md): yêu cầu 12/10/2026 giữ navbar, bảng PCVT gọn/giá-NCC cũ cùng dòng, phần nguồn cuối và bỏ input SL chữ; PDF/Excel đơn gửi NCC 6 cột không thành tiền/tổng, tổng nội bộ giữ. Đây là kế hoạch chưa triển khai; ưu tiên yêu cầu mới này trên mẫu/tổng PDF ở plan cũ, giữ artifact revision và raw nguồn.
- Base API nằm ở `source/frontend/src/config/baseApi.ts`; request đi qua `services/apiClient.ts`. Không hard-code host trong component.
- Auth dùng session cookie HttpOnly và CSRF, không lưu token phiên trong localStorage. Sau login cần lấy lại CSRF token; tài khoản có mật khẩu tạm phải đổi mật khẩu.
- Client có auth/CSRF, dashboard, nhân sự/tài khoản, vật tư/NCC, giá, giỏ/PO/revisions/PDF/XLSX và import preview/commit. Role/permission/audit UI chưa có. Browser tests và MCP UI smoke dùng API giả lập; test API/database thật chạy riêng. Đọc `docs/DOI_CHIEU_UNG_DUNG_CU.md` khi sửa luồng từ Index.html/Mã.js.
- Phát hành PO dùng POST `/{id}/issue` có quyền ghi và CSRF. GET PDF chỉ đọc artifact đã có; không cho người chỉ đọc phát hành/ghi giá. Khi sửa dòng hàng, flush orphan deletes trước khi gắn dòng thay thế để tránh trùng `(purchase_order_id,line_no)`; thao tác sửa/hủy/phát hành khóa bản ghi PO trong transaction.
- Legacy commit khóa bản ghi lô trong transaction trước khi kiểm tra status; không bỏ khóa làm hai request cùng lô nhập trùng. Preview trùng checksum phải giữ số nhóm PO/cảnh báo từ staging, kể cả sau commit; không trả số 0 giả.
- PCVT/báo giá mới dùng `/api/imports/documents`, V7 và `source_document_imports`, tách khỏi workbook legacy. Sổ CVT nguồn vẫn PREVIEW để chọn nhiều phiếu; confirm tạo child theo `scope_key` và `source_document_id`, khóa nguồn và chống trùng. Không biến báo giá/PCVT thành lịch sử mua hoặc PO. Giữ file private/checksum, parsed/raw và snapshot xác nhận; số decimal JSON là chuỗi. Tra/gom/so sánh cần `IMPORT_OPERATIONAL` + `PRICE_READ`; giá so sánh phải có cơ sở tương thích, giữ các nhóm khác VAT/tiền tệ để không mất giá phù hợp. Chưa OCR/alias duyệt/phân bổ PO. Đọc `docs/HUONG_DAN_IMPORT_PCVT_VA_BAO_GIA.md` và `docs/NGHIEM_THU_IMPORT_PCVT_VA_BAO_GIA.md` khi sửa luồng này.
- Browser MCP ở `tools/browser-mcp/`, hướng dẫn kết nối tại README cùng thư mục; cấu hình VS Code trong `.vscode/mcp.json`. Test thật tạo schema/namespace UUID riêng ở `tools/local-test/run.mjs`; không log bí mật. Workbook H2 opt-in là kiểm tra bổ sung, không thay kiểm chứng MySQL/Flyway/Redis.
- `tools/local-test/document-run.mjs` kiểm thử sáu chứng từ thật trên schema/namespace UUID và cổng 8082, có MCP/UI thật khi `QMH_DOCUMENT_BROWSER=true`; whitelist MCP chỉ thêm origin loopback 8082. Browser fixtures vẫn kiểm tra UI riêng, không thay test file thật. Lượt cuối 2026-10-11 đạt 14 nhóm với `completed: true`, main giữ V6/số bản ghi; không tự khởi động main để chứng minh nghiệm thu.
- Sau login/đổi mật khẩu tạm, vào `/modules`. Danh sách ứng dụng và permission hiệu lực do backend trả trong thông tin phiên; frontend kiểm tra `modules` và `permissions`, không suy ra quyền chỉ từ tên role. Admin hiện cấp ứng dụng qua vai trò; chưa có grant độc lập hoặc xin/duyệt quyền. Đọc [cổng ứng dụng và phân quyền](docs/CONG_UNG_DUNG_VA_PHAN_QUYEN.md) trước khi thêm module hoặc thay mô hình quyền.
- Phase 1 gom danh bạ/phòng ban/chức vụ vào `ADMINISTRATION`, không còn module UI `PERSONNEL`; giữ module backend `personnel` và dữ liệu. Người có `USER_READ` vào tài khoản, chỉ có `PERSONNEL_READ` vào danh bạ; không tự thêm quyền tài khoản cho `HR_MANAGER`. `/api/admin/users` hỗ trợ `query`, `status`, `departmentId` và paging; `/options`, `/employees` trả danh mục/hồ sơ tối thiểu cho form. `employeeProfile` tùy chọn được lưu cùng transaction tài khoản/audit; hồ sơ mới trống không sinh bản ghi, hồ sơ hiện có trống xóa phòng ban/chức vụ và giữ mã. Giữ danh mục ngừng nếu đúng ID đang liên kết, chặn gán mới. Backend kiểm tra role/target không vượt actor cả tạo/sửa/reset; `assignable` ở UI chỉ phản ánh kiểm tra server. Chưa grant/deny, remember-me hoặc phiên bản quyền chống race login/đổi quyền. Xem `docs/HUONG_DAN_QUAN_TRI_PHASE_1.md`; driver thật `tools/local-test/admin-phase1.mjs` dùng schema/namespace UUID, 8082 và tự dừng helper.
- Tách page/component theo tính năng khi triển khai; không tiếp tục dồn toàn bộ tính năng vào `App.tsx`.
- Nhãn trạng thái API, KPI và bảng phải phản ánh dữ liệu thật hoặc ghi rõ dữ liệu mẫu. Có loading, empty, error, 401/403 và trạng thái gửi form; kiểm tra keyboard và màn hình nhỏ.
- Chạy `npm.cmd ci`, `npm.cmd run build`, `npm.cmd run dev` trong `source/frontend/` trên PowerShell; dùng `npm` trên shell phù hợp. Kiểm tra scripts và các test thực sự có trước khi tuyên bố đã chạy E2E.
- Bản đóng gói dùng UI và `/api` cùng origin 8080; dev dùng Vite 5173 hoặc VS Code backend. Không đổi base API component để phục vụ từng chế độ. Production dùng `-Profile prod`, `APP_FRONTEND_URL` là origin HTTPS thực tế và cookie Secure; Cloudflare trỏ `http://127.0.0.1:8080`.

## Dữ liệu và vận hành

- Không hiển thị, commit hay đưa nội dung `.env`, mật khẩu, hash, session cookie hoặc CSRF token vào chat, tài liệu, log/audit. Chỉ đọc bí mật khi tác vụ cần.
- MySQL/Redis local dùng Compose project `qmh-local`, cổng loopback 3307/6380. Compose hiện chỉ chứa hai dịch vụ này.
- Từ root, lệnh Compose cần `--env-file .\source\.env -f .\source\docker-compose.yml`; root không có file Compose. Cấu hình hiện do người dùng chỉnh: MySQL 1536 MiB/2 CPU (buffer pool 512 MiB, tối đa 80 kết nối), Redis 256 MiB/1 CPU (`maxmemory` 128 MiB, `noeviction`). Giữ volume/AOF, không giảm RAM bằng cách xóa dữ liệu. Java launcher mặc định heap 256 MB; heap khác RSS và không đổi Java của VS Code.
- Session dùng Redis indexed repository với namespace `qmh:session:indexed` để thu hồi theo tài khoản. Cấu hình namespace cũ đã đổi; cookie cũ cần đăng nhập lại, keys cũ để hết TTL. Không chuyển về repository mặc định khi service cần `FindByIndexNameSessionRepository`.
- Không xóa volume hoặc reset database có dữ liệu để giải quyết lỗi khởi động. Thao tác phá hủy cần phạm vi rõ ràng, kiểm tra dữ liệu và bản sao lưu.
- Không truy cập database hoặc secrets của dự án demo để chạy ứng dụng chính.
- Legacy workbook: preview -> kiểm tra số dòng/cảnh báo -> commit khi đã có quyền và điều kiện dữ liệu phù hợp. Không tự commit workbook thật chỉ để chứng minh API hoạt động.
- Cloudflare Tunnel/domain chưa được triển khai trong source. Launcher chưa đăng ký Windows Service hay tự chạy sau reboot; không báo đã có production deployment nếu chưa kiểm tra. Hướng dẫn chuẩn bị nằm ở `docs/CHAY_WINDOWS_VA_CLOUDFLARE.md`.

## Báo cáo và cập nhật tài liệu

- Phân biệt: có code, build qua, đã test từng API, đã kiểm thử luồng nghiệp vụ, đã nghiệm thu.
- Nêu lệnh đã chạy, kết quả, thời điểm của log cũ và phần chưa kiểm chứng. Không suy ra tỷ lệ hoàn thành từ số file hoặc số test.
- Khi thay đổi cấu trúc hoặc hoàn thành tính năng, cập nhật `docs/HE_THONG_HIEN_TAI.md` và README liên quan theo bằng chứng mới.
- Tuân thủ toàn bộ [NGUYEN_TAC_TRUNG_THUC.md](docs/NGUYEN_TAC_TRUNG_THUC.md); thiếu bằng chứng thì ghi rõ, không bổ sung số liệu hoặc nguồn tưởng tượng.
