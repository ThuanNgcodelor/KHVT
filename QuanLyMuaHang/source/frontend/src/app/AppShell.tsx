import { Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useLogout } from '../features/auth/hooks/useLogout'
import { Header } from '../components/layout/Header'
import { Sidebar } from '../components/layout/Sidebar'
import { Footer } from '../components/layout/Footer'
import { useSidebar } from './hooks/useSidebar'
import { navigation } from './navigation'

export function AppShell() {
  const { user } = useAuth()
  const sidebar = useSidebar()
  const logout = useLogout()
  const { pathname } = useLocation()
  if (!user) return null
  const initials = user.displayName.trim().split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase()
  const title = pathname === '/purchase-orders/new' ? 'Lập đơn mua'
    : /^\/purchase-orders\/\d+\/edit$/.test(pathname) ? 'Sửa đơn mua'
    : /^\/purchase-orders\/\d+$/.test(pathname) ? 'Chi tiết đơn mua'
    : navigation.find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))?.label ?? 'Hệ thống'
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Đến nội dung chính</a>
    <Sidebar user={user} initials={initials} open={sidebar.open} onClose={sidebar.close} />
    <div className="main-content">
      <Header title={title} initials={initials} sidebarOpen={sidebar.open} menuRef={sidebar.toggleRef}
        onToggleMenu={sidebar.toggle} onLogout={logout.signout} loggingOut={logout.pending} />
      <main className="page-wrap" id="main-content" tabIndex={-1}>
        {logout.error && <p className="form-error" role="alert">{logout.error}</p>}
        <Outlet />
      </main>
      <Footer />
    </div>
  </div>
}
