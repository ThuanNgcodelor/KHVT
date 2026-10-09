import { NavLink } from 'react-router-dom'
import { navigation } from '../../app/navigation'
import { hasRole, type CurrentUser } from '../../features/auth/types'

export function Navigation({ user, onNavigate }: { user: CurrentUser; onNavigate: () => void }) {
  const items = navigation.filter((item) => !item.roles || hasRole(user, item.roles))
  return <nav className="nav-list" aria-label="Điều hướng chính">
    <p className="nav-label">Chức năng</p>
    {items.map((item) => <NavLink key={item.path} to={item.path}
      className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`} onClick={onNavigate}>
      <span className="nav-icon" aria-hidden="true">{item.icon}</span>
      <span className="nav-copy"><strong>{item.label}</strong></span>
    </NavLink>)}
  </nav>
}
