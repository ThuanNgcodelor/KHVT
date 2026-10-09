import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { useAuth } from './features/auth/hooks/useAuth'
import { LoginPage } from './features/auth/pages/LoginPage'
import { ChangePasswordPage } from './features/auth/pages/ChangePasswordPage'
import { AppShell } from './app/AppShell'
import { DashboardPage } from './features/dashboard/pages/DashboardPage'
import { PageState } from './components/PageState'
import { hasEveryPermission } from './features/auth/types'
import { EmployeesPage } from './features/personnel/pages/EmployeesPage'
import { UnitsPage } from './features/personnel/pages/UnitsPage'
import { UsersPage } from './features/identity-admin/pages/UsersPage'
import { ModulesPage } from './features/portal/pages/ModulesPage'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } })

function RequireSession() {
  const { user, pending, error, refresh } = useAuth()
  if (pending) return <PageState title="Đang kiểm tra phiên đăng nhập…" />
  if (error) return <PageState title="Chưa kết nối được hệ thống" message="Kiểm tra kết nối rồi thử lại." onRetry={refresh} />
  if (!user) return <Navigate to="/login" replace />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  return <Outlet />
}

function RequirePermissions({ permissions }: { permissions: string[] }) {
  const { user } = useAuth()
  return user && hasEveryPermission(user, permissions) ? <Outlet />
    : <PageState title="Bạn không có quyền truy cập" message="Liên hệ quản trị viên nếu bạn cần sử dụng chức năng này." />
}

function PlannedPage({ title }: { title: string }) {
  return <PageState title={title} message="Giao diện chức năng này đang được triển khai. Hiện chưa có thao tác ghi dữ liệu trên màn hình này." />
}
function RequireModule({ code }: { code: string }) {
  const { user } = useAuth()
  return user?.modules?.some((module) => module.code === code) ? <Outlet />
    : <PageState title="Bạn không có quyền truy cập" message="Ứng dụng này chưa được cấp quyền cho tài khoản của bạn." />
}

export function App() {
  return <QueryClientProvider client={queryClient}><BrowserRouter><AuthProvider><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/change-password" element={<ChangePasswordPage />} />
    <Route element={<RequireSession />}>
      <Route index element={<Navigate to="/modules" replace />} />
      <Route path="modules" element={<ModulesPage />} />
      <Route element={<AppShell />}>
      <Route element={<RequireModule code="PURCHASING" />}>
      <Route path="dashboard" element={<DashboardPage />} />
      <Route element={<RequirePermissions permissions={['PO_READ']} />}>
        <Route path="purchase-orders" element={<PlannedPage title="Đơn mua hàng" />} />
      </Route>
      <Route element={<RequirePermissions permissions={['PRICE_READ']} />}><Route path="price-search" element={<PlannedPage title="Tra cứu giá" />} /></Route>
      <Route element={<RequirePermissions permissions={['IMPORT_OPERATIONAL']} />}><Route path="imports" element={<PlannedPage title="Nhập dữ liệu" />} /></Route>
      </Route>
      <Route element={<RequireModule code="PERSONNEL" />}>
      <Route element={<RequirePermissions permissions={['PERSONNEL_READ']} />}>
        <Route path="admin/employees" element={<EmployeesPage />} />
        <Route path="admin/departments" element={<UnitsPage key="departments" kind="departments" />} />
        <Route path="admin/positions" element={<UnitsPage key="positions" kind="positions" />} />
      </Route>
      </Route>
      <Route element={<RequireModule code="ADMINISTRATION" />}>
      <Route element={<RequirePermissions permissions={['USER_READ']} />}><Route path="admin/users" element={<UsersPage />} /></Route>
      </Route>
      <Route path="*" element={<PageState title="Không tìm thấy trang" message="Chọn một chức năng trong thanh điều hướng để tiếp tục." />} />
    </Route></Route>
  </Routes></AuthProvider></BrowserRouter></QueryClientProvider>
}
