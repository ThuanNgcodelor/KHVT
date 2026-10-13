import { roleLabels, type CurrentUser, type RoleCode } from '../../features/auth/types'
import { Brand } from './Brand'
import { Navigation } from './Navigation'
import { Icon } from '../Icon'

export function Sidebar({ user, initials, open, onClose, collapsed, canCollapse, onToggleCollapsed }: {
  user: CurrentUser; initials: string; open: boolean; onClose: () => void; collapsed: boolean; canCollapse: boolean; onToggleCollapsed: () => void
}) {
  return <>
    {open && <button className="sidebar-backdrop" type="button" onClick={onClose} aria-label="Đóng menu" />}
    <aside className={`sidebar ${open ? 'sidebar-open' : ''} ${collapsed ? 'sidebar-collapsed' : ''}`} id="main-navigation">
      <Brand />
      <button className="sidebar-close" type="button" aria-label="Đóng thanh điều hướng" onClick={onClose}><Icon name="close" /></button>
      {canCollapse && <button className="sidebar-collapse-toggle" type="button" aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'} aria-pressed={collapsed} title={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'} onClick={onToggleCollapsed}>
        <Icon name={collapsed ? 'expand' : 'collapse'} /><span>{collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}</span>
      </button>}
      <div className="company-chip">Phòng Kế hoạch Vật tư</div>
      <Navigation user={user} onNavigate={onClose} collapsed={collapsed} />
      <div className="sidebar-footer">
        <div className="user-avatar" aria-hidden="true">{initials}</div>
        <div className="user-copy"><strong>{user.displayName}</strong>
          <span>{user.roles.map((role) => roleLabels[role as RoleCode] ?? role).join(', ')}</span>
        </div>
      </div>
    </aside>
  </>
}
