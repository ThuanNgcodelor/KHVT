export type IconName = 'overview' | 'orders' | 'search' | 'import' | 'people' | 'users' | 'menu' | 'close' | 'plus' | 'arrow' | 'refresh' | 'collapse' | 'expand' | 'download' | 'check' | 'alert'
const paths: Record<IconName, React.ReactNode> = {
  overview: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  orders: <><path d="M8 3h8v4H8zM8 5H5v16h14V5h-3M8 12h8M8 16h5" /></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>,
  import: <><path d="M12 15V3m-4 4 4-4 4 4M4 14v6h16v-6" /></>,
  people: <><circle cx="9" cy="8" r="3" /><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 4v2" /></>,
  users: <><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="M6 16a3 3 0 0 1 6 0m3-7h3m-3 4h3" /></>,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  plus: <path d="M12 5v14M5 12h14" />,
  arrow: <path d="M5 12h14m-5-5 5 5-5 5" />,
  refresh: <><path d="M20 7a8 8 0 1 0 1 9M20 3v5h-5" /></>,
  collapse: <><path d="M14 5 7 12l7 7M20 5v14" /></>,
  expand: <><path d="m10 5 7 7-7 7M4 5v14" /></>,
  download: <><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" /></>,
  check: <polyline points="20 6 9 17 4 12" />,
  alert: <><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></>,
}
export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
