import { useState } from 'react'
import { PageHeader } from '../../../components/PageHeader'
import { Icon } from '../../../components/Icon'
import { TableFeedback } from '../../../components/TableFeedback'
import { PersonnelTabs } from '../components/PersonnelTabs'
import { UnitForm } from '../components/UnitForm'
import { useUnits } from '../hooks/usePersonnel'
import type { Unit } from '../types'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'

export function UnitsPage({ kind }: { kind: 'departments' | 'positions' }) {
  const { user } = useAuth()
  const canManage = !!user && hasPermission(user, 'PERSONNEL_MANAGE')
  const units = useUnits(kind)
  const [editing, setEditing] = useState<Unit | 'new' | null>(null)
  const [notice, setNotice] = useState('')
  const department = kind === 'departments'
  const title = department ? 'Phòng ban' : 'Chức vụ'
  return <>
    <PageHeader title={title} description={`Quản lý danh mục ${title.toLowerCase()} dùng trong hồ sơ nhân viên.`}
      actions={canManage && <button className="primary-button" onClick={() => { setNotice(''); setEditing('new') }} disabled={units.isPending || !!units.error}><Icon name="plus" />Thêm {title.toLowerCase()}</button>} />
    <PersonnelTabs />
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="panel" aria-label={`Danh sách ${title.toLowerCase()}`}>
      <div className="panel-heading"><h2>{title}</h2>{units.data && !units.error && <span className="table-count">{units.data.length} bản ghi</span>}</div>
      <TableFeedback pending={units.isPending} error={units.error} empty={!units.data?.length} onRetry={() => { void units.refetch() }} emptyTitle={`Chưa có ${title.toLowerCase()}`} />
      {!units.error && !!units.data?.length && <div className="table-wrap"><table>
        <caption className="sr-only">Danh mục {title.toLowerCase()}</caption>
        <thead><tr><th scope="col">Mã</th><th scope="col">Tên {title.toLowerCase()}</th>{department && <th scope="col">Cấp trên</th>}<th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{units.data.map((unit) => <tr key={unit.id}><td className="code-cell">{unit.code ?? '—'}</td><td><strong className="cell-title">{unit.name}</strong></td>{department && <td>{units.data?.find((item) => item.id === unit.parentId)?.name ?? '—'}</td>}
          <td><span className={`status-badge ${unit.active ? 'active' : 'inactive'}`}>{unit.active ? 'Đang hoạt động' : 'Đã ngừng'}</span></td><td>{canManage ? <div className="table-actions"><button className="text-button" aria-label={`Sửa ${unit.name}`} onClick={() => setEditing(unit)}>Sửa</button></div> : '—'}</td></tr>)}</tbody>
      </table></div>}
    </section>
    {editing && <UnitForm key={editing === 'new' ? 'new' : editing.id} kind={kind} units={units.data ?? []} unit={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setNotice(`Đã lưu ${title.toLowerCase()}.`) }} />}
  </>
}
