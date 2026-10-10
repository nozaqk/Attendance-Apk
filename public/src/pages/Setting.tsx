import { useState, type FormEvent } from "react";
import { post } from "../shared/api";
import { useAuth } from "../shared/auth";
import { Button, Card, Confirm, ErrorBox, Field, Notice } from "../shared/ui";

export default function Setting() {
  const { user, logout } = useAuth();
  const [cur, setCur] = useState(""); const [pw, setPw] = useState(""); const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<unknown>(null); const [ok, setOk] = useState(false);
  const [out, setOut] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); setErr(null); setOk(false);
    if (pw !== pw2) { setErr(new Error("The two passwords do not match")); return; }
    setBusy(true);
    try { await post("/api/auth/change-password", { currentPassword: cur, newPassword: pw }); setOk(true); setCur(""); setPw(""); setPw2(""); } catch (x) { setErr(x); }
    setBusy(false);
  }
  return (
    <div className="stack">
      <h1>Setting</h1>
      <Card title="Profile">
        <p><strong>{user?.fullName}</strong></p>
        <p className="muted">{user?.email}</p>
        <p className="muted">Staff ID: <span className="mono">{user?.lecturer?.staffId ?? "—"}</span></p>
        {user?.department && <p className="muted">{user.department.name}</p>}
      </Card>
      <Card title="Change password">
        <form className="stack" onSubmit={submit}>
          <Field label="Current password"><input type="password" required value={cur} onChange={(e) => setCur(e.target.value)} autoComplete="current-password" /></Field>
          <Field label="New password" hint="At least 8 characters, with a letter and a digit"><input type="password" required minLength={8} value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></Field>
          <Field label="Repeat new password"><input type="password" required minLength={8} value={pw2} onChange={(e) => setPw2(e.target.value)} autoComplete="new-password" /></Field>
          {err ? <ErrorBox error={err} /> : null}
          {ok && <Notice tone="green">Password changed.</Notice>}
          <Button type="submit" busy={busy}>Change password</Button>
        </form>
      </Card>
      <Button variant="danger" onClick={() => setOut(true)}>Log out</Button>
      {out && <Confirm title="Log out?" message="You will need to sign in again." confirmLabel="Log out" onConfirm={() => logout()} onCancel={() => setOut(false)} />}
    </div>
  );
}
