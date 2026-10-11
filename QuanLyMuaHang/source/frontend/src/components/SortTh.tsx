import type { ReactNode } from 'react'

export interface SortThProps {
  label: ReactNode
  column: string
  currentSort?: string
  onSort: (newSort: string) => void
  defaultDirection?: 'asc' | 'desc'
  className?: string
  scope?: 'col'
}

export function SortTh({
  label,
  column,
  currentSort,
  onSort,
  defaultDirection = 'desc',
  className = '',
  scope = 'col',
}: SortThProps) {
  const [activeColumn, activeDirection] = (currentSort || '').split(',')
  const isActive = activeColumn === column
  const direction = isActive ? (activeDirection === 'asc' ? 'asc' : 'desc') : undefined

  function handleClick() {
    if (!isActive) {
      onSort(`${column},${defaultDirection}`)
    } else {
      // Toggle direction
      const nextDirection = activeDirection === 'desc' ? 'asc' : 'desc'
      onSort(`${column},${nextDirection}`)
    }
  }

  return (
    <th
      scope={scope}
      className={`sortable-th ${isActive ? 'sorted' : ''} ${className}`.trim()}
      aria-sort={isActive ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}
      onClick={handleClick}
      tabIndex={0}
      role="columnheader"
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          handleClick()
        }
      }}
    >
      <div className="sort-th-content">
        <span>{label}</span>
        <span className="sort-indicator" aria-hidden="true">
          {isActive ? (direction === 'asc' ? '▲' : '▼') : '↕'}
        </span>
      </div>
    </th>
  )
}
