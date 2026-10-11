import type { Decimal, DocumentLine } from './types'
export const priceBasisLabels: Record<string, string> = { UNKNOWN: 'Chưa rõ VAT', EXCL_VAT: 'Chưa VAT', INCL_VAT: 'Đã có VAT' }
export const sourceLabels: Record<string, string> = { LEGACY_WORKBOOK: 'Lịch sử Apps Script', LEGACY_HISTORY: 'Lịch sử Apps Script', LEGACY_ORDER: 'PO Apps Script', PURCHASE_ORDER: 'PO đã phát hành' }
export const matchLabels: Record<string, string> = { EXACT_CODE: 'Khớp mã vật tư', EXACT_NAME: 'Khớp tên chính xác', MATCHED: 'Có lịch sử', NO_MATCH: 'Chưa tìm thấy lịch sử', AMBIGUOUS: 'Cần xác nhận vật tư', SUGGESTION: 'Ứng viên cần đối chiếu' }
export const decimalLabel = (value: Decimal | undefined) => value == null || value === '' ? 'Chưa có' : String(value)
export function sourceLocation(line: DocumentLine) {
  return [line.sourceSheet, line.sourceRow ? `dòng ${line.sourceRow}` : null, line.sourcePage ? `trang ${line.sourcePage}` : null].filter(Boolean).join(' · ') || 'Nhập tay'
}
export function newManualLine(): DocumentLine {
  return { lineKey: `manual-${crypto.randomUUID()}`, sourceSheet: null, sourceRow: null, sourcePage: null, requestNumber: null, materialCode: null, materialName: '', specification: null, unit: null, quantity: null, quantityText: null, unitPrice: null, lineTotal: null, vatPercent: null, priceBasis: 'UNKNOWN', currency: null, note: null, raw: {}, issues: [] }
}
