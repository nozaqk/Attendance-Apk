import { useCallback, useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { get, post } from "./shared/api";
import { RequireAuth, useAuth } from "./shared/auth";
import { ForgotPassword, ResetPassword } from "./shared/pages";
import { startBackgroundNotifications, stopBackgroundNotifications, useNotificationPolling } from "./shared/native";
import Login from "./pages/Login";
import Home from "./pages/Home";
import ClassPage from "./pages/ClassPage";
import Courses from "./pages/Courses";
import Settings from "./pages/Settings";
import Notifications from "./pages/Notifications";

const TABS = [
  { to: "/", ico: "🏠", label: "Home", end: true },
  { to: "/class", ico: "📡", label: "Class" },
  { to: "/course", ico: "📚", label: "Course" },
  { to: "/setting", ico: "⚙️", label: "Setting" },
];

function Shell() {
  const { user } = useAuth();
  const loc = useLocation();
  const fetchItems = useCallback(async () => (await get<{ items: any[] }>("/api/student/notifications")).items, []);
  useNotificationPolling(fetchItems);
  // Android app: keep telling the student about classes even when the app is closed. Signing out
  // (this component unmounting) stops it.
  useEffect(() => {
    void startBackgroundNotifications(async () => (await post<{ token: string }>("/api/student/notifications/device-token")).token);
    return () => stopBackgroundNotifications();
  }, []);

  const ready = !!(user?.student?.semester && user.student.departmentId);
  if (!ready && loc.pathname !== "/setting") return <Navigate to="/setting" replace />;

  return (
    <div className="app-mobile">
      <main className="content">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/class" element={<ClassPage />} />
          <Route path="/course" element={<Courses />} />
          <Route path="/setting" element={<Settings />} />
          <Route path="/notifications" element={<Notifications onRead={() => {}} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <nav className="tabbar" aria-label="Main"><div className="tabbar-inner">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => (isActive ? "active" : "") + (!ready && t.to !== "/setting" ? " tab-lock" : "")}>
            <span className="ico">{t.ico}</span>{t.label}
          </NavLink>
        ))}
      </div></nav>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/*" element={<RequireAuth><Shell /></RequireAuth>} />
    </Routes>
  );
}
