import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import { posix } from 'node:path';

const SHEETS = ['NCC', 'LICH_SU', 'DON_HANG'];
const CODE_FIELDS = new Set(['supplierCode', 'materialCode', 'poNumber']);
const NUMERIC_FIELDS = new Set(['quantity', 'unitPrice', 'vatPercent']);
const HEADERS = { ngay: 'date', sopo: 'poNumber', mancc: 'supplierCode', nhacungcap: 'supplierName', tenncc: 'supplierName', diachi: 'address', mahang: 'materialCode', tenhang: 'materialName', dvt: 'unit', soluong: 'quantity', dongia: 'unitPrice', nguoilap: 'preparedBy', quycach: 'specification', ghichu: 'note', vat: 'vatPercent', loaitien: 'currency' };
const trim = (value) => String(value ?? '').trim();
const normalize = (value) => trim(value).normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().replace(/\s+/g, ' ');
const decode = (value) => String(value ?? '').replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (_, code) => code[0] === '#' ? String.fromCodePoint(parseInt(code.slice(code[1].toLowerCase() === 'x' ? 2 : 1), code[1].toLowerCase() === 'x' ? 16 : 10)) : ({ amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" }[code.toLowerCase()]));
const attributes = (xml) => Object.fromEntries([...xml.matchAll(/([\w:]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)].map((match) => [match[1], decode(match[2] ?? match[3])]));
const text = (xml) => [...String(xml).replace(/<rPh\b[^>]*>[\s\S]*?<\/rPh>/g, '').matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((match) => decode(match[1]).replace(/_x([0-9a-f]{4})_/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)))).join('');

// Decimal values stay exact: canonical signed mantissa and base-ten exponent.
function decimal(value) {
  const match = trim(value).match(/^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/);
  if (!match || !(match[2] || match[3])) throw new Error('Unsupported numeric cell token');
  let digits = (match[2] + (match[3] ?? '')).replace(/^0+/, ''), exponent = BigInt(match[4] ?? 0) - BigInt((match[3] ?? '').length);
  if (!digits) return { mantissa: 0n, exponent: 0n };
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); exponent++; }
  return { mantissa: BigInt((match[1] === '-' ? '-' : '') + digits), exponent };
}
function plainDecimal(value) {
  const { mantissa, exponent } = decimal(value);
  if (exponent > 10000n || exponent < -10000n) throw new Error('Numeric exponent exceeds verifier limits');
  const negative = mantissa < 0n, digits = (negative ? -mantissa : mantissa).toString();
  if (mantissa === 0n) return '0';
  if (exponent >= 0n) return (negative ? '-' : '') + digits + '0'.repeat(parseInt(exponent.toString(), 10));
  const position = BigInt(digits.length) + exponent;
  return (negative ? '-' : '') + (position > 0n ? digits.slice(0, parseInt(position.toString(), 10)) + '.' + digits.slice(parseInt(position.toString(), 10)) : '0.' + '0'.repeat(parseInt((-position).toString(), 10)) + digits);
}
function excelDate(value, use1904) {
  const serialText = plainDecimal(value);
  if (!/^\d+$/.test(serialText)) throw new Error('Date with time or negative serial is unsupported by independent verifier');
  const serial = BigInt(serialText);
  if (!use1904 && serial === 60n) throw new Error('Excel fictitious 1900 leap day is unsupported');
  const epochDays = serial - (use1904 ? 24107n : serial < 60n ? 25568n : 25569n);
  // Gregorian civil date using integer arithmetic, independent of machine timezone.
  const z = epochDays + 719468n, era = (z >= 0n ? z : z - 146096n) / 146097n, dayOfEra = z - era * 146097n;
  const yearOfEra = (dayOfEra - dayOfEra / 1460n + dayOfEra / 36524n - dayOfEra / 146096n) / 365n;
  let year = yearOfEra + era * 400n;
  const dayOfYear = dayOfEra - (365n * yearOfEra + yearOfEra / 4n - yearOfEra / 100n), monthPrime = (5n * dayOfYear + 2n) / 153n;
  const day = dayOfYear - (153n * monthPrime + 2n) / 5n + 1n, month = monthPrime + (monthPrime < 10n ? 3n : -9n);
  if (month <= 2n) year++;
  if (year < 1n || year > 9999n) throw new Error('Date outside verifier range');
  return `${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`;
}

