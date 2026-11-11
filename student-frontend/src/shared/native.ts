/**
 * Bridge helpers for the Android containers. In a normal browser everything here is a safe no-op.
 *
 * There is no push service (Firebase is not used). While the app is open the page polls
 * /api/student/notifications and asks the native shell to show a notification for each new item.
 * When the app is closed, a small Android foreground service keeps checking the server with a
 * restricted device token (see startBackgroundNotifications) and shows the same notifications.
 */
import { useEffect, useRef } from "react";

declare global {
  interface Window {
    AndroidNotify?: {
      show(id: string, title: string, body: string): void;
      setSession?(apiBase: string, token: string): void;
      clearSession?(): void;
      backgroundStatus?(): string;
      requestUnrestricted?(): void;
      saveFile?(name: string, mime: string, base64: string): string;
    };
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


// ---- Notifications while the app is closed (Android app only; harmless in a browser) ---------------
const API = ((import.meta as any).env?.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

export interface BackgroundStatus { available: boolean; active: boolean; unrestricted: boolean }
export function backgroundStatus(): BackgroundStatus {
  try {
    const raw = window.AndroidNotify?.backgroundStatus?.();
    if (!raw) return { available: false, active: false, unrestricted: false };
    const o = JSON.parse(raw);
    return { available: true, active: !!o.active, unrestricted: !!o.unrestricted };
  } catch { return { available: false, active: false, unrestricted: false }; }
}

/** Asks the server for a restricted device token and hands it to the phone's background service. */
export async function startBackgroundNotifications(
  getToken: () => Promise<string>,
) {
  try {
    if (!window.AndroidNotify?.setSession || !API) return;
    if (backgroundStatus().active) return;
    window.AndroidNotify.setSession(API, await getToken());
  } catch { /* offline: tried again next time the app opens */ }
}
export function stopBackgroundNotifications() { try { window.AndroidNotify?.clearSession?.(); } catch { /* bridge missing */ } }
export function requestUnrestricted() { try { window.AndroidNotify?.requestUnrestricted?.(); } catch { /* bridge missing */ } }
