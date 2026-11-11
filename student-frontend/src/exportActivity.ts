import { get } from "./shared/api";
import "./shared/native";
import { LOGO_DATA_URL } from "./logo";

interface Rec { id: string; date: string; courseCode: string; courseName: string; classroom: string; status: string; method: string; observationCount: number; firstDetectedAt: string | null; lastDetectedAt: string | null }
export type Period = "week" | "month";

const pad = (n: number) => String(n).padStart(2, "0");
const ymd = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const hm = (iso: string | null) => (iso ? new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—");

/** Builds a PDF of the student's own class activity for the last 7 days or the current month. */
export async function exportMyActivity(period: Period, student: { name: string; studentId?: string }): Promise<"saved" | "downloaded"> {
  const [{ jsPDF }, autoTableMod, data] = await Promise.all([import("jspdf"), import("jspdf-autotable"), get<{ items: Rec[] }>("/api/student/attendance")]);
  const autoTable = autoTableMod.default;
  const now = new Date();
  const from = period === "week" ? ymd(new Date(now.getTime() - 6 * 86_400_000)) : `${now.getFullYear()}-${pad(now.getMonth() + 1)}-01`;
  const to = ymd(now);
  const rows = data.items.filter((r) => r.date >= from && r.date <= to).sort((a, b) => (a.date < b.date ? 1 : -1));
  const counts: Record<string, number> = {};
  for (const r of rows) counts[r.status] = (counts[r.status] ?? 0) + 1;
  const attended = rows.filter((r) => r.status !== "ABSENT").length;
  const label = period === "week" ? "Weekly" : "Monthly";

  const doc = new jsPDF({ unit: "pt", format: "a4" });
  const W = doc.internal.pageSize.getWidth(); const H = doc.internal.pageSize.getHeight();
  const watermark = () => {
    const size = 280;
    doc.saveGraphicsState();
    doc.setGState(new (doc as unknown as { GState: new (o: { opacity: number }) => unknown }).GState({ opacity: 0.12 }) as never);
    doc.addImage(LOGO_DATA_URL, "PNG", (W - size) / 2, (H - size) / 2, size, size * (148 / 153));
    doc.restoreGraphicsState();
  };

  doc.setFont("helvetica", "bold"); doc.setFontSize(16); doc.text(`${label} Class Activity`, 40, 46);
  doc.setFont("helvetica", "normal"); doc.setFontSize(10); doc.setTextColor(80);
  doc.text(`${student.name}${student.studentId ? `  ·  ${student.studentId}` : ""}`, 40, 64);
  doc.text(`Period: ${from} to ${to}   ·   Generated ${now.toLocaleString()}`, 40, 79);
  const sum = Object.entries(counts).map(([k, v]) => `${k}: ${v}`).join("   ");
  doc.text(rows.length ? `Classes: ${rows.length}   ·   Attended: ${attended} (${Math.round((attended / rows.length) * 100)}%)${sum ? `   ·   ${sum}` : ""}` : "No class activity in this period.", 40, 94);
  doc.setTextColor(0);

  autoTable(doc, {
    startY: 108,
    head: [["Date", "Course", "Room", "Status", "How", "First", "Last"]],
    body: rows.map((r) => [r.date, `${r.courseCode} ${r.courseName}`, r.classroom, r.status, r.method === "LECTURER_MANUAL" ? "Lecturer" : "Bluetooth", hm(r.firstDetectedAt), hm(r.lastDetectedAt)]),
    theme: "plain",
    styles: { fontSize: 8.5, cellPadding: 4, lineColor: [190, 195, 205], lineWidth: 0.4 },
    headStyles: { fillColor: [18, 38, 64], textColor: 255, fontStyle: "bold" },
    margin: { left: 40, right: 40, top: 40, bottom: 40 },
    willDrawPage: watermark,
    didDrawPage: () => { doc.setFontSize(8); doc.setTextColor(120); doc.text(`Page ${doc.getNumberOfPages()}`, W - 40, H - 20, { align: "right" }); doc.setTextColor(0); },
  });
  if (rows.length === 0) watermark();
  const fileName = `my-${period === "week" ? "weekly" : "monthly"}-activity-${to}.pdf`;
  // Inside the Android app WebView cannot download files, so hand the PDF to the native shell.
  if (typeof window.AndroidNotify?.saveFile === "function") {
    const b64 = doc.output("datauristring").split(",")[1] ?? "";
    const r = window.AndroidNotify.saveFile(fileName, "application/pdf", b64);
    if (r !== "ok") throw new Error(`Could not save the file: ${r}`);
    return "saved";
  }
  doc.save(fileName);
  return "downloaded";
}
