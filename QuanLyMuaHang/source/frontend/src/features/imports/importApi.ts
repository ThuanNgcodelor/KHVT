import { apiClient } from '../../services/apiClient'
import type { DraftPreview, LegacyPreview, LegacyCommit } from './types'
function form(file: File) { const data = new FormData(); data.set('file', file); return data }
export const importApi = {
  operational: (file: File) => apiClient.upload<DraftPreview>('/imports/operational/preview', form(file)),
  paste: (content: string) => apiClient.post<DraftPreview>('/imports/operational/paste', { content }),
  legacy: (file: File) => apiClient.upload<LegacyPreview>('/imports/legacy/preview', form(file)),
  batch: (id: number) => apiClient.get<LegacyPreview>(`/imports/legacy/${id}`),
  issues: (id: number) => apiClient.download(`/imports/legacy/${id}/issues.csv`),
  commit: (id: number) => apiClient.post<LegacyCommit>(`/imports/legacy/${id}/commit`),
}
