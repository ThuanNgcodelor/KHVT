export type DocumentKind = 'REQUEST' | 'QUOTATION'
export type Decimal = number | string | null
export type DocumentLine = {
  lineKey: string; sourceSheet: string | null; sourceRow: number | null; sourcePage: number | null
  requestNumber: string | null; materialCode: string | null; materialName: string; specification: string | null
  unit: string | null; quantity: Decimal; quantityText: string | null; unitPrice: Decimal; lineTotal: Decimal
  vatPercent: Decimal; priceBasis: string | null; currency: string | null; note: string | null
  raw: Record<string, string>; issues: string[]
}
export type ParsedDocument = {
  kind: DocumentKind; fileName: string; format: string
  sheets: { name: string; detectedLines: number; requestNumbers: string[]; quotationDate: string | null }[]
  lines: DocumentLine[]; supplierName: string | null; quotationDate: string | null; currency: string | null
  vatPercent: Decimal; priceBasis: string | null; validityNote: string | null; warnings: string[]; parserVersion: string
}
export type InquirySelection = { lineKey: string; supplierKey: string }
export type Confirmation = {
  title: string; requestNumber: string | null; supplierName: string | null; quotationDate: string | null
  currency: string | null; priceBasis: string | null; vatPercent: Decimal; lines: DocumentLine[]
  requestId: number | null; requestLineMappings: Record<string, string>; acknowledged: boolean
}
export type DocumentView = {
  id: number; kind: DocumentKind; title: string; fileName: string; status: string; duplicate: boolean
  parsed: ParsedDocument; confirmed: Confirmation | null; inquirySelections: InquirySelection[]
  sourceDocumentId?: number | null
}
export type HistoryCandidate = {
  supplierKey: string; supplierId?: number | null; supplierCode: string | null; supplierName: string
  materialCode: string | null; materialName: string; unit: string | null; unitPrice: Decimal
  currency: string | null; currencyBasis: string | null; purchaseDate: string | null
  source: string; sourceReference: string; qualityWarnings: string[]; count: number
  specification?: string | null; priceBasis?: string | null; vatPercent?: Decimal
}
export type HistoryLookup = { lines: {
  lineKey: string; matchStatus: string; materialCode: string | null; materialName: string; candidates: HistoryCandidate[]
}[] }
export type QuoteComparison = { lines: {
  lineKey: string; materialName: string; unit: string | null; quantity: Decimal; history: HistoryCandidate[]
  quotes: { documentId: number; supplierName: string; unit: string | null; quantity: Decimal; unitPrice: Decimal
    currency: string | null; vatPercent: Decimal; priceBasis: string | null; comparable: boolean
    reason: string | null; delta: Decimal; percent: Decimal; baseline?: HistoryCandidate | null }[]
}[] }
