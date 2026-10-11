import { useState } from "react";
import { get, post } from "../shared/api";
import { BleVerifyPanel } from "../shared/ble";
import { Async, Badge, Button, Card, Confirm, Empty, ErrorBox, Modal, Notice, Spinner, useAsync } from "../shared/ui";
import { fmtStamp } from "../shared/format";
import { EditWindow, isPresent, ManualAdd, RosterRow, type RosterItem } from "../lib";

interface Sess { id: string; courseCode: string; courseName: string; classroom: { name: string }; nodeId: string | null; status: string; lecturerVerified: boolean; editableUntil: string | null; locked: boolean }
interface Room { id: string; name: string; building: string | null; floor: string | number | null; zone: string | null; nodeId: string | null; nodeActive: boolean; nodeOnline: boolean; occupiedBy: { lecturer: string | null; courseCode: string; courseName: string } | null }
interface Course { id: string; code: string; name: string }
interface Finished { session: Sess; present: number; absent: number; total: number }

export default function ClassPage() {
  const cur = useAsync(() => get<{ session: Sess | null }>("/api/lecturer/current"), [], 5000);
  const [finished, setFinished] = useState<Finished | null>(null);

  if (finished) return <FinishedView f={finished} onNew={() => { setFinished(null); void cur.reload(); }} />;
  return (
    <div className="stack">
      <h1>Class</h1>
      <Async state={cur}>{(d) => !d.session ? <Select onCreated={() => void cur.reload()} />
        : d.session.status === "SCHEDULED" ? <Verify s={d.session} reload={() => void cur.reload()} />
        : <Live s={d.session} onEnded={(f) => { setFinished(f); void cur.reload(); }} />}</Async>
    </div>
  );
}

function Select({ onCreated }: { onCreated: () => void }) {
  const courses = useAsync(() => get<{ items: Course[] }>("/api/lecturer/courses"), []);
  const rooms = useAsync(() => get<{ items: Room[] }>("/api/lecturer/classrooms"), [], 10000);
  const [courseId, setCourseId] = useState(""); const [roomId, setRoomId] = useState("");
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  async function go() {
    setBusy(true); setErr(null);
    try { await post("/api/lecturer/classes", { courseId, classroomId: roomId }); onCreated(); }
    catch (e: any) { setErr(e.message); void rooms.reload(); setBusy(false); }
  }
  return (
    <>
      <Card title="1. Choose your course">
        <Async state={courses}>{(d) => d.items.length === 0 ? <Empty title="No courses assigned" hint="Ask the administrator to assign a course to you." /> : (
          <div className="stack">{d.items.map((c) => (
            <button key={c.id} className={`room-btn ${courseId === c.id ? "sel" : ""}`} onClick={() => setCourseId(c.id)}><strong>{c.code}</strong> · {c.name}</button>
          ))}</div>
        )}</Async>
      </Card>
      <Card title="2. Choose the classroom">
        <Async state={rooms}>{(d) => d.items.length === 0 ? <Empty title="No classrooms registered" /> : (
          <div className="stack">{d.items.map((r) => {
            const taken = !!r.occupiedBy; const noNode = !r.nodeId || !r.nodeActive;
            return (
              <button key={r.id} className={`room-btn ${roomId === r.id ? "sel" : ""}`} disabled={taken || noNode} onClick={() => setRoomId(r.id)}>
                <div className="row between"><strong>{r.name}</strong>
                  {taken ? <Badge tone="red">TAKEN</Badge> : noNode ? <Badge tone="red">NO NODE</Badge> : r.nodeOnline ? <Badge tone="green">NODE ONLINE</Badge> : <Badge tone="yellow">NODE OFFLINE</Badge>}</div>
                <div className="muted">{[r.building, r.floor != null ? `Floor ${r.floor}` : null, r.zone].filter(Boolean).join(" · ")}</div>
                {taken && <div style={{ color: "var(--red)" }}>Selected by {r.occupiedBy!.lecturer ?? "another lecturer"} lecturing {r.occupiedBy!.courseCode}</div>}
                {!taken && noNode && <div style={{ color: "var(--red)" }}>No Bluetooth node: attendance cannot be verified here.</div>}
                {!taken && !noNode && !r.nodeOnline && <div style={{ color: "var(--yellow)" }}>The node has not reported recently. Check it is powered.</div>}
              </button>
            );
          })}</div>
        )}</Async>
      </Card>
      {err && <Notice tone="red">{err}</Notice>}
      <Button className="btn-big" disabled={!courseId || !roomId} busy={busy} onClick={() => void go()}>Select course and room</Button>
    </>
  );
}

