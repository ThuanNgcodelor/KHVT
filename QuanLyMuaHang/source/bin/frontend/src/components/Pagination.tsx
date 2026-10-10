export function Pagination({ page, size, total, pages, busy, onChange }: {
  page: number; size: number; total: number; pages: number; busy: boolean; onChange: (page: number) => void
}) {
  return <div className="pagination">
    <span>{total ? `${page * size + 1}–${Math.min((page + 1) * size, total)} / ${total} bản ghi` : '0 bản ghi'}</span>
    <div className="pagination-actions">
      <button className="secondary-button" type="button" disabled={busy || page === 0} onClick={() => onChange(page - 1)}>Trước</button>
      <span>Trang {pages ? page + 1 : 1} / {Math.max(1, pages)}</span>
      <button className="secondary-button" type="button" disabled={busy || page + 1 >= pages} onClick={() => onChange(page + 1)}>Sau</button>
    </div>
  </div>
}
