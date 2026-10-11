import { NavLink, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { RequireAuth, useAuth } from "./shared/auth";
import { ForcePasswordChange, ForgotPassword, ResetPassword } from "./shared/pages";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Result from "./pages/Result";
import Students from "./pages/Students";
import Courses from "./pages/Courses";
import ClassPage from "./pages/ClassPage";
import Setting from "./pages/Setting";

const TABS: [string, string, string][] = [["/", "Home", "🏠"], ["/students", "Students", "👥"], ["/courses", "Courses", "📚"], ["/class", "Class", "📡"], ["/setting", "Setting", "⚙️"]];

function Shell() {
  const { user } = useAuth();
  if (user?.mustChangePassword) return <ForcePasswordChange />;
  return (
    <div className="app-mobile">
      <header className="app-header"><strong>Lecturer</strong><span style={{ fontSize: ".85rem", opacity: .85 }}>{user?.fullName}</span></header>
      <main className="content"><Outlet /></main>
      <nav className="tabbar" aria-label="Main"><div className="tabbar-inner">
        {TABS.map(([to, l, i]) => <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => (isActive ? "active" : "")}><span className="ico" aria-hidden>{i}</span>{l}</NavLink>)}
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
      <Route element={<RequireAuth><Shell /></RequireAuth>}>
        <Route index element={<Home />} />
        <Route path="results/:id" element={<Result />} />
        <Route path="students" element={<Students />} />
        <Route path="courses" element={<Courses />} />
        <Route path="class" element={<ClassPage />} />
        <Route path="setting" element={<Setting />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
