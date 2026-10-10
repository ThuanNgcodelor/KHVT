import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useSaveSupplier } from '../hooks/useCatalog'
import type { Supplier } from '../types'

const schema = z.object({
  code: z.string().trim().max(50, 'Tối đa 50 ký tự.'), name: z.string().trim().min(1, 'Nhập tên nhà cung cấp.').max(500, 'Tối đa 500 ký tự.'),
  address: z.string().trim().max(1000, 'Tối đa 1.000 ký tự.'), taxCode: z.string().trim().max(50, 'Tối đa 50 ký tự.'), phone: z.string().trim().max(50, 'Tối đa 50 ký tự.'),
  email: z.string().trim().max(320, 'Tối đa 320 ký tự.').refine((value) => !value || /^[^\s@]+@[^\s@]+$/.test(value), 'Email chưa đúng định dạng.'),
})
type Values = z.infer<typeof schema>
export function SupplierForm({ supplier, onClose, onSaved }: { supplier?: Supplier; onClose: () => void; onSaved: (name: string) => void }) {
  const save = useSaveSupplier(supplier?.id)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { code: supplier?.code ?? '', name: supplier?.name ?? '', address: supplier?.address ?? '', taxCode: supplier?.taxCode ?? '', phone: supplier?.phone ?? '', email: supplier?.email ?? '' } })
  const errors = form.formState.errors, busy = form.formState.isSubmitting
  async function submit(values: Values) {
    try {
      const saved = await save.mutateAsync({ code: values.code || null, name: values.name, address: values.address || null, taxCode: values.taxCode || null, phone: values.phone || null, email: values.email || null, active: supplier?.active ?? true })
      onSaved(saved.name)
    } catch { /* Keep submitted values available while displaying the API error. */ }
  }
  return <Dialog title={supplier ? 'Sửa nhà cung cấp' : 'Thêm nhà cung cấp'} busy={busy} onClose={onClose}>
    <form noValidate onSubmit={form.handleSubmit(submit)}><fieldset disabled={busy}>
      <Field id="supplier-code" label="Mã nhà cung cấp" error={errors.code?.message}><input id="supplier-code" {...form.register('code')} {...fieldA11y('supplier-code', errors.code?.message)} /></Field>
      <Field id="supplier-name" label="Tên nhà cung cấp *" error={errors.name?.message}><input id="supplier-name" {...form.register('name')} {...fieldA11y('supplier-name', errors.name?.message)} /></Field>
      <Field id="supplier-address" label="Địa chỉ" error={errors.address?.message}><textarea id="supplier-address" rows={2} {...form.register('address')} {...fieldA11y('supplier-address', errors.address?.message)} /></Field>
      <div className="form-grid"><Field id="supplier-tax" label="Mã số thuế" error={errors.taxCode?.message}><input id="supplier-tax" {...form.register('taxCode')} {...fieldA11y('supplier-tax', errors.taxCode?.message)} /></Field>
        <Field id="supplier-phone" label="Điện thoại" error={errors.phone?.message}><input id="supplier-phone" type="tel" {...form.register('phone')} {...fieldA11y('supplier-phone', errors.phone?.message)} /></Field></div>
      <Field id="supplier-email" label="Email" error={errors.email?.message}><input id="supplier-email" type="email" {...form.register('email')} {...fieldA11y('supplier-email', errors.email?.message)} /></Field>
      <p className="field-help">{supplier ? `Trạng thái: ${supplier.active ? 'Đang sử dụng' : 'Đã ngừng'}. Đổi trạng thái bằng thao tác Ngừng hoặc Kích hoạt trong danh sách.` : 'Nhà cung cấp mới được tạo ở trạng thái đang sử dụng.'}</p>
      {save.error && <p className="form-error" role="alert">{errorMessage(save.error)}</p>}
      <div className="dialog-actions"><button type="button" className="secondary-button" onClick={onClose}>Hủy</button><button type="submit" className="primary-button">{busy ? 'Đang lưu…' : 'Lưu'}</button></div>
    </fieldset></form>
  </Dialog>
}
