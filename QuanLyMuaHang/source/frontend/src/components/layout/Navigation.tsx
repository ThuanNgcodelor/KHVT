import { NavLink } from 'react-router-dom'
import { navigation } from '../../app/navigation'
import { hasRole, type CurrentUser } from '../../features/auth/types'
import { Icon } from '../Icon'

export function Navigation({ user, onNavigate }: { user: CurrentUser; onNavigate: () => void }) {
  const items = navigation.filter((item) => !item.roles || hasRole(user, item.roles))
  return <nav className="nav-list" aria-label="Điều hướng chính">
    {['Làm việc', 'Quản trị'].filter((group) => items.some((item) => item.group === group)).map((group) => <div className="nav-group" key={group}>
    <p className="nav-label">{group}</p>
    {items.filter((item) => item.group === group).map((item) => <NavLink key={item.path} to={item.path}
      className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`} onClick={onNavigate}>
      <span className="nav-icon"><Icon name={item.icon} /></span>
      <span className="nav-copy"><strong>{item.label}</strong></span>
    </NavLink>)}
    </div>)}
  </nav>
}
