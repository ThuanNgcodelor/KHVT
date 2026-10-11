import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../../services/apiClient'
import { errorMessage } from '../../../services/errorMessage'
import { documentApi } from './documentApi'
import type { DocumentView } from './types'

export function useDocumentImports() {
  const client = useQueryClient(), [params, setParams] = useSearchParams()
  const rawId = Number(params.get('document')), id = Number.isSafeInteger(rawId) && rawId > 0 ? rawId : null
  const list = useQuery({ queryKey: ['imports', 'documents'], queryFn: documentApi.list })
  const detail = useQuery({ queryKey: ['imports', 'documents', id], queryFn: () => documentApi.get(id!), enabled: !!id })
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const pending = useRef(false)
  function open(nextId: number | null) {
    setParams((current) => { const next = new URLSearchParams(current); if (nextId) next.set('document', String(nextId)); else next.delete('document'); return next })
    setError(''); setNotice('')
  }
  async function remember(document: DocumentView) {
    client.setQueryData(['imports', 'documents', document.id], document)
    open(document.id)
    await client.invalidateQueries({ queryKey: ['imports', 'documents'], exact: true })
  }
  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    if (pending.current) return undefined
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try { return await action() }
    catch (failure) { setError(failure instanceof Error && !(failure instanceof ApiError) ? failure.message : errorMessage(failure)); return undefined }
    finally { pending.current = false; setBusy(false) }
  }
  return { list, detail, active: detail.data, id, busy, error, notice, open, remember, run, setNotice }
}
