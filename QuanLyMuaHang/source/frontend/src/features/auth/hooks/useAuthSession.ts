import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, authEvents, resetCsrf } from '../../../services/apiClient'
import { authApi } from '../authApi'
import type { CurrentUser } from '../types'
import type { AuthContextValue } from '../AuthContext'

const authKey = ['auth', 'me'] as const
export function useAuthSession(): AuthContextValue {
  const client = useQueryClient()
  const session = useQuery({
    queryKey: authKey,
    queryFn: async ({ signal }) => {
      try { return await authApi.me(signal) }
      catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    retry: false,
    staleTime: 30_000,
  })

  async function clearSession() {
    await client.cancelQueries()
    client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
    resetCsrf()
    client.setQueryData(authKey, null)
  }

  useEffect(() => {
    const expired = () => {
      void client.cancelQueries()
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
      resetCsrf()
      client.setQueryData(authKey, null)
    }
    const passwordRequired = () => {
      client.setQueryData<CurrentUser | null>(authKey, (user) => user ? { ...user, mustChangePassword: true } : null)
    }
    authEvents.addEventListener('session-expired', expired)
    authEvents.addEventListener('password-change-required', passwordRequired)
    return () => {
      authEvents.removeEventListener('session-expired', expired)
      authEvents.removeEventListener('password-change-required', passwordRequired)
    }
  }, [client])

  useEffect(() => {
    if (session.data === null) {
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
      resetCsrf()
    }
  }, [session.data, client])

  const value: AuthContextValue = {
    user: session.data ?? null,
    pending: session.isPending,
    error: session.error,
    refresh: () => { void session.refetch() },
    login: async (email, password) => {
      const user = await authApi.login(email.trim(), password)
      await client.cancelQueries()
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
      client.setQueryData(authKey, user)
      // The next mutation will retry fetching CSRF if this refresh is offline.
      await authApi.refreshCsrf().catch(() => undefined)
    },
    changePassword: async (currentPassword, newPassword) => {
      const user = await authApi.changePassword(currentPassword, newPassword)
      client.setQueryData(authKey, user)
    },
    logout: async () => {
      try { await authApi.logout() }
      catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error }
      await clearSession()
    },
  }
  return value
}
