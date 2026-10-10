import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { useAuth } from './features/auth/hooks/useAuth'
import { LoginPage } from './features/auth/pages/LoginPage'
import { ChangePasswordPage } from './features/auth/pages/ChangePasswordPage'
import { AppShell } from './app/AppShell'
import { DashboardPage } from './features/dashboard/pages/DashboardPage'
import { PageState } from './components/PageState'
import { hasEveryPermission, hasPermission } from './features/auth/types'
import { EmployeesPage } from './features/personnel/pages/EmployeesPage'
import { UnitsPage } from './features/personnel/pages/UnitsPage'
import { UsersPage } from './features/identity-admin/pages/UsersPage'
import { ModulesPage } from './features/portal/pages/ModulesPage'
import { MaterialsPage } from './features/catalog/pages/MaterialsPage'
import { SuppliersPage } from './features/catalog/pages/SuppliersPage'
import { PriceSearchPage } from './features/pricing/pages/PriceSearchPage'
import { ImportsPage } from './features/imports/pages/ImportsPage'
import { PurchaseOrdersPage } from './features/procurement/pages/PurchaseOrdersPage'
import { PurchaseOrderFormPage } from './features/procurement/pages/PurchaseOrderFormPage'
import { PurchaseOrderDetailPage } from './features/procurement/pages/PurchaseOrderDetailPage'

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

function RequireAnyPermission({ permissions }: { permissions: string[] }) {
  const { user } = useAuth()
  return user && permissions.some((permission) => hasPermission(user, permission)) ? <Outlet />
    : <PageState title="Bạn không có quyền truy cập" message="Liên hệ quản trị viên nếu cần sử dụng chức năng này." />
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
        <Route path="purchase-orders" element={<PurchaseOrdersPage />} />
        <Route path="purchase-orders/:id" element={<PurchaseOrderDetailPage />} />
      </Route>
      <Route element={<RequirePermissions permissions={['PO_CREATE']} />}><Route path="purchase-orders/new" element={<PurchaseOrderFormPage key="new" />} /></Route>
      <Route element={<RequirePermissions permissions={['PO_EDIT']} />}><Route path="purchase-orders/:id/edit" element={<PurchaseOrderFormPage key="edit" />} /></Route>
      <Route element={<RequirePermissions permissions={['CATALOG_READ']} />}><Route path="catalog/materials" element={<MaterialsPage />} /><Route path="catalog/suppliers" element={<SuppliersPage />} /></Route>
      <Route element={<RequirePermissions permissions={['PRICE_READ']} />}><Route path="price-search" element={<PriceSearchPage />} /></Route>
      <Route element={<RequireAnyPermission permissions={['IMPORT_OPERATIONAL', 'IMPORT_LEGACY']} />}><Route path="imports" element={<ImportsPage />} /></Route>
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
