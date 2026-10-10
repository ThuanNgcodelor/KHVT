import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useCreateUser, useRoles, useUpdateUser } from '../hooks/useUsers'
import { useAuth } from '../../auth/hooks/useAuth'
import { EmployeePicker } from './EmployeePicker'
import { RoleOptions } from './RoleOptions'
import type { UserAccount } from '../types'

const base = z.object({
  email: z.string().trim(), displayName: z.string().trim().min(1, 'Nhập tên hiển thị.').max(255, 'Tối đa 255 ký tự.'),
  password: z.string(), confirmation: z.string(), roleCodes: z.array(z.string()).min(1, 'Chọn ít nhất một vai trò.'),
  status: z.enum(['ACTIVE', 'LOCKED', 'DISABLED']),
})
type Values = z.infer<typeof base>
export function UserForm({ account, onClose, onSaved }: { account?: UserAccount; onClose: () => void; onSaved: () => void }) {
  const roles = useRoles()
  const create = useCreateUser()
  const update = useUpdateUser(account?.id)
  const auth = useAuth()
  const [employeeId, setEmployeeId] = useState<number | null>(account?.employeeId ?? null)
  const [confirmed, setConfirmed] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const form = useForm<Values>({ resolver: zodResolver(base.superRefine((value, context) => {
    if (account) return
    if (!/^[^\s@]+@[^\s@]+$/.test(value.email)) context.addIssue({ code: 'custom', path: ['email'], message: 'Nhập email hợp lệ.' })
    if (value.password.length < 12 || value.password.length > 200) context.addIssue({ code: 'custom', path: ['password'], message: 'Mật khẩu tạm cần từ 12 đến 200 ký tự.' })
    if (value.password !== value.confirmation) context.addIssue({ code: 'custom', path: ['confirmation'], message: 'Hai mật khẩu chưa khớp.' })
  })), defaultValues: { email: account?.email ?? '', displayName: account?.displayName ?? '', password: '', confirmation: '', status: account?.status ?? 'ACTIVE', roleCodes: account?.roleCodes ?? [] } })
  const errors = form.formState.errors
  const busy = form.formState.isSubmitting
  const saveError = account ? update.error : create.error
  const unavailableRoles = account?.roleCodes.filter((code) => roles.data && !roles.data.some((role) => role.code === code)) ?? []
  async function submit(values: Values) {
    if (account && !confirmed) { setConfirmed(true); return }
    try {
      if (account) {
        await update.mutateAsync({ displayName: values.displayName, employeeId, status: values.status, roleCodes: values.roleCodes })
        if (account.id === auth.user?.id) auth.refresh()
      } else {
        await create.mutateAsync({ email: values.email, displayName: values.displayName, initialPassword: values.password, employeeId, roleCodes: values.roleCodes })
      }
      form.reset()
      onSaved()
    } catch { setConfirmed(false) }
  }
  return <Dialog title={account ? 'Sửa tài khoản' : 'Thêm tài khoản'} busy={busy} onClose={onClose}>
    <p className="dialog-description">{account ? 'Lưu thay đổi sẽ thu hồi các phiên đăng nhập hiện có của tài khoản này.' : 'Tài khoản mới dùng mật khẩu tạm và phải đổi mật khẩu khi đăng nhập lần đầu.'}</p>
    <form noValidate onSubmit={form.handleSubmit(submit)} onChange={() => setConfirmed(false)}><fieldset disabled={busy}>
      <div className="form-grid">
        <Field id="account-email" label="Email đăng nhập *" error={errors.email?.message}>
          <input id="account-email" type="email" readOnly={!!account} autoComplete="off" {...form.register('email')} {...fieldA11y('account-email', errors.email?.message)} />
        </Field>
        <Field id="account-name" label="Tên hiển thị *" error={errors.displayName?.message}>
          <input id="account-name" {...form.register('displayName')} {...fieldA11y('account-name', errors.displayName?.message)} />
        </Field>
        {!account && <>
          <Field id="account-password" label="Mật khẩu tạm *" error={errors.password?.message} help="Từ 12 đến 200 ký tự.">
            <input id="account-password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" {...form.register('password')} {...fieldA11y('account-password', errors.password?.message)} />
          </Field>
          <Field id="account-confirmation" label="Nhập lại mật khẩu tạm *" error={errors.confirmation?.message}>
            <input id="account-confirmation" type={showPassword ? 'text' : 'password'} autoComplete="new-password" {...form.register('confirmation')} {...fieldA11y('account-confirmation', errors.confirmation?.message)} />
          </Field>
        </>}
        {account && <Field id="account-status" label="Trạng thái"><select id="account-status" {...form.register('status')}><option value="ACTIVE">Hoạt động</option><option value="LOCKED" disabled={account.id === auth.user?.id}>Khóa</option><option value="DISABLED" disabled={account.id === auth.user?.id}>Vô hiệu hóa</option></select></Field>}
      </div>
      {!account && <label className="checkbox-field"><input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} />Hiện mật khẩu</label>}
      <EmployeePicker value={employeeId} onChange={(id) => { setEmployeeId(id); setConfirmed(false) }} disabled={busy} />
      <RoleOptions roles={roles.data ?? []} unavailable={unavailableRoles} registration={form.register('roleCodes')} pending={roles.isPending} error={errors.roleCodes?.message} />
      {roles.error && <p className="form-error" role="alert">{errorMessage(roles.error)} <button className="text-button" type="button" onClick={() => { void roles.refetch() }}>Tải lại vai trò</button></p>}
      {confirmed && <p className="notice" role="status">Xác nhận lưu thay đổi cho {account?.email}. Các phiên đăng nhập của tài khoản sẽ bị thu hồi.</p>}
      {saveError && <p className="form-error" role="alert">{errorMessage(saveError)}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose}>Hủy</button><button className="primary-button" type="submit" disabled={roles.isPending || !!roles.error || !roles.data?.length}>{busy ? 'Đang lưu…' : confirmed ? 'Xác nhận lưu' : account ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></div>
    </fieldset></form>
  </Dialog>
}
