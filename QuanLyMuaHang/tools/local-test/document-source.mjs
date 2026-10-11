import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import { posix } from 'node:path';

// Independent OpenXML reader for acceptance: it never calls the Java parser or POI.
const decode = (value) => String(value ?? '').replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (_, key) => key[0] === '#' ? String.fromCodePoint(parseInt(key.slice(key[1].toLowerCase() === 'x' ? 2 : 1), key[1].toLowerCase() === 'x' ? 16 : 10)) : ({ amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" }[key.toLowerCase()]));
const attrs = (value) => Object.fromEntries([...value.matchAll(/([\w:]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)].map((m) => [m[1], decode(m[2] ?? m[3])]));
const xmlText = (value) => [...value.replace(/<rPh\b[^>]*>[\s\S]*?<\/rPh>/g, '').matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((m) => decode(m[1])).join('');

function decimal(value) {
  const m = String(value).trim().match(/^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/);
  if (!m || !(m[2] || m[3])) throw new Error('Unsupported decimal acceptance value');
  let digits = (m[2] + (m[3] ?? '')).replace(/^0+/, ''), exponent = BigInt(m[4] ?? 0) - BigInt((m[3] ?? '').length);
  if (!digits) return ['0', 0n];
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); exponent++; }
  return [(m[1] === '-' ? '-' : '') + digits, exponent];
}
export function equalDecimal(a, b) { if (a == null || a === '' || b == null || b === '') return (a == null || a === '') && (b == null || b === ''); const x = decimal(a), y = decimal(b); return x[0] === y[0] && x[1] === y[1]; }
const trim = (v) => String(v ?? '').trim();

// JSON numeric tokens are quoted before JSON.parse, avoiding binary float rounding in acceptance.
export function readJsonExact(content) {
  let result = '', quoted = false, escaped = false;
  for (let i = 0; i < content.length; i++) {
    const c = content[i];
    if (quoted) { result += c; if (escaped) escaped = false; else if (c === '\\') escaped = true; else if (c === '"') quoted = false; continue; }
    if (c === '"') { quoted = true; result += c; continue; }
    if (c === '-' || /\d/.test(c)) { const token = content.slice(i).match(/^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/); if (token) { result += '"' + token[0] + '"'; i += token[0].length - 1; continue; } }
    result += c;
  }
  return JSON.parse(result);
}

export async function readDocumentWorkbook(path) {
  const bytes = await readFile(path), entries = new Map();
  let end = bytes.length - 22;
  while (end >= Math.max(0, bytes.length - 65557) && bytes.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0) throw new Error('OpenXML ZIP end missing');
  let offset = bytes.readUInt32LE(end + 16);
  for (let count = bytes.readUInt16LE(end + 10), i = 0; i < count; i++) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) throw new Error('OpenXML ZIP directory invalid');
    const nameLength = bytes.readUInt16LE(offset + 28), extra = bytes.readUInt16LE(offset + 30), comment = bytes.readUInt16LE(offset + 32);
    entries.set(bytes.subarray(offset + 46, offset + 46 + nameLength).toString('utf8'), { method: bytes.readUInt16LE(offset + 10), length: bytes.readUInt32LE(offset + 20), local: bytes.readUInt32LE(offset + 42) });
    offset += 46 + nameLength + extra + comment;
  }
  function xml(name) {
    const e = entries.get(name); if (!e) return '';
    if (![0, 8].includes(e.method)) throw new Error('Unsupported ZIP compression');
    const start = e.local + 30 + bytes.readUInt16LE(e.local + 26) + bytes.readUInt16LE(e.local + 28), data = bytes.subarray(start, start + e.length);
    // XML normalizes literal CRLF/CR before parsing; character references are decoded later.
    return (e.method === 8 ? inflateRawSync(data, { maxOutputLength: 64 * 1024 * 1024 }) : data).toString('utf8').replace(/\r\n?/g, '\n');
  }
  const strings = [...xml('xl/sharedStrings.xml').matchAll(/<si\b[^>]*>([\s\S]*?)<\/si>/g)].map((m) => xmlText(m[1]));
  const relationships = new Map([...xml('xl/_rels/workbook.xml.rels').matchAll(/<Relationship\b([^>]*)(?:\/?>)/g)].map((m) => { const a = attrs(m[1]); if (a.TargetMode === 'External') throw new Error('External sheet unsupported by acceptance reader'); return [a.Id, a.Target]; }));
  const sheets = [];
  for (const m of xml('xl/workbook.xml').matchAll(/<sheet\b([^>]*)(?:\/?>)/g)) {
    const descriptor = attrs(m[1]), target = relationships.get(descriptor['r:id']); if (!target) throw new Error('Worksheet relation missing');
    const entry = target.startsWith('/') ? target.slice(1) : posix.normalize('xl/' + target); if (!entry.startsWith('xl/')) throw new Error('Worksheet path outside workbook');
    const rows = new Map();
    for (const rm of xml(entry).matchAll(/<row\b([^>]*?)(?:\/>|>([\s\S]*?)<\/row>)/g)) {
      const row = Number(attrs(rm[1]).r), cells = {};
      for (const cm of (rm[2] ?? '').matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
        const a = attrs(cm[1]), body = cm[2] ?? '', token = decode(body.match(/<v\b[^>]*>([\s\S]*?)<\/v>/)?.[1] ?? '');
        const value = a.t === 's' ? strings[Number(token)] : a.t === 'inlineStr' ? xmlText(body) : token;
        cells[a.r.replace(/\d+$/, '')] = { ref: a.r, value: value ?? '', numeric: !a.t || a.t === 'n', formula: decode(body.match(/<f\b[^>]*>([\s\S]*?)<\/f>/)?.[1] ?? '') };
      }
      rows.set(row, cells);
    }
    sheets.push({ name: descriptor.name, rows });
  }
  return { sha256: createHash('sha256').update(bytes).digest('hex'), sheets };
}

