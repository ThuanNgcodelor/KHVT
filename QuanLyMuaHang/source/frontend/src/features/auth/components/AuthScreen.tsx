import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { ApiError } from '../../services/apiClient'
import { PageState } from '../../components/PageState'

const schema = z.object({ email: z.string(), password: z.string().min(1, 'Nhập mật khẩu hiện tại.'), newPassword: z.string(), confirmation: z.string() })
type FormValues = z.infer<typeof schema>

export function AuthPage({ mode }: { mode: 'login' | 'change-password' }) {
  const auth = useAuth()
  const changing = mode === 'change-password'
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema.superRefine((values, context) => {
      if (!changing && !z.email().safeParse(values.email.trim()).success) context.addIssue({ code: 'custom', path: ['email'], message: 'Nhập địa chỉ email hợp lệ.' })
      if (changing) {
        if (values.newPassword.length < 12 || values.newPassword.length > 200) context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới cần từ 12 đến 200 ký tự.' })
        if (values.newPassword === values.password) context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới phải khác mật khẩu hiện tại.' })
        if (values.newPassword !== values.confirmation) context.addIssue({ code: 'custom', path: ['confirmation'], message: 'Hai mật khẩu mới chưa khớp.' })
      }
    })),
    defaultValues: { email: '', password: '', newPassword: '', confirmation: '' },
  })

  if (auth.pending) return <PageState title="Đang kiểm tra phiên đăng nhập…" />
  if (auth.error) return <PageState title="Chưa kết nối được hệ thống" message="Kiểm tra kết nối rồi thử lại." onRetry={auth.refresh} />
  if (changing && !auth.user) return <Navigate to="/login" replace />
  if (auth.user && !auth.user.mustChangePassword) return <Navigate to="/dashboard" replace />
  if (!changing && auth.user?.mustChangePassword) return <Navigate to="/change-password" replace />

  async function submit(values: FormValues) {
    setServerError('')
    try {
      if (changing) await auth.changePassword(values.password, values.newPassword)
      else await auth.login(values.email, values.password)
      form.reset()
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Chưa kết nối được hệ thống. Hãy thử lại.')
      form.setValue('password', '')
    }
  }
  async function logout() {
    setLoggingOut(true)
    setServerError('')
    try { await auth.logout() }
    catch { setServerError('Chưa đăng xuất được. Hãy kiểm tra kết nối và thử lại.') }
    finally { setLoggingOut(false) }
  }

  const busy = form.formState.isSubmitting || loggingOut
  const errors = form.formState.errors
  return <main className="auth-layout">
    <section className="auth-intro" aria-label="Quản lý mua hàng KHVT"><div className="brand-block"><div className="brand-mark">M</div><div><p className="brand-name">MUA HÀNG</p><p className="brand-caption">PHÒNG KẾ HOẠCH VẬT TƯ</p></div></div><h2>Công việc mua hàng,<br />trong một nơi.</h2><p>Tra cứu vật tư, theo dõi đơn mua và phối hợp công việc theo quyền được cấp.</p><span className="auth-footnote">Hệ thống nội bộ · Quản lý mua hàng KHVT</span></section>
    <section className="auth-card" aria-labelledby="auth-title">
      <p className="eyebrow">{changing ? 'Bảo vệ tài khoản' : 'Chào mừng bạn trở lại'}</p><h1 id="auth-title">{changing ? 'Đổi mật khẩu lần đầu' : 'Đăng nhập'}</h1><p className="auth-description">{changing ? 'Đặt mật khẩu riêng trước khi sử dụng các chức năng của hệ thống.' : 'Dùng tài khoản đã được quản trị viên cấp.'}</p>
      {changing && <p className="account-email">{auth.user?.email}</p>}
      <form noValidate onSubmit={form.handleSubmit(submit)}><fieldset disabled={busy}>
        {!changing && <div className="form-field"><label htmlFor="email">Email</label><input id="email" type="email" autoComplete="username" autoFocus {...form.register('email')} aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} />{errors.email && <p id="email-error" className="field-error">{errors.email.message}</p>}</div>}
        <div className="form-field"><label htmlFor="password">{changing ? 'Mật khẩu hiện tại' : 'Mật khẩu'}</label><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" autoFocus={changing} {...form.register('password')} aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : undefined} />{errors.password && <p id="password-error" className="field-error">{errors.password.message}</p>}</div>
        {changing && <><div className="form-field"><label htmlFor="new-password">Mật khẩu mới</label><input id="new-password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" {...form.register('newPassword')} aria-invalid={!!errors.newPassword} aria-describedby="new-password-help new-password-error" /><p id="new-password-help" className="field-help">Từ 12 đến 200 ký tự, khác mật khẩu hiện tại.</p><p id="new-password-error" className="field-error">{errors.newPassword?.message}</p></div><div className="form-field"><label htmlFor="confirmation">Nhập lại mật khẩu mới</label><input id="confirmation" type={showPassword ? 'text' : 'password'} autoComplete="new-password" {...form.register('confirmation')} aria-invalid={!!errors.confirmation} aria-describedby={errors.confirmation ? 'confirmation-error' : undefined} />{errors.confirmation && <p id="confirmation-error" className="field-error">{errors.confirmation.message}</p>}</div></>}
        <label className="checkbox-field"><input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} />Hiện mật khẩu</label>
        {serverError && <p className="form-error" role="alert">{serverError}</p>}
        <button className="primary-button auth-submit" type="submit">{form.formState.isSubmitting ? 'Đang xử lý…' : changing ? 'Lưu mật khẩu mới' : 'Đăng nhập'}</button>
      </fieldset></form>
      {changing && <button className="secondary-button auth-signout" type="button" disabled={busy} onClick={() => { void logout() }}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>}
    </section>
  </main>
}
