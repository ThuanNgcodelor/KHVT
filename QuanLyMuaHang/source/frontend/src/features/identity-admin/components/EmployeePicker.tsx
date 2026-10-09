import { useDeferredValue, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { personnelApi } from '../../personnel/personnelApi'
import { errorMessage } from '../../../services/errorMessage'
export function EmployeePicker({ value, onChange, disabled }: { value: number | null; onChange: (value: number | null) => void; disabled: boolean }) {
  const [search, setSearch] = useState('')
  const q = useDeferredValue(search.trim())
  const selected = useQuery({ queryKey: ['personnel', 'employee', value], queryFn: ({ signal }) => personnelApi.employee(value!, signal), enabled: value !== null })
  const choices = useQuery({ queryKey: ['personnel', 'employee-options', q], queryFn: ({ signal }) => personnelApi.employees({ q, status: 'ACTIVE', page: 0, size: 25 }, signal), enabled: value === null && q.length > 0 })
  return <div className="form-field">
    <label htmlFor="linked-employee">Liên kết nhân viên</label>
    {value !== null ? <>
      <div className="selected-employee"><span>{selected.data ? `${selected.data.employeeCode} · ${selected.data.fullName}` : selected.isPending ? 'Đang tải nhân viên…' : `Nhân viên #${value}`}</span><button className="text-button" type="button" disabled={disabled} onClick={() => { onChange(null); setSearch('') }}>Bỏ liên kết</button></div>
      {selected.error && <p className="field-error" role="alert">{errorMessage(selected.error)} <button className="text-button" type="button" onClick={() => { void selected.refetch() }}>Thử lại</button></p>}
      {selected.data?.status === 'INACTIVE' && <p className="field-error">Nhân viên đã ngừng làm việc; cần kích hoạt lại hồ sơ hoặc bỏ liên kết trước khi lưu.</p>}
    </> : <>
      <input id="linked-employee" type="search" placeholder="Tìm mã hoặc tên nhân viên…" value={search} disabled={disabled} onChange={(event) => setSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') event.preventDefault() }} />
      <p className="field-help">Không bắt buộc. Chỉ liên kết với nhân viên đang hoạt động.</p>
      {q && choices.isPending && <p className="field-help" role="status">Đang tìm nhân viên…</p>}
      {q && choices.error && <p className="field-error" role="alert">{errorMessage(choices.error)} <button className="text-button" type="button" onClick={() => { void choices.refetch() }}>Thử lại</button></p>}
      {q && choices.data && !choices.error && <div className="employee-choices" aria-label="Kết quả tìm nhân viên">
        {choices.data.content.length ? choices.data.content.map((employee) => <button className="employee-choice" type="button" key={employee.id} disabled={disabled} onClick={() => onChange(employee.id)}>{employee.employeeCode} · {employee.fullName}</button>) : <p className="field-help">Không có nhân viên phù hợp.</p>}
        {choices.data.totalElements > choices.data.content.length && <p className="field-help">Có thêm kết quả. Nhập cụ thể hơn để chọn đúng nhân viên.</p>}
      </div>}
    </>}
  </div>
}
