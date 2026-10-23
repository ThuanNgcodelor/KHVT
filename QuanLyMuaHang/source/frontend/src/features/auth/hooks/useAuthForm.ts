import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '../../../services/apiClient'
import { useAuth } from './useAuth'
import { useLogout } from './useLogout'

const schema = z.object({
  email: z.string(), password: z.string().min(1, 'Nhập mật khẩu hiện tại.'),
  newPassword: z.string(), confirmation: z.string(),
  rememberMe: z.boolean().optional(),
})
type FormValues = z.infer<typeof schema>
const loginEmail = z.string().trim().regex(/^[^\s@]+@[^\s@]+$/)

export function useAuthForm(mode: 'login' | 'change-password') {
  const auth = useAuth()
  const changing = mode === 'change-password'
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState('')
  const logout = useLogout()
  const savedEmail = typeof window !== 'undefined' ? localStorage.getItem('qmh_saved_email') || '' : ''
  const savedRememberMe = typeof window !== 'undefined' ? localStorage.getItem('qmh_remember_me') !== 'false' : true

  const form = useForm<FormValues>({
    resolver: zodResolver(schema.superRefine((values, context) => {
      if (!changing && !loginEmail.safeParse(values.email).success) {
        context.addIssue({ code: 'custom', path: ['email'], message: 'Nhập địa chỉ email hợp lệ.' })
      }
      if (changing) {
        if (values.newPassword.length < 12 || values.newPassword.length > 200) context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới cần từ 12 đến 200 ký tự.' })
        if (values.newPassword === values.password) context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới phải khác mật khẩu hiện tại.' })
        if (values.newPassword !== values.confirmation) context.addIssue({ code: 'custom', path: ['confirmation'], message: 'Hai mật khẩu mới chưa khớp.' })
      }
    })),
    defaultValues: { email: savedEmail, password: '', newPassword: '', confirmation: '', rememberMe: savedRememberMe },
  })

  async function submit(values: FormValues) {
    setServerError('')
    try {
      if (changing) {
        await auth.changePassword(values.password, values.newPassword)
        form.reset()
      } else {
        if (values.rememberMe) {
          localStorage.setItem('qmh_saved_email', values.email.trim())
          localStorage.setItem('qmh_remember_me', 'true')
        } else {
          localStorage.removeItem('qmh_saved_email')
          localStorage.setItem('qmh_remember_me', 'false')
        }
        await auth.login(values.email, values.password, values.rememberMe)
        // Keep credentials in DOM so the browser password manager triggers "Save password?"
      }
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Chưa kết nối được hệ thống. Hãy thử lại.')
      form.setValue('password', '')
    }
  }

  return {
    auth, changing, form, submit, showPassword, setShowPassword,
    serverError: serverError || logout.error, loggingOut: logout.pending, logout: logout.signout,
    busy: form.formState.isSubmitting || logout.pending, errors: form.formState.errors,
  }
}
