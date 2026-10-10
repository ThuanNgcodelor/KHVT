import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Dialog } from '../../../components/Dialog'
import { Field, fieldA11y } from '../../../components/Field'
import { errorMessage } from '../../../services/errorMessage'
import { useSaveMaterial } from '../hooks/useCatalog'
import { materialCategoryLabels, type Material } from '../types'

const schema = z.object({
  code: z.string().trim().max(80, 'Tối đa 80 ký tự.'), name: z.string().trim().min(1, 'Nhập tên vật tư.').max(500, 'Tối đa 500 ký tự.'),
  category: z.enum(['MATERIAL', 'SERVICE', 'OTHER']), defaultUnit: z.string().trim().max(100, 'Tối đa 100 ký tự.'),
})
type Values = z.infer<typeof schema>
export function MaterialForm({ material, onClose, onSaved }: { material?: Material; onClose: () => void; onSaved: (name: string) => void }) {
  const save = useSaveMaterial(material?.id)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { code: material?.code ?? '', name: material?.name ?? '', category: material?.category ?? 'MATERIAL', defaultUnit: material?.defaultUnit ?? '' } })
  const errors = form.formState.errors, busy = form.formState.isSubmitting
  async function submit(values: Values) {
    try {
      const saved = await save.mutateAsync({ ...values, code: values.code || null, defaultUnit: values.defaultUnit || null, active: material?.active ?? true })
      onSaved(saved.name)
    } catch { /* Keep submitted values available while displaying the API error. */ }
  }
  return <Dialog title={material ? 'Sửa vật tư' : 'Thêm vật tư'} busy={busy} onClose={onClose}>
    <form noValidate onSubmit={form.handleSubmit(submit)}><fieldset disabled={busy}>
      <Field id="material-code" label="Mã vật tư" error={errors.code?.message}><input id="material-code" {...form.register('code')} {...fieldA11y('material-code', errors.code?.message)} /></Field>
      <Field id="material-name" label="Tên vật tư *" error={errors.name?.message}><input id="material-name" {...form.register('name')} {...fieldA11y('material-name', errors.name?.message)} /></Field>
      <div className="form-grid"><Field id="material-category" label="Phân loại *" error={errors.category?.message}><select id="material-category" {...form.register('category')} {...fieldA11y('material-category', errors.category?.message)}>{Object.entries(materialCategoryLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
        <Field id="material-unit" label="Đơn vị tính mặc định" error={errors.defaultUnit?.message}><input id="material-unit" {...form.register('defaultUnit')} {...fieldA11y('material-unit', errors.defaultUnit?.message)} /></Field></div>
      <p className="field-help">{material ? `Trạng thái: ${material.active ? 'Đang sử dụng' : 'Đã ngừng'}. Đổi trạng thái bằng thao tác Ngừng hoặc Kích hoạt trong danh sách.` : 'Vật tư mới được tạo ở trạng thái đang sử dụng.'}</p>
      {save.error && <p className="form-error" role="alert">{errorMessage(save.error)}</p>}
      <div className="dialog-actions"><button type="button" className="secondary-button" onClick={onClose}>Hủy</button><button type="submit" className="primary-button">{busy ? 'Đang lưu…' : 'Lưu'}</button></div>
    </fieldset></form>
  </Dialog>
}
