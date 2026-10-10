import { useMemo, useState } from "react";
import { get } from "../shared/api";
import { Async, Badge, Card, Empty, useAsync } from "../shared/ui";
import { streamLabel } from "../lib";

interface Stu { id: string; studentId: string; fullName: string; department: string | null; departmentCode: string | null; semester: number | null; stream: string; courses: string[] }

export default function Students() {
  const [courseId, setCourseId] = useState(""); const [q, setQ] = useState("");
  const courses = useAsync(() => get<{ items: { id: string; code: string; name: string }[] }>("/api/lecturer/courses"), []);
  const st = useAsync(() => get<{ items: Stu[] }>(`/api/lecturer/students${courseId ? `?courseId=${encodeURIComponent(courseId)}` : ""}`), [courseId]);
  const rows = useMemo(() => {
    const n = q.trim().toLowerCase();
    return (st.data?.items ?? []).filter((s) => !n || s.fullName.toLowerCase().includes(n) || s.studentId.toLowerCase().includes(n));
  }, [st.data, q]);
  return (
    <div className="stack">
      <h1>Students</h1>
      <div className="filters">
        <input aria-label="Search" placeholder="Search name or index number" value={q} onChange={(e) => setQ(e.target.value)} style={{ font: "inherit", padding: ".6rem .7rem", border: "1px solid var(--line)", borderRadius: 10 }} />
        <select aria-label="Course" value={courseId} onChange={(e) => setCourseId(e.target.value)} style={{ font: "inherit", padding: ".6rem .7rem", border: "1px solid var(--line)", borderRadius: 10, background: "#fff" }}>
          <option value="">All my courses</option>
          {courses.data?.items.map((c) => <option key={c.id} value={c.id}>{c.code} · {c.name}</option>)}
        </select>
      </div>
      <Async state={st}>{() => rows.length === 0 ? <Card><Empty title="No students found" hint={q ? "Try a different search." : "Students appear here once they pick a department and semester matching your courses."} /></Card> : (
        <Card title={`${rows.length} student${rows.length === 1 ? "" : "s"}`}>
          {rows.map((s) => (
            <div className="list-card" key={s.id}>
              <div className="row between"><strong>{s.fullName}</strong><Badge tone="blue">{streamLabel(s.stream)}</Badge></div>
              <div className="mono muted">{s.studentId}</div>
              <div className="muted">{s.department ?? "No department"}{s.semester ? ` · Semester ${s.semester}` : ""}</div>
              <div className="muted">{s.courses.join(", ")}</div>
            </div>
          ))}
        </Card>
      )}</Async>
    </div>
  );
}
