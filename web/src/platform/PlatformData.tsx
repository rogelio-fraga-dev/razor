import { useState } from "react";
import { ApiError } from "../api/client";
import { useApi } from "../lib/useApi";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import {
  papi, dt, brl,
  type ConversationDetail, type ConversationFilter, type ConversationRow, type DayPoint, type Overview, type Revenue,
} from "./api";

function Loading({ loading, error }: { loading: boolean; error: string | null }) {
  if (error) return <div className="error" role="alert">{error}</div>;
  return loading ? <SkeletonRows rows={4} /> : null;
}

function useShops() {
  return useApi<Overview>(() => papi.get("/overview?days=7"), []).data?.rows ?? [];
}

// ------------------------------------------------------------------ conversas

const FILTERS: { id: ConversationFilter; label: string }[] = [
  { id: "ALL", label: "Todas" },
  { id: "AI_ERROR", label: "IA falhou" },
  { id: "BOOKING_FAILED", label: "Agendamento falhou" },
  { id: "ANGRY", label: "Cliente irritado" },
  { id: "FLAGGED", label: "Marcadas para revisão" },
];

export function ConversationsTab() {
  const shops = useShops();
  const [shop, setShop] = useState("");
  const [filter, setFilter] = useState<ConversationFilter>("ALL");
  const [days, setDays] = useState(7);
  const list = useApi<ConversationRow[]>(
    () => papi.get(`/conversations?filter=${filter}&days=${days}${shop ? `&shop=${shop}` : ""}`),
    [filter, days, shop],
  );
  const [open, setOpen] = useState<string | null>(null);

  return (
    <>
      <div className="plat-toolbar">
        <h2 style={{ margin: 0, flex: 1 }}>Conversas do bot</h2>
        <select className="input" value={shop} onChange={(e) => setShop(e.target.value)} aria-label="Barbearia">
          <option value="">Todas as barbearias</option>
          {shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <select className="input" value={filter} onChange={(e) => setFilter(e.target.value as ConversationFilter)} aria-label="Filtro">
          {FILTERS.map((f) => <option key={f.id} value={f.id}>{f.label}</option>)}
        </select>
        <select className="input" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período">
          <option value={1}>24 h</option><option value={7}>7 dias</option><option value={30}>30 dias</option>
        </select>
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        A lista mostra só os 4 últimos dígitos do telefone. Abrir uma conversa fica registrado na trilha (LGPD).
        "Cliente irritado" é uma heurística por palavras — um humano julga.
      </p>
      <div className="plat-grid2">
        <div className="card"><div className="card-pad" style={{ overflowX: "auto" }}>
          {!list.data ? <Loading loading={list.loading} error={list.error} /> : list.data.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>Nenhuma conversa com esse filtro.</p>
          ) : (
            <table className="table" style={{ minWidth: 0 }}>
              <thead><tr><th>Cliente</th><th>Barbearia</th><th>Última</th><th>Sinais</th></tr></thead>
              <tbody>
                {list.data.map((c) => (
                  <tr key={c.customerId} className="clickable" onClick={() => setOpen(c.customerId)}
                    aria-selected={open === c.customerId} style={open === c.customerId ? { outline: "2px solid var(--accent)" } : undefined}>
                    <td>{c.customerName || "—"}<div className="muted" style={{ fontSize: 12 }}>{c.phoneMasked} · {c.messages} msg</div></td>
                    <td>{c.shopName}</td>
                    <td>{dt(c.lastAt)}</td>
                    <td className="row wrap" style={{ gap: 4 }}>
                      {c.aiErrors > 0 && <span className="badge badge-amber">IA ×{c.aiErrors}</span>}
                      {c.bookingFailures > 0 && <span className="badge badge-amber">agend. ×{c.bookingFailures}</span>}
                      {c.angry && <span className="badge badge-amber">irritado?</span>}
                      {c.flagged && <span className="badge badge-blue">revisar</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div></div>
        {open ? <ConversationView id={open} onChanged={list.reload} /> : (
          <div className="card"><div className="card-pad muted">Escolha uma conversa para ler.</div></div>
        )}
      </div>
    </>
  );
}

function ConversationView({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { data, loading, error, reload } = useApi<ConversationDetail>(() => papi.get(`/conversations/${id}?days=30`), [id]);
  const toast = useToast();
  const [note, setNote] = useState("");

  const flag = async (on: boolean) => {
    try {
      if (on) await papi.post(`/conversations/${id}/flag`, { note: note || null });
      else await papi.del(`/conversations/${id}/flag`);
      setNote("");
      reload();
      onChanged();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro.");
    }
  };

  if (!data) return <div className="card"><div className="card-pad"><Loading loading={loading} error={error} /></div></div>;

  // Mensagens e eventos numa linha do tempo só.
  const timeline = [
    ...data.messages.map((m) => ({ at: m.at, kind: "msg" as const, m })),
    ...data.events.map((e) => ({ at: e.at, kind: "ev" as const, e })),
  ].sort((a, b) => a.at.localeCompare(b.at));

  return (
    <div className="card">
      <div className="card-head">
        <h3>{data.customerName || "Cliente"} · {data.shopName}</h3>
        <span className="muted" style={{ fontSize: 13 }}>{data.phone}</span>
      </div>
      <div className="card-pad stack">
        <div className="plat-chat">
          {timeline.length === 0 && <span className="muted">Sem mensagens nos últimos 30 dias.</span>}
          {timeline.map((t, i) => t.kind === "msg" ? (
            <div key={i} className={`plat-msg ${t.m.type}`}>{t.m.content}<span className="t">{dt(t.m.at)}</span></div>
          ) : (
            <div key={i} className="plat-event">
              ⚠️ {t.e.kind === "AI_ERROR" ? "IA indisponível — cliente recebeu a contingência" : "Agendamento não foi salvo"}
              {t.e.detail ? ` (${t.e.detail})` : ""} · {dt(t.e.at)}
            </div>
          ))}
        </div>
        {data.flagged ? (
          <div className="row wrap" style={{ gap: 8, alignItems: "center" }}>
            <span className="badge badge-blue">Marcada para revisão</span>
            {data.flagNote && <span className="muted">{data.flagNote}</span>}
            <button className="btn btn-ghost btn-sm" onClick={() => flag(false)}>Revisada</button>
          </div>
        ) : (
          <div className="row wrap" style={{ gap: 8 }}>
            <input className="input" style={{ flex: 1, minWidth: 180 }} maxLength={500} placeholder="Nota (opcional)"
              value={note} onChange={(e) => setNote(e.target.value)} />
            <button className="btn btn-ghost btn-sm" onClick={() => flag(true)}>Marcar para revisão</button>
          </div>
        )}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ métricas

function Bars({ title, points, value, tone, fmt }: {
  title: string; points: DayPoint[]; value: (p: DayPoint) => number | null; tone?: "alt" | "bad"; fmt?: (n: number) => string;
}) {
  const vals = points.map((p) => value(p) ?? 0);
  const max = Math.max(1, ...vals);
  const total = vals.reduce((a, b) => a + b, 0);
  const shown = fmt ? fmt(total / Math.max(1, vals.filter((v) => v > 0).length)) : String(total);
  return (
    <div className="card plat-chart"><div className="card-pad">
      <h4>{title}</h4>
      <div className="muted" style={{ fontSize: 13 }}>{fmt ? `média ${shown}` : `total ${shown}`}</div>
      <div className="plat-bars" role="img" aria-label={`${title}: ${points.map((p, i) => `${p.day} ${vals[i]}`).join(", ")}`}>
        {points.map((p, i) => (
          <div key={p.day} className={`b${tone ? ` ${tone}` : ""}`} style={{ height: `${(vals[i] / max) * 100}%` }}
            title={`${p.day.split("-").reverse().join("/")}: ${fmt ? fmt(vals[i]) : vals[i]}`} />
        ))}
      </div>
      <div className="axis"><span>{points[0]?.day.split("-").reverse().slice(0, 2).join("/")}</span>
        <span>{points[points.length - 1]?.day.split("-").reverse().slice(0, 2).join("/")}</span></div>
    </div></div>
  );
}

export function MetricsTab() {
  const shops = useShops();
  const [shop, setShop] = useState("");
  const [days, setDays] = useState(30);
  const { data, loading, error } = useApi<DayPoint[]>(() => papi.get(`/metrics?days=${days}${shop ? `&shop=${shop}` : ""}`), [days, shop]);
  const secs = (n: number) => (n >= 60 ? `${(n / 60).toFixed(1)} min` : `${n.toFixed(1)} s`);

  return (
    <>
      <div className="plat-toolbar">
        <h2 style={{ margin: 0, flex: 1 }}>Métricas</h2>
        <select className="input" value={shop} onChange={(e) => setShop(e.target.value)} aria-label="Barbearia">
          <option value="">Todas as barbearias</option>
          {shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <select className="input" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período">
          <option value={7}>7 dias</option><option value={30}>30 dias</option><option value={90}>90 dias</option>
        </select>
      </div>
      {!data ? <Loading loading={loading} error={error} /> : (
        <div className="plat-grid2">
          <Bars title="Mensagens recebidas" points={data} value={(p) => p.messagesIn} />
          <Bars title="Respostas do bot" points={data} value={(p) => p.messagesOut} />
          <Bars title="Agendamentos feitos pela IA" points={data} value={(p) => p.botAppointments} tone="alt" />
          <Bars title="Tempo de resposta do bot" points={data} value={(p) => p.avgResponseSeconds} fmt={secs} />
          <Bars title="Chamadas à IA" points={data} value={(p) => p.aiCalls} />
          <Bars title="Erros (IA fora + falhas + agendamento não salvo)" points={data} tone="bad"
            value={(p) => p.aiFailures + p.aiErrorEvents + p.bookingFailures} />
        </div>
      )}
    </>
  );
}

// ------------------------------------------------------------------ caixa do Razor

export function RevenueTab() {
  const shops = useShops();
  const { data, loading, error, reload } = useApi<Revenue>(() => papi.get("/revenue?months=12"), []);
  const toast = useToast();
  const thisMonth = new Date().toISOString().slice(0, 7);
  const [form, setForm] = useState({ month: thisMonth, barbershopId: "", plan: "", amount: "", note: "" });

  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = Number(form.amount.replace(/\./g, "").replace(",", "."));
    if (!Number.isFinite(amount) || amount < 0) return toast.error("Valor inválido.");
    try {
      await papi.post("/revenue", { month: form.month, barbershopId: form.barbershopId || null, plan: form.plan || null, amount, note: form.note || null });
      setForm({ ...form, amount: "", note: "" });
      toast.success("Lançado ✓");
      reload();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Erro ao lançar.");
    }
  };

  const remove = async (id: string) => {
    if (!window.confirm("Apagar este lançamento?")) return;
    await papi.del(`/revenue/${id}`).catch(() => toast.error("Erro ao apagar."));
    reload();
  };

  const max = Math.max(1, ...(data?.months.map((m) => m.total) ?? [1]));
  return (
    <>
      <h2 style={{ margin: 0 }}>Caixa do Razor</h2>
      <p className="muted" style={{ margin: 0 }}>Receita lançada à mão enquanto a cobrança automática não existe. MRR = total do mês corrente.</p>
      {!data ? <Loading loading={loading} error={error} /> : (
        <>
          <div className="plat-kpis">
            <div className="plat-kpi"><div className="l">MRR (este mês)</div><div className="v">{brl(data.currentMonth)}</div></div>
            <div className="plat-kpi"><div className="l">Mês anterior</div><div className="v">{brl(data.previousMonth)}</div></div>
            {Object.entries(data.byPlanThisMonth).map(([plan, v]) => (
              <div key={plan} className="plat-kpi"><div className="l">Plano {plan}</div><div className="v">{brl(v)}</div></div>
            ))}
          </div>
          <div className="card plat-chart"><div className="card-pad">
            <h4>Últimos 12 meses</h4>
            <div className="plat-bars">
              {data.months.map((m) => <div key={m.month} className="b" style={{ height: `${(m.total / max) * 100}%` }} title={`${m.month}: ${brl(m.total)}`} />)}
            </div>
            <div className="axis"><span>{data.months[0]?.month}</span><span>{data.months[data.months.length - 1]?.month}</span></div>
          </div></div>
        </>
      )}
      <div className="card">
        <div className="card-head"><h3>Lançar receita</h3></div>
        <form className="card-pad row wrap" style={{ gap: 10, alignItems: "flex-end" }} onSubmit={add}>
          <div className="field"><label>Mês<input className="input" type="month" value={form.month} required
            onChange={(e) => setForm({ ...form, month: e.target.value })} /></label></div>
          <div className="field"><label>Barbearia<select className="input" value={form.barbershopId}
            onChange={(e) => setForm({ ...form, barbershopId: e.target.value })}>
            <option value="">—</option>{shops.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label></div>
          <div className="field"><label>Plano<input className="input" maxLength={40} value={form.plan}
            onChange={(e) => setForm({ ...form, plan: e.target.value })} style={{ maxWidth: 140 }} /></label></div>
          <div className="field"><label>Valor (R$)<input className="input" inputMode="decimal" required value={form.amount}
            onChange={(e) => setForm({ ...form, amount: e.target.value.replace(/[^\d,.]/g, "") })} style={{ maxWidth: 120 }} /></label></div>
          <div className="field" style={{ flex: 1, minWidth: 160 }}><label>Nota<input className="input" maxLength={200} value={form.note}
            onChange={(e) => setForm({ ...form, note: e.target.value })} /></label></div>
          <button className="btn btn-primary">Lançar</button>
        </form>
      </div>
      {data && data.entries.length > 0 && (
        <div className="card"><div className="card-pad" style={{ overflowX: "auto" }}>
          <table className="table">
            <thead><tr><th>Mês</th><th>Barbearia</th><th>Plano</th><th>Valor</th><th>Nota</th><th></th></tr></thead>
            <tbody>
              {data.entries.map((e) => (
                <tr key={e.id}>
                  <td>{e.month.slice(0, 7)}</td><td>{e.barbershopName ?? "—"}</td><td>{e.plan ?? "—"}</td>
                  <td>{brl(e.amount)}</td><td>{e.note ?? "—"}</td>
                  <td><button className="btn btn-danger btn-sm" onClick={() => remove(e.id)}>Apagar</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div></div>
      )}
    </>
  );
}
