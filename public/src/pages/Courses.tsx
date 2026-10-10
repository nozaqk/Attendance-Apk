import { get } from "../shared/api";
import { Async, Badge, Card, Empty, useAsync } from "../shared/ui";

interface Course { id: string; code: string; name: string; description: string | null; eligibility: { department: string; code: string; semester: number }[] }

export default function Courses() {
  const st = useAsync(() => get<{ items: Course[] }>("/api/lecturer/courses"), []);
  return (
    <div className="stack">
      <h1>My courses</h1>
      <Async state={st}>{(d) => d.items.length === 0 ? <Card><Empty title="No courses assigned" hint="The administrator assigns courses to you." /></Card> : <>
        {d.items.map((c) => (
          <Card key={c.id} title={`${c.code} · ${c.name}`}>
            {c.description && <p className="muted">{c.description}</p>}
            <p style={{ marginTop: ".5rem" }}><strong>Taken by</strong></p>
            {c.eligibility.length === 0 ? <p className="muted">No department/semester set yet.</p> : <div className="res-counts">{c.eligibility.map((e, i) => <Badge key={i} tone="blue">{e.code} · Sem {e.semester}</Badge>)}</div>}
          </Card>
        ))}
      </>}</Async>
    </div>
  );
}
