import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { get } from "../shared/api";
import { Async, Badge, Card, Modal, useAsync } from "../shared/ui";
import { EditWindow, ManualAdd, RosterRow, type RosterItem } from "../lib";

interface Res { session: { courseCode: string; courseName: string; classroom: { name: string }; sessionDate: string; status: string; editableUntil: string | null; locked: boolean; startedAt: string | null; endedAt: string | null };
  summary: { total: number; present: number; verified: number; late: number; manual: number; absent: number }; items: RosterItem[] }

export default function Result() {
  const { id } = useParams(); const nav = useNavigate();
  const st = useAsync(() => get<Res>(`/api/lecturer/sessions/${id}/attendance`), [id], 10000);
  const [adding, setAdding] = useState(false);
  return (
    <div className="stack">
      <button className="back" onClick={() => nav("/")}>← Class results</button>
      <Async state={st}>{({ session: s, summary: m, items }) => {
        const present = m.total - m.absent;
        return <>
          <Card title={`${s.courseCode} · ${s.courseName}`}>
            <p className="muted">{s.sessionDate} · {s.classroom.name}</p>
            <p><Badge tone={s.status === "ACTIVE" ? "green" : "grey"}>{s.status}</Badge></p>
            <div className="counts-head" style={{ marginTop: ".6rem" }}>
              <div><div className="num">{m.total}</div><small className="muted">Students</small></div>
              <div className="g"><div className="num">{present}</div><small className="muted">Present</small></div>
              <div className="r"><div className="num">{m.absent}</div><small className="muted">Absent</small></div>
            </div>
            <p className="muted" style={{ marginTop: ".6rem" }}>Bluetooth verified {m.verified + m.present + m.late} · Manual {m.manual}</p>
          </Card>
          {s.status === "ENDED" && <EditWindow locked={s.locked} editableUntil={s.editableUntil} />}
          {s.status === "ACTIVE" && <Notice2 />}
          {(s.status === "ENDED" && !s.locked || s.status === "ACTIVE") && <button className="btn btn-secondary" onClick={() => setAdding(true)}>+ Add student manually</button>}
          <Card title="Roster">{items.length === 0 ? <p className="muted">No students on this class.</p> : items.map((r) => <RosterRow key={r.studentProfileId} r={r} live />)}</Card>
          {adding && <Modal title="Manual attendance" onClose={() => setAdding(false)}><ManualAdd sessionId={id!} onDone={() => void st.reload()} /></Modal>}
        </>;
      }}</Async>
    </div>
  );
}
function Notice2() { return <p className="muted">This class is still in progress. Go to the Class tab to manage it.</p>; }
