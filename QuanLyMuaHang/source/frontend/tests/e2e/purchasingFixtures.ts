import type { Page } from '@playwright/test'
import { mockApi } from './fixtures.ts'
import type { Material, Supplier } from '../../src/features/catalog/types'
import type { PurchaseOrder } from '../../src/features/procurement/types'
import type { LegacyCommit, LegacyPreview } from '../../src/features/imports/types'

export async function purchasing(page: Page, role = 'ADMIN') {
  const auth = await mockApi(page, { signedIn: true, role })
  const state = { failCatalog: false, failSave: false, commits: 0, legacyDetected: false, legacyPreviewCalls: 0, operationalPreviewCalls: 0,
    legacyPreview: { batchId:1,fileName:'fixture.xlsx',sha256:'synthetic',status:'PREVIEW',summary:{rowsPerSheet:{NCC:1,LICH_SU:2,DON_HANG:1},errorRows:0,warningRows:1,legacyPurchaseOrderGroups:1,totalRows:4,note:'CONFIG bỏ qua'},rowIssues:[{sheet:'DON_HANG',rowNumber:2,status:'WARNING',issues:['WARNING:VAT_UNKNOWN: Cần đối chiếu VAT']}],duplicate:false,message:null } as LegacyPreview,
    legacyCommit: {batchId:1,status:'COMMITTED',committedRows:4,errorRows:0,warningRows:1,supplierRowsProcessed:1,materialRowsProcessed:1,historyRowsImported:2,purchaseOrdersImported:1,message:'Đã commit dữ liệu thử',verification:{sourceRows:4,verifiedRows:4,historyRows:2,purchaseOrders:1,purchaseOrderItems:1,supplierRows:1}} as LegacyCommit,
    issueCsv: 'sheet,rowNumber,status,issue\r\nDON_HANG,2,WARNING,WARNING:VAT_UNKNOWN\r\n',
    writes: [] as { path: string; data: Record<string, unknown> }[],
    materials: [{ id: 1, code: '0001', name: 'Thép kiểm thử', category: 'MATERIAL', defaultUnit: 'kg', active: true }] as Material[],
    suppliers: [{ id: 1, code: 'NCC1', name: 'NCC mẫu A', address: 'Địa chỉ mẫu', taxCode: null, phone: null, email: null, active: true }, { id: 2, code: 'NCC2', name: 'NCC mẫu B', address: null, taxCode: null, phone: null, email: null, active: true }] as Supplier[],
    orders: [] as PurchaseOrder[] }
  const paged = <T>(rows: T[], params: URLSearchParams) => { const number = Number(params.get('page') ?? 0), size = Number(params.get('size') ?? 25); return { content: rows.slice(number*size,(number+1)*size), number, size, totalElements: rows.length, totalPages: Math.ceil(rows.length/size) } }
  await page.route('**/api/**', async (route) => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname, method = req.method()
    if (!/^\/api\/(catalog|prices|purchase-orders|imports|exports)/.test(path)) return route.fallback()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (method !== 'GET' && req.headers()['x-xsrf-token'] !== `fixture-${auth.csrf}`) return reply({ code: 'CSRF', message: 'Thiếu CSRF' },403)
    const body = () => req.postDataJSON() as Record<string, unknown>
    if (path.startsWith('/api/catalog/')) {
      if (state.failCatalog) return reply({ message: 'Danh mục tạm thời không khả dụng' },503)
      const kind = path.includes('/materials') ? 'materials' : 'suppliers'
      const data = state[kind], match = path.match(/\/(\d+)$/)
      if (method === 'GET') {
        if (match) return reply(data.find((row) => row.id === Number(match[1])))
        const q = (url.searchParams.get('q') ?? '').toLowerCase(), active = url.searchParams.get('active'), category = url.searchParams.get('category')
        const filtered = data.filter((row) => (!q || `${row.code} ${row.name}`.toLowerCase().includes(q)) && (!active || String(row.active) === active) && (!category || ('category' in row && row.category === category)))
        return reply(path.endsWith('/page') ? paged(filtered, url.searchParams) : filtered.filter((row) => row.active))
      }
      state.writes.push({ path, data: body() })
      if (state.failSave) { state.failSave = false; return reply({ code: 'CODE_EXISTS', message: 'Mã đã tồn tại' },409) }
      const saved = { ...body(), id: match ? Number(match[1]) : data.length+1 }
      if (kind === 'materials') state.materials = [...state.materials.filter((row) => row.id !== saved.id), saved as Material]
      else state.suppliers = [...state.suppliers.filter((row) => row.id !== saved.id), saved as Supplier]
      return reply(saved)
    }
    if (path.startsWith('/api/prices')) {
      const price = { id: 1, materialName: 'Thép kiểm thử', materialCode: '0001', unit: 'kg', quantity: 2, quantityText: null, unitPrice: 100, currency: 'VND', currencyBasis: 'SOURCE', purchaseDate: '2026-10-10', supplierName: 'NCC mẫu A', supplierCode: 'NCC1', source: 'PURCHASE_ORDER', sourceSheet: null, sourceRowNumber: 1 }
      return reply(path.endsWith('/latest') ? price : paged([price],url.searchParams))
    }
    if (path.startsWith('/api/purchase-orders')) {
      const match = path.match(/^\/api\/purchase-orders\/(\d+)(?:\/(.*))?$/), order = state.orders.find((item) => item.id === Number(match?.[1]))
      if (method === 'GET') {
        if (!match) return reply(paged(state.orders,url.searchParams))
        if (match[2] === 'revisions') return reply([{ revision: order!.revision, changedBy: 1, changeReason: 'Tạo mới', createdAt: '2026-10-10T08:00:00Z', pdfAvailable: order!.status === 'EXPORTED' }])
        if (match[2]?.includes('pdf')) return route.fulfill({ contentType: 'application/pdf', headers: {'Content-Disposition':'attachment; filename="test.pdf"'}, body: '%PDF-1.4 synthetic' })
        return reply(order)
      }
      state.writes.push({ path, data: req.postData() ? body() : {} })
      if (match?.[2] === 'issue') { order!.status = 'EXPORTED'; return reply(order) }
      if (match?.[2] === 'cancel') { order!.status = 'CANCELLED'; return reply(order) }
      if (state.failSave) { state.failSave = false; return reply({ message: 'Chưa thể lưu đơn' },409) }
      const command = body(), id = match ? Number(match[1]) : state.orders.length+1
      const items = (command.items as Record<string,unknown>[]).map((item,index) => ({...item,id:index+1,lineNo:index+1,lineTotal:item.quantity == null ? null : Number(item.quantity)*Number(item.unitPrice)}))
      const subtotal = items.reduce((sum,item) => sum+(item.lineTotal ?? 0),0), taxAmount = subtotal*Number(command.vatPercent)/100
      const saved = { ...command, id, poNumber: `PO-TEST-${id}`, supplierName: state.suppliers.find((row)=>row.id===command.supplierId)!.name, supplierAddress: 'Địa chỉ mẫu', status: 'DRAFT', revision: match ? (order!.revision+1) : 1, version:0, items, subtotal,taxAmount,grandTotal:subtotal+taxAmount,quantityTextLineCount:items.filter((item)=>item.quantity==null).length } as PurchaseOrder
      state.orders = [...state.orders.filter((item)=>item.id!==id),saved]; return reply(saved)
    }
    if (path.includes('/imports/operational/')) {
      state.operationalPreviewCalls++
      if (state.legacyDetected && path.endsWith('/preview')) return reply({code:'LEGACY_WORKBOOK_DETECTED',message:'Đây là workbook dữ liệu cũ'},400)
      return reply({ fileName:'paste.tsv',sourceType:'PASTE',confidence:.9,message:'Kiểm tra bản nháp',items:[{sourceRow:2,materialName:'Thép kiểm thử',materialCode:null,specification:null,unit:'kg',quantity:null,quantityText:'Qua cân thực tế',unitPrice:100,warnings:[]}],warnings:[] })
    }
    if (path === '/api/imports/legacy/preview') { state.legacyPreviewCalls++; return reply({...state.legacyPreview,duplicate:state.legacyPreview.status==='COMMITTED'}) }
    if (path === '/api/imports/legacy/1' && method === 'GET') return reply(state.legacyPreview)
    if (path === '/api/imports/legacy/1/issues.csv') return route.fulfill({contentType:'text/csv; charset=utf-8',headers:{'Content-Disposition':'attachment; filename="legacy-import-1-issues.csv"'},body:state.issueCsv})
    if (path.includes('/imports/legacy/') && path.endsWith('/commit')) { state.commits++; state.legacyPreview.status='COMMITTED'; return reply(state.legacyCommit) }
    return route.fulfill({ contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',headers:{'Content-Disposition':'attachment; filename="fixture.xlsx"'},body:'PKsynthetic' })
  })
  return state
}

