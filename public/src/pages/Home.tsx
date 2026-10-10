import { useNavigate } from "react-router-dom";
import { get } from "../shared/api";
import { Async, Badge, Card, Empty, useAsync } from "../shared/ui";
import { fmtStamp } from "../shared/format";

export interface SessionItem {
  id: string; courseCode: string; courseName: string; classroom: { name: string }; sessionDate: string; startedAt: string | null;
  endedAt: string | null; editableUntil: string | null; locked: boolean; status: string; presentCount: number; absentCount: number;
}

export default function Home() {
  const nav = useNavigate();
  const st = useAsync(() => get<{ items: SessionItem[] }>("/api/lecturer/sessions"), [], 15000);
  return (
    <div className="stack">
      <h1>Class results</h1>
      <Card>
        <Async state={st}>{(d) => d.items.length === 0 ? <Empty title="No classes yet" hint="Classes you start from the Class tab will appear here with their results." /> : (
          <div>
            {d.items.map((s) => (
              <button key={s.id} className="res-card" onClick={() => nav(`/results/${s.id}`)}>
                <div className="row between">
                  <strong>{s.courseCode}</strong>
                  <Badge tone={s.status === "ACTIVE" ? "green" : "grey"}>{s.status === "ACTIVE" ? "IN PROGRESS" : s.locked ? "LOCKED" : "ENDED"}</Badge>
                </div>
                <div>{s.courseName}</div>
                <div className="muted">{s.sessionDate} · {s.classroom.name}</div>
                <div className="res-counts">
                  <Badge tone="green">Present {s.presentCount}</Badge><Badge tone="red">Absent {s.absentCount}</Badge>
                  {!s.locked && s.editableUntil && <Badge tone="yellow">Edit until {fmtStamp(s.editableUntil)}</Badge>}
                </div>
              </button>
            ))}
          </div>
        )}</Async>
      </Card>
    </div>
  );
}
