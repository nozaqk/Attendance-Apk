import { useState } from "react";
import { Link } from "react-router-dom";
import { get } from "../shared/api";
import { Async, Card, Empty, useAsync } from "../shared/ui";
import { AttBadge, timeRange, type SessionItem } from "./common";

interface Dash { period: string; from: string; to: string; items: SessionItem[]; liveClass: SessionItem | null; stats: Record<string, number>; unreadNotifications: number }

export default function Home() {
  const [period, setPeriod] = useState<"day" | "week">("day");
  const st = useAsync(() => get<Dash>(`/api/student/dashboard?period=${period}`), [period], 20_000);
  return (
    <div className="stack">
      <div className="seg" role="tablist">
        <button className={period === "day" ? "on" : ""} onClick={() => setPeriod("day")}>Today</button>
        <button className={period === "week" ? "on" : ""} onClick={() => setPeriod("week")}>This week</button>
      </div>
      <Async state={st}>{(d) => (
        <>
          {d.liveClass && (
            <Link to="/class" className="live-banner"><span className="pulse" />
              <div><strong>{d.liveClass.courseName} is in session</strong><div className="muted" style={{ color: "inherit" }}>{d.liveClass.classroom.name} · tap to verify attendance</div></div>
            </Link>
          )}
          {d.unreadNotifications > 0 && <Link to="/notifications" className="link">{d.unreadNotifications} unread notification{d.unreadNotifications > 1 ? "s" : ""}</Link>}
          <div className="stat-row">
            <div className="card"><div className="num num-green">{(d.stats.PRESENT ?? 0)}</div><div className="lbl">Present</div></div>
            <div className="card"><div className="num num-yellow">{d.stats.LATE ?? 0}</div><div className="lbl">Late</div></div>
            <div className="card"><div className="num num-yellow">{d.stats.MANUAL ?? 0}</div><div className="lbl">Manual</div></div>
            <div className="card"><div className="num num-red">{d.stats.ABSENT ?? 0}</div><div className="lbl">Absent</div></div>
          </div>
          <p className="muted" style={{ marginTop: "-.4rem" }}>Overall totals across all your classes.</p>
          <Card title={period === "day" ? "Today's classes" : `Classes ${d.from} to ${d.to}`}>
            {d.items.length === 0 ? <Empty title={period === "day" ? "No classes today yet" : "No classes this week"} hint="Classes appear here once your lecturer starts them." /> :
              d.items.map((s) => (
                <div className="class-item" key={s.id}>
                  <div className="class-time">{new Date(s.startedAt ?? s.openedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</div>
                  <div className="grow">
                    <div className="title">{s.courseName}</div>
                    <div className="muted">{s.courseCode} · {s.classroom.name}{period === "week" ? ` · ${s.sessionDate}` : ""}</div>
                    <div className="muted">{timeRange(s)}{s.status === "ACTIVE" ? " · in session" : ""}</div>
                  </div>
                  <AttBadge s={s} />
                </div>
              ))}
          </Card>
        </>
      )}</Async>
    </div>
  );
}
