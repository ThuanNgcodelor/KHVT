import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { useAuth } from './features/auth/hooks/useAuth'
import { LoginPage } from './features/auth/pages/LoginPage'
import { ChangePasswordPage } from './features/auth/pages/ChangePasswordPage'
import { AppShell } from './app/AppShell'
import { DashboardPage } from './features/dashboard/pages/DashboardPage'
import { PageState } from './components/PageState'
import { hasRole, type RoleCode } from './features/auth/types'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } })

function RequireSession() {
  const { user, pending, error, refresh } = useAuth()
  if (pending) return <PageState title="Đang kiểm tra phiên đăng nhập…" />
  if (error) return <PageState title="Chưa kết nối được hệ thống" message="Kiểm tra kết nối rồi thử lại." onRetry={refresh} />
  if (!user) return <Navigate to="/login" replace />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  return <Outlet />
}

function RequireRole({ roles }: { roles: RoleCode[] }) {
  const { user } = useAuth()
  return user && hasRole(user, roles) ? <Outlet />
    : <PageState title="Bạn không có quyền truy cập" message="Liên hệ quản trị viên nếu bạn cần sử dụng chức năng này." />
}

function PlannedPage({ title }: { title: string }) {
  return <PageState title={title} message="Giao diện chức năng này đang được triển khai. Hiện chưa có thao tác ghi dữ liệu trên màn hình này." />
}

export function App() {
  return <QueryClientProvider client={queryClient}><BrowserRouter><AuthProvider><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/change-password" element={<ChangePasswordPage />} />
    <Route element={<RequireSession />}><Route element={<AppShell />}>
      <Route index element={<Navigate to="/dashboard" replace />} />
      <Route path="dashboard" element={<DashboardPage />} />
      <Route element={<RequireRole roles={['ADMIN', 'PLANNER', 'VIEWER']} />}>
        <Route path="purchase-orders" element={<PlannedPage title="Đơn mua hàng" />} />
        <Route path="price-search" element={<PlannedPage title="Tra cứu giá" />} />
      </Route>
      <Route element={<RequireRole roles={['ADMIN', 'PLANNER']} />}><Route path="imports" element={<PlannedPage title="Nhập dữ liệu" />} /></Route>
      <Route element={<RequireRole roles={['ADMIN', 'HR_MANAGER']} />}><Route path="admin/employees" element={<PlannedPage title="Quản lý nhân sự" />} /></Route>
      <Route element={<RequireRole roles={['ADMIN']} />}><Route path="admin/users" element={<PlannedPage title="Quản lý tài khoản" />} /></Route>
      <Route path="*" element={<PageState title="Không tìm thấy trang" message="Chọn một chức năng trong thanh điều hướng để tiếp tục." />} />
    </Route></Route>
  </Routes></AuthProvider></BrowserRouter></QueryClientProvider>
}
