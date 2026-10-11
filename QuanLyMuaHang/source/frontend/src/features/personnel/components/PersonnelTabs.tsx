import { NavLink } from 'react-router-dom'
export function PersonnelTabs() {
  return <nav className="tab-list" aria-label="Danh mục quản trị">
    <NavLink to="/admin/employees">Danh bạ nhân viên</NavLink>
    <NavLink to="/admin/departments">Phòng ban</NavLink>
    <NavLink to="/admin/positions">Chức vụ</NavLink>
  </nav>
}
