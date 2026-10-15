import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { post } from "./api";
import { useAuth } from "./auth";
import { Button, Card, ErrorBox, Field, Notice } from "./ui";

export function ForgotPassword({ title = "Reset your password" }: { title?: string }) {
  const nav = useNavigate();
  const [step, setStep] = useState<"email" | "code">("email");
  const [email, setEmail] = useState(""); const [code, setCode] = useState("");
  const [pw, setPw] = useState(""); const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false); const [done, setDone] = useState(false);
  const [err, setErr] = useState<unknown>(null); const [info, setInfo] = useState("");
  async function sendCode(e?: FormEvent) {
    e?.preventDefault(); setBusy(true); setErr(null); setInfo("");
    try { await post("/api/auth/forgot-password", { email }); setStep("code"); setInfo("If that email has an account, a 6-digit code has been sent. Check your inbox (and spam)."); } catch (x) { setErr(x); }
    setBusy(false);
  }
  async function reset(e: FormEvent) {
    e.preventDefault(); setErr(null);
    if (pw !== pw2) { setErr(new Error("The two passwords do not match")); return; }
    setBusy(true);
    try { await post("/api/auth/reset-password", { email, code, newPassword: pw }); setDone(true); setTimeout(() => nav("/login", { replace: true }), 1800); }
    catch (x) { setErr(x); }
    setBusy(false);
  }
  return (
    <div className="center-screen"><Card className="auth-card" title={step === "email" ? title : "Enter the code"}>
      {done ? <Notice tone="green">Password changed. Taking you to sign in…</Notice> : step === "email" ? (
        <form className="stack" onSubmit={sendCode}>
          <p className="muted">Enter your account email and we will send you a 6-digit code.</p>
          <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
          {err ? <ErrorBox error={err} /> : null}
          <Button type="submit" busy={busy}>Send code</Button>
          <Link to="/login">Back to sign in</Link>
        </form>
      ) : (
        <form className="stack" onSubmit={reset}>
          {info && <Notice tone="green">{info}</Notice>}
          <Field label="6-digit code"><input required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} autoComplete="one-time-code" placeholder="123456" style={{ letterSpacing: ".3em", fontSize: "1.2rem" }} /></Field>
          <Field label="New password" hint="At least 8 characters, with a letter and a digit"><input type="password" required minLength={8} value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></Field>
          <Field label="Repeat new password"><input type="password" required minLength={8} value={pw2} onChange={(e) => setPw2(e.target.value)} autoComplete="new-password" /></Field>
          {err ? <ErrorBox error={err} /> : null}
          <Button type="submit" busy={busy}>Change password</Button>
          <p className="muted">No code? <button type="button" className="link" onClick={() => void sendCode()}>Send a new code</button></p>
          <Link to="/login">Back to sign in</Link>
        </form>
      )}
    </Card></div>
  );
}

/** Old e-mailed links point here; the reset now happens with a code on the forgot-password page. */
export function ResetPassword() { return <Navigate to="/forgot-password" replace />; }

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
