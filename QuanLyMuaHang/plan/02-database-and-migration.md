# MySQL schema, Flyway và migration workbook

## Quyết định chung

- MySQL là source of truth; Redis chỉ lưu Spring Session và dữ liệu cache có thể tái tạo.
- Dùng Flyway versioned migration; production không sửa schema thủ công, Hibernate đặt validate.
- Migration đã chạy ở bất kỳ môi trường chia sẻ nào được xem là immutable; thêm V2/V3... để đổi schema, không sửa V1__init.sql đã áp dụng. Trước khi chọn version phải kiểm tra flyway_schema_history của source DB.
- Tên bảng/cột snake_case; timestamp UTC, timezone nghiệp vụ Asia/Ho_Chi_Minh.
- PK BIGINT auto-increment cho CRUD và import; PO number là business key unique.
- Tiền DECIMAL(20,4), quantity DECIMAL(20,6); không dùng float/double.
- JPA persistence entities/mappers nằm trong infrastructure context, không rò ra domain/API.

## Identity và phân quyền

### user_accounts

Trường tối thiểu: id, email unique/case-normalized, display_name, password_hash, employee_id nullable unique, status ACTIVE/LOCKED/DISABLED, must_change_password, failed_login_count, locked_until, last_login_at, created_at, updated_at, version.

- Employee_id nullable để tài khoản bootstrap ADMIN không bắt buộc có hồ sơ nhân viên.
- Một employee tối đa một tài khoản; account không bị xóa cứng.
- Không lưu password plaintext, access/refresh token hoặc session id trong MySQL.

### roles, permissions, user_roles, role_permissions

- Role code hệ thống: ADMIN, HR_MANAGER, PLANNER, VIEWER.
- Permission code: USER_READ, USER_MANAGE, ROLE_MANAGE, PERSONNEL_READ, PERSONNEL_MANAGE, CATALOG_READ, CATALOG_MANAGE, PRICE_READ, PO_READ, PO_CREATE, PO_EDIT, PO_CANCEL, IMPORT_LEGACY, IMPORT_OPERATIONAL, AUDIT_READ.
- Quan hệ nhiều-nhiều cho user-role và role-permission, unique composite keys.
- ADMIN được gán wildcard permission *; không có shortcut bỏ qua authorization/audit.
- Các role nền được seed idempotent; thay đổi assignment/permission ghi audit.

### personnel tables

- employees: id, employee_code unique, full_name, email nullable/unique khi có, phone nullable, department_id, position_id, manager_id nullable, status ACTIVE/INACTIVE, joined_at, left_at, timestamps, version.
- departments: id, code nullable/unique khi có, name, parent_id nullable, active, timestamps.
- positions: id, code nullable/unique khi có, name, active, timestamps.
- Deactivate thay vì delete; account liên kết employee inactive phải bị vô hiệu hóa/revoke session trong cùng use case.
- Employee không chứa role, password hoặc thông tin lương/chấm công.

## Procurement và legacy history

### suppliers và supplier_aliases

Supplier có code nullable, name, normalized_name, address snapshot/master, tax_code/phone/email tùy dữ liệu được cung cấp, active/timestamps. Alias có normalized_alias và supplier_id.

### materials và material_aliases

Material có code nullable, name, normalized_name, category MATERIAL/SERVICE/OTHER, default_unit, active/timestamps. Alias có normalized_alias/material_id. Không ép liên kết nếu workbook không có mã chắc chắn.

### historical_purchases

Trường: id, purchase_date nullable, supplier_id nullable, supplier_snapshot, supplier_code_snapshot, material_id nullable, material_code_snapshot, material_name_snapshot, material_name_normalized_snapshot, category, unit, quantity nullable, quantity_text nullable, unit_price DECIMAL, currency, currency_basis SOURCE/ASSUMED_LEGACY, source, source_sheet, source_row_number, import_batch_id, data_quality_flags.

- purchase_date nullable để giữ hai lịch sử thiếu ngày mà không bịa ngày; record không ngày không tham gia truy vấn “giá gần nhất”.
- Supplier/material link nullable; snapshot gốc luôn giữ.
- LICH_SU không có currency nên import VND với basis ASSUMED_LEGACY.
- LoaiTien rỗng trong DON_HANG được gán VND và gắn warning ASSUMED_VND; USD/VND có filter riêng.

