import { useState } from "react";
import { del, get, post } from "../shared/api";
import { Async, Badge, Button, Card, Confirm, Empty, Notice, useAsync } from "../shared/ui";

interface C { id: string; code: string; name: string; lecturer: string | null; isExtra?: boolean; semester: number | null }

export default function Courses() {
  const mine = useAsync(() => get<{ semester: number | null; items: C[] }>("/api/student/courses"), []);
  const avail = useAsync(() => get<{ items: C[] }>("/api/student/courses/available"), []);
  const [showAdd, setShowAdd] = useState(false);
  const [remove, setRemove] = useState<C | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const refresh = async () => { await Promise.all([mine.reload(), avail.reload()]); };

  async function add(c: C) {
    setBusyId(c.id); setErr(null);
    try { await post("/api/student/courses/extra", { courseId: c.id }); await refresh(); } catch (e: any) { setErr(e.message); }
    setBusyId(null);
  }

  const groups = new Map<number, C[]>();
  avail.data?.items.forEach((c) => { const k = c.semester ?? 0; groups.set(k, [...(groups.get(k) ?? []), c]); });

  return (
    <div className="stack">
      <Card title={mine.data?.semester ? `Semester ${mine.data.semester} courses` : "My courses"}>
        <Async state={mine}>{(d) => d.items.length === 0 ? <Empty title="No courses yet" hint="Courses for your department and semester appear here once the administrator sets them up." /> :
          d.items.map((c) => (
            <div className="class-item" key={c.id}>
              <div className="grow">
                <div className="title">{c.name}</div>
                <div className="muted">{c.code}{c.lecturer ? ` · ${c.lecturer}` : " · No lecturer assigned"}</div>
              </div>
              {c.isExtra ? <div className="stack" style={{ gap: ".3rem", alignItems: "flex-end" }}><Badge tone="blue">Added</Badge><button className="link" onClick={() => setRemove(c)}>Remove</button></div> : null}
            </div>
          ))}</Async>
      </Card>
      <Card title="Add courses from other semesters" actions={<Button variant="secondary" onClick={() => setShowAdd(!showAdd)}>{showAdd ? "Hide" : "Browse"}</Button>}>
        {!showAdd ? <p className="muted">Taking a course from another semester (for example a carry-over)? Add it here so you can be marked for its classes.</p> : (
          <Async state={avail}>{() => groups.size === 0 ? <Empty title="Nothing to add" hint="There are no other courses in your department." /> : (
            <>
              {err && <Notice tone="red">{err}</Notice>}
              {[...groups.entries()].sort((a, b) => a[0] - b[0]).map(([sem, list]) => (
                <div key={sem}>
                  <div className="sem-head">Semester {sem || "?"}</div>
                  {list.map((c) => (
                    <div className="class-item" key={c.id}>
                      <div className="grow"><div className="title">{c.name}</div><div className="muted">{c.code}{c.lecturer ? ` · ${c.lecturer}` : ""}</div></div>
                      <Button variant="secondary" busy={busyId === c.id} onClick={() => void add(c)}>Add</Button>
                    </div>
                  ))}
                </div>
              ))}
            </>
          )}</Async>
        )}
      </Card>
      {remove && <Confirm title="Remove course" danger confirmLabel="Remove" message={<>Remove <strong>{remove.name}</strong> ({remove.code}) from your courses? You will no longer be marked for its classes.</>}
        onCancel={() => setRemove(null)} onConfirm={async () => { await del(`/api/student/courses/extra/${remove.id}`); setRemove(null); await refresh(); }} />}
    </div>
  );
}
