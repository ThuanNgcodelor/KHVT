import type { Ref } from 'react'
import { Icon } from '../Icon'
import { Link } from 'react-router-dom'

export function Header({ title, initials, sidebarOpen, menuRef, onToggleMenu, onLogout, loggingOut }: {
  title: string; initials: string; sidebarOpen: boolean; menuRef: Ref<HTMLButtonElement>
  onToggleMenu: () => void; onLogout: () => Promise<void>; loggingOut: boolean
}) {
  return <header className="topbar">
    <button ref={menuRef} className="mobile-menu" type="button" onClick={onToggleMenu}
      aria-label="Mở menu" aria-expanded={sidebarOpen} aria-controls="main-navigation"><Icon name="menu" /></button>
    <div className="breadcrumb"><span>KHVT</span><b>/</b><strong>{title}</strong></div>
    <div className="topbar-actions">
      <Link className="switch-app" to="/modules" aria-label="Đổi ứng dụng"><Icon name="overview" size={17} /><span>Đổi ứng dụng</span></Link>
      <button className="secondary-button" type="button" disabled={loggingOut} onClick={() => { void onLogout() }}>
        {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
      </button>
      <div className="top-avatar" aria-hidden="true">{initials}</div>
    </div>
  </header>
}