// Sample definitions are source-layout expectations, deliberately separate from parser heuristics.
export function compareDocumentWorkbook(source, parsed, kind, sheetName) {
  const sheet = source.sheets.find((s) => s.name === sheetName); if (!sheet) throw new Error('Acceptance worksheet missing');
  const columns = kind === 'REQUEST' ? { materialName: 'B', specification: 'C', unit: 'D', quantity: 'E', requestNumber: 'O' } : sheetName === 'BG' ? { materialName: 'C', specification: 'E', quantity: 'G', unitPrice: 'F', lineTotal: 'H', vatPercent: 'I' } : { materialName: 'C', specification: 'F', quantity: 'H', unitPrice: 'G', lineTotal: 'I', vatPercent: 'J' };
  const start = kind === 'REQUEST' ? 6 : 10, end = kind === 'REQUEST' ? 2057 : sheetName === 'BG' ? 52 : 27;
  const expected = [...sheet.rows.entries()].filter(([r, c]) => r >= start && r <= end && trim(c[columns.materialName]?.value));
  const actual = new Map(), mismatches = []; let checkedFields = 0;
  for (const line of parsed.lines) {
    const sourceRow = Number(line.sourceRow);
    if (line.sourceSheet !== sheetName || actual.has(sourceRow)) mismatches.push({ row: sourceRow, field: '__source', reason: 'unexpected or duplicate source coordinate' });
    actual.set(sourceRow, line);
  }
  for (const [row, cells] of expected) {
    const line = actual.get(row); if (!line) { mismatches.push({ row, field: '__line', reason: 'source item missing' }); continue; }
    for (const [field, column] of Object.entries(columns)) {
      const cell = cells[column], expectedValue = trim(cell?.value), actualValue = line[field]; checkedFields++;
      if (['quantity', 'unitPrice', 'lineTotal', 'vatPercent'].includes(field)) {
        let transformed = expectedValue;
        if (field === 'vatPercent' && cell?.numeric && expectedValue) { const [mantissa, exponent] = decimal(expectedValue); transformed = `${mantissa}e${exponent + 2n}`; }
        if (!equalDecimal(transformed, actualValue)) mismatches.push({ row, field, reason: 'exact decimal mismatch' });
      } else if (trim(actualValue) !== expectedValue) mismatches.push({ row, field, reason: 'text mismatch' });
    }
    actual.delete(row);
  }
  for (const row of actual.keys()) mismatches.push({ row, field: '__line', reason: 'unexpected item not present in source item range' });
  return { sheet: sheetName, sourceRows: expected.length, parsedRows: parsed.lines.length, checkedFields, mismatches };
}

