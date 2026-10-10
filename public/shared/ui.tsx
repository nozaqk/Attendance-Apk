import { useCallback, useEffect, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from "react";

export type Tone = "green" | "yellow" | "red" | "grey" | "blue";
const STATUS_TONE: Record<string, Tone> = {
  PRESENT: "green", VERIFIED: "green", ONLINE: "green", ACTIVE: "green", BLE: "green",
  LATE: "yellow", MANUAL: "yellow", LECTURER_MANUAL: "yellow", SCHEDULED: "yellow", PENDING: "yellow",
  ABSENT: "red", OFFLINE: "red", ERROR: "red", LOCKED: "red", CANCELLED: "red",
  ENDED: "grey", NONE: "grey",
};
export function Badge({ children, tone }: { children: ReactNode; tone?: Tone }) {
  const t = tone ?? STATUS_TONE[String(children)] ?? "grey";
  return <span className={`badge badge-${t}`}>{children}</span>;
}

export function Button({ variant = "primary", busy, children, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "ghost"; busy?: boolean }) {
  return <button {...p} disabled={p.disabled || busy} className={`btn btn-${variant} ${p.className ?? ""}`}>{busy ? "Please wait…" : children}</button>;
}
export const Card = ({ title, actions, children, className = "" }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) => (
  <section className={`card ${className}`}>{(title || actions) && <header className="card-head"><h2>{title}</h2><div>{actions}</div></header>}{children}</section>
);
export const Spinner = ({ label = "Loading…" }: { label?: string }) => <div className="spinner-wrap" role="status"><span className="spinner" />{label}</div>;
export const ErrorBox = ({ error, retry }: { error: unknown; retry?: () => void }) => (
  <div className="alert alert-red" role="alert">
    <strong>Something went wrong.</strong> {error instanceof Error ? error.message : String(error)}
    {retry && <> <button className="link" onClick={retry}>Try again</button></>}
  </div>
);
export const Empty = ({ title, hint }: { title: string; hint?: string }) => <div className="empty"><p><strong>{title}</strong></p>{hint && <p className="muted">{hint}</p>}</div>;
export const Notice = ({ tone = "yellow", children }: { tone?: Tone; children: ReactNode }) => <div className={`alert alert-${tone}`}>{children}</div>;

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return <label className="field"><span>{label}</span>{children}{hint && <small className="muted">{hint}</small>}</label>;
}

export function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && onClose(); window.addEventListener("keydown", k); return () => window.removeEventListener("keydown", k); }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? "modal-wide" : ""}`} role="dialog" aria-modal="true" aria-label={title}>
        <header className="card-head"><h2>{title}</h2><button className="link" onClick={onClose} aria-label="Close">✕</button></header>
        {children}
      </div>
    </div>
  );
}

/** Confirmation dialog for destructive or irreversible actions. */
export function Confirm({ title, message, confirmLabel = "Confirm", danger, onConfirm, onCancel }: { title: string; message: ReactNode; confirmLabel?: string; danger?: boolean; onConfirm: () => Promise<void> | void; onCancel: () => void }) {
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<unknown>(null);
  return (
    <Modal title={title} onClose={onCancel}>
      <div className="stack">
        <div>{message}</div>
        {err ? <ErrorBox error={err} /> : null}
        <div className="row end">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant={danger ? "danger" : "primary"} busy={busy} onClick={async () => { setBusy(true); try { await onConfirm(); } catch (e) { setErr(e); setBusy(false); } }}>{confirmLabel}</Button>
        </div>
      </div>
    </Modal>
  );
}

/** Loads data with loading / error states; `reload()` refetches. `every` polls (ms). */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = [], every?: number) {
  const [data, setData] = useState<T | null>(null); const [error, setError] = useState<unknown>(null); const [loading, setLoading] = useState(true);
  const fnRef = useRef(fn); fnRef.current = fn;
  const load = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try { setData(await fnRef.current()); setError(null); } catch (e) { setError(e); }
    setLoading(false);
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, deps);
  useEffect(() => { if (!every) return; const id = setInterval(() => void load(true), every); return () => clearInterval(id); }, [every, load]);
  return { data, error, loading, reload: () => load(), setData };
}

export function Async<T>({ state, children, empty }: { state: ReturnType<typeof useAsync<T>>; children: (d: T) => ReactNode; empty?: (d: T) => boolean | ReactNode }) {
  if (state.loading && !state.data) return <Spinner />;
  if (state.error && !state.data) return <ErrorBox error={state.error} retry={state.reload} />;
  if (!state.data) return null;
  return <>{state.error ? <ErrorBox error={state.error} retry={state.reload} /> : null}{children(state.data)}</>;
}

export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return <div className="table-wrap"><table><thead><tr>{head.map((h) => <th key={h}>{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>;
}
