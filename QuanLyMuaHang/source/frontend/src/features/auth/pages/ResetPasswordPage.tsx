import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Brand } from '../../../components/layout/Brand'
import { Footer } from '../../../components/layout/Footer'
import { authApi } from '../authApi'

const schema = z.object({
  newPassword: z.string().min(12, 'Mật khẩu mới cần từ 12 đến 200 ký tự').max(200, 'Mật khẩu mới tối đa 200 ký tự'),
  confirmation: z.string().min(1, 'Vui lòng nhập lại mật khẩu mới'),
}).superRefine((values, ctx) => {
  if (values.newPassword !== values.confirmation) {
    ctx.addIssue({ code: 'custom', path: ['confirmation'], message: 'Hai mật khẩu mới chưa khớp nhau' })
  }
})

type FormValues = z.infer<typeof schema>

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [showPassword, setShowPassword] = useState(false)
  const [success, setSuccess] = useState(false)
  const [serverError, setServerError] = useState('')

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', confirmation: '' },
  })

  async function submit(values: FormValues) {
    setServerError('')
    try {
      await authApi.resetPassword(token, values.newPassword)
      setSuccess(true)
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } }; message?: string }
      setServerError(errorObj?.response?.data?.message || errorObj?.message || 'Không thể đặt lại mật khẩu.')
    }
  }

  return (
    <div className="auth-layout">
      <header className="auth-header">
        <Brand />
        <span>Phòng Kế hoạch Vật tư</span>
      </header>
      <main className="auth-content">
        <section className="auth-card" aria-labelledby="auth-title">
          <p className="eyebrow">Quản lý mua hàng KHVT</p>
          <h1 id="auth-title">Đặt lại mật khẩu</h1>

          {!token ? (
            <div className="auth-feedback-box error" style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '6px', padding: '16px' }}>
              <p className="feedback-title" style={{ fontWeight: 600, color: '#991b1b', marginBottom: '8px' }}>
                Liên kết không hợp lệ
              </p>
              <p className="feedback-desc" style={{ color: '#7f1d1d', fontSize: '14px', lineHeight: 1.5 }}>
                Đường dẫn đặt lại mật khẩu thiếu mã xác thực hợp lệ. Vui lòng kiểm tra lại liên kết trong email hoặc gửi lại yêu cầu mới.
              </p>
              <div style={{ marginTop: '20px' }}>
                <Link to="/forgot-password" className="primary-button" style={{ display: 'block', textAlign: 'center', width: '100%', boxSizing: 'border-box' }}>
                  Gửi lại yêu cầu quên mật khẩu
                </Link>
              </div>
            </div>
          ) : success ? (
            <div className="auth-feedback-box success" style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '6px', padding: '16px' }}>
              <p className="feedback-title" style={{ fontWeight: 600, color: '#166534', marginBottom: '8px' }}>
                Đặt lại mật khẩu thành công!
              </p>
              <p className="feedback-desc" style={{ color: '#14532d', fontSize: '14px', lineHeight: 1.5 }}>
                Mật khẩu tài khoản của bạn đã được cập nhật thành công. Vui lòng đăng nhập lại với mật khẩu mới.
              </p>
              <div style={{ marginTop: '20px' }}>
                <Link to="/login" className="primary-button" style={{ display: 'block', textAlign: 'center', width: '100%', boxSizing: 'border-box' }}>
                  Đăng nhập ngay
                </Link>
              </div>
            </div>
          ) : (
            <>
              <p className="auth-description">
                Nhập mật khẩu mới cho tài khoản của bạn (từ 12 đến 200 ký tự).
              </p>
              <form noValidate onSubmit={form.handleSubmit(submit)}>
                <fieldset disabled={form.formState.isSubmitting}>
                  <div className="form-field">
                    <label htmlFor="new-password">Mật khẩu mới</label>
                    <input
                      id="new-password"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="new-password"
                      autoFocus
                      {...form.register('newPassword')}
                      aria-invalid={!!form.formState.errors.newPassword}
                      aria-describedby="new-password-help new-password-error"
                    />
                    <p id="new-password-help" className="field-help">Từ 12 đến 200 ký tự.</p>
                    {form.formState.errors.newPassword && (
                      <p id="new-password-error" className="field-error">
                        {form.formState.errors.newPassword.message}
                      </p>
                    )}
                  </div>

                  <div className="form-field">
                    <label htmlFor="confirmation">Nhập lại mật khẩu mới</label>
                    <input
                      id="confirmation"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="new-password"
                      {...form.register('confirmation')}
                      aria-invalid={!!form.formState.errors.confirmation}
                      aria-describedby={form.formState.errors.confirmation ? 'confirmation-error' : undefined}
                    />
                    {form.formState.errors.confirmation && (
                      <p id="confirmation-error" className="field-error">
                        {form.formState.errors.confirmation.message}
                      </p>
                    )}
                  </div>

                  <div className="auth-options" style={{ margin: '10px 0 16px' }}>
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={showPassword}
                        onChange={(event) => setShowPassword(event.target.checked)}
                      />
                      Hiện mật khẩu
                    </label>
                  </div>

                  {serverError && <p className="form-error" role="alert">{serverError}</p>}
                  <button className="primary-button auth-submit" type="submit">
                    {form.formState.isSubmitting ? 'Đang cập nhật mật khẩu…' : 'Đổi mật khẩu'}
                  </button>
                </fieldset>
              </form>
              <div style={{ marginTop: '16px', textAlign: 'center' }}>
                <Link to="/login" className="auth-link">
                  Quay lại trang Đăng nhập
                </Link>
              </div>
            </>
          )}
        </section>
      </main>
      <Footer />
    </div>
  )
}
