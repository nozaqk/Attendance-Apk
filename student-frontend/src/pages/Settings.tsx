import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { get, put } from "../shared/api";
import { useAuth } from "../shared/auth";
import { Async, Button, Card, Confirm, ErrorBox, Field, Notice, useAsync } from "../shared/ui";

interface Dept { id: string; code: string; name: string; semesterCount: number }
interface Profile { fullName: string; email: string; studentId: string; department: Dept | null; semester: number | null; stream: string; semesterCount: number }
const STREAM: Record<string, string> = { REGULAR: "Regular", WEEKLY: "Weekly", EVENING: "Evening" };

const Svg = ({ children, className }: { children: ReactNode; className?: string }) => <svg className={className} viewBox="0 0 24 24" aria-hidden="true">{children}</svg>;
const Chev = () => <Svg className="chev"><path d="M9 5l7 7-7 7" /></Svg>;
const IcCap = () => <Svg className="ico"><path d="M2 9l10-5 10 5-10 5z" /><path d="M6 11.5V16c0 1.5 3 3 6 3s6-1.5 6-3v-4.5" /></Svg>;
const IcBuilding = () => <Svg className="ico"><path d="M4 21V4h10v17M14 9h6v12M2 21h20M8 8h2M8 12h2M8 16h2" /></Svg>;
const IcCal = () => <Svg className="ico"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></Svg>;
const IcTree = () => <Svg className="ico"><circle cx="12" cy="5" r="2.2" /><circle cx="5" cy="19" r="2.2" /><circle cx="19" cy="19" r="2.2" /><path d="M12 7.5V13M12 13H5v3.8M12 13h7v3.8" /></Svg>;
const IcUser = () => <Svg className="ico"><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7" /></Svg>;
const IcId = () => <Svg className="ico"><rect x="2" y="5" width="20" height="14" rx="2" /><circle cx="8" cy="11" r="2" /><path d="M5 16c.5-1.6 1.7-2.3 3-2.3s2.5.7 3 2.3M14 10h5M14 14h5" /></Svg>;
const IcMail = () => <Svg className="ico"><rect x="2" y="5" width="20" height="14" rx="2" /><path d="M3 7l9 6 9-6" /></Svg>;

const Row = ({ icon, k, v }: { icon: ReactNode; k: string; v: ReactNode }) => <div className="info-row">{icon}<span className="k">{k}</span><span className="v">{v}</span></div>;

function ProfileView({ p, onEdit }: { p: Profile; onEdit: () => void }) {
  const { logout } = useAuth();
  const [confirmOut, setConfirmOut] = useState(false);
  return (
    <>
      <Card className="prof-card">
        <div className="avatar"><Svg><circle cx="12" cy="8" r="4.2" /><path d="M3.5 21c0-4.6 3.8-7.4 8.5-7.4s8.5 2.8 8.5 7.4z" /></Svg></div>
        <div><h2>{p.fullName}</h2><p>Student</p><p>Index number: {p.studentId}</p></div>
        <button className="icon-btn" aria-label="Edit details" onClick={onEdit}><Svg><path d="M4 20l1-4L17 4l3 3L8 19z" /><path d="M14.5 6.5l3 3" /></Svg></button>
      </Card>
      <Card className="info-card">
        <button className="info-head" onClick={onEdit}><IcCap /><span>Academic Details</span><Chev /></button>
        <Row icon={<IcBuilding />} k="Department" v={p.department?.name ?? "Not chosen"} />
        <Row icon={<IcCal />} k="Semester" v={p.semester ? `Semester ${p.semester}` : "Not chosen"} />
        <Row icon={<IcTree />} k="Stream" v={STREAM[p.stream] ?? p.stream} />
      </Card>
      <Card className="info-card">
        <button className="info-head" onClick={onEdit}><IcUser /><span>Personal Information</span><Chev /></button>
        <Row icon={<IcUser />} k="Name" v={p.fullName} />
        <Row icon={<IcId />} k="Index number" v={p.studentId} />
        <Row icon={<IcMail />} k="Email" v={p.email} />
      </Card>
      <Button className="btn-big logout-btn" onClick={() => setConfirmOut(true)}>
        <Svg><path d="M9 4H5v16h4M16 8l4 4-4 4M20 12H9" /></Svg>Log out
      </Button>
      {confirmOut && <Confirm title="Log out" confirmLabel="Log out" message="Sign out of this device?" onCancel={() => setConfirmOut(false)} onConfirm={() => logout()} />}
    </>
  );
}

