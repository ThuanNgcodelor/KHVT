import { Navigate } from 'react-router-dom'
import { useAuthForm } from '../hooks/useAuthForm'
import { PageState } from '../../../components/PageState'
import { Brand } from '../../../components/layout/Brand'
import { Footer } from '../../../components/layout/Footer'

export function AuthScreen({ mode }: { mode: 'login' | 'change-password' }) {
  const { auth, changing, form, submit, showPassword, setShowPassword, serverError, loggingOut, logout, busy, errors } = useAuthForm(mode)

  if (auth.pending) return <PageState title="Đang kiểm tra phiên đăng nhập…" />
  if (auth.error) return <PageState title="Chưa kết nối được hệ thống" message="Kiểm tra kết nối rồi thử lại." onRetry={auth.refresh} />
  if (changing && !auth.user) return <Navigate to="/login" replace />
  if (auth.user && !auth.user.mustChangePassword) return <Navigate to="/dashboard" replace />
  if (!changing && auth.user?.mustChangePassword) return <Navigate to="/change-password" replace />

  return <div className="auth-layout">
    <header className="auth-header"><Brand /><span>Phòng Kế hoạch Vật tư</span></header>
    <main className="auth-content">
    <section className="auth-card" aria-labelledby="auth-title">
      <p className="eyebrow">Quản lý mua hàng KHVT</p><h1 id="auth-title">{changing ? 'Đổi mật khẩu lần đầu' : 'Đăng nhập'}</h1><p className="auth-description">{changing ? 'Đặt mật khẩu riêng trước khi sử dụng hệ thống.' : 'Nhập thông tin tài khoản được cấp để tiếp tục.'}</p>
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
    <Footer />
  </div>
}