function Verify({ s, reload }: { s: Sess; reload: () => void }) {
  const [verified, setVerified] = useState(false); const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false); const [cancel, setCancel] = useState(false);
  const ok = verified || s.lecturerVerified;
  async function start() {
    setBusy(true); setErr(null);
    try { await post(`/api/lecturer/sessions/${s.id}/start`); reload(); } catch (e: any) { setErr(e.message); setBusy(false); }
  }
  return (
    <>
      <Card title={`${s.courseCode} · ${s.courseName}`}>
        <p className="muted">{s.classroom.name}{s.nodeId ? ` · node ${s.nodeId}` : ""}</p>
        <Notice>This room is reserved for you. Others cannot use it until you press <strong>Class over</strong>.</Notice>
      </Card>
      <Card title="Verify you are in the room">
        <BleVerifyPanel active sessionId={s.id} minGapMs={5000} idleLabel="Waiting…" onResult={(r) => { if (r.verified) { setVerified(true); reload(); } }} />
        {ok && <div className="big-status alert-green" style={{ marginTop: ".8rem" }}>LECTURER VERIFIED ✓</div>}
      </Card>
      {err && <Notice tone="red">{err}</Notice>}
      <Button className="btn-big" disabled={!ok} busy={busy} onClick={() => void start()}>Start class</Button>
      <Button variant="secondary" className="btn-big" onClick={() => setCancel(true)}>Cancel selection</Button>
      {cancel && <Confirm title="Cancel selection?" message={`This releases ${s.classroom.name} so another lecturer can use it.`} confirmLabel="Cancel selection" danger
        onConfirm={async () => { await post(`/api/lecturer/sessions/${s.id}/cancel`); setCancel(false); reload(); }} onCancel={() => setCancel(false)} />}
    </>
  );
}

function Live({ s, onEnded }: { s: Sess; onEnded: (f: Finished) => void }) {
  const r = useAsync(() => get<{ items: RosterItem[] }>(`/api/lecturer/sessions/${s.id}/attendance`), [s.id], 5000);
  const [end, setEnd] = useState(false); const [add, setAdd] = useState(false);
  const items = r.data?.items ?? [];
  const present = items.filter((x) => isPresent(x.status)).length;
  return (
    <>
      <Card title={`${s.courseCode} · ${s.courseName}`} actions={<Badge tone="green">IN PROGRESS</Badge>}>
        <p className="muted">{s.classroom.name}</p>
        <div className="counts-head" style={{ marginTop: ".6rem" }}>
          <div><div className="num">{items.length}</div><small className="muted">Students</small></div>
          <div className="g"><div className="num">{present}</div><small className="muted">Present</small></div>
          <div className="r"><div className="num">{items.length - present}</div><small className="muted">Absent</small></div>
        </div>
      </Card>
      <Card title="Your presence"><BleVerifyPanel active sessionId={s.id} minGapMs={60000} idleLabel="Waiting…" /></Card>
      <Card title="Live attendance" actions={<button className="link" onClick={() => void r.reload()}>Refresh</button>}>
        {r.loading && !r.data ? <Spinner /> : r.error && !r.data ? <ErrorBox error={r.error} retry={r.reload} /> : items.length === 0 ? <Empty title="No students on this course yet" /> : items.map((x) => <RosterRow key={x.studentProfileId} r={x} live />)}
        <p className="muted">Updates every 5 seconds.</p>
      </Card>
      <Button variant="secondary" className="btn-big" onClick={() => setAdd(true)}>+ Add student manually</Button>
      <Notice>The room stays reserved for you until you press Class over.</Notice>
      <Button variant="danger" className="btn-big" onClick={() => setEnd(true)}>Class over</Button>
      {add && <Modal title="Manual attendance" onClose={() => setAdd(false)}><ManualAdd sessionId={s.id} onDone={() => void r.reload()} /></Modal>}
      {end && <Confirm title="Class over?" danger confirmLabel="Class over"
        message={<>Everyone not detected will be marked <strong>ABSENT</strong>, the room is released, and you will have 6 hours to correct attendance manually. Continue?</>}
        onConfirm={async () => {
          const res = await post<{ session: Sess }>(`/api/lecturer/sessions/${s.id}/end`);
          const final = await get<{ items: RosterItem[] }>(`/api/lecturer/sessions/${s.id}/attendance`).catch(() => ({ items }));
          const p = final.items.filter((x) => isPresent(x.status)).length;
          setEnd(false); onEnded({ session: res.session, present: p, absent: final.items.length - p, total: final.items.length });
        }} onCancel={() => setEnd(false)} />}
    </>
  );
}

function FinishedView({ f, onNew }: { f: Finished; onNew: () => void }) {
  return (
    <div className="stack">
      <h1>Class finished</h1>
      <Card title={`${f.session.courseCode} · ${f.session.courseName}`}>
        <p className="muted">{f.session.classroom.name} · room released</p>
        <div className="counts-head" style={{ marginTop: ".6rem" }}>
          <div><div className="num">{f.total}</div><small className="muted">Students</small></div>
          <div className="g"><div className="num">{f.present}</div><small className="muted">Present</small></div>
          <div className="r"><div className="num">{f.absent}</div><small className="muted">Absent</small></div>
        </div>
      </Card>
      <EditWindow locked={f.session.locked} editableUntil={f.session.editableUntil} />
      <p className="muted">Open the class from Home to add students manually until {fmtStamp(f.session.editableUntil)}.</p>
      <Button className="btn-big" onClick={onNew}>Done</Button>
    </div>
  );
}
