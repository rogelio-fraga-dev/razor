// Faixa do teste grátis e da configuração guiada (V49), no topo do painel do dono.
//
// Só informa: o fim do teste não bloqueia nada enquanto a cobrança da assinatura não existe.
// Quem saiu do assistente no meio ganha aqui o caminho de volta ("Continuar configuração").
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { getOnce } from "../lib/dedupe";
import { shortDate, type OnboardingProgress } from "../api/onboarding";
import "./trial-banner.css";

export function TrialBanner() {
  const [p, setP] = useState<OnboardingProgress | null>(null);

  useEffect(() => {
    let alive = true;
    getOnce<OnboardingProgress>("/api/admin/onboarding")
      .then((r) => alive && setP(r))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  if (!p) return null;
  const trial = p.status === "TRIAL" && p.trialEndsAt;
  if (!trial && p.completed) return null;

  return (
    <div className="trial-strip" role="status">
      {trial && (
        <span>
          <b>Teste grátis até {shortDate(p.trialEndsAt)}</b>
          <span className="faint"> · sem cartão, nada é cobrado automaticamente</span>
        </span>
      )}
      {!p.completed && (
        <Link to="/comecar" className="btn btn-primary btn-sm">
          Continuar configuração guiada ({p.stepsDone.length}/{p.steps.length}) →
        </Link>
      )}
    </div>
  );
}
