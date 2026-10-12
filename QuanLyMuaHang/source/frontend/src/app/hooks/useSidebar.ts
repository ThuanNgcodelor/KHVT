import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

export function useSidebar() {
  const [open, setOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => window.localStorage.getItem('khvt.sidebar.collapsed') === 'true')
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
    const onResize = () => { if (window.innerWidth > 760) setOpen(false) }
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('resize', onResize)
    }
  }, [open])

  function toggleCollapsed() {
    setCollapsed((value) => {
      const next = !value
      window.localStorage.setItem('khvt.sidebar.collapsed', String(next))
      return next
    })
  }
  return { open, collapsed, toggleCollapsed, toggleRef, toggle: () => setOpen((value) => !value), close: () => setOpen(false) }
}
