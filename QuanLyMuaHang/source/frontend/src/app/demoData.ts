import type { DashboardResponse } from '../types/api'

export const demoDashboard: DashboardResponse = {
  purchaseOrders: { label: 'PO tháng này', value: 18, hint: '+12% so với tháng trước' },
  materials: { label: 'Vật tư đang quản lý', value: 1248, hint: '36 cập nhật trong tuần' },
  suppliers: { label: 'Nhà cung cấp', value: 279, hint: '12 NCC đang hoạt động' },
  recentOrders: [
    { poNumber: 'PO-261009-01', supplier: 'Công ty Minh Long', status: 'Đã xuất', total: '28.400.000 ₫' },
    { poNumber: 'PO-261008-07', supplier: 'NCC Vật tư An Phát', status: 'Nháp', total: '6.850.000 ₫' },
    { poNumber: 'PO-261007-03', supplier: 'Công ty Kỹ thuật Đông Á', status: 'Đã xuất', total: '14.200.000 ₫' },
  ],
  generatedAt: new Date().toISOString(),
}
