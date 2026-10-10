import { buildApiUrl } from '../config/baseApi'
import type { ApiEnvelope } from '../types/api'

type CsrfToken = { headerName: string; token: string }
let csrfToken: CsrfToken | null = null
let csrfRequest: Promise<CsrfToken> | null = null
let csrfGeneration = 0
export const authEvents = new EventTarget()

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export function resetCsrf() {
  csrfGeneration += 1
  csrfToken = null
  csrfRequest = null
}

export async function refreshCsrf(): Promise<CsrfToken> {
  resetCsrf()
  return ensureCsrf()
}

async function ensureCsrf(): Promise<CsrfToken> {
  if (csrfToken) return csrfToken
  if (csrfRequest) return csrfRequest
  const generation = csrfGeneration
  const pending = request<CsrfToken>('/auth/csrf').then((token) => {
    if (!token || typeof token.headerName !== 'string' || typeof token.token !== 'string') {
      throw new ApiError(502, 'Không nhận được mã bảo vệ phiên. Hãy thử lại.')
    }
    if (generation === csrfGeneration) csrfToken = token
    return token
  }).finally(() => { if (csrfRequest === pending) csrfRequest = null })
  csrfRequest = pending
  return pending
}

async function fetchResponse(path: string, init?: RequestInit, accept = 'application/json'): Promise<Response> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const headers = new Headers(init?.headers)
  headers.set('Accept', accept)
  headers.set('X-Request-ID', crypto.randomUUID())
  if (init?.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await ensureCsrf()
    headers.set(token.headerName, token.token)
  }
  const response = await fetch(buildApiUrl(path), {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as
      { code?: string; message?: string; error?: { code?: string; message?: string } } | null
    const detail = payload?.error ?? payload
    if (response.status === 401 && path !== '/auth/login' && path !== '/auth/me') {
      resetCsrf()
      authEvents.dispatchEvent(new Event('session-expired'))
    }
    if (response.status === 403) resetCsrf()
    if (detail?.code === 'PASSWORD_CHANGE_REQUIRED') authEvents.dispatchEvent(new Event('password-change-required'))
    const fallback = response.status === 401 ? 'Phiên đăng nhập đã hết hạn.'
      : response.status === 403 && path === '/auth/login' ? 'Không thể xác nhận phiên đăng nhập. Hãy tải lại trang và thử lại.'
      : response.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.' : 'Không thể xử lý yêu cầu. Hãy thử lại.'
    throw new ApiError(response.status, detail?.message || fallback, detail?.code)
  }

  return response
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetchResponse(path, init)
  if (response.status === 204) return undefined as T

  // Some Spring controllers return 200 with no body (e.g. password reset).
  const content = await response.text()
  if (!content.trim()) return undefined as T
  const payload = JSON.parse(content) as T | ApiEnvelope<T>
  if (payload && typeof payload === 'object' && 'data' in payload && 'error' in payload) {
    const envelope = payload as ApiEnvelope<T>
    if (envelope.error) throw new ApiError(response.status, envelope.error.message)
    return envelope.data
  }
  return payload as T
}

export const apiClient = {
  get: <T>(path: string, init?: Pick<RequestInit, 'signal'>) => request<T>(path, init),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  upload: <T>(path: string, body: FormData) => request<T>(path, { method: 'POST', body }),
  download: async (path: string, method: 'GET' | 'POST' = 'GET'): Promise<DownloadFile> => {
    const response = await fetchResponse(path, { method }, 'application/pdf, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    const disposition = response.headers.get('Content-Disposition') ?? ''
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const plain = disposition.match(/filename="([^"]+)"|filename=([^;]+)/i)
    let name = plain?.[1] ?? plain?.[2] ?? 'tai-lieu'
    if (encoded) { try { name = decodeURIComponent(encoded) } catch { /* Keep the safe fallback. */ } }
    const fileName = name.replace(/[\\/\u0000-\u001f]/g, '_').trim() || 'tai-lieu'
    return { blob: await response.blob(), fileName, truncated: response.headers.get('X-Export-Truncated') === 'true' }
  },
}

export type DownloadFile = { blob: Blob; fileName: string; truncated: boolean }
export function saveDownload(file: DownloadFile) {
  const url = URL.createObjectURL(file.blob)
  const link = document.createElement('a')
  link.href = url; link.download = file.fileName
  document.body.append(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}
