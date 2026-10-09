import { useState } from 'react'
import { PageHeader } from '../../../components/PageHeader'
import { Icon } from '../../../components/Icon'
import { Pagination } from '../../../components/Pagination'
import { TableFeedback } from '../../../components/TableFeedback'
import { roleLabels, type RoleCode } from '../../auth/types'
import { useUsers } from '../hooks/useUsers'
import { UserForm } from '../components/UserForm'
import { ResetPasswordForm } from '../components/ResetPasswordForm'
import type { UserAccount } from '../types'

const statusLabels = { ACTIVE: 'Hoạt động', LOCKED: 'Khóa', DISABLED: 'Vô hiệu hóa' }
export function UsersPage() {
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<UserAccount | 'new' | null>(null)
  const [resetting, setResetting] = useState<UserAccount | null>(null)
  const [notice, setNotice] = useState('')
  const users = useUsers(page)
  const data = users.data
  return <>
    <PageHeader title="Tài khoản" description="Quản lý quyền truy cập, trạng thái đăng nhập và liên kết hồ sơ nhân viên."
      actions={<button className="primary-button" onClick={() => { setNotice(''); setEditing('new') }}><Icon name="plus" />Thêm tài khoản</button>} />
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="panel" aria-label="Danh sách tài khoản">
      <div className="panel-heading"><h2>Tài khoản hệ thống</h2><div className="header-actions">{data && !users.error && <span className="table-count">{data.totalElements} tài khoản</span>}<button className="text-button" disabled={users.isFetching} onClick={() => { void users.refetch() }}>Tải lại</button></div></div>
      <TableFeedback pending={users.isPending} error={users.error} empty={!data?.content.length} onRetry={() => { void users.refetch() }} emptyTitle="Chưa có tài khoản" />
      {!users.error && data && !!data.content.length && <div className="table-wrap"><table>
        <caption className="sr-only">Danh sách tài khoản hệ thống</caption>
        <thead><tr><th scope="col">Người dùng</th><th scope="col">Vai trò</th><th scope="col">Trạng thái</th><th scope="col">Đăng nhập gần nhất</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.content.map((account) => <tr key={account.id}>
          <td><strong className="cell-title">{account.displayName}</strong><span className="cell-subtitle">{account.email}</span></td>
          <td>{account.roleCodes.map((code) => <span className="role-tag" key={code}>{roleLabels[code as RoleCode] ?? code}</span>)}</td>
          <td><span className={`status-badge ${account.status === 'ACTIVE' ? 'active' : account.status === 'LOCKED' ? 'locked' : 'inactive'}`}>{statusLabels[account.status]}</span>{account.mustChangePassword && <span className="cell-subtitle">Cần đổi mật khẩu</span>}{account.lockedUntil && new Date(account.lockedUntil).getTime() > Date.now() && <span className="cell-subtitle">Tạm khóa do đăng nhập sai</span>}</td>
          <td>{account.lastLoginAt ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(account.lastLoginAt)) : 'Chưa đăng nhập'}</td>
          <td><div className="table-actions"><button className="text-button" aria-label={`Sửa tài khoản ${account.email}`} onClick={() => setEditing(account)}>Sửa</button><button className="text-button" aria-label={`Đặt lại mật khẩu ${account.email}`} onClick={() => { setNotice(''); setResetting(account) }}>Đặt lại mật khẩu</button></div></td>
        </tr>)}</tbody>
      </table></div>}
      {!users.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={users.isFetching} onChange={setPage} />}
    </section>
    {editing && <UserForm key={editing === 'new' ? 'new' : editing.id} account={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setNotice(editing === 'new' ? 'Đã tạo tài khoản. Người dùng cần đổi mật khẩu tạm khi đăng nhập lần đầu.' : 'Đã cập nhật tài khoản và thu hồi các phiên đăng nhập hiện có.') }} />}
    {resetting && <ResetPasswordForm key={resetting.id} account={resetting} onClose={() => setResetting(null)} onSaved={() => { setResetting(null); setNotice('Đã đặt lại mật khẩu tạm và thu hồi các phiên đăng nhập. Người dùng cần đổi mật khẩu khi đăng nhập lại.') }} />}
  </>
}
