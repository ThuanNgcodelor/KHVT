import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiClient, authEvents, resetCsrf } from './apiClient'
import { authApi } from '../features/auth/authApi'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const fetchMock = vi.fn<typeof fetch>()
beforeEach(() => { resetCsrf(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock) })
afterEach(() => vi.unstubAllGlobals())

describe('cookie and CSRF requests', () => {
  it('gets CSRF before writing and includes cookies and JSON headers', async () => {
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fixture-csrf' })).mockResolvedValueOnce(json({ id: 1 }))
    await apiClient.post('/example', { name: 'test' })
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/auth/csrf', '/api/example'])
    for (const [, options] of fetchMock.mock.calls) expect(options?.credentials).toBe('include')
    const options = fetchMock.mock.calls[1][1]!
    const headers = new Headers(options.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('fixture-csrf')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-Request-ID')).toBeTruthy()
    expect(options.body).toBe(JSON.stringify({ name: 'test' }))
  })

  it('shares an in-flight CSRF request across concurrent writes', async () => {
    fetchMock.mockImplementation(async (url) => String(url).endsWith('/csrf')
      ? json({ headerName: 'X-XSRF-TOKEN', token: 'fixture' }) : json({ ok: true }))
    await Promise.all([apiClient.post('/one'), apiClient.post('/two')])
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))).toHaveLength(1)
  })

  it('fetches a fresh CSRF token after login before the next mutation', async () => {
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'before-login' }))
      .mockResolvedValueOnce(json({ id: 1 })).mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'after-login' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    await authApi.login('fixture@example.test', 'synthetic-password')
    await authApi.logout()
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-XSRF-TOKEN')).toBe('after-login')
  })

  it('refreshes a cached CSRF token before login after another tab changes its cookie', async () => {
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'stale-from-another-tab' }))
      .mockResolvedValueOnce(json({ ok: true }))
      .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'current-cookie-token' }))
      .mockResolvedValueOnce(json({ id: 1 }))
    await apiClient.post('/example')
    await authApi.login('fixture@example.test', 'synthetic-password')
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/auth/csrf', '/api/example', '/api/auth/csrf', '/api/auth/login'])
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-XSRF-TOKEN')).toBe('current-cookie-token')
  })

  it('does not describe a non-JSON login rejection as a missing account permission', async () => {
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fixture' }))
      .mockResolvedValueOnce(new Response('Invalid CORS request', { status: 403 }))
    await expect(authApi.login('fixture@example.test', 'synthetic-password'))
      .rejects.toThrow('Không thể xác nhận phiên đăng nhập. Hãy tải lại trang và thử lại.')
  })

  it('keeps business permission errors distinct from CSRF errors', async () => {
    fetchMock.mockResolvedValueOnce(json({ code: 'FORBIDDEN', message: 'Bạn không có quyền thực hiện thao tác này' }, 403))
    await expect(apiClient.get('/admin/users')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fixture' }))
      .mockResolvedValueOnce(json({ code: 'CSRF_INVALID', message: 'Phiên bảo vệ đã thay đổi. Hãy tải lại trang và đăng nhập lại.' }, 403))
    await expect(authApi.login('fixture@example.test', 'synthetic-password'))
      .rejects.toMatchObject({ status: 403, code: 'CSRF_INVALID', message: 'Phiên bảo vệ đã thay đổi. Hãy tải lại trang và đăng nhập lại.' })
  })

  it('signals expired business sessions, but does not signal a rejected login', async () => {
    const expired = vi.fn()
    authEvents.addEventListener('session-expired', expired)
    try {
      fetchMock.mockResolvedValueOnce(json({ code: 'UNAUTHENTICATED' }, 401))
      await expect(apiClient.get('/dashboard')).rejects.toBeInstanceOf(ApiError)
      expect(expired).toHaveBeenCalledTimes(1)
      fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fixture' })).mockResolvedValueOnce(json({ message: 'Thông tin đăng nhập không đúng.' }, 401))
      await expect(authApi.login('fixture@example.test', 'wrong')).rejects.toThrow('Thông tin đăng nhập không đúng.')
      expect(expired).toHaveBeenCalledTimes(1)
    } finally { authEvents.removeEventListener('session-expired', expired) }
  })

  it('signals the forced password change and clears stale CSRF on 403', async () => {
    const required = vi.fn()
    authEvents.addEventListener('password-change-required', required)
    try {
      fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'stale' })).mockResolvedValueOnce(json({ code: 'PASSWORD_CHANGE_REQUIRED' }, 403))
      await expect(apiClient.post('/example')).rejects.toBeInstanceOf(ApiError)
      expect(required).toHaveBeenCalledOnce()
      fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fresh' })).mockResolvedValueOnce(json({ ok: true }))
      await apiClient.post('/example')
      expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-XSRF-TOKEN')).toBe('fresh')
    } finally { authEvents.removeEventListener('password-change-required', required) }
  })

  it('uses a safe error when a proxy returns HTML', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>internal details</html>', { status: 502 }))
    await expect(apiClient.get('/dashboard')).rejects.toThrow('Không thể xử lý yêu cầu. Hãy thử lại.')
  })

  it('accepts Spring password-reset responses with an empty 200 body', async () => {
    fetchMock.mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'fixture' }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))
    await expect(apiClient.post<void>('/admin/users/20/reset-password', { temporaryPassword: 'synthetic-new-password' })).resolves.toBeUndefined()
  })
})