export async function readWorkbookSource(filePath) {
  const bytes = await readFile(filePath), entries = new Map();
  let end = bytes.length - 22;
  while (end >= Math.max(0, bytes.length - 65557) && bytes.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < Math.max(0, bytes.length - 65557)) throw new Error('Workbook ZIP directory missing');
  let offset = bytes.readUInt32LE(end + 16);
  for (let index = 0; index < bytes.readUInt16LE(end + 10); index++) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) throw new Error('Workbook ZIP entry invalid');
    const nameLength = bytes.readUInt16LE(offset + 28), extraLength = bytes.readUInt16LE(offset + 30), commentLength = bytes.readUInt16LE(offset + 32);
    entries.set(bytes.subarray(offset + 46, offset + 46 + nameLength).toString('utf8'), { method: bytes.readUInt16LE(offset + 10), flags: bytes.readUInt16LE(offset + 8), length: bytes.readUInt32LE(offset + 20), local: bytes.readUInt32LE(offset + 42) });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  function xml(name, required = true) {
    const entry = entries.get(name);
    if (!entry) { if (required) throw new Error('Required workbook XML entry missing'); return ''; }
    if (entry.flags & 1 || ![0, 8].includes(entry.method) || bytes.readUInt32LE(entry.local) !== 0x04034b50) throw new Error('Unsupported workbook ZIP encoding');
    const begin = entry.local + 30 + bytes.readUInt16LE(entry.local + 26) + bytes.readUInt16LE(entry.local + 28), data = bytes.subarray(begin, begin + entry.length);
    return (entry.method === 8 ? inflateRawSync(data, { maxOutputLength: 64 * 1024 * 1024 }) : data).toString('utf8');
  }
  const workbook = xml('xl/workbook.xml'), relationships = new Map();
  for (const match of xml('xl/_rels/workbook.xml.rels').matchAll(/<Relationship\b([^>]*)(?:\/?>)/g)) { const item = attributes(match[1]); if (item.TargetMode !== 'External') relationships.set(item.Id, item.Target); }
  const descriptors = [...workbook.matchAll(/<sheet\b([^>]*)(?:\/?>)/g)].map((match) => attributes(match[1]));
  const stylesXml = xml('xl/styles.xml', false), formats = new Map([...stylesXml.matchAll(/<numFmt\b([^>]*)(?:\/?>)/g)].map((match) => { const item = attributes(match[1]); return [item.numFmtId, item.formatCode]; }));
  const styles = [...(stylesXml.match(/<cellXfs\b[^>]*>([\s\S]*?)<\/cellXfs>/)?.[1] ?? '').matchAll(/<xf\b([^>]*)(?:\/?>)/g)].map((match) => attributes(match[1]));
  const styleFor = (index) => { const item = styles[parseInt(index ?? '0', 10)] ?? {}; return { id: parseInt(item.numFmtId ?? '0', 10), format: formats.get(item.numFmtId) }; };
  const dateStyle = (style) => [14, 15, 16, 17, 22, 27, 30, 36, 50, 57].includes(style.id) || (![18, 19, 20, 21, 45, 46, 47].includes(style.id) && /[dy]/i.test((style.format ?? '').replace(/"[^"]*"|\[[^\]]*\]|\\./g, '')));
  const neededStrings = new Set(), parsed = {}, sheets = {};
  for (const name of SHEETS) {
    const descriptor = descriptors.find((item) => item.name === name), target = relationships.get(descriptor?.['r:id']);
    if (!target) throw new Error(`Required worksheet missing: ${name}`);
    const entry = target.startsWith('/') ? target.slice(1) : posix.normalize(`xl/${target}`);
    if (!entry.startsWith('xl/')) throw new Error('Worksheet target outside workbook');
    parsed[name] = [...xml(entry).matchAll(/<row\b([^>]*?)(?:\/>|>([\s\S]*?)<\/row>)/g)].map((match) => {
      const row = parseInt(attributes(match[1]).r, 10), cells = [];
      for (const cell of (match[2] ?? '').matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
        const attr = attributes(cell[1]), body = cell[2] ?? '', type = attr.t ?? 'n', style = styleFor(attr.s);
        if (/<f\b/.test(body)) throw new Error(`Formula unsupported by independent verifier: ${name} ${attr.r}`);
        if (!['n', 's', 'inlineStr', 'str', 'b'].includes(type)) throw new Error(`Unsupported cell type: ${name} ${attr.r}`);
        let value = type === 'inlineStr' ? text(body) : body.match(/<v\b[^>]*>([\s\S]*?)<\/v>/)?.[1] ?? null;
        if (value !== null && type !== 'inlineStr') value = decode(value);
        if (type === 's' && value !== null) neededStrings.add(parseInt(value, 10));
        const numericDate = type === 'n' && value !== null && dateStyle(style);
        if (numericDate) value = excelDate(value, /date1904="(?:1|true)"/.test(workbook));
        if (type === 'b' && value !== null) { if (!['0', '1'].includes(value)) throw new Error('Invalid boolean cell'); value = value === '1' ? 'true' : 'false'; }
        cells.push({ ref: attr.r, type, numericDate, value, format: style.format });
      }
      return { row, cells };
    });
  }
  const strings = new Map(); let stringIndex = 0;
  // CONFIG is never opened; unreferenced shared-string entries are never decoded.
  for (const match of xml('xl/sharedStrings.xml', false).matchAll(/<si\b[^>]*>([\s\S]*?)<\/si>/g)) { if (neededStrings.has(stringIndex)) strings.set(stringIndex, text(match[1])); stringIndex++; }
  for (const name of SHEETS) {
    const rows = parsed[name], columns = new Map(), seenHeaders = new Set();
    const resolve = (cell) => { if (cell.type !== 's' || cell.value === null) return cell.value; const value = strings.get(parseInt(cell.value, 10)); if (value === undefined) throw new Error(`Referenced shared string missing: ${name} ${cell.ref}`); return value; };
    if (!rows.length) throw new Error(`Worksheet header missing: ${name}`);
    for (const cell of rows[0].cells) {
      const title = trim(resolve(cell)), field = HEADERS[normalize(title)];
      if (!title) continue;
      if (!field || seenHeaders.has(field)) throw new Error(`Unsupported or duplicate worksheet header: ${name} ${cell.ref}`);
      seenHeaders.add(field); columns.set(cell.ref.replace(/\d+$/, ''), field);
    }
    const dataRows = [];
    for (const sourceRow of rows.slice(1)) {
      const data = {}, cellTypes = {};
      for (const cell of sourceRow.cells) {
        let value = trim(resolve(cell)); if (!value) continue;
        const field = columns.get(cell.ref.replace(/\d+$/, ''));
        if (!field) throw new Error(`Unmapped worksheet cell: ${name} ${cell.ref}`);
        if (CODE_FIELDS.has(field) && cell.type === 'n' && !cell.numericDate) { value = plainDecimal(value); if (cell.format && /^0+$/.test(cell.format) && /^\d+$/.test(value)) value = value.padStart(cell.format.length, '0'); }
        data[field] = value; cellTypes[field] = { type: cell.type, numericDate: cell.numericDate, ref: cell.ref };
      }
      if (Object.keys(data).length) dataRows.push({ row: sourceRow.row, data, cellTypes });
    }
    sheets[name] = { rows: dataRows };
  }
  return { sha256: createHash('sha256').update(bytes).digest('hex'), sheets };
}

export function compareWorkbookStaging(source, stagingRows) {
  const staged = new Map(), mismatches = [], rowsPerSheet = {}; let sourceRows = 0, matchedRows = 0, checkedFields = 0, numericFlagsChecked = 0;
  const mismatch = (sheet, row, field, reason) => mismatches.push({ sheet, row, field, reason });
  for (const record of stagingRows) {
    const sheet = record.sheet_name ?? record.sheet, row = record.row_number ?? record.rowNumber ?? record.row, key = `${sheet}:${row}`;
    if (!SHEETS.includes(sheet)) { mismatch(sheet, row, '__sheet', 'unexpected staging sheet'); continue; }
    if (staged.has(key)) mismatch(sheet, row, '__row', 'duplicate staging row');
    const payload = record.mapped_json ?? record.mapped ?? record.data;
    staged.set(key, typeof payload === 'string' ? JSON.parse(payload) : payload);
  }
  for (const sheet of SHEETS) {
    const rows = source.sheets[sheet]?.rows; if (!rows) throw new Error(`Source worksheet missing: ${sheet}`);
    rowsPerSheet[sheet] = rows.length;
    for (const record of rows) {
      sourceRows++; const key = `${sheet}:${record.row}`, data = staged.get(key), before = mismatches.length;
      if (!data || typeof data !== 'object') { mismatch(sheet, record.row, '__row', 'source row absent in staging'); continue; }
      staged.delete(key);
      for (const field of new Set([...Object.keys(record.data), ...Object.keys(data).filter((key) => !key.includes('__'))])) {
        checkedFields++; const expected = trim(record.data[field]), actual = trim(data[field]), cell = record.cellTypes[field];
        let equal = expected === actual;
        if (!equal && expected && actual && cell?.type === 'n' && !cell.numericDate && !CODE_FIELDS.has(field)) { try { const left = decimal(expected), right = decimal(actual); equal = left.mantissa === right.mantissa && left.exponent === right.exponent; } catch { /* Nondecimal staging value remains a mismatch. */ } }
        if (!equal) mismatch(sheet, record.row, field, 'mapped field differs from source');
        if (NUMERIC_FIELDS.has(field)) { numericFlagsChecked++; const numeric = cell?.type === 'n' && !cell.numericDate; if (numeric !== (trim(data[`${field}__numeric`]) === 'true')) mismatch(sheet, record.row, `${field}__numeric`, 'numeric provenance differs from source'); }
      }
      if (mismatches.length === before) matchedRows++;
    }
  }
  for (const key of staged.keys()) { const [sheet, row] = key.split(':'); mismatch(sheet, parseInt(row, 10), '__row', 'staging row absent in source'); }
  return { sourceRows, stagingRows: stagingRows.length, matchedRows, checkedFields, numericFlagsChecked, rowsPerSheet, mismatches };
}
