import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useSaveEmployee, useUnits } from '../hooks/usePersonnel'
import type { Employee } from '../types'

const schema = z.object({
  employeeCode: z.string().trim().min(1, 'Nhập mã nhân viên.').max(80, 'Tối đa 80 ký tự.'),
  fullName: z.string().trim().min(1, 'Nhập họ tên nhân viên.').max(255, 'Tối đa 255 ký tự.'),
  email: z.string().trim().max(255, 'Tối đa 255 ký tự.').refine((value) => !value || /^[^\s@]+@[^\s@]+$/.test(value), 'Email chưa hợp lệ.'),
  phone: z.string().trim().max(40, 'Tối đa 40 ký tự.'),
  departmentId: z.string(), positionId: z.string(), joinedAt: z.string(),
})
type Values = z.infer<typeof schema>

export function EmployeeForm({ employee, onClose, onSaved }: { employee?: Employee; onClose: () => void; onSaved: (name: string) => void }) {
  const departments = useUnits('departments')
  const positions = useUnits('positions')
  const save = useSaveEmployee(employee?.id)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: {
    employeeCode: employee?.employeeCode ?? '', fullName: employee?.fullName ?? '', email: employee?.email ?? '', phone: employee?.phone ?? '',
    departmentId: employee?.departmentId?.toString() ?? '', positionId: employee?.positionId?.toString() ?? '', joinedAt: employee?.joinedAt ?? '',
  } })
  const errors = form.formState.errors
  const busy = form.formState.isSubmitting
  const lookupError = departments.error || positions.error
  const lookupPending = departments.isPending || positions.isPending
  async function submit(values: Values) {
    try {
      const saved = await save.mutateAsync({ ...values, email: values.email || null, phone: values.phone || null,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        positionId: values.positionId ? Number(values.positionId) : null, joinedAt: values.joinedAt || null })
      onSaved(saved.fullName)
    } catch { /* The server error is displayed in the form. */ }
  }
  return <Dialog title={employee ? 'Sửa hồ sơ nhân viên' : 'Thêm nhân viên'} busy={busy} onClose={onClose}>
    <p className="dialog-description">Dấu * là trường bắt buộc. Hồ sơ nhân viên được quản lý riêng với tài khoản đăng nhập.</p>
    <form noValidate onSubmit={form.handleSubmit(submit)}>
      <fieldset disabled={busy}>
        <div className="form-grid">
          <Field id="employee-code" label="Mã nhân viên *" error={errors.employeeCode?.message}>
            <input id="employee-code" {...form.register('employeeCode')} {...fieldA11y('employee-code', errors.employeeCode?.message)} />
          </Field>
          <Field id="employee-name" label="Họ và tên *" error={errors.fullName?.message}>
            <input id="employee-name" {...form.register('fullName')} {...fieldA11y('employee-name', errors.fullName?.message)} />
          </Field>
          <Field id="employee-email" label="Email" error={errors.email?.message}>
            <input id="employee-email" type="email" {...form.register('email')} {...fieldA11y('employee-email', errors.email?.message)} />
          </Field>
          <Field id="employee-phone" label="Số điện thoại" error={errors.phone?.message}>
            <input id="employee-phone" type="tel" {...form.register('phone')} {...fieldA11y('employee-phone', errors.phone?.message)} />
          </Field>
          <Field id="employee-department" label="Phòng ban">
            <select id="employee-department" disabled={busy || lookupPending || !!lookupError} {...form.register('departmentId')}>
              <option value="">Chưa phân phòng ban</option>
              {departments.data?.filter((unit) => unit.active || unit.id === employee?.departmentId).map((unit) => <option key={unit.id} value={unit.id}>{unit.name}{!unit.active ? ' (ngừng hoạt động)' : ''}</option>)}
            </select>
          </Field>
          <Field id="employee-position" label="Chức vụ">
            <select id="employee-position" disabled={busy || lookupPending || !!lookupError} {...form.register('positionId')}>
              <option value="">Chưa phân chức vụ</option>
              {positions.data?.filter((unit) => unit.active || unit.id === employee?.positionId).map((unit) => <option key={unit.id} value={unit.id}>{unit.name}{!unit.active ? ' (ngừng hoạt động)' : ''}</option>)}
            </select>
          </Field>
          <Field id="employee-joined" label="Ngày vào làm">
            <input id="employee-joined" type="date" {...form.register('joinedAt')} />
          </Field>
        </div>
        {lookupError && <p className="form-error" role="alert">Chưa tải được phòng ban hoặc chức vụ. <button className="text-button" type="button" onClick={() => { void departments.refetch(); void positions.refetch() }}>Tải lại danh mục</button></p>}
        {save.error && <p className="form-error" role="alert">{errorMessage(save.error)}</p>}
        <div className="dialog-actions">
          <button type="button" className="secondary-button" onClick={onClose}>Hủy</button>
          <button type="submit" className="primary-button" disabled={lookupPending || !!lookupError}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button>
        </div>
      </fieldset>
    </form>
  </Dialog>
}
