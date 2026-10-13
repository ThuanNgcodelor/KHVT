import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

const PURCHASING_SIDEBAR_KEY = 'qmh:ui:purchasing-sidebar:v1'

function readCollapsedPreference() {
  try {
    const current = window.localStorage.getItem(PURCHASING_SIDEBAR_KEY)
    if (current !== null) return current === 'true'
    // Migrate the earlier preference once without making storage a runtime dependency.
    return window.localStorage.getItem('khvt.sidebar.collapsed') === 'true'
  } catch {
    return false
  }
}

export function useSidebar() {
  const [open, setOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(readCollapsedPreference)
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
      try { window.localStorage.setItem(PURCHASING_SIDEBAR_KEY, String(next)) } catch { /* Keep the choice for this session. */ }
      return next
    })
  }
  return { open, collapsed, toggleCollapsed, toggleRef, toggle: () => setOpen((value) => !value), close: () => setOpen(false) }
}
