import { Badge } from "../shared/ui";
import { fmtTime } from "../shared/format";

export interface Att { status: string; method: string; observationCount: number; firstDetectedAt: string | null; lastDetectedAt: string | null }
export interface SessionItem {
  id: string; courseCode: string; courseName: string; status: string; nodeId: string | null;
  classroom: { name: string; building?: string | null }; lecturer: { name: string } | null;
  startedAt: string | null; openedAt: string; endedAt: string | null; sessionDate: string; attendance: Att | null;
}

/** Green present/verified, yellow late/manual, red absent. */
export function AttBadge({ s }: { s: SessionItem }) {
  const a = s.attendance;
  if (a) return <Badge>{a.method === "LECTURER_MANUAL" && a.status === "PRESENT" ? "MANUAL" : a.status}</Badge>;
  if (s.status === "ACTIVE") return <Badge tone="yellow">PENDING</Badge>;
  return <Badge tone="grey">NOT RECORDED</Badge>;
}
export const timeRange = (s: SessionItem) => `${fmtTime(s.startedAt ?? s.openedAt)}${s.endedAt ? ` – ${fmtTime(s.endedAt)}` : ""}`;
