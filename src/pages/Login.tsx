import { useState, type FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../shared/auth";
import { Button, Card, ErrorBox, Field } from "../shared/ui";

export default function Login() {
  const { user, login } = useAuth();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<unknown>(null);
  if (user) return <Navigate to="/" replace />;
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await login(email.trim(), password); } catch (x) { setErr(x); setBusy(false); }
  }
  return (
    <div className="center-screen"><Card className="auth-card" title="Lecturer sign in">
      <form className="stack" onSubmit={submit}>
        <p className="muted">Smart Attendance · Lecturer. Your account is created by the administrator.</p>
        <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
        <Field label="Password"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></Field>
        {err ? <ErrorBox error={err} /> : null}
        <Button type="submit" busy={busy}>Sign in</Button>
        <Link to="/forgot-password">Forgot password?</Link>
      </form>
    </Card></div>
  );
}
