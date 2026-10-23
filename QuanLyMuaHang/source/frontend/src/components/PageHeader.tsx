import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export function PageHeader({
  title,
  description,
  actions,
  backTo,
  onBack,
  backLabel = 'Quay lại'
}: {
  title: string
  description: string
  actions?: ReactNode
  backTo?: string
  onBack?: () => void
  backLabel?: string
}) {
  const navigate = useNavigate()
  const showBack = Boolean(backTo || onBack)

  return (
    <div className="page-header">
      <div>
        {showBack && (
          <div>
            {backTo ? (
              <Link to={backTo} className="page-back-link">
                <span className="back-arrow" aria-hidden="true">←</span>
                <span>{backLabel}</span>
              </Link>
            ) : (
              <button
                type="button"
                onClick={onBack ?? (() => navigate(-1))}
                className="page-back-button"
              >
                <span className="back-arrow" aria-hidden="true">←</span>
                <span>{backLabel}</span>
              </button>
            )}
          </div>
        )}
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {actions && <div className="header-actions">{actions}</div>}
    </div>
  )
}
