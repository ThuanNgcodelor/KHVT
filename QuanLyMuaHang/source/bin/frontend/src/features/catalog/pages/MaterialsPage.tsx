import { useState } from 'react'
import { PageHeader } from '../../../components/PageHeader'
import { Icon } from '../../../components/Icon'
import { Pagination } from '../../../components/Pagination'
import { TableFeedback } from '../../../components/TableFeedback'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { errorMessage } from '../../../services/errorMessage'
import { useAuth } from '../../auth/hooks/useAuth'
import { hasPermission } from '../../auth/types'
import { CatalogTabs } from '../components/CatalogTabs'
import { MaterialForm } from '../components/MaterialForm'
import { useMaterials, useMaterialStatus } from '../hooks/useCatalog'
import { materialCategoryLabels, type Material, type MaterialCategory } from '../types'

export function MaterialsPage() {
  const { user } = useAuth()
  const canManage = !!user && hasPermission(user, 'CATALOG_MANAGE')
  const [search, setSearch] = useState(''), [q, setQuery] = useState('')
  const [active, setActive] = useState(''), [category, setCategory] = useState<MaterialCategory | ''>('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Material | 'new' | null>(null)
  const [confirm, setConfirm] = useState<Material | null>(null), [notice, setNotice] = useState('')
  const materials = useMaterials({ q, active: active === '' ? undefined : active === 'true', category: category || undefined, page, size: 25 })
  const changeStatus = useMaterialStatus(), data = materials.data
  async function confirmStatus() {
    if (!confirm) return
    try {
      const saved = await changeStatus.mutateAsync({ id: confirm.id, active: !confirm.active })
      setConfirm(null)
      setNotice(`Đã ${saved.active ? 'kích hoạt' : 'ngừng'} vật tư ${saved.name}.`)
    } catch { /* Keep the confirmation open with the API error. */ }
  }
  return <>
    <PageHeader title="Vật tư" description="Danh mục vật tư, dịch vụ và đơn vị tính dùng khi lập đơn mua hàng."
      actions={canManage && <button className="primary-button" onClick={() => { setNotice(''); setEditing('new') }}><Icon name="plus" />Thêm vật tư</button>} />
    <CatalogTabs />
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="panel" aria-label="Danh sách vật tư">
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); setPage(0) }}>
        <div className="form-field search-field"><label htmlFor="material-search">Mã hoặc tên vật tư</label><input id="material-search" type="search" placeholder="Nhập mã hoặc tên…" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
        <div className="form-field"><label htmlFor="material-active">Trạng thái</label><select id="material-active" value={active} onChange={(event) => { setActive(event.target.value); setPage(0) }}><option value="">Tất cả</option><option value="true">Đang sử dụng</option><option value="false">Đã ngừng</option></select></div>
        <div className="form-field"><label htmlFor="material-filter-category">Phân loại</label><select id="material-filter-category" value={category} onChange={(event) => { setCategory(event.target.value as MaterialCategory | ''); setPage(0) }}><option value="">Tất cả</option>{Object.entries(materialCategoryLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <button className="secondary-button" type="submit"><Icon name="search" size={16} />Tìm kiếm</button>
        <button className="text-button" type="button" onClick={() => { setSearch(''); setQuery(''); setActive(''); setCategory(''); setPage(0) }}>Xóa lọc</button>
        {data && !materials.error && <span className="table-count">{data.totalElements} vật tư</span>}
      </form>
      <TableFeedback pending={materials.isPending} error={materials.error} empty={!data?.content.length} onRetry={() => { void materials.refetch() }}
        emptyTitle={q || active || category ? 'Không có vật tư phù hợp' : 'Chưa có vật tư'} emptyMessage={q || active || category ? 'Thử từ khóa khác hoặc xóa bộ lọc.' : canManage ? 'Thêm vật tư để bắt đầu lập danh mục.' : 'Danh mục sẽ hiển thị khi có vật tư.'} />
      {!materials.error && data && data.content.length > 0 && <div className="table-wrap"><table>
        <caption className="sr-only">Danh sách vật tư KHVT</caption>
        <thead><tr><th scope="col">Mã vật tư</th><th scope="col">Tên vật tư</th><th scope="col">Phân loại</th><th scope="col">Đơn vị tính</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.content.map((material) => <tr key={material.id}>
          <td className="code-cell">{material.code ?? '—'}</td><td><strong className="cell-title">{material.name}</strong></td>
          <td>{materialCategoryLabels[material.category]}</td><td>{material.defaultUnit ?? '—'}</td>
          <td><span className={`status-badge ${material.active ? 'active' : 'inactive'}`}>{material.active ? 'Đang sử dụng' : 'Đã ngừng'}</span></td>
          <td>{canManage ? <div className="table-actions"><button className="text-button" aria-label={`Sửa ${material.name}`} onClick={() => { setNotice(''); setEditing(material) }}>Sửa</button>
            <button className={`text-button ${material.active ? 'danger' : ''}`} aria-label={`${material.active ? 'Ngừng' : 'Kích hoạt'} ${material.name}`} onClick={() => { changeStatus.reset(); setNotice(''); setConfirm(material) }}>{material.active ? 'Ngừng' : 'Kích hoạt'}</button></div> : '—'}</td>
        </tr>)}</tbody>
      </table></div>}
      {!materials.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={materials.isFetching} onChange={setPage} />}
    </section>
    {editing && <MaterialForm key={editing === 'new' ? 'new' : editing.id} material={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={(name) => { setEditing(null); setNotice(`Đã lưu vật tư ${name}.`) }} />}
    {confirm && <ConfirmDialog title={confirm.active ? 'Ngừng vật tư' : 'Kích hoạt vật tư'}
      message={confirm.active ? `Ngừng ${confirm.name}? Bản ghi vẫn được giữ nhưng không còn xuất hiện trong danh sách chọn vật tư đang hoạt động.` : `Kích hoạt ${confirm.name}? Vật tư sẽ xuất hiện lại trong danh sách chọn khi lập đơn mua hàng.`}
      confirmLabel={confirm.active ? 'Xác nhận ngừng' : 'Xác nhận kích hoạt'} danger={confirm.active} busy={changeStatus.isPending}
      error={changeStatus.error ? errorMessage(changeStatus.error) : undefined} onClose={() => setConfirm(null)} onConfirm={() => { void confirmStatus() }} />}
  </>
}
