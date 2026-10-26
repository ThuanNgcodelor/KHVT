import cfcLogo from '../../assets/cfc-logo-primary.png'

export function Brand() {
  return <div className="brand-block">
    <img className="brand-logo" src={cfcLogo} alt="CFC" />
    <div><p className="brand-name">CFC</p><p className="brand-caption">Quản lý mua hàng KHVT</p></div>
  </div>
}
