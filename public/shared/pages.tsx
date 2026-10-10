import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { post } from "./api";
import { useAuth } from "./auth";
import { Button, Card, ErrorBox, Field, Notice } from "./ui";

export function ForgotPassword({ title = "Reset your password" }: { title?: string }) {
  const [email, setEmail] = useState(""); const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false); const [err, setErr] = useState<unknown>(null);
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await post("/api/auth/forgot-password", { email }); setDone(true); } catch (x) { setErr(x); }
    setBusy(false);
  }
  return (
    <div className="center-screen"><Card className="auth-card" title={title}>
      {done ? <div className="stack"><Notice tone="green">If that email has an account, a reset link has been sent. Check your inbox.</Notice><Link to="/login">Back to sign in</Link></div> : (
        <form className="stack" onSubmit={submit}>
          <p className="muted">Enter your account email and we will send you a link to set a new password.</p>
          <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
          {err ? <ErrorBox error={err} /> : null}
          <Button type="submit" busy={busy}>Send reset link</Button>
          <Link to="/login">Back to sign in</Link>
        </form>
      )}
    </Card></div>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams(); const nav = useNavigate();
  const token = params.get("token") ?? "";
  const [pw, setPw] = useState(""); const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false); const [done, setDone] = useState(false); const [err, setErr] = useState<unknown>(null);
  async function submit(e: FormEvent) {
    e.preventDefault(); setErr(null);
    if (pw !== pw2) { setErr(new Error("The two passwords do not match")); return; }
    setBusy(true);
    try { await post("/api/auth/reset-password", { token, newPassword: pw }); setDone(true); setTimeout(() => nav("/login", { replace: true }), 1800); }
    catch (x) { setErr(x); }
    setBusy(false);
  }
  return (
    <div className="center-screen"><Card className="auth-card" title="Set a new password">
      {!token ? <Notice tone="red">This reset link is incomplete. Request a new one.</Notice> : done ? <Notice tone="green">Password changed. Taking you to sign in…</Notice> : (
        <form className="stack" onSubmit={submit}>
          <Field label="New password" hint="At least 8 characters, with a letter and a digit"><input type="password" required minLength={8} value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></Field>
          <Field label="Repeat new password"><input type="password" required minLength={8} value={pw2} onChange={(e) => setPw2(e.target.value)} autoComplete="new-password" /></Field>
          {err ? <ErrorBox error={err} /> : null}
          <Button type="submit" busy={busy}>Change password</Button>
        </form>
      )}
      <p className="muted"><Link to="/forgot-password">Request a new link</Link></p>
    </Card></div>
  );
}

/**
 * Staff accounts get a random first password. This screen replaces the whole app until the user
 * has chosen their own, so nobody else (including the admin) ever knows it.
 */
export function ForcePasswordChange() {
  const { user, logout, refresh } = useAuth();
  const [cur, setCur] = useState(""); const [pw, setPw] = useState(""); const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<unknown>(null);
  async function submit(e: FormEvent) {
    e.preventDefault(); setErr(null);
    if (pw !== pw2) { setErr(new Error("The two passwords do not match")); return; }
    setBusy(true);
    try { await post("/api/auth/change-password", { currentPassword: cur, newPassword: pw }); await refresh(); }
    catch (x) { setErr(x); setBusy(false); }
  }
  return (
    <div className="center-screen"><Card className="auth-card" title="Choose your own password">
      <form className="stack" onSubmit={submit}>
        <Notice>Hello {user?.fullName}. You signed in with a temporary password. Set a new one to continue. Nobody else will know it.</Notice>
        <Field label="Temporary password"><input type="password" required value={cur} onChange={(e) => setCur(e.target.value)} autoComplete="current-password" /></Field>
        <Field label="New password" hint="At least 8 characters, with a letter and a digit"><input type="password" required minLength={8} value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></Field>
        <Field label="Repeat new password"><input type="password" required minLength={8} value={pw2} onChange={(e) => setPw2(e.target.value)} autoComplete="new-password" /></Field>
        {err ? <ErrorBox error={err} /> : null}
        <Button type="submit" busy={busy}>Save and continue</Button>
        <button type="button" className="link" onClick={() => void logout()}>Sign out</button>
      </form>
    </Card></div>
  );
}
