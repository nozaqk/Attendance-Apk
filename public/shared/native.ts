/**
 * Bridge helpers for the Android containers. In a normal browser everything here is a safe no-op.
 *
 * There is no push service (Firebase is not used). The student app polls /api/student/notifications
 * while it is open or running in the Android foreground service and asks the native shell to show
 * a local notification for each new item.
 */
import { useEffect, useRef } from "react";

declare global {
  interface Window {
    AndroidNotify?: { show(id: string, title: string, body: string): void };
  }
}

const SEEN_KEY = "attendance.notified";
function seen(): Set<string> { try { return new Set(JSON.parse(localStorage.getItem(SEEN_KEY) ?? "[]")); } catch { return new Set(); } }
function remember(s: Set<string>) { try { localStorage.setItem(SEEN_KEY, JSON.stringify([...s].slice(-200))); } catch { /* storage unavailable */ } }

export function nativeNotify(id: string, title: string, body: string) {
  try { window.AndroidNotify?.show(id, title, body); } catch { /* bridge missing */ }
}

/** Polls `fetchItems` and raises a native notification for every unseen unread item. */
export function useNotificationPolling(
  fetchItems: () => Promise<{ id: string; title: string; body: string; read: boolean }[]>,
  onChange?: (items: { id: string; title: string; body: string; read: boolean }[]) => void,
  everyMs = 15_000,
) {
  const fetchRef = useRef(fetchItems); fetchRef.current = fetchItems;
  const changeRef = useRef(onChange); changeRef.current = onChange;
  useEffect(() => {
    let stop = false; let first = true;
    const run = async () => {
      try {
        const items = await fetchRef.current();
        if (stop) return;
        const s = seen();
        for (const n of items) {
          if (!n.read && !s.has(n.id)) { if (!first || !s.size) nativeNotify(n.id, n.title, n.body); s.add(n.id); }
        }
        first = false; remember(s); changeRef.current?.(items);
      } catch { /* offline: try again next tick */ }
    };
    void run();
    const t = setInterval(run, everyMs);
    return () => { stop = true; clearInterval(t); };
  }, [everyMs]);
}
