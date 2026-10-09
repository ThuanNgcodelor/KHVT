import { NavLink } from 'react-router-dom'
export function CatalogTabs() {
  return <nav className="tab-list" aria-label="Danh mục mua hàng">
    <NavLink to="/catalog/materials">Vật tư</NavLink>
    <NavLink to="/catalog/suppliers">Nhà cung cấp</NavLink>
  </nav>
}
