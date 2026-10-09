/**
 * QUẢN LÝ MUA HÀNG - PHÒNG KẾ HOẠCH VẬT TƯ FHCT
 * Web App Apps Script - dữ liệu dùng chung cả phòng trên Google Sheet
 *
 * Cấu trúc Sheet (tạo tự động khi chạy hàm setup()):
 *  - LICH_SU : lịch sử mua nhúng ban đầu (import từ LICH_SU.csv) - Ngay | NhaCungCap | TenHang | DVT | SoLuong | DonGia
 *  - DON_HANG: giao dịch mới do web app ghi         - Ngay | SoPO | NhaCungCap | TenHang | DVT | SoLuong | DonGia | NguoiLap
 *  - NCC     : sổ địa chỉ nhà cung cấp               - TenNCC | DiaChi
 *  - CONFIG  : B1 = số thứ tự PO hiện tại
 */

const SHEET_HIST = 'LICH_SU';
const SHEET_PO = 'DON_HANG';
const SHEET_NCC = 'NCC';
const SHEET_CFG = 'CONFIG';
const PDF_FOLDER = 'Don hang KHVT'; // thư mục Drive lưu PDF đơn hàng

/** Chạy 1 lần sau khi tạo project để tạo các sheet còn thiếu */
function setup() {
  const ss = SpreadsheetApp.getActive();
  const need = {
    [SHEET_HIST]: ['Ngay', 'MaNCC', 'NhaCungCap', 'MaHang', 'TenHang', 'DVT', 'SoLuong', 'DonGia'],
    [SHEET_PO]: ['Ngay', 'SoPO', 'MaNCC', 'NhaCungCap', 'MaHang', 'TenHang', 'DVT', 'SoLuong', 'DonGia', 'NguoiLap', 'QuyCach', 'GhiChu', 'VAT', 'DiaChi', 'LoaiTien'],
    [SHEET_NCC]: ['MaNCC', 'TenNCC', 'DiaChi'],
    [SHEET_CFG]: ['PO_SEQ', 0]
  };
  Object.keys(need).forEach(name => {
    let sh = ss.getSheetByName(name);
    if (!sh) {
      sh = ss.insertSheet(name);
      sh.getRange(1, 1, 1, need[name].length).setValues([need[name]]);
      sh.getRange(1, 1, 1, need[name].length).setFontWeight('bold');
    }
  });
  // Mã truy cập app: đặt tại CONFIG!B2 — đổi mã thì nhân viên phải nhập lại
  const cfg = ss.getSheetByName(SHEET_CFG);
  if (!String(cfg.getRange('A2').getValue())) cfg.getRange('A2:B2').setValues([['ACCESS_CODE', 'KHVT@2026']]);
}

/** CHẠY 1 LẦN từ Apps Script (menu Run) để thêm ngay cột O "LoaiTien" vào DON_HANG hiện có */
function themCotLoaiTien() {
  const ss = SpreadsheetApp.getActive();
  ensureSchema_(ss);
  const shP = ss.getSheetByName(SHEET_PO);
  return 'Đã đảm bảo DON_HANG có cột O = LoaiTien. Cột O1 hiện là: "' + shP.getRange('O1').getValue() + '"';
}

/** Kiểm tra mã truy cập — sai thì chặn ngay từ server */
function checkCode_(code) {
  const real = String(SpreadsheetApp.getActive().getSheetByName(SHEET_CFG).getRange('B2').getValue()).trim();
  if (!real) return; // chưa đặt mã = mở tự do
  if (String(code || '').trim() !== real) throw new Error('Sai mã truy cập');
}

