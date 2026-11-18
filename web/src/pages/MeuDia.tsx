// "Meu dia" — a tela inicial do barbeiro (V44). Duas perguntas, nessa ordem:
//   1. "Quem eu atendo agora e depois?"  → próximo cliente em destaque + lista do dia.
//   2. "Quanto eu já ganhei?"             → comissão de hoje e da quinzena.
// Tudo escopado pelo profissional ligado ao login — a API recusa ver o de outro barbeiro.
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { ErrorState } from "../components/ErrorState";
import { Hint } from "../components/Hint";
import { Skeleton } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { addDays, hhmmToMin } from "../lib/agenda";
import { money, todayIso, timeShort, dateBr } from "../lib/format";
import type { AgendaBoard, AgendaItem, MyEarnings } from "../api/types";

function nowMin(): number {
  const d = new Date();
  return d.getHours() * 60 + d.getMinutes();
}

function firstName(name: string | null | undefined): string {
  return (name ?? "").trim().split(/\s+/)[0] || "";
}

function MoneyCard({ label, q }: { label: string; q: ReturnType<typeof useApi<MyEarnings>> }) {
  return (
    <div className="card myday-money">
      <div className="label">
        {label}
        <Hint term="comissao" label="comissão" align="start" />
      </div>
      {q.loading && !q.data ? (
        <>
          <Skeleton w="60%" h={28} style={{ display: "block", marginTop: 8 }} />
          <Skeleton w="40%" h={12} style={{ display: "block", marginTop: 8 }} />
        </>
      ) : q.error ? (
        <ErrorState compact message={q.error} onRetry={q.reload} />
      ) : (
        <>
          <div className="value">{money(q.data?.comissao)}</div>
          <div className="sub">
            {q.data?.atendimentos ?? 0} atendimento(s)
            {(q.data?.produtos ?? 0) > 0 && ` · ${q.data?.produtos} produto(s)`}
          </div>
        </>
      )}
    </div>
  );
}

export function MeuDia() {
  const { session } = useAuth();
  const today = todayIso();
  const week = addDays(today, 7);

  const todayEarn = useApi<MyEarnings>(() => api.get("/api/admin/me/earnings?period=today"), []);
  const fortnight = useApi<MyEarnings>(() => api.get("/api/admin/me/earnings?period=fortnight"), []);
  const board = useApi<AgendaBoard>(() => api.get(`/api/admin/agenda?from=${today}&to=${week}`), [today, week]);

  const myId = todayEarn.data?.professionalId ?? fortnight.data?.professionalId ?? null;
  const unlinked = todayEarn.data?.linked === false;

  const mine: AgendaItem[] = (board.data?.appointments ?? [])
    .filter((a) => myId && a.professionalId === myId)
    .sort((a, b) => (a.date + (a.time ?? "")).localeCompare(b.date + (b.time ?? "")));
  const todays = mine.filter((a) => a.date === today);
  const now = nowMin();
  const upcomingToday = todays.filter((a) => (hhmmToMin(a.time) ?? 0) + (a.durationMinutes || 30) > now);
  const next = upcomingToday[0] ?? mine.find((a) => a.date > today) ?? null;
  const fortnightLabel =
    fortnight.data ? `Quinzena (${dateBr(fortnight.data.from).slice(0, 5)}–${dateBr(fortnight.data.to).slice(0, 5)})` : "Quinzena";

  return (
    <Layout title={`Meu dia${firstName(session?.name) ? ` · ${firstName(session?.name)}` : ""}`}>
      {unlinked ? (
        <div className="card">
          <EmptyState
            icon="🔗"
            title="Seu login ainda não está ligado ao seu cadastro de barbeiro."
            hint="Peça ao dono da barbearia: Profissionais → seu nome → “Login do painel”. Depois disso seus horários e sua comissão aparecem aqui."
            action={<Link className="btn btn-ghost" to="/agenda">Ver a agenda geral</Link>}
          />
        </div>
      ) : (
        <>
          <div className="card myday-next" aria-live="polite">
            {board.loading && !board.data ? (
              <div className="grow">
                <Skeleton w="30%" h={28} />
                <Skeleton w="55%" h={14} style={{ display: "block", marginTop: 8 }} />
              </div>
            ) : board.error ? (
              <div className="grow">
                <ErrorState compact message={board.error} onRetry={board.reload} />
              </div>
            ) : next ? (
              <>
                <span className="time">{timeShort(next.time)}</span>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="faint" style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                    {next.date === today ? "Próximo cliente" : `Próximo · ${dateBr(next.date)}`}
                  </div>
                  <div style={{ fontWeight: 600, fontSize: 17 }}>
                    {next.customerName ?? "Cliente"}
                    {next.subscriber && <span className="badge badge-plan" style={{ marginLeft: 8 }}>💎 assinante</span>}
                  </div>
                  <div className="muted" style={{ fontSize: 14 }}>
                    {next.service ?? "Serviço"} · {next.durationMinutes} min
                  </div>
                </div>
              </>
            ) : (
              <div className="grow">
                <div style={{ fontWeight: 600 }}>Nenhum cliente marcado com você nos próximos 7 dias.</div>
                <div className="muted" style={{ fontSize: 14 }}>
                  Quando a IA ou a recepção marcar, aparece aqui na hora.
                </div>
              </div>
            )}
          </div>

          <div className="myday-grid">
            <MoneyCard label="Ganhei hoje" q={todayEarn} />
            <MoneyCard label={fortnightLabel} q={fortnight} />
          </div>

          <div className="card">
            <div className="card-head">
              <h3>Meus horários de hoje</h3>
              <Link to="/agenda" className="btn btn-ghost btn-sm">Agenda completa</Link>
            </div>
            {board.loading && !board.data ? (
              <div className="stack" style={{ padding: 18, gap: 10 }}>
                {[0, 1, 2].map((i) => <Skeleton key={i} h={40} />)}
              </div>
            ) : board.error ? (
              <ErrorState message={board.error} onRetry={board.reload} />
            ) : todays.length === 0 ? (
              <EmptyState
                icon="☕"
                title="Agenda livre hoje."
                hint="Nenhum horário seu marcado para hoje. Veja a agenda completa para os próximos dias."
              />
            ) : (
              <ul className="myday-list">
                {todays.map((a) => {
                  const end = (hhmmToMin(a.time) ?? 0) + (a.durationMinutes || 30);
                  const past = end <= now;
                  return (
                    <li key={a.id} className={past ? "past" : ""}>
                      <span className="t">{timeShort(a.time)}</span>
                      <span className="who">
                        <strong>{a.customerName ?? "Cliente"}</strong>
                        <span className="muted" style={{ fontSize: 13 }}>
                          {a.service ?? "Serviço"} · {a.durationMinutes} min
                        </span>
                      </span>
                      {a.attendanceStatus === "ATTENDED" ? (
                        <span className="badge badge-green">✓ atendido</span>
                      ) : a.attendanceStatus === "NO_SHOW" ? (
                        <span className="badge badge-amber">faltou</span>
                      ) : a.subscriber ? (
                        <span className="badge badge-plan">💎</span>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </>
      )}
    </Layout>
  );
}
