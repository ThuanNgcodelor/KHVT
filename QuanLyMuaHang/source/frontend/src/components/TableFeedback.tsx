import { ApiError } from '../services/apiClient'
export function TableFeedback({ pending, error, empty, onRetry, emptyTitle = 'Chưa có dữ liệu', emptyMessage = 'Thêm bản ghi đầu tiên để bắt đầu quản lý.' }: {
  pending: boolean; error: Error | null; empty: boolean; onRetry: () => void; emptyTitle?: string; emptyMessage?: string
}) {
  if (pending) return <div className="table-message" role="status">Đang tải dữ liệu…</div>
  if (error) return <div className="table-message" role="alert">
    <strong>{error instanceof ApiError && error.status === 403 ? 'Bạn không có quyền truy cập dữ liệu này' : 'Chưa tải được dữ liệu'}</strong>
    <p>{error instanceof ApiError ? error.message : 'Kiểm tra kết nối và thử lại.'}</p>
    <button type="button" className="secondary-button" onClick={onRetry}>Thử lại</button>
  </div>
  if (empty) return <div className="table-message"><strong>{emptyTitle}</strong><p>{emptyMessage}</p></div>
  return null
}