/* ===== Helper dùng chung ===== */
function norm_(s) {
  return String(s == null ? '' : s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/\s+/g, ' ').trim();
}
/** MaHang luôn trả về text; nếu ô bị Google Sheets tự đổi thành Date thì dựng lại "Thang.Nam" */
function maHangText_(v) {
  if (v instanceof Date) return (v.getMonth() + 1) + '.' + v.getFullYear();
  return String(v == null ? '' : v).trim();
}
/** SL có thể là SỐ hoặc CHỮ (VD "Qua cân thực tế") — số thì trả số, còn lại trả nguyên văn */
function qtyVal_(v) {
  if (typeof v === 'number') return v;
  var t = String(v == null ? '' : v).trim();
  if (t === '') return 0;
  if (/[^\d.,\s]/.test(t)) return t;                 // có chữ -> giữ nguyên
  var n = parseFloat(t.replace(/\./g, '').replace(',', '.'));
  return isNaN(n) ? t : n;
}
function todayYmd_() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyMMdd');
}
/** Bảo đảm DON_HANG có đủ 14 cột (di trú sheet cũ) + ép cột MaHang về dạng text */
function ensureSchema_(ss) {
  const shP = ss.getSheetByName(SHEET_PO);
  const hdr = ['Ngay', 'SoPO', 'MaNCC', 'NhaCungCap', 'MaHang', 'TenHang', 'DVT', 'SoLuong', 'DonGia', 'NguoiLap', 'QuyCach', 'GhiChu', 'VAT', 'DiaChi', 'LoaiTien'];
  const cur = shP.getRange(1, 1, 1, hdr.length).getValues()[0];
  if (String(cur[10]) !== 'QuyCach' || String(cur[13]) !== 'DiaChi' || String(cur[14]) !== 'LoaiTien') {
    shP.getRange(1, 1, 1, hdr.length).setValues([hdr]).setFontWeight('bold');
  }
  shP.getRange(1, 5, shP.getMaxRows(), 1).setNumberFormat('@'); // cột E MaHang = text
}
/** Số PO kế tiếp trong ngày: PO-yyMMdd-NN (NN tăng dần theo từng đơn tạo trong ngày) */
function poForDay_(ss) {
  const ymd = todayYmd_();
  const prefix = 'PO-' + ymd + '-';
  const shP = ss.getSheetByName(SHEET_PO);
  let max = 0;
  if (shP && shP.getLastRow() > 1) {
    shP.getRange(2, 2, shP.getLastRow() - 1, 1).getValues().forEach(r => {
      const s = String(r[0] || '');
      if (s.indexOf(prefix) === 0) { const n = parseInt(s.slice(prefix.length), 10); if (n > max) max = n; }
    });
  }
  return { prefix: prefix, next: max + 1 };
}

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Mua hàng - P.KHVT FHCT')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

/** Trả toàn bộ dữ liệu cho client: lịch sử + đơn đã xuất + sổ NCC + số PO kế tiếp */
function getAllData(code) {
  checkCode_(code);
  const ss = SpreadsheetApp.getActive();
  ensureSchema_(ss);
  const hist = [];
  const shH = ss.getSheetByName(SHEET_HIST);
  if (shH && shH.getLastRow() > 1) {
    shH.getRange(2, 1, shH.getLastRow() - 1, 8).getValues().forEach(r => {
      if (!r[4]) return;
      hist.push({ d: toISO(r[0]), mn: String(r[1] || ''), n: String(r[2]), mh: maHangText_(r[3]), i: String(r[4]), u: String(r[5]), q: qtyVal_(r[6]), p: +r[7] || 0 });
    });
  }
  const shP = ss.getSheetByName(SHEET_PO);
  if (shP && shP.getLastRow() > 1) {
    shP.getRange(2, 1, shP.getLastRow() - 1, 15).getValues().forEach(r => {
      if (!r[5]) return;
      hist.push({ d: toISO(r[0]), mn: String(r[2] || ''), n: String(r[3]), mh: maHangText_(r[4]), i: String(r[5]), u: String(r[6]), q: qtyVal_(r[7]), p: +r[8] || 0, src: 'app', po: String(r[1]), cur: String(r[14] || 'VND') });
    });
  }
  const ncc = {}, nccCode = {};
  const shN = ss.getSheetByName(SHEET_NCC);
  if (shN && shN.getLastRow() > 1) {
    shN.getRange(2, 1, shN.getLastRow() - 1, 3).getValues().forEach(r => {
      if (!r[1]) return;
      ncc[String(r[1])] = String(r[2] || '');
      if (r[0]) nccCode[String(r[1])] = String(r[0]);
    });
  }
  return { hist: hist, ncc: ncc, nccCode: nccCode, poNext: poSuggest_() };
}

function toISO(v) {
  if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  const s = String(v || '');
  return s.length >= 10 ? s.slice(0, 10) : s;
}

function poSuggest_() {
  const p = poForDay_(SpreadsheetApp.getActive());
  return p.prefix + ('0' + p.next).slice(-2);
}

