import { useEffect, useState, type ReactNode } from 'react'
import { demoDashboard } from './app/demoData'
import { dashboardApi } from './services/dashboardApi'
import type { DashboardResponse } from './types/api'

type View = 'dashboard' | 'purchase' | 'price' | 'imports' | 'personnel'

const navItems: { id: View; label: string; icon: string; caption: string }[] = [
  { id: 'dashboard', label: 'Tổng quan', icon: '⌂', caption: 'Dashboard' },
  { id: 'purchase', label: 'Mua hàng', icon: '▣', caption: 'Purchase orders' },
  { id: 'price', label: 'Tra cứu giá', icon: '⌕', caption: 'Price history' },
  { id: 'imports', label: 'Nhập dữ liệu', icon: '↥', caption: 'Import workbook' },
  { id: 'personnel', label: 'Nhân sự', icon: '♙', caption: 'Personnel & access' },
]

export function App() {
  const [view, setView] = useState<View>('dashboard')
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-shell">
      <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="brand-block">
          <div className="brand-mark">M</div>
          <div>
            <p className="brand-name">MUA HÀNG</p>
            <p className="brand-caption">PHÒNG KẾ HOẠCH VẬT TƯ</p>
          </div>
        </div>
        <div className="company-chip">
          <span className="status-dot" />
          <span>Công ty CP Phân bón &amp; Hóa chất Cần Thơ</span>
        </div>
        <nav className="nav-list" aria-label="Điều hướng chính">
          <p className="nav-label">Không gian làm việc</p>
          {navItems.map((item) => (
            <button
              type="button"
              key={item.id}
              className={`nav-item ${view === item.id ? 'nav-item-active' : ''}`}
              onClick={() => {
                setView(item.id)
                setSidebarOpen(false)
              }}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-copy">
                <strong>{item.label}</strong>
                <small>{item.caption}</small>
              </span>
              {view === item.id && <span className="nav-arrow">›</span>}
            </button>
          ))}
          <p className="nav-label nav-label-spaced">Quản trị</p>
          <button type="button" className="nav-item" onClick={() => setView('personnel')}>
            <span className="nav-icon">⚙</span>
            <span className="nav-copy"><strong>Thiết lập</strong><small>Admin console</small></span>
          </button>
        </nav>
        <div className="sidebar-footer">
          <div className="user-avatar">AD</div>
          <div className="user-copy"><strong>Admin demo</strong><span>Toàn quyền hệ thống</span></div>
          <button className="more-button" type="button" aria-label="Tùy chọn tài khoản">•••</button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <button className="mobile-menu" type="button" onClick={() => setSidebarOpen((open) => !open)} aria-label="Mở menu">☰</button>
          <div className="breadcrumb"><span>Workspace</span><b>/</b><strong>{navItems.find((item) => item.id === view)?.label}</strong></div>
          <div className="topbar-actions">
            <span className="api-status"><i /> API sẵn sàng</span>
            <button type="button" className="icon-button" aria-label="Thông báo">♧<span className="notification-dot" /></button>
            <div className="top-avatar">AD</div>
          </div>
        </header>
        <div className="page-wrap">
          {view === 'dashboard' && <DashboardPage onNavigate={setView} />}
          {view === 'purchase' && <PurchasePage />}
          {view === 'price' && <PricePage />}
          {view === 'imports' && <ImportPage />}
          {view === 'personnel' && <PersonnelPage />}
        </div>
      </main>
    </div>
  )
}

