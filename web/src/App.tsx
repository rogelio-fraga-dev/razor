import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Services } from "./pages/Services";
import { Professionals } from "./pages/Professionals";
import { Commissions } from "./pages/Commissions";
import { Settings } from "./pages/Settings";

function Protected() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  return <Outlet />;
}

export default function App() {
  const { session } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={session ? <Navigate to="/" replace /> : <Login />} />
      <Route element={<Protected />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/services" element={<Services />} />
        <Route path="/professionals" element={<Professionals />} />
        <Route path="/commissions" element={<Commissions />} />
        <Route path="/settings" element={<Settings />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
