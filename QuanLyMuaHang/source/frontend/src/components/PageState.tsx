export function PageState({ title, message, onRetry }: { title: string; message?: string; onRetry?: () => void }) {
  return <section className="page-state" aria-live="polite"><h1>{title}</h1>{message && <p>{message}</p>}{onRetry && <button className="secondary-button" type="button" onClick={onRetry}>Thử lại</button>}</section>
}
