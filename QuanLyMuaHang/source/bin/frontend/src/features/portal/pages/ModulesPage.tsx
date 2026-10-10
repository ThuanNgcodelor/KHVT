import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/hooks/useAuth'
import { useLogout } from '../../auth/hooks/useLogout'
import { Brand } from '../../../components/layout/Brand'
import { Footer } from '../../../components/layout/Footer'
import { Icon, type IconName } from '../../../components/Icon'

const icons: Record<string, IconName> = { PURCHASING: 'orders', PERSONNEL: 'people', ADMINISTRATION: 'users' }
export function ModulesPage() {
  const { user } = useAuth()
  const logout = useLogout()
  const modules = user?.modules ?? []
  return <div className="portal-layout">
    <header className="portal-header"><Brand /><div className="portal-account"><span>{user?.displayName}</span><button className="secondary-button" type="button" disabled={logout.pending} onClick={() => { void logout.signout() }}>{logout.pending ? 'Đang đăng xuất…' : 'Đăng xuất'}</button></div></header>
    <main className="portal-content">
      <p className="eyebrow">KHVT · Cổng ứng dụng</p><h1>Ứng dụng của bạn</h1>
      <p className="portal-description">Chọn ứng dụng để bắt đầu làm việc.</p>
      {logout.error && <p className="form-error" role="alert">{logout.error}</p>}
      <div className="module-grid">{modules.map((module) => <Link className="module-tile" key={module.code} to={module.entryPath}>
        <span className={`module-icon module-${module.code.toLowerCase()}`}><Icon name={icons[module.code] ?? 'overview'} size={29} /></span>
        <strong>{module.name}</strong><span>{module.description}</span>
        <span className="module-enter">Mở ứng dụng <Icon name="arrow" size={15} /></span>
      </Link>)}</div>
      {modules.length === 0 && <div className="table-message"><strong>Chưa có ứng dụng được cấp quyền</strong><p>Liên hệ quản trị viên để được cấp quyền truy cập.</p></div>}
      <p className="portal-help">Các ứng dụng hiển thị theo quyền được quản trị viên cấp cho tài khoản.</p>
    </main>
    <Footer />
  </div>
}
