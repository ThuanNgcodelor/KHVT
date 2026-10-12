import type { Page } from '@playwright/test'
import { mockApi } from './fixtures'
import type { Confirmation, DocumentLine, DocumentView, HistoryLookup, QuoteComparison } from '../../src/features/imports/documents/types'

export function line(key: string, overrides: Partial<DocumentLine> = {}): DocumentLine {
  return { lineKey: key, sourceSheet: '2026', sourceRow: Number(key.replace(/\D/g, '')) || 5, sourcePage: null,
    requestNumber: '1', materialCode: 'VT1', materialName: 'Thép kiểm thử', specification: 'Dày 2 mm', unit: 'kg',
    quantity: 3, quantityText: null, unitPrice: null, lineTotal: null, vatPercent: null, priceBasis: 'UNKNOWN', currency: null,
    note: null, raw: { B: 'Thép kiểm thử' }, issues: [], ...overrides }
}
export function doc(id = 1, overrides: Partial<DocumentView> = {}): DocumentView {
  return { id, kind: 'REQUEST', title: 'Sổ CVT mẫu', fileName: 'request-fixture.xlsx', status: 'PREVIEW', duplicate: false, confirmed: null, inquirySelections: [],
    parsed: { kind: 'REQUEST', fileName: 'request-fixture.xlsx', format: 'XLSX', sheets: [{ name: '2026', detectedLines: 3, requestNumbers: ['1', '2'], quotationDate: null }],
      lines: [line('row5'), line('row6', { requestNumber: '2', materialName: 'Bulong mẫu' }), line('row7', { requestNumber: null, materialName: 'Dòng chưa gán phiếu' })],
      supplierName: null, quotationDate: null, currency: null, vatPercent: null, priceBasis: 'UNKNOWN', validityNote: null,
      warnings: ['Chọn đúng số phiếu và các dòng cần mua.'], parserVersion: 'fixture-only' }, ...overrides }
}
export function confirmedRequest(): DocumentView {
  const item = doc(); item.status = 'CONFIRMED'
  item.confirmed = { title: item.title, requestNumber: '1', supplierName: null, quotationDate: null, currency: null, priceBasis: 'UNKNOWN', vatPercent: null, lines: [line('row5')], requestId: null, requestLineMappings: {}, acknowledged: true }
  return item
}
export async function documentFixtures(page: Page, initial: DocumentView[] = [], permissions?: string[]) {
  const auth = await mockApi(page, { signedIn: true, role: 'PLANNER', permissions })
  const base = { materialCode: 'VT1', materialName: 'Thép kiểm thử', unit: 'kg', unitPrice: 100, currency: 'VND', currencyBasis: 'ASSUMED_LEGACY', purchaseDate: '2026-10-01', source: 'LEGACY_HISTORY', sourceReference: 'LICH_SU!2', qualityWarnings: ['VAT của lịch sử chưa xác nhận'], count: 2 }
  const state = { documents: structuredClone(initial), hiddenListIds: [] as number[], preview: doc(), confirmCalls: [] as Confirmation[], inquiryCalls: [] as { selections: { lineKey: string; supplierKey: string }[] }[], previewLookupCalls: [] as string[][], previewSheets: [] as string[], failConfirm: false, denyList: false,
    lookup: { lines: [{ lineKey: 'row5', matchStatus: 'EXACT_CODE', materialCode: 'VT1', materialName: 'Thép kiểm thử', candidates: [{ ...base, supplierKey: 'id:1', supplierId: 1, supplierCode: 'NCC1', supplierName: 'NCC mẫu A' }, { ...base, supplierKey: 'id:2', supplierId: 2, supplierCode: 'NCC2', supplierName: 'NCC mẫu B', unitPrice: 110 }] }] } as HistoryLookup,
    comparison: { lines: [{ lineKey: 'row5', materialName: 'Thép kiểm thử', unit: 'kg', quantity: 3, history: [], quotes: [{ documentId: 2, supplierName: 'NCC mẫu mới', unit: 'kg', quantity: 3, unitPrice: 120, currency: 'VND', vatPercent: 8, priceBasis: 'EXCL_VAT', comparable: false, reason: 'Tiền tệ lịch sử chỉ là giả định', delta: null, percent: null }] }] } as QuoteComparison }
  await page.route('**/api/imports/documents**', async (route) => {
    const request = route.request(), url = new URL(request.url()), method = request.method(), path = url.pathname
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (method !== 'GET' && request.headers()['x-xsrf-token'] !== `fixture-${auth.csrf}`) return reply({ message: 'Thiếu CSRF' }, 403)
    if (path === '/api/imports/documents') return state.denyList ? reply({ message: 'Không có quyền đọc chứng từ.' }, 403) : reply(state.documents.filter((item) => !state.hiddenListIds.includes(item.id)))
    if (path.endsWith('/preview')) {
      const sheet = url.searchParams.get('sheet') ?? ''; state.previewSheets.push(sheet)
      const result = structuredClone(state.preview)
      if (sheet) { result.id = Math.max(...state.documents.map((item) => item.id), 0) + 1; result.parsed.lines = (result.parsed.lines.length ? result.parsed.lines.slice(0, 1) : [line('row5')]).map((item) => ({ ...item, sourceSheet: sheet })); result.parsed.quotationDate = result.parsed.sheets.find((item) => item.name === sheet)?.quotationDate ?? null; result.parsed.warnings = [] }
      state.documents = [...state.documents.filter((item) => item.id !== result.id), result]; return reply(result)
    }
    const match = path.match(/\/documents\/(\d+)(?:\/(.*))?$/), item = state.documents.find((document) => document.id === Number(match?.[1]))
    if (!item) return reply({ message: 'Không tìm thấy chứng từ' }, 404)
    const action = match?.[2]
    if (!action) return reply(item)
    if (action === 'confirm') {
      const data = request.postDataJSON() as Confirmation; state.confirmCalls.push(data)
      if (state.failConfirm) { state.failConfirm = false; return reply({ message: 'Chưa lưu được; kiểm tra lại dữ liệu.' }, 409) }
      if (item.kind === 'REQUEST' && item.parsed.lines.some((entry) => !!entry.requestNumber)) {
        const child = structuredClone(item); child.id = Math.max(...state.documents.map((entry) => entry.id), 0) + 1
        child.confirmed = data; child.status = 'CONFIRMED'; child.title = data.title; child.parsed.lines = data.lines; child.sourceDocumentId = item.id
        state.documents.push(child); return reply(child)
      }
      item.confirmed = data; item.status = 'CONFIRMED'; item.title = data.title; return reply(item)
    }
    if (action === 'lookup') return reply(state.lookup)
    if (action === 'lookup-preview') { state.previewLookupCalls.push((request.postDataJSON() as { lineKeys: string[] }).lineKeys); return reply(state.lookup) }
    if (action === 'comparison') return reply(state.comparison)
    if (action === 'source') return route.fulfill({ contentType: 'application/octet-stream', headers: { 'Content-Disposition': `attachment; filename="${item.fileName}"` }, body: 'synthetic source fixture' })
    if (action === 'inquiries' || action === 'inquiries.xlsx') {
      const body = request.postDataJSON(); state.inquiryCalls.push(body); item.inquirySelections = body.selections
      if (action === 'inquiries') return reply(item)
      return route.fulfill({ contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', headers: { 'Content-Disposition': 'attachment; filename="hoi-gia-mau.xlsx"' }, body: 'PK synthetic download, not an actual workbook' })
    }
    return reply({ message: 'Fixture route chưa hỗ trợ' }, 404)
  })
  return state
}