/**
 * Xuất đơn hàng: tạo PDF lưu Drive + ghi giao dịch vào DON_HANG + lưu địa chỉ NCC
 * payload = { code, editPO, ghiChu, addr, vatPct, ncc, maNCC, nguoiLap, rows:[{name,spec,unit,qty,price,maHang}] }
 *  - editPO rỗng: cấp số PO mới theo ngày (PO-yyMMdd-NN)
 *  - editPO có giá trị: sửa đơn cũ → xóa các dòng PO đó rồi ghi lại, GIỮ nguyên số PO & ngày gốc
 * return = { pdfBase64, fileName, fileUrl, po, poNext }
 */
function exportPO(payload) {
  checkCode_(payload.code);
  const lock = LockService.getScriptLock();
  lock.waitLock(20000); // tránh 2 người xuất cùng lúc trùng số PO
  try {
    const ss = SpreadsheetApp.getActive();
    ensureSchema_(ss);
    const shP = ss.getSheetByName(SHEET_PO);
    const tz = Session.getScriptTimeZone();

    let po, ngay;
    const isEdit = payload.editPO && String(payload.editPO).trim();
    if (isEdit) {
      po = String(payload.editPO).trim();
      // xóa các dòng cũ của PO này (từ dưới lên) + lấy lại ngày gốc
      ngay = Utilities.formatDate(new Date(), tz, 'yyyy-MM-dd');
      if (shP.getLastRow() > 1) {
        const vals = shP.getRange(2, 1, shP.getLastRow() - 1, 2).getValues();
        for (let i = vals.length - 1; i >= 0; i--) {
          if (String(vals[i][1]) === po) { ngay = toISO(vals[i][0]) || ngay; shP.deleteRow(i + 2); }
        }
      }
    } else {
      const p = poForDay_(ss);
      po = p.prefix + ('0' + p.next).slice(-2);
      ngay = Utilities.formatDate(new Date(), tz, 'yyyy-MM-dd');
    }

    const cur = (String(payload.cur || 'VND').toUpperCase() === 'USD') ? 'USD' : 'VND';
    const p2 = { po: po, ngay: ngay, ncc: payload.ncc, addr: payload.addr, ghiChu: payload.ghiChu || payload.cvt || '', vatPct: +payload.vatPct || 0, cur: cur, rows: payload.rows };
    const html = buildPoHtml_(p2);
    const pdf = Utilities.newBlob(html, MimeType.HTML, po + '.html').getAs(MimeType.PDF).setName(po + '.pdf');

    // Lưu PDF vào thư mục Drive dùng chung (xóa file trùng tên cũ nếu sửa)
    const it = DriveApp.getFoldersByName(PDF_FOLDER);
    const folder = it.hasNext() ? it.next() : DriveApp.createFolder(PDF_FOLDER);
    if (isEdit) { const old = folder.getFilesByName(po + '.pdf'); while (old.hasNext()) old.next().setTrashed(true); }
    const file = folder.createFile(pdf);

    // Ghi giao dịch (14 cột)
    const vatVal = (+payload.vatPct || 0);
    const rows = payload.rows.map(r => [ngay, po, payload.maNCC || '', payload.ncc, maHangText_(r.maHang || ''), r.name, r.unit, r.qty, r.price, payload.nguoiLap || '', r.spec || '', payload.ghiChu || payload.cvt || '', vatVal, payload.addr || '', cur]);
    const startRow = shP.getLastRow() + 1;
    shP.getRange(startRow, 5, rows.length, 1).setNumberFormat('@'); // MaHang text
    if (payload.rows.some(function (r) { return typeof r.qty !== 'number'; })) {
      shP.getRange(startRow, 8, rows.length, 1).setNumberFormat('@'); // SoLuong ghi chữ -> text
    }
    shP.getRange(startRow, 1, rows.length, 15).setValues(rows);

    // Lưu địa chỉ NCC nếu mới/thay đổi
    if (payload.addr) {
      const shN = ss.getSheetByName(SHEET_NCC);
      const names = shN.getLastRow() > 1 ? shN.getRange(2, 2, shN.getLastRow() - 1, 1).getValues().map(r => String(r[0])) : [];
      const idx = names.indexOf(payload.ncc);
      if (idx >= 0) shN.getRange(idx + 2, 3).setValue(payload.addr);
      else shN.appendRow([payload.maNCC || '', payload.ncc, payload.addr]);
    }

    return { pdfBase64: Utilities.base64Encode(pdf.getBytes()), fileName: po + '.pdf', fileUrl: file.getUrl(), po: po, poNext: poSuggest_() };
  } finally {
    lock.releaseLock();
  }
}

