import { useState, type FormEvent } from "react";
import { post } from "./shared/api";
import { Badge, Button, ErrorBox, Field, Notice } from "./shared/ui";
import { fmtStamp, fmtTime } from "./shared/format";

export interface RosterItem {
  studentProfileId: string; studentId: string; fullName: string; department: string | null; semester: number | null; stream: string;
  status: string; method: string; firstDetectedAt: string | null; lastDetectedAt: string | null; observationCount: number;
  lastRssi: number | null; note: string | null; provisional: boolean;
}
export const isPresent = (s: string) => s !== "ABSENT";
export const STREAM: Record<string, string> = { REGULAR: "Regular", WEEKLY: "Weekly", EVENING: "Evening" };
export const streamLabel = (s?: string | null) => (s ? STREAM[s] ?? s : "—");

export function MethodBadge({ method }: { method: string }) {
  if (method === "BLE") return <Badge tone="green">Bluetooth verified</Badge>;
  if (method === "LECTURER_MANUAL") return <Badge tone="yellow">LECTURER_MANUAL</Badge>;
  return null;
}

export function RosterRow({ r, live }: { r: RosterItem; live?: boolean }) {
  const yes = isPresent(r.status);
  return (
    <div className="roster-row">
      <span className={`mark ${yes ? "mark-yes" : "mark-no"}`} aria-label={yes ? "Present" : "Absent"}>{yes ? "✓" : "✗"}</span>
      <div className="roster-main">
        <div className="nm">{r.fullName}</div>
        <div className="roster-meta">{r.studentId}{r.department ? ` · ${r.department}` : ""}</div>
        {live && r.firstDetectedAt && (
          <div className="roster-meta">First {fmtTime(r.firstDetectedAt)} · Last {fmtTime(r.lastDetectedAt)} · {r.observationCount} obs{r.lastRssi != null ? ` · ${r.lastRssi} dBm` : ""}</div>
        )}
        {r.note && <div className="roster-meta">Note: {r.note}</div>}
      </div>
      <div style={{ textAlign: "right" }}>
        <Badge>{r.status}</Badge>
        <div><MethodBadge method={r.method} /></div>
      </div>
    </div>
  );
}

export function EditWindow({ locked, editableUntil }: { locked: boolean; editableUntil?: string | null }) {
  if (locked) return <Notice tone="red"><strong>ATTENDANCE LOCKED</strong></Notice>;
  if (!editableUntil) return null;
  return <Notice tone="yellow">Attendance editing closes: <strong>{fmtStamp(editableUntil)}</strong></Notice>;
}

/** Manual attendance (clearly recorded as LECTURER_MANUAL). The server enforces the 6-hour window. */
export function ManualAdd({ sessionId, onDone }: { sessionId: string; onDone: () => void }) {
  const [studentId, setStudentId] = useState(""); const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<unknown>(null); const [ok, setOk] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(null); setOk(null);
    try {
      const r = await post<{ record: { fullName: string; studentId: string } }>("/api/lecturer/attendance/manual", { sessionId, studentId: studentId.trim(), ...(note.trim() ? { note: note.trim() } : {}) });
      setOk(`${r.record.fullName} (${r.record.studentId}) recorded as MANUAL present.`); setStudentId(""); setNote(""); onDone();
    } catch (x) { setErr(x); }
    setBusy(false);
  }
  return (
    <form className="stack" onSubmit={submit}>
      <p className="muted">For a student without a phone, with a flat battery or who forgot it. Recorded as <strong>LECTURER_MANUAL</strong>, separate from Bluetooth-verified attendance.</p>
      <Field label="Student index number"><input required minLength={3} value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="e.g. ECE/21/005" autoCapitalize="characters" /></Field>
      <Field label="Note (optional)"><input value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} placeholder="e.g. phone battery dead" /></Field>
      {err ? <ErrorBox error={err} /> : null}
      {ok && <Notice tone="green">{ok}</Notice>}
      <Button type="submit" busy={busy}>Mark present manually</Button>
    </form>
  );
}