function DashboardPage({ onNavigate }: { onNavigate: (view: View) => void }) {
  const [data, setData] = useState<DashboardResponse>(demoDashboard)
  const [source, setSource] = useState<'api' | 'demo'>('demo')

  useEffect(() => {
    let mounted = true
    dashboardApi.getSummary().then((summary) => {
      if (mounted) {
        setData(summary)
        setSource('api')
      }
    }).catch(() => {
      if (mounted) setSource('demo')
    })
    return () => { mounted = false }
  }, [])

  const kpis = [data.purchaseOrders, data.materials, data.suppliers]
  return (
    <>
      <PageIntro eyebrow="Thứ năm, 09 tháng 10, 2026" title="Chào buổi sáng, Admin 👋" description="Theo dõi hoạt động mua hàng và xử lý công việc trong ngày." action={<button className="primary-button" onClick={() => onNavigate('purchase')} type="button"><span>＋</span> Tạo phiếu mua</button>} />
      <div className="info-strip"><span className="info-icon">i</span><span>Đây là giao diện khung React/Vite. API đang dùng cấu hình trung tâm tại <code>src/config/baseApi.ts</code>.</span><span className={`source-pill ${source}`}>{source === 'api' ? 'Live API' : 'Demo data'}</span></div>
      <section className="kpi-grid" aria-label="Chỉ số tổng quan">
        {kpis.map((kpi, index) => <KpiCard key={kpi.label} data={kpi} tone={index} />)}
        <div className="kpi-card kpi-highlight"><div className="kpi-top"><span className="kpi-icon fire">↗</span><span className="trend-up">+8.4%</span></div><p className="kpi-label">Giá trị mua hàng</p><strong className="kpi-value">1,24 <small>TỶ ₫</small></strong><p className="kpi-hint">so với cùng kỳ tháng trước</p></div>
      </section>
      <div className="content-grid">
        <section className="panel chart-panel">
          <div className="panel-heading"><div><p className="section-kicker">Dòng tiền</p><h2>Giá trị mua hàng</h2></div><select className="period-select" defaultValue="6"><option value="6">6 tháng gần nhất</option><option value="12">12 tháng gần nhất</option></select></div>
          <div className="chart-legend"><span><i className="legend-dot orange" /> Năm nay</span><span><i className="legend-dot pale" /> Năm trước</span></div>
          <MiniBarChart />
        </section>
        <section className="panel quick-panel">
          <div className="panel-heading"><div><p className="section-kicker">Thao tác nhanh</p><h2>Công việc thường dùng</h2></div></div>
          <QuickAction icon="↥" title="Import phiếu yêu cầu" detail="Excel, CSV hoặc PDF" onClick={() => onNavigate('imports')} />
          <QuickAction icon="⌕" title="Tra cứu giá vật tư" detail="Tìm lịch sử và NCC" onClick={() => onNavigate('price')} />
          <QuickAction icon="♙" title="Quản lý nhân sự" detail="Tài khoản và phân quyền" onClick={() => onNavigate('personnel')} />
        </section>
      </div>
      <section className="panel orders-panel"><div className="panel-heading"><div><p className="section-kicker">Cập nhật mới nhất</p><h2>Đơn mua hàng gần đây</h2></div><button className="link-button" type="button" onClick={() => onNavigate('purchase')}>Xem tất cả <span>→</span></button></div><OrdersTable orders={data.recentOrders} /></section>
    </>
  )
}

function KpiCard({ data, tone }: { data: DashboardResponse['purchaseOrders']; tone: number }) {
  const icon = ['▣', '◇', '♧'][tone]
  return <div className="kpi-card"><div className="kpi-top"><span className={`kpi-icon tone-${tone}`}>{icon}</span><span className="trend-up">↗ 12%</span></div><p className="kpi-label">{data.label}</p><strong className="kpi-value">{data.value.toLocaleString('vi-VN')}</strong><p className="kpi-hint">{data.hint}</p></div>
}