/** Gọi lại 1 đơn PO đã tạo để sửa */
function getPO(code, po) {
  checkCode_(code);
  const ss = SpreadsheetApp.getActive();
  ensureSchema_(ss);
  const shP = ss.getSheetByName(SHEET_PO);
  if (shP.getLastRow() < 2) return null;
  const vals = shP.getRange(2, 1, shP.getLastRow() - 1, 15).getValues();
  let head = null; const rows = [];
  vals.forEach(r => {
    if (String(r[1]).trim() !== String(po).trim()) return;
    if (!head) head = { po: String(r[1]), ngay: toISO(r[0]), maNCC: String(r[2] || ''), ncc: String(r[3] || ''), nguoiLap: String(r[9] || ''), ghiChu: String(r[11] || ''), vat: (r[12] === '' || r[12] == null ? 8 : +r[12] || 0), addr: String(r[13] || ''), cur: String(r[14] || 'VND') };
    rows.push({ maHang: maHangText_(r[4]), name: String(r[5]), unit: String(r[6]), qty: qtyVal_(r[7]), price: +r[8] || 0, spec: String(r[10] || '') });
  });
  if (!head) return null;
  head.rows = rows;
  return head;
}

/**
 * CHẠY 1 LẦN để sửa MaHang cũ bị Google Sheets đổi thành ngày tháng.
 * Bước làm: (1) re-import lại tab LICH_SU từ file CSV sạch (Convert text to numbers/dates = TẮT)
 * → (2) chạy hàm này. Nó dựng lại MaHang của DON_HANG theo Tên hàng khớp với LICH_SU.
 */
function repairMaHang() {
  const ss = SpreadsheetApp.getActive();
  ensureSchema_(ss);
  // map Tên hàng -> MaHang từ LICH_SU (bản sạch)
  const shH = ss.getSheetByName(SHEET_HIST);
  const map = {};
  if (shH.getLastRow() > 1) {
    shH.getRange(2, 4, shH.getLastRow() - 1, 2).setNumberFormat('@');
    const v = shH.getRange(2, 4, shH.getLastRow() - 1, 2).getValues(); // D MaHang, E TenHang
    v.forEach(r => { const mh = maHangText_(r[0]); const name = norm_(r[1]); if (name && mh && mh.indexOf(' ') < 0) map[name] = mh; });
  }
  // sửa MaHang trong DON_HANG
  const shP = ss.getSheetByName(SHEET_PO);
  let fixed = 0;
  if (shP.getLastRow() > 1) {
    const n = shP.getLastRow() - 1;
    shP.getRange(2, 5, n, 1).setNumberFormat('@');
    const cur = shP.getRange(2, 5, n, 1).getValues();      // E MaHang
    const nm = shP.getRange(2, 6, n, 1).getValues();        // F TenHang
    const out = cur.map((r, i) => {
      const v = r[0]; const name = norm_(nm[i][0]);
      if (v instanceof Date) { fixed++; return [map[name] || maHangText_(v)]; }
      return [maHangText_(v)];
    });
    shP.getRange(2, 5, n, 1).setValues(out);
  }
  return 'Đã sửa ' + fixed + ' dòng MaHang trong DON_HANG.';
}

