/**
 * Chỉ sửa file .env (VITE_API_BASE_URL) khi đổi môi trường.
 * File này là cổng cấu hình duy nhất cho mọi API của frontend.
 */
const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api'

export const BASE_API_URL = configuredBaseUrl.replace(/\/$/, '')

export function buildApiUrl(path: string): string {
  return `${BASE_API_URL}/${path.replace(/^\//, '')}`
}
