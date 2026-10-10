import { useState } from 'react'
import { useAuth } from './useAuth'

export function useLogout() {
  const { logout } = useAuth()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function signout() {
    setPending(true)
    setError('')
    try { await logout() }
    catch { setError('Chưa đăng xuất được. Hãy kiểm tra kết nối và thử lại.') }
    finally { setPending(false) }
  }
  return { signout, pending, error }
}
