import { buildApiUrl } from '../config/baseApi'
import type { ApiEnvelope } from '../types/api'

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(buildApiUrl(path), {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(response.status, body || `API request failed (${response.status})`)
  }

  if (response.status === 204) return undefined as T

  const payload = (await response.json()) as T | ApiEnvelope<T>
  if (payload && typeof payload === 'object' && 'data' in payload && 'error' in payload) {
    const envelope = payload as ApiEnvelope<T>
    if (envelope.error) throw new ApiError(response.status, envelope.error.message)
    return envelope.data
  }
  return payload as T
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
}
