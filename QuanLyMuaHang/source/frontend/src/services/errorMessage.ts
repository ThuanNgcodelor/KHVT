import { ApiError } from './apiClient'
export const errorMessage = (error: unknown) => error instanceof ApiError ? error.message : 'Chưa xử lý được yêu cầu. Kiểm tra kết nối và thử lại.'