function MiniBarChart() {
  const bars = [42, 55, 49, 68, 58, 78, 66, 88, 73, 94, 82, 100]
  return <div className="mini-chart"><div className="chart-y"><span>1.5 tỷ</span><span>1.0 tỷ</span><span>500 tr</span><span>0</span></div><div className="bar-area">{bars.map((height, index) => <div className="bar-column" key={index}><div className="bar-stack"><span className="bar pale" style={{ height: `${Math.max(height - 24, 20)}%` }} /><span className="bar orange" style={{ height: `${height}%` }} /></div><small>{['T4', 'T5', 'T6', 'T7', 'T8', 'T9', 'T10', 'T11', 'T12', 'T1', 'T2', 'T3'][index]}</small></div>)}</div></div>
}

function OrdersTable({ orders }: { orders: DashboardResponse['recentOrders'] }) {
  const fallback = orders.length ? orders : demoDashboard.recentOrders
  return <div className="table-wrap"><table><thead><tr><th>SỐ PO</th><th>NHÀ CUNG CẤP</th><th>NGÀY TẠO</th><th>GIÁ TRỊ</th><th>TRẠNG THÁI</th><th /></tr></thead><tbody>{fallback.map((order, index) => <tr key={order.poNumber}><td><strong className="po-code">{order.poNumber}</strong></td><td><span className="supplier-cell"><i className={`supplier-avatar sa-${index}`}>{order.supplier.slice(0, 1)}</i>{order.supplier}</span></td><td className="muted-cell">{index === 0 ? '09/10/2026' : index === 1 ? '08/10/2026' : '07/10/2026'}</td><td><strong>{order.total}</strong></td><td><span className={`status-badge ${order.status === 'Nháp' ? 'draft' : 'exported'}`}><i />{order.status}</span></td><td><button className="row-more" type="button">•••</button></td></tr>)}</tbody></table></div>
}

function QuickAction({ icon, title, detail, onClick }: { icon: string; title: string; detail: string; onClick: () => void }) {
  return <button className="quick-action" type="button" onClick={onClick}><span className="quick-icon">{icon}</span><span><strong>{title}</strong><small>{detail}</small></span><span className="quick-arrow">→</span></button>
}