### purchase_orders, purchase_order_items, purchase_order_revisions

purchase_orders: id, po_number unique, order_date, supplier_id nullable, supplier_name_snapshot, supplier_address_snapshot, currency, currency_basis, vat_percent nullable, note, prepared_by, status, revision, created_by/updated_by, timestamps, version.

purchase_order_items: id, order_id, line_no, material_id nullable, material_code_snapshot, material_name_snapshot, specification, unit, quantity nullable, quantity_text nullable, unit_price, timestamps.

purchase_order_revisions: order_id, revision, immutable header/line snapshot, changed_by, change_reason, created_at. Giữ lịch sử sửa; không ghi đè PDF/revision cũ.

VAT null chỉ dùng cho legacy record bị thiếu VAT; PO mới bắt buộc chọn 0/5/8/10. Currency là một loại cho mỗi PO.

### po_daily_sequences

- sequence_date primary key, last_issued_number.
- Transaction locking/atomic increment; unique po_number là lớp bảo vệ cuối.
- Khi legacy import, parse số PO đúng định dạng để khởi tạo sequence theo từng ngày nếu cần; không tin PO_SEQ từ CONFIG.
- Số mới dùng padStart ít nhất hai chữ số, không cắt số khi sequence vượt 99.

## Import, file và audit

- import_batches: id, file_name, sha256, mode LEGACY_WORKBOOK/OPERATIONAL_REQUEST, status, counts, created_by, timestamps.
- import_batch_rows: batch_id, sheet, row_number, raw_data_json, normalized_json, status, warnings/errors.
- generated_files: id, entity type/id, revision, file_type, storage_key, name, sha256, created_at.
- audit_logs: actor_id, action, entity_type/id, before_json, after_json, request_id, result, created_at. Exclude password/hash/session secrets.
- Bản gốc workbook/PDF lưu ngoài DB trong vùng private nếu cần; không lưu BLOB.
- Spring Session Redis không tạo auth_sessions SQL table bắt buộc. Session expiry/revocation dùng Redis repository.

## Mapping workbook

| Sheet/cột | Đích |
|---|---|
| LICH_SU: Ngay | historical_purchases.purchase_date |
| LICH_SU: MaNCC/NhaCungCap | supplier_code_snapshot/supplier_snapshot; supplier_id có thể null |
| LICH_SU: MaHang/TenHang | material_code_snapshot/material_name_snapshot |
| LICH_SU: DVT/SoLuong/DonGia | unit, quantity hoặc quantity_text, unit_price |
| DON_HANG: SoPO/Ngay | một purchase_order cho mỗi SoPO, giữ nguyên chuỗi/ngày |
| DON_HANG: MaNCC/NhaCungCap/DiaChi | supplier reference nullable + header snapshots |
| DON_HANG: MaHang/TenHang/QuyCach/DVT/SoLuong/DonGia | purchase_order_items |
| DON_HANG: VAT/LoaiTien/GhiChu/NguoiLap | header; null/missing được ghi warning, không đoán VAT |
| NCC: MaNCC/TenNCC/DiaChi | supplier master staging; match exact code/name, xung đột cần review |
| CONFIG | bỏ ACCESS_CODE; bỏ qua PO_SEQ như dữ liệu nghiệp vụ và dựng lại sequence từ PO hợp lệ |

## Index và integrity

- Unique case-insensitive email; unique employee_code.
- Unique (user_id, role_id), (role_id, permission_id), (order_id, line_no), (order_id, revision).
- Index historical_purchases(material_id, currency, purchase_date), historical_purchases(material_name_normalized_snapshot, currency, purchase_date).
- Index orders(order_date, supplier_id), users(status), employees(department_id, status), audit_logs(actor_id, created_at).
- FK dùng RESTRICT/soft deactivate thay vì cascade delete nghiệp vụ.

## Flyway phase outline

- V1: supplier/material/history/PO/import/audit base.
- V2: identity roles/permissions/user_accounts.
- V3: personnel departments/positions/employees + account link.
- V4+: indices, revisions, import staging and adjustments.
- Thứ tự migration thực tế phải được chốt theo schema starter hiện có; mỗi migration chỉ thêm/sửa có kiểm thử.

