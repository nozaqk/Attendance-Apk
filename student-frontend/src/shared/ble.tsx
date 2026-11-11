/**
 * Bridge to the native Android app.
 *
 * The Android container (android/) scans with the real BluetoothLeScanner and calls
 *   window.onBleMeasurement({ nodeId, rssi, timestamp })
 * for each sighting of a classroom ESP32 node. In a normal browser there is no bridge, so no
 * Bluetooth verification is possible — measurements are NEVER simulated.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { post } from "./api";
import { Button, Notice } from "./ui";

export interface BleMeasurement { nodeId: string; rssi: number; timestamp: number }
export interface BleStatus { available: boolean; bluetoothOn: boolean; permissionsGranted: boolean; scanning: boolean; message?: string }

declare global {
  interface Window {
    AndroidBle?: { startScan(): string; stopScan(): void; getStatus(): string; requestPermissions(): void };
    onBleMeasurement?: (m: BleMeasurement) => void;
    onBleStatus?: (s: BleStatus) => void;
  }
}
export const hasNativeBle = () => typeof window !== "undefined" && !!window.AndroidBle;

const parse = (s: string): any => { try { return JSON.parse(s); } catch { return {}; } };

export function useNativeBle(onMeasurement: (m: BleMeasurement) => void) {
  const native = hasNativeBle();
  const [status, setStatus] = useState<BleStatus | null>(null);
  const cb = useRef(onMeasurement); cb.current = onMeasurement;

  useEffect(() => {
    window.onBleMeasurement = (m) => cb.current(m);
    window.onBleStatus = (s) => setStatus(s);
    if (window.AndroidBle) setStatus(parse(window.AndroidBle.getStatus()));
    return () => { window.onBleMeasurement = undefined; window.onBleStatus = undefined; };
  }, []);

  const start = useCallback(() => {
    if (!window.AndroidBle) return;
    const r = parse(window.AndroidBle.startScan());
    setStatus(parse(window.AndroidBle.getStatus()));
    if (r.ok === false && r.needsPermission) window.AndroidBle.requestPermissions();
  }, []);
  const stop = useCallback(() => { window.AndroidBle?.stopScan(); if (window.AndroidBle) setStatus(parse(window.AndroidBle.getStatus())); }, []);
  useEffect(() => () => window.AndroidBle?.stopScan(), []);
  return { native, status, start, stop };
}

export interface VerifyResult { verified: boolean; counted?: boolean; message: string; reason?: string; attendance?: any; session?: any }

/**
 * Scans for the classroom node while `active`, sends each real sighting to the backend
 * (at most one per `minGapMs`) and reports the server's verdict.
 */
export function BleVerifyPanel({ active, sessionId, minGapMs = 20_000, idleLabel, onResult }: {
  active: boolean; sessionId?: string; minGapMs?: number; idleLabel: string; onResult?: (r: VerifyResult) => void;
}) {
  const [last, setLast] = useState<{ nodeId: string; rssi: number } | null>(null);
  const [result, setResult] = useState<VerifyResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [scanning, setScanning] = useState(false);
  const lastSent = useRef(0);

  const handle = useCallback(async (m: BleMeasurement) => {
    setLast({ nodeId: m.nodeId, rssi: m.rssi });
    const now = Date.now();
    if (now - lastSent.current < minGapMs) return;
    lastSent.current = now;
    try {
      const r = await post<VerifyResult>("/api/attendance/ble-observation", { nodeId: m.nodeId, rssi: m.rssi, timestamp: m.timestamp, ...(sessionId ? { sessionId } : {}) });
      setResult(r); setError(null); onResult?.(r);
    } catch (e: any) { setError(e.message); setResult(null); lastSent.current = now - minGapMs + 5000; }
  }, [minGapMs, sessionId, onResult]);

  const { native, status, start, stop } = useNativeBle(handle);

  useEffect(() => { if (!native) return; if (active && !scanning) { start(); setScanning(true); } if (!active && scanning) { stop(); setScanning(false); } }, [active, native, scanning, start, stop]);

  if (!native) {
    return <Notice>Bluetooth verification needs the <strong>Smart Attendance Android app</strong>. This browser cannot scan for the classroom Bluetooth node, so nothing is verified here. Open this page inside the app.</Notice>;
  }
  return (
    <div className="stack">
      {status && !status.permissionsGranted && <Notice>Bluetooth permission is needed. <Button variant="secondary" onClick={() => window.AndroidBle?.requestPermissions()}>Grant permission</Button></Notice>}
      {status && status.permissionsGranted && !status.bluetoothOn && <Notice tone="red">Bluetooth is turned off. Turn it on to verify attendance.</Notice>}
      {status?.message && <p className="muted">{status.message}</p>}
      <p className="muted">{active ? (status?.scanning ? "Scanning for the classroom node…" : "Starting scan…") : idleLabel}</p>
      {last && <p>Node <strong>{last.nodeId}</strong> detected · signal {last.rssi} dBm</p>}
      {error && <Notice tone="red">{error}</Notice>}
      {result && <Notice tone={result.verified ? "green" : "yellow"}><strong>{result.verified ? `${result.message} ✓` : result.message}</strong></Notice>}
    </div>
  );
}
