import { apiClient } from '../../../services/apiClient'
import type { Confirmation, DocumentKind, DocumentView, HistoryLookup, InquirySelection, QuoteComparison } from './types'
const path = '/imports/documents'
export const documentApi = {
  list: () => apiClient.get<DocumentView[]>(path),
  get: (id: number) => apiClient.get<DocumentView>(`${path}/${id}`),
  preview: (file: File, kind: DocumentKind, sheet = '') => {
    const data = new FormData(); data.set('file', file)
    const query = new URLSearchParams({ kind }); if (sheet) query.set('sheet', sheet)
    return apiClient.upload<DocumentView>(`${path}/preview?${query}`, data)
  },
  confirm: (id: number, data: Confirmation) => apiClient.post<DocumentView>(`${path}/${id}/confirm`, data),
  source: (id: number) => apiClient.download(`${path}/${id}/source`),
  lookup: (id: number) => apiClient.post<HistoryLookup>(`${path}/${id}/lookup`),
  saveInquiries: (id: number, selections: InquirySelection[]) => apiClient.post<DocumentView>(`${path}/${id}/inquiries`, { selections }),
  inquiries: (id: number, selections: InquirySelection[]) => apiClient.download(`${path}/${id}/inquiries.xlsx`, 'POST', { selections }),
  comparison: (id: number) => apiClient.get<QuoteComparison>(`${path}/${id}/comparison`),
}