function buildPoHtml_(p) {
  const isUsd = (String(p.cur || 'VND').toUpperCase() === 'USD');
  const curLbl = isUsd ? 'USD' : 'VNĐ';
  const fmt = isUsd
    ? (n => Number(n).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 }))
    : (n => Math.round(n).toLocaleString('vi-VN'));
  // Số lượng luôn 2 số thập phân kiểu VN (3 -> 3,00 ; 1000 -> 1.000,00)
  const fmtQty = function (n) {
    if (typeof n !== 'number') {
      var t = String(n == null ? '' : n).trim();
      if (/[^\d.,\s]/.test(t)) return t;             // SL ghi chữ: in nguyên văn lên đơn hàng
      n = parseFloat(t.replace(/\./g, '').replace(',', '.'));
      if (isNaN(n)) return t;
    }
    return Number(n || 0).toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };
  const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const vat = (+p.vatPct || 0) / 100;
  const d = new Date();
  const ngay = Utilities.formatDate(d, Session.getScriptTimeZone(), 'dd / MM / yyyy');
  const trs = p.rows.map(function(r, i) {
    return '<tr><td class="c">' + (i + 1) + '</td><td><b>' + esc(r.name) + '</b></td><td class="c">' + esc(r.spec) +
      '</td><td class="c">' + esc(r.unit) + '</td><td class="' + (typeof r.qty === 'number' ? 'r' : 'c') + '">' + fmtQty(r.qty) + '</td><td class="r">' + fmt(r.price) +
      '</td></tr>';
  }).join('');
  return '<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><style>' +
    'body{font-family:Arial,sans-serif;font-size:12px;color:#222;margin:16px}' +
    '.top{text-align:center;border-bottom:4px solid #1F3864;padding-bottom:8px}' +
    '.top .t1{font-size:10px;color:#777}.top .t2{font-size:16px;font-weight:800;color:#1F3864}.top .t3{font-size:9px;color:#777}' +
    'h1{text-align:center;font-size:20px;margin:16px 0 6px;letter-spacing:1px}' +
    '.meta{text-align:center;margin-bottom:14px}.meta b{color:#2E74B5}' +
    '.sec{background:#2E74B5;color:#fff;font-weight:bold;padding:6px 10px;font-size:11px;margin-top:10px}' +
    '.info td{padding:4px 10px;font-size:12px}.info td:first-child{font-weight:bold;width:110px}' +
    'table.items{width:100%;border-collapse:collapse;margin-top:10px}' +
    'table.items th{background:#D9E1F2;color:#000;font-weight:bold;padding:7px 5px;font-size:10px;border:1px solid #1F3864}' +
    'table.items td{border:1px solid #999;padding:6px 7px}' +
    '.c{text-align:center}.r{text-align:right;white-space:nowrap}' +
    '.tot td{font-weight:bold;background:#1F3864;color:#fff}' +
    '.vat td{background:#fff;color:#2E74B5}' +
    '.grand td{background:#E36C0A;color:#fff;font-size:13px}' +
    '.sign{width:100%;margin-top:36px;text-align:center}.sign td{width:33%}.role{font-weight:bold}.note{font-size:10px;color:#777}' +
    '</style></head><body>' +
    '<div class="top"><div class="t1">TẬP ĐOÀN HÓA CHẤT VIỆT NAM</div>' +
    '<div class="t2">CÔNG TY CỔ PHẦN PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ</div>' +
    '<div class="t3">KCN Trà Nóc 1, Phường Thới An Đông, TP Cần Thơ | ĐT: (0292) 3.841.599 | www.cfccobay.com</div></div>' +
    '<h1>ĐƠN ĐẶT HÀNG</h1>' +
    '<div class="meta">Số PO: <b>' + esc(p.po) + '</b> &nbsp;&nbsp;&nbsp; Ngày: ' + ngay + '</div>' +
    '<div class="sec">THÔNG TIN NHÀ CUNG CẤP</div>' +
    '<table class="info"><tr><td>Bên Bán:</td><td>' + esc(p.ncc) + '</td></tr>' +
    '<tr><td>Địa Chỉ:</td><td>' + esc(p.addr) + '</td></tr>' +
    ((p.ghiChu || p.cvt) ? '<tr><td>Ghi chú:</td><td>' + esc(p.ghiChu || p.cvt) + '</td></tr>' : '') + '</table>' +
    '<table class="items"><thead><tr><th style="width:32px">STT</th><th>TÊN HÀNG HÓA / VẬT TƯ</th><th>QUY CÁCH / MÔ TẢ</th>' +
    '<th style="width:42px">ĐVT</th><th style="width:95px">SỐ LƯỢNG</th><th style="width:120px">ĐƠN GIÁ CHƯA VAT (' + curLbl + ')</th></tr></thead><tbody>' +
    trs +
    '</tbody></table>' +
    '<table class="sign"><tr><td><div class="role">NGƯỜI LẬP</div><div class="note">(Ký, họ tên)</div></td>' +
    '<td><div class="role">TRƯỞNG P. KHVT</div><div class="note">(Ký, họ tên)</div></td>' +
    '<td><div class="role">TỔNG GIÁM ĐỐC</div><div class="note">(Ký, họ tên)</div></td></tr></table>' +
    '</body></html>';
}
