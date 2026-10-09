import { useState } from 'react'
import { PageHeader } from '../../../components/PageHeader'
import { Icon } from '../../../components/Icon'
import { Pagination } from '../../../components/Pagination'
import { TableFeedback } from '../../../components/TableFeedback'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { errorMessage } from '../../../services/errorMessage'
import { EmployeeForm } from '../components/EmployeeForm'
import { PersonnelTabs } from '../components/PersonnelTabs'
import { useEmployees, useEmployeeStatus } from '../hooks/usePersonnel'
import type { Employee } from '../types'

export function EmployeesPage() {
  const [search, setSearch] = useState('')
  const [q, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Employee | 'new' | null>(null)
  const [confirm, setConfirm] = useState<Employee | null>(null)
  const [notice, setNotice] = useState('')
  const employees = useEmployees({ q, status, page, size: 25 })
  const changeStatus = useEmployeeStatus()
  const data = employees.data
  async function confirmStatus() {
    if (!confirm) return
    try {
      const saved = await changeStatus.mutateAsync({ id: confirm.id, active: confirm.status !== 'ACTIVE' })
      setConfirm(null)
      setNotice(saved.status === 'ACTIVE' ? `Đã kích hoạt nhân viên ${saved.fullName}. Tài khoản đăng nhập cần được quản trị viên kiểm tra riêng.` : `Đã ngừng nhân viên ${saved.fullName}.`)
    } catch { /* Keep dialog open with the server error. */ }
  }
  return <>
    <PageHeader title="Nhân sự" description="Danh bạ nhân viên, phòng ban và chức vụ của đơn vị."
      actions={<button className="primary-button" onClick={() => { setNotice(''); setEditing('new') }}><Icon name="plus" />Thêm nhân viên</button>} />
    <PersonnelTabs />
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="panel" aria-label="Danh sách nhân viên">
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); setPage(0) }}>
        <div className="form-field search-field"><label htmlFor="employee-search">Mã hoặc tên nhân viên</label><input id="employee-search" type="search" placeholder="Nhập mã hoặc họ tên…" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
        <div className="form-field"><label htmlFor="employee-status">Trạng thái</label><select id="employee-status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}><option value="">Tất cả</option><option value="ACTIVE">Đang làm việc</option><option value="INACTIVE">Đã ngừng</option></select></div>
        <button className="secondary-button" type="submit"><Icon name="search" size={16} />Tìm kiếm</button>
        <button className="text-button" type="button" onClick={() => { setSearch(''); setQuery(''); setStatus(''); setPage(0) }}>Xóa lọc</button>
        {data && !employees.error && <span className="table-count">{data.totalElements} nhân viên</span>}
      </form>
      <TableFeedback pending={employees.isPending} error={employees.error} empty={!data?.content.length} onRetry={() => { void employees.refetch() }}
        emptyTitle={q || status ? 'Không có nhân viên phù hợp' : 'Chưa có nhân viên'} emptyMessage={q || status ? 'Thử tên khác hoặc xóa bộ lọc.' : 'Thêm nhân viên để bắt đầu xây dựng danh bạ.'} />
      {!employees.error && data && data.content.length > 0 && <div className="table-wrap"><table>
        <caption className="sr-only">Danh sách nhân viên KHVT</caption>
        <thead><tr><th scope="col">Mã NV</th><th scope="col">Họ và tên</th><th scope="col">Phòng ban / Chức vụ</th><th scope="col">Liên hệ</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.content.map((employee) => <tr key={employee.id}>
          <td className="code-cell">{employee.employeeCode}</td>
          <td><strong className="cell-title">{employee.fullName}</strong><span className="cell-subtitle">{employee.joinedAt ? `Vào làm: ${new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(new Date(`${employee.joinedAt}T00:00:00Z`))}` : 'Chưa có ngày vào làm'}</span></td>
          <td>{employee.departmentName ?? 'Chưa phân phòng ban'}<span className="cell-subtitle">{employee.positionName ?? 'Chưa phân chức vụ'}</span></td>
          <td>{employee.email ?? '—'}<span className="cell-subtitle">{employee.phone ?? '—'}</span></td>
          <td><span className={`status-badge ${employee.status === 'ACTIVE' ? 'active' : 'inactive'}`}>{employee.status === 'ACTIVE' ? 'Đang làm việc' : 'Đã ngừng'}</span></td>
          <td><div className="table-actions"><button className="text-button" aria-label={`Sửa ${employee.fullName}`} onClick={() => setEditing(employee)}>Sửa</button>
            <button className={`text-button ${employee.status === 'ACTIVE' ? 'danger' : ''}`} aria-label={`${employee.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'} ${employee.fullName}`} onClick={() => { changeStatus.reset(); setConfirm(employee) }}>{employee.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}</button></div></td>
        </tr>)}</tbody>
      </table></div>}
      {!employees.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={employees.isFetching} onChange={setPage} />}
    </section>
    {editing && <EmployeeForm key={editing === 'new' ? 'new' : editing.id} employee={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={(name) => { setEditing(null); setNotice(`Đã lưu hồ sơ ${name}.`) }} />}
    {confirm && <ConfirmDialog title={confirm.status === 'ACTIVE' ? 'Ngừng nhân viên' : 'Kích hoạt nhân viên'}
      message={confirm.status === 'ACTIVE' ? `Ngừng ${confirm.fullName}? Tài khoản liên kết sẽ bị vô hiệu hóa và các phiên đăng nhập bị thu hồi.` : `Kích hoạt lại ${confirm.fullName}? Thao tác này chỉ kích hoạt hồ sơ nhân viên, không tự mở lại tài khoản đăng nhập.`}
      confirmLabel={confirm.status === 'ACTIVE' ? 'Xác nhận ngừng' : 'Xác nhận kích hoạt'} danger={confirm.status === 'ACTIVE'} busy={changeStatus.isPending}
      error={changeStatus.error ? errorMessage(changeStatus.error) : undefined} onClose={() => setConfirm(null)} onConfirm={() => { void confirmStatus() }} />}
  </>
}