// Decimal spellings and STT copied from the extracted documents, not generated from parser output.
export const PDF_EXPECTATIONS = {
  vanPhuc: { file: '08.10.2026 BÁO GIÁ CÔNG TY PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ - VẠN PHÚC.pdf', date: '2026-10-08', rows: [['1','4000','350','1400000'],['2','4000','370','1480000'],['3','1000','160','160000'],['4','10','2600','26000'],['5','10','4870','48700'],['6','50','3870','193500'],['7','2000','130','260000'],['8','1000','160','160000']] },
  aChau: { file: 'BÁO GIÁ VẬT TƯ C.F.C - 02.10.2026.pdf', date: '2026-10-02', rows: [['1','100','80000','8000000'],['2','50','95000','4750000'],['3','1','45000','45000'],['4','10','40000','400000'],['5','6','560000','3360000'],['6','14','126000','1764000'],['7','10','741000','7410000'],['8','10','200000','2000000'],['9','10','15000','150000'],['10','10','55000','550000'],['11','2','120000','240000'],['12','5','380000','1900000'],['13','10','70000','700000'],['14','1','65000','65000'],['15','5','52000','260000'],['17','2','182000','364000'],['18','1','234000','234000'],['19','1','253500','253500'],['20','8','76700','613600'],['21','10','46800','468000'],['22','10','39000','390000'],['23','10','28600','286000'],['24','10','23400','234000'],['25','8','21600','172800'],['26','5','486000','2430000'],['27','5','756000','3780000'],['30','1','5460000','5460000'],['31','1','5100000','5100000'],['32','2','1200000','2400000'],['33','1','1350000','1350000'],['34','1','6300000','6300000']] },
  trungDong: { file: 'C021_PB&HC CAN THO 07.10.26.pdf', date: '2026-10-07', rows: [['1','10000','6740','67400000'],['2','1','5000000','5000000'],['3','8000','9700','77600000'],['4','1','6000000','6000000'],['5','10000','8450','84500000'],['6','1','6300000','6300000'],['7','10000','8450','84500000'],['8','1','6300000','6300000'],['9','8000','12350','74400000'],['10','10000','8450','84500000'],['11','1','5670000','5670000']] }
};

const PDF_NAME_FRAGMENTS = {
  '2026-10-08': [['Vòng Đệm','M8'],['Vòng Đệm','M10'],['Tán','M8'],['Lục Giác','M8x60'],['Lục Giác','M8x120'],['Tắc Kê','M12x100'],['Pake','16mm'],['Pake','19mm']],
  '2026-10-02': [['Dây vải','15x1366mm'],['silicone','M30'],['Than máy mài'],['chổi nhựa'],['Lõi lọc vi sinh'],['Lõi lọc sợi'],['xích','60-2R'],['Bánh xe','70x80'],['Đá mài','125mm'],['bàn chải'],['Lưỡi cắt','125mm'],['Kìm','licota'],['Apollo','A300'],['Đầu kẹp'],['Phốt','35x10'],['Kiềm','250mm'],['lục giác','1.5-10mm'],['lục giác sao','1.5-10mm'],['Cờ lê','24'],['Cờ lê','19'],['Cờ lê','17'],['Cờ lê','13'],['Cờ lê','10'],['Cờ lê','8'],['amiang','2mm'],['amiang','3mm'],['GXSV','1004','122K9321'],['GXSV','1003','121K2423'],['piston','RMS8'],['Đủa đánh lữa'],['Biến thế','RMS8']],
  '2026-10-07': [['Bao trắng','45*78'],['Tiền làm mới bản in'],['Bao trắng','58*95'],['Tiền làm mới bản in'],['Bao TRONG','37+8*78'],['Tiền làm mới trục'],['Bao TRONG','37+8*78'],['Tiền làm mới trục'],['Bao trong','58*92'],['Bao TRONG','37+8*78'],['Tiền sửa trục']]
};

export function compareDocumentPdf(parsed, expectation) {
  const mismatches = []; let checkedFields = 0;
  if (parsed.quotationDate !== expectation.date) mismatches.push({ field: 'quotationDate', reason: 'document date mismatch' });
  if (parsed.lines.length !== expectation.rows.length) mismatches.push({ field: '__rows', reason: 'actual item count mismatch' });
  for (let i = 0; i < expectation.rows.length; i++) {
    const [stt, quantity, unitPrice, lineTotal] = expectation.rows[i], line = parsed.lines[i];
    if (!line) continue;
    for (const [field, expected] of Object.entries({ quantity, unitPrice, lineTotal })) { checkedFields++; if (!equalDecimal(line[field], expected)) mismatches.push({ row: stt, field, reason: 'exact decimal mismatch' }); }
    const product = `${line.materialName ?? ''} ${line.specification ?? ''}`.replace(/\s+/g, ' ').toLowerCase();
    for (const fragment of PDF_NAME_FRAGMENTS[expectation.date][i]) { checkedFields++; if (!product.includes(fragment.toLowerCase())) mismatches.push({ row: stt, field: 'materialName/specification', reason: 'source product detail missing' }); }
    if (!line.sourcePage || !trim(line.materialName) || !trim(line.unit)) mismatches.push({ row: stt, field: '__provenance', reason: 'name/unit/page absent' });
  }
  return { sourceRows: expectation.rows.length, parsedRows: parsed.lines.length, checkedFields, mismatches };
}
