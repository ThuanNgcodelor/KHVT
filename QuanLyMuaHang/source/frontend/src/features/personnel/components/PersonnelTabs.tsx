import { NavLink } from 'react-router-dom'
export function PersonnelTabs() {
  return <nav className="tab-list" aria-label="Danh mục nhân sự">
    <NavLink to="/admin/employees">Nhân viên</NavLink>
    <NavLink to="/admin/departments">Phòng ban</NavLink>
    <NavLink to="/admin/positions">Chức vụ</NavLink>
  </nav>
}
