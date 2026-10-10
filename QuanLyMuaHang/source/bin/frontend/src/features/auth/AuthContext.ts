import { createContext } from 'react'
import type { CurrentUser } from './types'

export type AuthContextValue = {
  user: CurrentUser | null
  pending: boolean
  error: Error | null
  refresh: () => void
  login: (email: string, password: string) => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
