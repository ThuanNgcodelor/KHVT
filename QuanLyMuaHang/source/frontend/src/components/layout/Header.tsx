import type { Ref } from 'react'

export function Header({ title, initials, sidebarOpen, menuRef, onToggleMenu, onLogout, loggingOut }: {
  title: string; initials: string; sidebarOpen: boolean; menuRef: Ref<HTMLButtonElement>
  onToggleMenu: () => void; onLogout: () => Promise<void>; loggingOut: boolean
}) {
  return <header className="topbar">
    <button ref={menuRef} className="mobile-menu" type="button" onClick={onToggleMenu}
      aria-label="Mở menu" aria-expanded={sidebarOpen} aria-controls="main-navigation">☰</button>
    <div className="breadcrumb"><span>Không gian làm việc</span><b>/</b><strong>{title}</strong></div>
    <div className="topbar-actions">
      <button className="secondary-button" type="button" disabled={loggingOut} onClick={() => { void onLogout() }}>
        {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
      </button>
      <div className="top-avatar" aria-hidden="true">{initials}</div>
    </div>
  </header>
}
