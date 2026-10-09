import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

export function useSidebar() {
  const [open, setOpen] = useState(false)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const { pathname } = useLocation()

  useEffect(() => { setOpen(false) }, [pathname])
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        toggleRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open])

  return { open, toggleRef, toggle: () => setOpen((value) => !value), close: () => setOpen(false) }
}
