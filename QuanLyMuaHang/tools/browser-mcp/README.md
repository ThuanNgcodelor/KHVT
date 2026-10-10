# MCP kiểm thử trình duyệt KHVT

`khvt-browser` dùng [Playwright MCP của Microsoft](https://github.com/microsoft/playwright-mcp), khóa phiên bản trong `package-lock.json`. Server chạy stdio, browser/profile riêng, headless và chỉ cho phép các origin localhost của dự án. Không dùng profile Chrome đang đăng nhập của người dùng. `start.mjs` lấy Chromium đã cài cho frontend để tránh tải thêm bản browser khác.

## Cài và kết nối

Từ thư mục repository, dùng Node đã cài:

```powershell
npm.cmd ci --prefix source/frontend
Push-Location source/frontend
npx.cmd playwright install chromium
Pop-Location
npm.cmd ci --prefix tools/browser-mcp
codex mcp add khvt-browser -- node.exe "$((Resolve-Path tools/browser-mcp/start.mjs).Path)"
codex mcp list
```

Máy hiện tại đã đăng ký server trong cấu hình Codex. Mở phiên Codex mới để nạp danh sách tool mới. VS Code cũng có cấu hình repository tại `.vscode/mcp.json`; khởi động server từ mục MCP của VS Code nếu dùng client đó. Xem [hướng dẫn MCP của Codex](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

Không ghi mật khẩu, session cookie hoặc CSRF vào prompt, trace hay bản chụp có dữ liệu nhạy cảm. MCP có tool thực thi Playwright code; chỉ chạy mã kiểm thử được kiểm soát. Bộ kiểm thử bên dưới tự tạo context riêng và không ghi nhật ký tham số/kết quả chứa bí mật.

## Tự kiểm tra MCP và UI

```powershell
npm.cmd run build --prefix source/frontend
npm.cmd run test:ui --prefix tools/browser-mcp
```

`test:ui` cần Node hỗ trợ chạy TypeScript trực tiếp (đã chạy trên Node 24 của máy hiện tại). Nó chạy server tĩnh cổng 5190, nối thật tới MCP qua stdio, kiểm tra initialize/tools/list, mở cổng ứng dụng, các trang mua hàng, lưu bản nháp/phát hành và viewport mobile. **API là fixture tổng hợp**, không phải MySQL/Redis. Server và browser tự đóng sau test. Kết quả/ảnh ở `source/target/runtime/`.

`npm.cmd run smoke --prefix tools/browser-mcp` mở login ở cổng 5173, cần frontend đang chạy. Đây là kiểm tra kết nối cơ bản. Bộ Playwright frontend (`npm.cmd run test:e2e --prefix source/frontend`) và bộ API/database thật trong `tools/local-test/` là các lớp kiểm tra khác; không cộng chúng thành nghiệm thu toàn bộ hệ thống.
