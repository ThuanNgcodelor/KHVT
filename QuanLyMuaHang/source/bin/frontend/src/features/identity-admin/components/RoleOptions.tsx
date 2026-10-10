import type { UseFormRegisterReturn } from 'react-hook-form'
import { roleLabels, type RoleCode } from '../../auth/types'
import type { Role } from '../types'

const moduleNames: Record<string, string> = { PURCHASING: 'Mua hàng', PERSONNEL: 'Nhân sự', ADMINISTRATION: 'Quản trị' }
function roleGroup(role: Role) {
  if (role.permissions.includes('*')) return 'Quản trị toàn hệ thống'
  return role.moduleCodes?.map((code) => moduleNames[code] ?? code).join(' · ') || 'Vai trò khác'
}
export function RoleOptions({ roles, unavailable, registration, pending, error }: {
  roles: Role[]; unavailable: string[]; registration: UseFormRegisterReturn; pending: boolean; error?: string
}) {
  const groups = [...new Set(roles.map(roleGroup))]
  return <div className="form-field"><label id="role-options-label">Ứng dụng và vai trò *</label>
    <div className="role-options" role="group" aria-labelledby="role-options-label" aria-describedby={error ? 'role-options-error' : undefined}>
      {pending && <p role="status" className="field-help">Đang tải vai trò…</p>}
      {groups.map((group) => <div className="role-group" key={group}>
        <p className="role-group-title">{group}</p>
        {roles.filter((role) => roleGroup(role) === group).map((role) => <div key={role.code}>
          <label className="checkbox-field"><input type="checkbox" value={role.code} {...registration} />{roleLabels[role.code as RoleCode] ?? role.name}</label>
          {role.description && <p className="field-help">{role.description}</p>}
        </div>)}
      </div>)}
      {unavailable.map((code) => <label key={code} className="checkbox-field"><input type="checkbox" value={code} {...registration} />{code} (không còn hoạt động; bỏ chọn trước khi lưu)</label>)}
    </div>
    <p className="field-help">Chọn vai trò để cấp ứng dụng và quyền thao tác tương ứng. Quản trị viên có quyền vào tất cả ứng dụng.</p>
    {error && <p className="field-error" id="role-options-error">{error}</p>}
  </div>
}
