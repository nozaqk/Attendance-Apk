import { useEffect } from "react";
import { get, post } from "../shared/api";
import { fmtDateTime } from "../shared/format";
import { Async, Button, Card, Empty, useAsync } from "../shared/ui";

interface N { id: string; title: string; body: string; read: boolean; createdAt: string }

export default function Notifications({ onRead }: { onRead: () => void }) {
  const st = useAsync(() => get<{ unread: number; items: N[] }>("/api/student/notifications"), []);
  async function markAll() { await post("/api/student/notifications/read", {}); onRead(); await st.reload(); }
  useEffect(() => { /* keep unread markers visible until the student chooses to clear them */ }, []);
  return (
    <Card title="Notifications" actions={st.data && st.data.unread > 0 ? <Button variant="secondary" onClick={() => void markAll()}>Mark all read</Button> : null}>
      <Async state={st}>{(d) => d.items.length === 0 ? <Empty title="No notifications" hint="You will be told when a class starts or attendance is finalised." /> :
        d.items.map((n) => (
          <div key={n.id} className={`notif ${n.read ? "" : "unread"}`}>
            <strong>{n.title}</strong><p>{n.body}</p><span className="muted">{fmtDateTime(n.createdAt)}</span>
          </div>
        ))}</Async>
    </Card>
  );
}
