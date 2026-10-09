export type MaterialCategory = 'MATERIAL' | 'SERVICE' | 'OTHER'
export const materialCategoryLabels: Record<MaterialCategory, string> = { MATERIAL: 'Vật tư', SERVICE: 'Dịch vụ', OTHER: 'Khác' }
export type Material = { id: number; code: string | null; name: string; category: MaterialCategory; defaultUnit: string | null; active: boolean }
export type Supplier = { id: number; code: string | null; name: string; address: string | null; taxCode: string | null; phone: string | null; email: string | null; active: boolean }
export type MaterialCommand = Omit<Material, 'id'>
export type SupplierCommand = Omit<Supplier, 'id'>
export type MaterialFilter = { q?: string; active?: boolean; category?: MaterialCategory; page: number; size: number }
export type SupplierFilter = { q?: string; active?: boolean; page: number; size: number }
