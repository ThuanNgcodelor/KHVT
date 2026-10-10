import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useSaveUnit } from '../hooks/usePersonnel'
import type { Unit } from '../types'

const schema = z.object({ code: z.string().trim().max(80, 'Tối đa 80 ký tự.'), name: z.string().trim().min(1, 'Nhập tên.').max(255, 'Tối đa 255 ký tự.'), parentId: z.string(), active: z.boolean() })
type Values = z.infer<typeof schema>
export function UnitForm({ kind, unit, units, onClose, onSaved }: { kind: 'departments' | 'positions'; unit?: Unit; units: Unit[]; onClose: () => void; onSaved: () => void }) {
  const department = kind === 'departments'
  const label = department ? 'phòng ban' : 'chức vụ'
  const save = useSaveUnit(kind, unit?.id)
  const [confirmed, setConfirmed] = useState(false)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { code: unit?.code ?? '', name: unit?.name ?? '', parentId: unit?.parentId?.toString() ?? '', active: unit?.active ?? true } })
  const errors = form.formState.errors
  const busy = form.formState.isSubmitting
  const inactiveChange = !!unit?.active && !form.watch('active')
  async function submit(values: Values) {
    if (inactiveChange && !confirmed) { setConfirmed(true); return }
    try {
      await save.mutateAsync({ ...values, code: values.code || null, parentId: department && values.parentId ? Number(values.parentId) : null })
      onSaved()
    } catch { /* Show server error without losing input. */ }
  }
  return <Dialog title={`${unit ? 'Sửa' : 'Thêm'} ${label}`} onClose={onClose} busy={busy}>
    <form noValidate onSubmit={form.handleSubmit(submit)} onChange={() => setConfirmed(false)}><fieldset disabled={busy}>
      <Field id="unit-code" label={`Mã ${label}`} error={errors.code?.message}><input id="unit-code" {...form.register('code')} {...fieldA11y('unit-code', errors.code?.message)} /></Field>
      <Field id="unit-name" label={`Tên ${label} *`} error={errors.name?.message}><input id="unit-name" {...form.register('name')} {...fieldA11y('unit-name', errors.name?.message)} /></Field>
      {department && <Field id="unit-parent" label="Phòng ban cấp trên"><select id="unit-parent" {...form.register('parentId')}><option value="">Không có</option>{units.filter((item) => item.id !== unit?.id).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>}
      {unit && <label className="checkbox-field"><input type="checkbox" {...form.register('active')} onChange={(event) => { form.setValue('active', event.target.checked); setConfirmed(false) }} />Đang hoạt động</label>}
      {!unit && <p className="field-help">{department ? 'Phòng ban' : 'Chức vụ'} mới sẽ được tạo ở trạng thái hoạt động.</p>}
      {inactiveChange && confirmed && <p className="form-error" role="alert">Bạn đang ngừng {label} này. Bản ghi vẫn được giữ trong danh bạ; nhấn “Xác nhận lưu” để tiếp tục.</p>}
      {save.error && <p className="form-error" role="alert">{errorMessage(save.error)}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose}>Hủy</button><button className="primary-button" type="submit">{busy ? 'Đang lưu…' : inactiveChange && confirmed ? 'Xác nhận lưu' : 'Lưu'}</button></div>
    </fieldset></form>
  </Dialog>
}
