import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { CommandPalette } from "./components/CommandPalette";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Agenda } from "./pages/Agenda";
import { Services } from "./pages/Services";
import { Products } from "./pages/Products";
import { Professionals } from "./pages/Professionals";
import { Customers } from "./pages/Customers";
import { Plans } from "./pages/Plans";
import { Caixa } from "./pages/Caixa";
import { Commissions } from "./pages/Commissions";
import { Settings } from "./pages/Settings";
import { Profile } from "./pages/Profile";

function Protected() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  return (
    <>
      <CommandPalette />
      <Outlet />
    </>
  );
}

export default function App() {
  const { session } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={session ? <Navigate to="/" replace /> : <Login />} />
      <Route element={<Protected />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/agenda" element={<Agenda />} />
        <Route path="/services" element={<Services />} />
        <Route path="/products" element={<Products />} />
        <Route path="/professionals" element={<Professionals />} />
        <Route path="/customers" element={<Customers />} />
        <Route path="/plans" element={<Plans />} />
        <Route path="/caixa" element={<Caixa />} />
        <Route path="/commissions" element={<Commissions />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/profile" element={<Profile />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
