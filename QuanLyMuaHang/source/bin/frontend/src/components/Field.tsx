import type { ReactNode } from 'react'
export function Field({ id, label, error, help, children, className = '' }: {
  id: string; label: string; error?: string; help?: string; children: ReactNode; className?: string
}) {
  return <div className={`form-field ${className}`}>
    <label htmlFor={id}>{label}</label>{children}
    {help && <p className="field-help" id={`${id}-help`}>{help}</p>}
    {error && <p className="field-error" id={`${id}-error`}>{error}</p>}
  </div>
}
export function fieldA11y(id: string, error?: string) {
  return { 'aria-invalid': !!error, 'aria-describedby': error ? `${id}-error` : undefined }
}
