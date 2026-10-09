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
import { SupplierForm } from '../components/SupplierForm'
import { useSuppliers, useSupplierStatus } from '../hooks/useCatalog'
import type { Supplier } from '../types'

export function SuppliersPage() {
  const { user } = useAuth()
  const canManage = !!user && hasPermission(user, 'CATALOG_MANAGE')
  const [search, setSearch] = useState(''), [q, setQuery] = useState(''), [active, setActive] = useState('')
  const [page, setPage] = useState(0), [editing, setEditing] = useState<Supplier | 'new' | null>(null)
  const [confirm, setConfirm] = useState<Supplier | null>(null), [notice, setNotice] = useState('')
  const suppliers = useSuppliers({ q, active: active === '' ? undefined : active === 'true', page, size: 25 })
  const changeStatus = useSupplierStatus(), data = suppliers.data
  async function confirmStatus() {
    if (!confirm) return
    try {
      const saved = await changeStatus.mutateAsync({ id: confirm.id, active: !confirm.active })
      setConfirm(null)
      setNotice(`Đã ${saved.active ? 'kích hoạt' : 'ngừng'} nhà cung cấp ${saved.name}.`)
    } catch { /* Keep the confirmation open with the API error. */ }
  }
  return <>
    <PageHeader title="Nhà cung cấp" description="Thông tin liên hệ và trạng thái của các nhà cung cấp trong danh mục mua hàng."
      actions={canManage && <button className="primary-button" onClick={() => { setNotice(''); setEditing('new') }}><Icon name="plus" />Thêm nhà cung cấp</button>} />
    <CatalogTabs />
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="panel" aria-label="Danh sách nhà cung cấp">
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); setPage(0) }}>
        <div className="form-field search-field"><label htmlFor="supplier-search">Mã hoặc tên nhà cung cấp</label><input id="supplier-search" type="search" placeholder="Nhập mã hoặc tên…" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
        <div className="form-field"><label htmlFor="supplier-active">Trạng thái</label><select id="supplier-active" value={active} onChange={(event) => { setActive(event.target.value); setPage(0) }}><option value="">Tất cả</option><option value="true">Đang sử dụng</option><option value="false">Đã ngừng</option></select></div>
        <button className="secondary-button" type="submit"><Icon name="search" size={16} />Tìm kiếm</button>
        <button className="text-button" type="button" onClick={() => { setSearch(''); setQuery(''); setActive(''); setPage(0) }}>Xóa lọc</button>
        {data && !suppliers.error && <span className="table-count">{data.totalElements} nhà cung cấp</span>}
      </form>
      <TableFeedback pending={suppliers.isPending} error={suppliers.error} empty={!data?.content.length} onRetry={() => { void suppliers.refetch() }}
        emptyTitle={q || active ? 'Không có nhà cung cấp phù hợp' : 'Chưa có nhà cung cấp'} emptyMessage={q || active ? 'Thử từ khóa khác hoặc xóa bộ lọc.' : canManage ? 'Thêm nhà cung cấp để bắt đầu lập danh mục.' : 'Danh mục sẽ hiển thị khi có nhà cung cấp.'} />
      {!suppliers.error && data && data.content.length > 0 && <div className="table-wrap"><table>
        <caption className="sr-only">Danh sách nhà cung cấp KHVT</caption>
        <thead><tr><th scope="col">Mã NCC</th><th scope="col">Nhà cung cấp</th><th scope="col">Liên hệ</th><th scope="col">Mã số thuế</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.content.map((supplier) => <tr key={supplier.id}>
          <td className="code-cell">{supplier.code ?? '—'}</td><td><strong className="cell-title">{supplier.name}</strong><span className="cell-subtitle">{supplier.address ?? 'Chưa có địa chỉ'}</span></td>
          <td>{supplier.email ?? '—'}<span className="cell-subtitle">{supplier.phone ?? '—'}</span></td><td>{supplier.taxCode ?? '—'}</td>
          <td><span className={`status-badge ${supplier.active ? 'active' : 'inactive'}`}>{supplier.active ? 'Đang sử dụng' : 'Đã ngừng'}</span></td>
          <td>{canManage ? <div className="table-actions"><button className="text-button" aria-label={`Sửa ${supplier.name}`} onClick={() => { setNotice(''); setEditing(supplier) }}>Sửa</button>
            <button className={`text-button ${supplier.active ? 'danger' : ''}`} aria-label={`${supplier.active ? 'Ngừng' : 'Kích hoạt'} ${supplier.name}`} onClick={() => { changeStatus.reset(); setNotice(''); setConfirm(supplier) }}>{supplier.active ? 'Ngừng' : 'Kích hoạt'}</button></div> : '—'}</td>
        </tr>)}</tbody>
      </table></div>}
      {!suppliers.error && data && <Pagination page={data.number} size={data.size} total={data.totalElements} pages={data.totalPages} busy={suppliers.isFetching} onChange={setPage} />}
    </section>
    {editing && <SupplierForm key={editing === 'new' ? 'new' : editing.id} supplier={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={(name) => { setEditing(null); setNotice(`Đã lưu nhà cung cấp ${name}.`) }} />}
    {confirm && <ConfirmDialog title={confirm.active ? 'Ngừng nhà cung cấp' : 'Kích hoạt nhà cung cấp'}
      message={confirm.active ? `Ngừng ${confirm.name}? Bản ghi vẫn được giữ nhưng không còn xuất hiện trong danh sách chọn nhà cung cấp đang hoạt động.` : `Kích hoạt ${confirm.name}? Nhà cung cấp sẽ xuất hiện lại trong danh sách chọn khi lập đơn mua hàng.`}
      confirmLabel={confirm.active ? 'Xác nhận ngừng' : 'Xác nhận kích hoạt'} danger={confirm.active} busy={changeStatus.isPending}
      error={changeStatus.error ? errorMessage(changeStatus.error) : undefined} onClose={() => setConfirm(null)} onConfirm={() => { void confirmStatus() }} />}
  </>
}
