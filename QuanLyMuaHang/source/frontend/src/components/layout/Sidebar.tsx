import { roleLabels, type CurrentUser, type RoleCode } from '../../features/auth/types'
import { Brand } from './Brand'
import { Navigation } from './Navigation'

export function Sidebar({ user, initials, open, onClose }: {
  user: CurrentUser; initials: string; open: boolean; onClose: () => void
}) {
  return <>
    {open && <button className="sidebar-backdrop" type="button" onClick={onClose} aria-label="Đóng menu" />}
    <aside className={`sidebar ${open ? 'sidebar-open' : ''}`} id="main-navigation">
      <Brand />
      <div className="company-chip">Không gian làm việc nội bộ</div>
      <Navigation user={user} onNavigate={onClose} />
      <div className="sidebar-footer">
        <div className="user-avatar" aria-hidden="true">{initials}</div>
        <div className="user-copy"><strong>{user.displayName}</strong>
          <span>{user.roles.map((role) => roleLabels[role as RoleCode] ?? role).join(', ')}</span>
        </div>
      </div>
    </aside>
  </>
}