function PageIntro({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <div className="page-intro"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="page-description">{description}</p></div>{action}</div>
}

function PurchasePage() {
  return <><PageIntro eyebrow="Mua hàng / Phiếu mua" title="Chuẩn bị danh sách mua" description="Import yêu cầu, chọn giá gần nhất và xuất đơn theo từng nhà cung cấp." action={<button className="primary-button" type="button">＋ Tạo phiếu mới</button>} /><div className="workspace-card"><div className="empty-hero"><span className="empty-icon">▣</span><h2>Bắt đầu một phiếu mua hàng</h2><p>Upload file Excel/PDF hoặc dán trực tiếp các dòng vật tư để hệ thống gợi ý giá và nhà cung cấp.</p><div className="empty-actions"><button className="primary-button" type="button">↥ Import file</button><button className="secondary-button" type="button">＋ Thêm thủ công</button></div></div></div></>
}

function PricePage() {
  return <><PageIntro eyebrow="Tra cứu giá" title="Tìm giá vật tư nhanh hơn" description="Tìm không dấu, xem giá gần nhất và lịch sử theo nhà cung cấp." /><div className="search-card"><div className="search-box"><span>⌕</span><input placeholder="Gõ tên vật tư cần tra giá..." /><kbd>⌘ K</kbd></div><p>Thử tìm: <button type="button">thép tấm</button>, <button type="button">bao bì</button>, <button type="button">dầu DO</button></p></div><div className="workspace-card empty-table"><span className="empty-icon">⌕</span><h2>Chưa có từ khóa tìm kiếm</h2><p>Kết quả giá và NCC gần nhất sẽ hiển thị tại đây.</p></div></>
}

function ImportPage() {
  return <><PageIntro eyebrow="Nhập dữ liệu / Legacy migration" title="Đưa dữ liệu cũ vào hệ thống" description="Preview trước, kiểm tra lỗi theo từng dòng rồi mới commit vào database." /><div className="import-layout"><div className="dropzone"><span className="upload-symbol">↥</span><h2>Kéo thả file vào đây</h2><p>Hỗ trợ .xls, .xlsx, .csv, .pdf tối đa 20 MB</p><button className="primary-button" type="button">Chọn file</button><small>File không bị upload khi chưa bấm Preview</small></div><div className="import-guide"><p className="section-kicker">Quy trình an toàn</p><h2>3 bước để dữ liệu sạch</h2><div className="guide-step"><b>01</b><span><strong>Preview</strong><small>Đọc sheet và báo lỗi theo dòng</small></span></div><div className="guide-step"><b>02</b><span><strong>Kiểm tra</strong><small>Match vật tư, NCC và tiền tệ</small></span></div><div className="guide-step"><b>03</b><span><strong>Commit</strong><small>Ghi batch vào database khi đã xác nhận</small></span></div></div></div></>
}

function PersonnelPage() {
  const [tab, setTab] = useState<'employees' | 'users'>('employees')
  return <><PageIntro eyebrow="Quản trị / Nhân sự" title="Nhân sự & quyền truy cập" description="Quản lý hồ sơ nhân viên, tài khoản và quyền theo mô hình DDD Identity/Personnel." action={<button className="primary-button" type="button">＋ Thêm nhân sự</button>} /><div className="tabs"><button className={tab === 'employees' ? 'tab active' : 'tab'} onClick={() => setTab('employees')} type="button">Danh sách nhân sự <span>12</span></button><button className={tab === 'users' ? 'tab active' : 'tab'} onClick={() => setTab('users')} type="button">Tài khoản & vai trò <span>8</span></button></div><div className="workspace-card personnel-card"><div className="personnel-toolbar"><div className="table-search">⌕<input placeholder={tab === 'employees' ? 'Tìm theo tên hoặc mã nhân viên...' : 'Tìm username...'} /></div><button className="secondary-button" type="button">Bộ lọc ↧</button></div>{tab === 'employees' ? <EmployeeTable /> : <UserTable />}</div></>
}

function EmployeeTable() {
  const employees = [['NV-001', 'Nguyễn Minh Anh', 'Kế hoạch vật tư', 'Trưởng phòng', 'Đang làm'], ['NV-014', 'Trần Quốc Bảo', 'Kế hoạch vật tư', 'Chuyên viên mua hàng', 'Đang làm'], ['NV-022', 'Lê Hoàng Nam', 'Kho vận', 'Nhân viên kho', 'Đang làm']]
  return <div className="table-wrap"><table><thead><tr><th>MÃ NV</th><th>HỌ VÀ TÊN</th><th>PHÒNG BAN</th><th>CHỨC VỤ</th><th>TRẠNG THÁI</th><th /></tr></thead><tbody>{employees.map((employee) => <tr key={employee[0]}>{employee.slice(0, 4).map((item, index) => <td key={item}>{index === 1 ? <strong>{item}</strong> : item}</td>)}<td><span className="status-badge exported"><i />{employee[4]}</span></td><td><button className="row-more" type="button">•••</button></td></tr>)}</tbody></table></div>
}

function UserTable() {
  const users = [['admin', 'Admin hệ thống', 'ADMIN', 'Hoạt động'], ['minhanh', 'Nguyễn Minh Anh', 'PLANNER', 'Hoạt động'], ['quocbao', 'Trần Quốc Bảo', 'VIEWER', 'Hoạt động']]
  return <div className="table-wrap"><table><thead><tr><th>USERNAME</th><th>NGƯỜI DÙNG</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th /></tr></thead><tbody>{users.map((user) => <tr key={user[0]}><td><strong className="po-code">{user[0]}</strong></td><td>{user[1]}</td><td><span className={`role-badge ${user[2] === 'ADMIN' ? 'admin' : ''}`}>{user[2]}</span></td><td><span className="status-badge exported"><i />{user[3]}</span></td><td><button className="row-more" type="button">•••</button></td></tr>)}</tbody></table></div>
}