function ProfileForm({ p, depts, firstTime, onSaved, onCancel }: { p: Profile; depts: Dept[]; firstTime: boolean; onSaved: () => Promise<void>; onCancel: () => void }) {
  const [fullName, setFullName] = useState(p.fullName);
  const [departmentId, setDepartmentId] = useState(p.department?.id ?? "");
  const [semester, setSemester] = useState(p.semester ? String(p.semester) : "");
  const [stream, setStream] = useState(p.stream);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const dept = depts.find((d) => d.id === departmentId) ?? p.department;
  const count = dept?.semesterCount ?? p.semesterCount;
  const deptChanged = !!p.department && departmentId !== p.department.id;
  useEffect(() => { if (semester && Number(semester) > count) setSemester(""); }, [count, semester]);

  async function save() {
    setBusy(true); setErr(null);
    try {
      await put("/api/student/settings", { fullName, ...(departmentId ? { departmentId } : {}), ...(semester ? { semester: Number(semester) } : {}), stream });
      await onSaved();
    } catch (x) { setErr(x); throw x; } finally { setBusy(false); }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    if (!semester) { setErr(new Error("Choose your semester")); return; }
    if (deptChanged) setConfirm(true); else void save().catch(() => {});
  }
  return (
    <form className="stack" onSubmit={submit}>
      {firstTime && <Notice>Welcome! Choose your department and semester, then save. This unlocks Home, Class and Course.</Notice>}
      <Card title="Academic Details">
        <div className="stack">
          <Field label="Department">
            <select required value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">Select department…</option>
              {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          {deptChanged && <Notice>Changing department removes any extra courses you added.</Notice>}
          <Field label="Semester">
            <select required value={semester} onChange={(e) => setSemester(e.target.value)}>
              <option value="">Select semester…</option>
              {Array.from({ length: count }, (_, i) => <option key={i + 1} value={i + 1}>Semester {i + 1}</option>)}
            </select>
          </Field>
          <Field label="Stream">
            <select value={stream} onChange={(e) => setStream(e.target.value)}>
              <option value="REGULAR">Regular</option><option value="WEEKLY">Weekly</option><option value="EVENING">Evening</option>
            </select>
          </Field>
        </div>
      </Card>
      <Card title="Personal Information">
        <div className="stack">
          <Field label="Name"><input required minLength={2} maxLength={120} value={fullName} onChange={(e) => setFullName(e.target.value)} /></Field>
          <Field label="Index number"><input value={p.studentId} readOnly disabled /></Field>
          <Field label="Email"><input value={p.email} readOnly disabled /></Field>
        </div>
      </Card>
      {err ? <ErrorBox error={err} /> : null}
      <Button type="submit" busy={busy} className="btn-big">Save details</Button>
      {!firstTime && <Button type="button" variant="secondary" className="btn-big" onClick={onCancel}>Cancel</Button>}
      {confirm && <Confirm title="Change department?" danger confirmLabel="Change department"
        message={<>Moving to <strong>{dept?.name}</strong> will <strong>remove all extra courses</strong> you added from other semesters. Your semester courses will be updated to the new department.</>}
        onCancel={() => setConfirm(false)} onConfirm={async () => { await save(); setConfirm(false); }} />}
    </form>
  );
}

export default function Settings() {
  const { refresh } = useAuth();
  const [editing, setEditing] = useState(false);
  const prof = useAsync(() => get<Profile>("/api/student/profile"), []);
  const depts = useAsync(() => get<{ items: Dept[] }>("/api/student/departments"), []);
  const loaded = { ...prof, data: prof.data && depts.data ? { p: prof.data, d: depts.data.items } : null, loading: prof.loading || depts.loading, error: prof.error ?? depts.error } as any;
  return (
    <div className="stack">
      <Async state={loaded}>{(x: { p: Profile; d: Dept[] }) => {
        const firstTime = !x.p.semester || !x.p.department;
        return editing || firstTime
          ? <ProfileForm p={x.p} depts={x.d} firstTime={firstTime} onCancel={() => setEditing(false)} onSaved={async () => { await Promise.all([refresh(), prof.reload()]); setEditing(false); }} />
          : <ProfileView p={x.p} onEdit={() => setEditing(true)} />;
      }}</Async>
    </div>
  );
}
