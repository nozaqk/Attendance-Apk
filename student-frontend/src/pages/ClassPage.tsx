import { useState } from "react";
import { get } from "../shared/api";
import { BleVerifyPanel, type VerifyResult } from "../shared/ble";
import { fmtTime } from "../shared/format";
import { useAuth } from "../shared/auth";
import { Async, Badge, Button, Card, Empty, ErrorBox, Modal, useAsync } from "../shared/ui";
import type { Att, SessionItem } from "./common";

function LiveCard({ s, onResult }: { s: SessionItem; onResult: () => void }) {
  const [res, setRes] = useState<VerifyResult | null>(null);
  const att: Att | null = res?.attendance ?? s.attendance;
  return (
    <Card>
      <div className="row between">
        <h2>{s.courseName} is in session</h2><Badge tone="green">LIVE</Badge>
      </div>
      <p className="muted">{s.courseCode} · {s.classroom.name}{s.classroom.building ? `, ${s.classroom.building}` : ""}</p>
      <p className="muted">Started {fmtTime(s.startedAt ?? s.openedAt)}{s.lecturer ? ` · ${s.lecturer.name}` : ""}</p>
      <hr style={{ border: 0, borderTop: "1px solid var(--line)", margin: ".8rem 0" }} />
      <div className="stack">
        <BleVerifyPanel active sessionId={s.id} idleLabel="Waiting" onResult={(r) => { setRes(r); onResult(); }} />
        {att && (
          <div className="stack">
            {res?.verified && <div className="verdict-big alert-green">ATTENDANCE VERIFIED ✓</div>}
            <div className="row"><Badge>{att.status}</Badge><span className="muted">{att.method === "LECTURER_MANUAL" ? "Recorded by lecturer" : "Verified by Bluetooth"}</span></div>
            <p className="muted">Observations: <strong>{att.observationCount}</strong> · First detected {fmtTime(att.firstDetectedAt)} · Last detected {fmtTime(att.lastDetectedAt)}</p>
          </div>
        )}
      </div>
    </Card>
  );
}

function ExportDialog({ onClose }: { onClose: () => void }) {
  const { user } = useAuth();
  const [busy, setBusy] = useState<"week" | "month" | null>(null); const [err, setErr] = useState<unknown>(null); const [done, setDone] = useState(false);
  async function run(period: "week" | "month") {
    setErr(null); setBusy(period);
    try {
      const { exportMyActivity } = await import("../exportActivity");
      const r = await exportMyActivity(period, { name: user?.fullName ?? "Student", studentId: user?.student?.studentId });
      if (r === "saved") { setDone(true); setBusy(null); } else onClose();
    } catch (x) { setErr(x); setBusy(null); }
  }
  return (
    <Modal title="Export my activity" onClose={onClose}>
      <div className="stack">
        <p className="muted">Download your class attendance as a document.</p>
        <Button busy={busy === "week"} disabled={busy !== null} onClick={() => void run("week")}>Last 7 days (weekly)</Button>
        <Button variant="secondary" busy={busy === "month"} disabled={busy !== null} onClick={() => void run("month")}>This month (monthly)</Button>
        {done && <p className="alert-green" style={{ padding: ".6rem", borderRadius: 8 }}>Saved to your Downloads folder.</p>}
        {err ? <ErrorBox error={err} /> : null}
      </div>
    </Modal>
  );
}

export default function ClassPage() {
  const st = useAsync(() => get<{ items: SessionItem[] }>("/api/student/classes/live"), [], 10_000);
  const [exporting, setExporting] = useState(false);
  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "flex-end" }}><Button variant="secondary" onClick={() => setExporting(true)}>Export</Button></div>
      {exporting && <ExportDialog onClose={() => setExporting(false)} />}
      <Async state={st}>{(d) => d.items.length === 0 ? (
        <Card><Empty title="No class in session right now" hint="When your lecturer starts a class for one of your courses it will show up here and you can verify your attendance." /></Card>
      ) : d.items.map((s) => <LiveCard key={s.id} s={s} onResult={() => void st.reload()} />)}</Async>
    </div>
  );
}
