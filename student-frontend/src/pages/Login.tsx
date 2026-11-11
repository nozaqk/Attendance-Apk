import { useState, type FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import { get, post } from "../shared/api";
import { useAuth } from "../shared/auth";
import { Button, Card, ErrorBox, Field, Notice, useAsync } from "../shared/ui";

interface Dept { id: string; code: string; name: string; semesterCount: number }

export default function Login() {
  const { user, login, applyToken } = useAuth();
  const [mode, setMode] = useState<"in" | "up">("in");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [password2, setPassword2] = useState("");
  const [f, setF] = useState({ firstName: "", secondName: "", studentId: "", departmentId: "", stream: "REGULAR" });
  const depts = useAsync(() => get<{ items: Dept[] }>("/api/public/departments"), []);

  if (user) return <Navigate to="/" replace />;

  async function signIn(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await login(email, password); } catch (x) { setErr(x); setBusy(false); }
  }
  async function signUp(e: FormEvent) {
    e.preventDefault(); setErr(null);
    if (password !== password2) { setErr(new Error("The two passwords do not match")); return; }
    setBusy(true);
    try {
      const r = await post<{ token: string }>("/api/auth/register/student", { ...f, email, password });
      await applyToken(r.token); // lands in Setting because no semester is set yet
    } catch (x) { setErr(x); setBusy(false); }
  }
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  return (
    <div className="center-screen"><div className="auth-wrap">
      <div className="auth-brand"><div style={{ fontSize: "2.4rem" }}>📡</div><h1>Smart Attendance</h1><p className="muted">Student portal</p></div>
      <Card className="auth-card" title={mode === "in" ? "Sign in" : "Create your account"}>
        {mode === "in" ? (
          <form className="stack" onSubmit={signIn}>
            <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
            <Field label="Password"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></Field>
            {err ? <ErrorBox error={err} /> : null}
            <Button type="submit" busy={busy}>Sign in</Button>
            <Link to="/forgot-password">Forgot password?</Link>
            <p className="muted">New student? <button type="button" className="link" onClick={() => { setMode("up"); setErr(null); }}>Create an account</button></p>
          </form>
        ) : (
          <form className="stack" onSubmit={signUp}>
            <div style={{ display: "grid", gridTemplateColumns: "minmax(0,1fr) minmax(0,1fr)", gap: ".6rem" }}>
              <Field label="First name"><input style={{ minWidth: 0, width: "100%" }} required maxLength={60} value={f.firstName} onChange={set("firstName")} autoComplete="given-name" /></Field>
              <Field label="Second name"><input style={{ minWidth: 0, width: "100%" }} required maxLength={60} value={f.secondName} onChange={set("secondName")} autoComplete="family-name" /></Field>
            </div>
            <Field label="Index number"><input required minLength={3} maxLength={40} value={f.studentId} onChange={set("studentId")} /></Field>
            <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
            <Field label="Department">
              {depts.loading ? <Notice tone="grey">Loading departments…</Notice> : depts.error ? <ErrorBox error={depts.error} retry={depts.reload} /> : (
                <select required value={f.departmentId} onChange={set("departmentId")}>
                  <option value="">Select department…</option>
                  {depts.data?.items.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              )}
            </Field>
            {depts.data && depts.data.items.length === 0 && <Notice>No departments have been set up yet. Ask the administrator.</Notice>}
            <Field label="Stream">
              <select value={f.stream} onChange={set("stream")}>
                <option value="REGULAR">Regular</option><option value="WEEKLY">Weekly</option><option value="EVENING">Evening</option>
              </select>
            </Field>
            <Field label="Password" hint="At least 8 characters, with a letter and a digit"><input type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" /></Field>
            <Field label="Confirm password"><input type="password" required minLength={8} value={password2} onChange={(e) => setPassword2(e.target.value)} autoComplete="new-password" /></Field>
            {err ? <ErrorBox error={err} /> : null}
            <Button type="submit" busy={busy}>Create account</Button>
            <p className="muted">Already registered? <button type="button" className="link" onClick={() => { setMode("in"); setErr(null); }}>Sign in</button></p>
          </form>
        )}
      </Card>
    </div></div>
  );
}
