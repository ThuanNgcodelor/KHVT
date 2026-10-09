import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useAuth } from '../../auth/hooks/useAuth'
import { useResetPassword } from '../hooks/useUsers'
import type { UserAccount } from '../types'
const schema = z.object({ password: z.string().min(12, 'Mật khẩu tạm cần ít nhất 12 ký tự.').max(200, 'Tối đa 200 ký tự.'), confirmation: z.string() })
  .refine((data) => data.password === data.confirmation, { path: ['confirmation'], message: 'Hai mật khẩu chưa khớp.' })
type Values = z.infer<typeof schema>
export function ResetPasswordForm({ account, onClose, onSaved }: { account: UserAccount; onClose: () => void; onSaved: () => void }) {
  const reset = useResetPassword(account.id)
  const auth = useAuth()
  const [confirmed, setConfirmed] = useState(false)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { password: '', confirmation: '' } })
  const busy = form.formState.isSubmitting
  async function submit(values: Values) {
    if (!confirmed) { setConfirmed(true); return }
    try {
      await reset.mutateAsync(values.password)
      form.reset()
      if (account.id === auth.user?.id) auth.refresh()
      onSaved()
    } catch { setConfirmed(false) }
  }
  return <Dialog title="Đặt lại mật khẩu" busy={busy} onClose={onClose}>
    <p className="dialog-description">Tài khoản: <strong>{account.email}</strong>. Đặt mật khẩu tạm sẽ thu hồi các phiên hiện có và yêu cầu đổi mật khẩu ở lần đăng nhập tiếp theo.</p>
    <form noValidate onSubmit={form.handleSubmit(submit)} onChange={() => setConfirmed(false)}><fieldset disabled={busy}>
      <Field id="reset-password" label="Mật khẩu tạm mới *" error={form.formState.errors.password?.message}><input id="reset-password" type="password" autoComplete="new-password" {...form.register('password')} {...fieldA11y('reset-password', form.formState.errors.password?.message)} /></Field>
      <Field id="reset-confirmation" label="Nhập lại mật khẩu tạm *" error={form.formState.errors.confirmation?.message}><input id="reset-confirmation" type="password" autoComplete="new-password" {...form.register('confirmation')} {...fieldA11y('reset-confirmation', form.formState.errors.confirmation?.message)} /></Field>
      {confirmed && <p className="form-error" role="alert">Nhấn “Xác nhận đặt lại” để thay mật khẩu và thu hồi các phiên đăng nhập.</p>}
      {reset.error && <p className="form-error" role="alert">{errorMessage(reset.error)}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose}>Hủy</button><button className={confirmed ? 'danger-button' : 'primary-button'} type="submit">{busy ? 'Đang xử lý…' : confirmed ? 'Xác nhận đặt lại' : 'Đặt lại mật khẩu'}</button></div>
    </fieldset></form>
  </Dialog>
}
