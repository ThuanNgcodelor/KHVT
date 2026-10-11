import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Brand } from '../../../components/layout/Brand'
import { Footer } from '../../../components/layout/Footer'
import { authApi } from '../authApi'

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập địa chỉ email').email('Địa chỉ email không hợp lệ'),
})

type FormValues = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)
  const [serverError, setServerError] = useState('')
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '' },
  })

  async function submit(values: FormValues) {
    setServerError('')
    try {
      await authApi.requestPasswordReset(values.email)
      setSubmitted(true)
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } }; message?: string }
      setServerError(errorObj?.response?.data?.message || errorObj?.message || 'Không thể gửi yêu cầu đặt lại mật khẩu.')
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
          <h1 id="auth-title">Quên mật khẩu</h1>
          {submitted ? (
            <div className="auth-feedback-box success">
              <p className="feedback-title" style={{ fontWeight: 600, color: '#173f35', marginBottom: '8px' }}>Đã gửi yêu cầu thành công!</p>
              <p className="feedback-desc" style={{ color: '#4d5d54', fontSize: '14px', lineHeight: 1.5 }}>
                Nếu địa chỉ email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu đến hòm thư của bạn. Vui lòng kiểm tra hộp thư đến (hoặc hòm thư spam).
              </p>
              <div style={{ marginTop: '20px' }}>
                <Link to="/login" className="primary-button" style={{ display: 'block', textAlign: 'center', width: '100%', boxSizing: 'border-box' }}>
                  Quay lại Đăng nhập
                </Link>
              </div>
            </div>
          ) : (
            <>
              <p className="auth-description">
                Nhập địa chỉ email tài khoản của bạn để nhận liên kết đặt lại mật khẩu.
              </p>
              <form noValidate onSubmit={form.handleSubmit(submit)}>
                <fieldset disabled={form.formState.isSubmitting}>
                  <div className="form-field">
                    <label htmlFor="email">Email tài khoản</label>
                    <input
                      id="email"
                      type="email"
                      autoComplete="email"
                      autoFocus
                      {...form.register('email')}
                      aria-invalid={!!form.formState.errors.email}
                      aria-describedby={form.formState.errors.email ? 'email-error' : undefined}
                    />
                    {form.formState.errors.email && (
                      <p id="email-error" className="field-error">
                        {form.formState.errors.email.message}
                      </p>
                    )}
                  </div>
                  {serverError && <p className="form-error" role="alert">{serverError}</p>}
                  <button className="primary-button auth-submit" type="submit">
                    {form.formState.isSubmitting ? 'Đang gửi email…' : 'Gửi liên kết đặt lại mật khẩu'}
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
