import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useCreateUser, useRoles, useUpdateUser, useUserOptions } from '../hooks/useUsers'
import { useAuth } from '../../auth/hooks/useAuth'
import { EmployeePicker } from './EmployeePicker'
import { RoleOptions } from './RoleOptions'
import type { UserAccount } from '../types'

const base = z.object({
  email: z.string().trim(), displayName: z.string().trim().min(1, 'Nhập tên hiển thị.').max(255, 'Tối đa 255 ký tự.'),
  password: z.string(), confirmation: z.string(), roleCodes: z.array(z.string()).min(1, 'Chọn ít nhất một vai trò.'),
  status: z.enum(['ACTIVE', 'LOCKED', 'DISABLED']),
  employeeCode: z.string().trim().max(80, 'Tối đa 80 ký tự.'), departmentId: z.string(), positionId: z.string(),
})
type Values = z.infer<typeof base>
export function UserForm({ account, onClose, onSaved }: { account?: UserAccount; onClose: () => void; onSaved: () => void }) {
  const roles = useRoles()
  const options = useUserOptions()
  const create = useCreateUser()
  const update = useUpdateUser(account?.id)
  const auth = useAuth()
  const [employeeId, setEmployeeId] = useState<number | null>(account?.employeeId ?? null)
  const [profileMode, setProfileMode] = useState<'INLINE' | 'EXISTING'>('INLINE')
  const [confirmed, setConfirmed] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const form = useForm<Values>({ resolver: zodResolver(base.superRefine((value, context) => {
    if (account) return
    if (!/^[^\s@]+@[^\s@]+$/.test(value.email)) context.addIssue({ code: 'custom', path: ['email'], message: 'Nhập email hợp lệ.' })
    if (value.password.length < 12 || value.password.length > 200) context.addIssue({ code: 'custom', path: ['password'], message: 'Mật khẩu tạm cần từ 12 đến 200 ký tự.' })
    if (value.password !== value.confirmation) context.addIssue({ code: 'custom', path: ['confirmation'], message: 'Hai mật khẩu chưa khớp.' })
  })), defaultValues: { email: account?.email ?? '', displayName: account?.displayName ?? '', password: '', confirmation: '', status: account?.status ?? 'ACTIVE', roleCodes: account?.roleCodes ?? [],
    employeeCode: account?.employeeCode ?? '', departmentId: account?.departmentId?.toString() ?? '', positionId: account?.positionId?.toString() ?? '' } })
  const errors = form.formState.errors
  const busy = form.formState.isSubmitting
  const saveError = account ? update.error : create.error
  const unavailableRoles = account?.roleCodes.filter((code) => roles.data && !roles.data.some((role) => role.code === code)) ?? []
  async function submit(values: Values) {
    if (account && !confirmed) { setConfirmed(true); return }
    const employeeProfile = profileMode === 'INLINE' && (employeeId !== null || values.employeeCode || values.departmentId || values.positionId)
      ? { employeeCode: values.employeeCode || null, departmentId: values.departmentId ? Number(values.departmentId) : null, positionId: values.positionId ? Number(values.positionId) : null } : null
    try {
      if (account) {
        await update.mutateAsync({ displayName: values.displayName, employeeId, status: values.status, roleCodes: values.roleCodes, employeeProfile })
        if (account.id === auth.user?.id) auth.refresh()
      } else {
        await create.mutateAsync({ email: values.email, displayName: values.displayName, initialPassword: values.password, employeeId, roleCodes: values.roleCodes, employeeProfile })
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
      <section className="account-profile" aria-labelledby="account-profile-title">
        <h3 id="account-profile-title">Thông tin nhân viên <span>(tùy chọn)</span></h3>
        <p className="field-help">Phòng ban và chức vụ dùng để mô tả người làm việc. Quyền dùng ứng dụng được cấp bằng vai trò bên dưới.</p>
        <label className="checkbox-field"><input type="checkbox" checked={profileMode === 'EXISTING'} onChange={(event) => {
          setProfileMode(event.target.checked ? 'EXISTING' : 'INLINE'); setConfirmed(false)
          if (!event.target.checked) { setEmployeeId(null); form.setValue('employeeCode', ''); form.setValue('departmentId', ''); form.setValue('positionId', '') }
        }} />Liên kết hồ sơ đã có</label>
        {profileMode === 'EXISTING' ? <EmployeePicker value={employeeId} onChange={(id) => { setEmployeeId(id); setConfirmed(false) }} disabled={busy} /> : <>
          <div className="form-grid">
            <Field id="account-employee-code" label="Mã nhân viên" error={errors.employeeCode?.message} help="Để trống để tự sinh mã khi có phòng ban hoặc chức vụ.">
              <input id="account-employee-code" {...form.register('employeeCode')} {...fieldA11y('account-employee-code', errors.employeeCode?.message)} />
            </Field>
            <Field id="account-department" label="Phòng ban"><select id="account-department" {...form.register('departmentId')} disabled={options.isPending || !!options.error}>
              <option value="">Chưa phân phòng ban</option>
              {options.data?.departments.filter((unit) => unit.active || unit.id === account?.departmentId).map((unit) => <option key={unit.id} value={unit.id}>{unit.name}{!unit.active ? ' (ngừng hoạt động)' : ''}</option>)}
            </select></Field>
            <Field id="account-position" label="Chức vụ"><select id="account-position" {...form.register('positionId')} disabled={options.isPending || !!options.error}>
              <option value="">Chưa phân chức vụ</option>
              {options.data?.positions.filter((unit) => unit.active || unit.id === account?.positionId).map((unit) => <option key={unit.id} value={unit.id}>{unit.name}{!unit.active ? ' (ngừng hoạt động)' : ''}</option>)}
            </select></Field>
          </div>
          <p className="field-help">{employeeId === null ? 'Có thể bỏ trống cả ba mục để tạo tài khoản độc lập. Khi điền, hồ sơ nhân viên và tài khoản được lưu cùng lúc.' : 'Đang sửa hồ sơ nhân viên liên kết. Họ tên trong hồ sơ sẽ được cập nhật theo tên hiển thị.'}</p>
          {account?.employeeStatus === 'INACTIVE' && <p className="field-error">Hồ sơ đã ngừng làm việc. Cần kích hoạt hồ sơ hoặc chọn bỏ liên kết trước khi mở tài khoản.</p>}
          {options.isPending && <p className="field-help" role="status">Đang tải phòng ban và chức vụ…</p>}
          {options.error && <p className="form-error" role="alert">Chưa tải được phòng ban hoặc chức vụ. <button className="text-button" type="button" onClick={() => { void options.refetch() }}>Tải lại danh mục</button></p>}
        </>}
      </section>
      <RoleOptions roles={roles.data ?? []} unavailable={unavailableRoles} registration={form.register('roleCodes')} pending={roles.isPending} error={errors.roleCodes?.message} />
      {roles.error && <p className="form-error" role="alert">{errorMessage(roles.error)} <button className="text-button" type="button" onClick={() => { void roles.refetch() }}>Tải lại vai trò</button></p>}
      {confirmed && <p className="notice" role="status">Xác nhận lưu thay đổi cho {account?.email}. Các phiên đăng nhập của tài khoản sẽ bị thu hồi.</p>}
      {saveError && <p className="form-error" role="alert">{errorMessage(saveError)}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose}>Hủy</button><button className="primary-button" type="submit" disabled={roles.isPending || !!roles.error || !roles.data?.length || (profileMode === 'INLINE' && (options.isPending || !!options.error))}>{busy ? 'Đang lưu…' : confirmed ? 'Xác nhận lưu' : account ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></div>
    </fieldset></form>
  </Dialog>
}
