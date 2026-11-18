import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { canAccess, homeFor } from "./lib/access";
import { CommandPalette } from "./components/CommandPalette";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Agenda } from "./pages/Agenda";
import { Services } from "./pages/Services";
import { Products } from "./pages/Products";
import { Comandas } from "./pages/Comandas";
import { Coupons } from "./pages/Coupons";
import { Packages } from "./pages/Packages";
import { Professionals } from "./pages/Professionals";
import { Customers } from "./pages/Customers";
import { Plans } from "./pages/Plans";
import { Caixa } from "./pages/Caixa";
import { Commissions } from "./pages/Commissions";
import { Expenses } from "./pages/Expenses";
import { RetentionPage } from "./pages/Retention";
import { CommissionSettlementPage } from "./pages/CommissionSettlement";
import { Settings } from "./pages/Settings";
import { Profile } from "./pages/Profile";
import { SitePage } from "./site/SitePage";

function Protected() {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) return <Navigate to="/login" replace />;
  // Rota que não é do papel devolve o usuário à sua tela inicial. É conveniência de navegação,
  // não segurança: quem recusa de verdade é a API (ver lib/access.ts).
  if (!canAccess(location.pathname, session.role)) {
    return <Navigate to={homeFor(session.role)} replace />;
  }
  return (
    <>
      <CommandPalette />
      <Outlet />
    </>
  );
}

export default function App() {
  const { session } = useAuth();
  const home = homeFor(session?.role);
  return (
    <Routes>
      {/* Site white-label — publico. Fora do <Protected> de proposito: um site que so abre
          com login nao e um site. */}
      <Route path="/b/:slug" element={<SitePage />} />
      <Route path="/login" element={session ? <Navigate to={home} replace /> : <Login />} />
      <Route element={<Protected />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/agenda" element={<Agenda />} />
        <Route path="/services" element={<Services />} />
        <Route path="/products" element={<Products />} />
        <Route path="/professionals" element={<Professionals />} />
        <Route path="/customers" element={<Customers />} />
        <Route path="/plans" element={<Plans />} />
        <Route path="/coupons" element={<Coupons />} />
        <Route path="/packages" element={<Packages />} />
        <Route path="/comandas" element={<Comandas />} />
        <Route path="/caixa" element={<Caixa />} />
        <Route path="/commissions" element={<Commissions />} />
        <Route path="/commissions/settlement" element={<CommissionSettlementPage />} />
        <Route path="/expenses" element={<Expenses />} />
        <Route path="/retention" element={<RetentionPage />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/profile" element={<Profile />} />
      </Route>
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  );
}
