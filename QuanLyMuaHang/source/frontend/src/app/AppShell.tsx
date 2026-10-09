import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthProvider'
import { hasRole, roleLabels, type RoleCode } from '../features/auth/types'

const navigation: { path: string; label: string; icon: string; roles?: RoleCode[] }[] = [
  { path: '/dashboard', label: 'Tổng quan', icon: '⌂' },
  { path: '/purchase-orders', label: 'Mua hàng', icon: '▣', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/price-search', label: 'Tra cứu giá', icon: '⌕', roles: ['ADMIN', 'PLANNER', 'VIEWER'] },
  { path: '/imports', label: 'Nhập dữ liệu', icon: '↥', roles: ['ADMIN', 'PLANNER'] },
  { path: '/admin/employees', label: 'Nhân sự', icon: '♙', roles: ['ADMIN', 'HR_MANAGER'] },
  { path: '/admin/users', label: 'Tài khoản', icon: '⚙', roles: ['ADMIN'] },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [error, setError] = useState('')
  const location = useLocation()
  if (!user) return null
  const initials = user.displayName.split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase()
  const title = navigation.find((item) => item.path === location.pathname)?.label ?? 'Hệ thống'
  async function signout() {
    setLoggingOut(true)
    setError('')
    try { await logout() }
    catch { setError('Chưa đăng xuất được. Hãy thử lại sau khi kiểm tra kết nối.') }
    finally { setLoggingOut(false) }
  }
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Đến nội dung chính</a>
    {sidebarOpen && <button className="sidebar-backdrop" type="button" onClick={() => setSidebarOpen(false)} aria-label="Đóng menu" />}
    <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`} id="main-navigation">
      <div className="brand-block"><div className="brand-mark">M</div><div><p className="brand-name">MUA HÀNG</p><p className="brand-caption">PHÒNG KẾ HOẠCH VẬT TƯ</p></div></div>
      <div className="company-chip">Không gian làm việc nội bộ</div>
      <nav className="nav-list" aria-label="Điều hướng chính"><p className="nav-label">Chức năng</p>{navigation.filter((item) => !item.roles || hasRole(user, item.roles)).map((item) => <NavLink key={item.path} to={item.path} className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`} onClick={() => setSidebarOpen(false)}><span className="nav-icon" aria-hidden="true">{item.icon}</span><span className="nav-copy"><strong>{item.label}</strong></span></NavLink>)}</nav>
      <div className="sidebar-footer"><div className="user-avatar" aria-hidden="true">{initials}</div><div className="user-copy"><strong>{user.displayName}</strong><span>{user.roles.map((role) => roleLabels[role as RoleCode] ?? role).join(', ')}</span></div></div>
    </aside>
    <main className="main-content" id="main-content"><header className="topbar"><button className="mobile-menu" type="button" onClick={() => setSidebarOpen((open) => !open)} aria-label="Mở menu" aria-expanded={sidebarOpen} aria-controls="main-navigation">☰</button><div className="breadcrumb"><span>Không gian làm việc</span><b>/</b><strong>{title}</strong></div><div className="topbar-actions"><button className="secondary-button" type="button" disabled={loggingOut} onClick={() => { void signout() }}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button><div className="top-avatar" aria-hidden="true">{initials}</div></div></header><div className="page-wrap">{error && <p className="form-error" role="alert">{error}</p>}<Outlet /></div></main>
  </div>
}
