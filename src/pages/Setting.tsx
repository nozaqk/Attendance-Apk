import { useState } from "react";
import { useAuth } from "../shared/auth";
import { Button, Card, Confirm } from "../shared/ui";

export default function Setting() {
  const { user, logout } = useAuth();
  const [out, setOut] = useState(false);
  return (
    <div className="stack">
      <h1>Setting</h1>
      <Card title="Profile">
        <p><strong>{user?.fullName}</strong></p>
        <p className="muted">{user?.email}</p>
        <p className="muted">Staff ID: <span className="mono">{user?.lecturer?.staffId ?? "—"}</span></p>
        {user?.department && <p className="muted">{user.department.name}</p>}
      </Card>
      <Button variant="danger" onClick={() => setOut(true)}>Log out</Button>
      {out && <Confirm title="Log out?" message="You will need to sign in again." confirmLabel="Log out" onConfirm={() => logout()} onCancel={() => setOut(false)} />}
    </div>
  );
}
