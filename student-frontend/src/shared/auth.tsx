import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { get, getToken, post, setToken, setUnauthorizedHandler } from "./api";
import { Spinner } from "./ui";

export interface User {
  id: string; email: string; fullName: string; role: "ADMIN" | "LECTURER" | "STUDENT" | "QA"; mustChangePassword: boolean;
  student?: { studentId: string; departmentId: string | null; semester: number | null; stream?: "REGULAR" | "WEEKLY" | "EVENING" };
  lecturer?: { staffId: string };
  department?: { id: string; name: string; code: string } | null;
}
interface Ctx {
  user: User | null; loading: boolean; allowedRole: User["role"];
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>; refresh(): Promise<void>; applyToken(token: string): Promise<void>;
}
const AuthCtx = createContext<Ctx>(null as never);
export const useAuth = () => useContext(AuthCtx);

export function AuthProvider({ role, children }: { role: User["role"]; children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(!!getToken());

  const refresh = useCallback(async () => {
    if (!getToken()) { setUser(null); setLoading(false); return; }
    try {
      const me = await get<User>("/api/auth/me");
      if (me.role !== role) { setToken(null); setUser(null); } else setUser(me);
    } catch { setToken(null); setUser(null); }
    setLoading(false);
  }, [role]);

  useEffect(() => { setUnauthorizedHandler(() => { setToken(null); setUser(null); }); void refresh(); return () => setUnauthorizedHandler(null); }, [refresh]);

  const value = useMemo<Ctx>(() => ({
    user, loading, allowedRole: role, refresh,
    async applyToken(token) { setToken(token); await refresh(); },
    async login(email, password) {
      const r = await post<{ token: string; user: User }>("/api/auth/login", { email, password });
      if (r.user.role !== role) {
        // Right credentials, wrong portal: make sure the token is not kept.
        const label = { ADMIN: "administrator", LECTURER: "lecturer", STUDENT: "student", QA: "quality assurance" }[r.user.role];
        throw new Error(`This is a ${role.toLowerCase()} portal. Your account is a ${label} account.`);
      }
      setToken(r.token); await refresh();
    },
    async logout() { try { await post("/api/auth/logout"); } catch { /* token may already be invalid */ } setToken(null); setUser(null); },
  }), [user, loading, role, refresh]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="center-screen"><Spinner label="Loading…" /></div>;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
