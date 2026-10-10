import type { Currency, Decimal } from './types'

export function formatMoney(value: Decimal | null, currency: Currency) {
  if (value === null) return 'Chưa xác định'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? new Intl.NumberFormat('vi-VN', { minimumFractionDigits: currency === 'USD' ? 2 : 0, maximumFractionDigits: currency === 'USD' ? 2 : 0 }).format(numeric) : String(value)
}
export function formatQuantity(value: Decimal | null, text?: string | null) {
  if (value === null) return text || '—'
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 6 }).format(Number(value))
}
export function formatOrderDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`)) : '—'
}
