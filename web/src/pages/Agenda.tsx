import { useMemo, useState } from "react";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { AppointmentView, PaymentStatus, Professional } from "../api/types";
import { todayIso } from "../lib/format";

// Grade do dia: das 08h às 21h, em faixas de 30min (como o Google Agenda).
const START_HOUR = 8;
const END_HOUR = 21;
const SLOT_MIN = 30;
const PX_PER_MIN = 1.5; // 30min = 45px de altura
const TOTAL_MIN = (END_HOUR - START_HOUR) * 60;

const PAYMENT_LABEL: Record<PaymentStatus, string> = {
  PIX: "PIX",
  PRESENCIAL: "Presencial",
  PENDENTE: "Pendente",
};

function hhmmToMin(t: string | null): number | null {
  if (!t) return null;
  const [h, m] = t.split(":").map(Number);
  if (Number.isNaN(h)) return null;
  return h * 60 + (m || 0);
}

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

const LONG_DATE = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "long" });

/** Coluna "sem barbeiro" para agendamentos que a IA ainda não atribuiu. */
const UNASSIGNED = "__none__";

export function Agenda() {
  const [date, setDate] = useState(todayIso());
  const agenda = useApi<AppointmentView[]>(() => api.get(`/api/admin/reports/agenda?date=${date}`), [date]);
  const pros = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);

  const columns = useMemo(() => {
    const active = (pros.data ?? []).filter((p) => p.active);
    const cols = active.map((p) => ({ id: p.id, name: p.name }));
    const hasUnassigned = (agenda.data ?? []).some((a) => !a.professionalId);
    if (hasUnassigned || cols.length === 0) cols.push({ id: UNASSIGNED, name: "Sem barbeiro" });
    return cols;
  }, [pros.data, agenda.data]);

  const isToday = date === todayIso();
  const nowMin = new Date().getHours() * 60 + new Date().getMinutes();
  const nowTop = (nowMin - START_HOUR * 60) * PX_PER_MIN;
  const showNow = isToday && nowMin >= START_HOUR * 60 && nowMin <= END_HOUR * 60;

  const slots = Array.from({ length: (TOTAL_MIN / SLOT_MIN) + 1 }, (_, i) => {
    const min = START_HOUR * 60 + i * SLOT_MIN;
    return { min, label: `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}` };
  });

  const total = agenda.data?.length ?? 0;

  return (
    <Layout
      title="Agenda"
      actions={
        <div className="chip-row">
          <button className="chip-btn" onClick={() => setDate(addDays(date, -1))} aria-label="Dia anterior">‹</button>
          <button className={`chip-btn ${isToday ? "active" : ""}`} onClick={() => setDate(todayIso())}>Hoje</button>
          <button className="chip-btn" onClick={() => setDate(addDays(date, 1))} aria-label="Próximo dia">›</button>
          <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} style={{ width: 158, padding: "6px 10px" }} />
        </div>
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>{LONG_DATE.format(new Date(`${date}T12:00:00`))}</h3>
          <span className="badge badge-muted">{total} {total === 1 ? "agendamento" : "agendamentos"}</span>
        </div>

        {agenda.loading ? (
          <div className="stack" style={{ padding: 20, gap: 10 }}>
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} h={42} />)}
          </div>
        ) : agenda.error ? (
          <div className="empty error">{agenda.error}</div>
        ) : total === 0 ? (
          <EmptyState
            icon="📅"
            title="Nenhum corte neste dia."
            hint="Quando um cliente pedir horário no WhatsApp, a IA agenda e o bloco aparece aqui na hora."
          />
        ) : (
          <div className="cal">
            {/* cabeçalho das colunas (barbeiros) */}
            <div className="cal-head">
              <div className="cal-gutter" />
              {columns.map((c) => (
                <div className="cal-col-head" key={c.id}>
                  <span className="cal-avatar" aria-hidden="true">{c.name.charAt(0).toUpperCase()}</span>
                  <span className="cal-pro">{c.name}</span>
                  <span className="cal-count mono">
                    {(agenda.data ?? []).filter((a) => (a.professionalId ?? UNASSIGNED) === c.id).length}
                  </span>
                </div>
              ))}
            </div>

            {/* grade */}
            <div className="cal-body" style={{ height: TOTAL_MIN * PX_PER_MIN }}>
              <div className="cal-gutter">
                {slots.map((s) => (
                  <div className="cal-time" key={s.min} style={{ top: (s.min - START_HOUR * 60) * PX_PER_MIN }}>
                    {s.min % 60 === 0 ? s.label : ""}
                  </div>
                ))}
              </div>

              {columns.map((c) => (
                <div className="cal-col" key={c.id}>
                  {slots.map((s) => (
                    <div
                      key={s.min}
                      className={`cal-slot ${s.min % 60 === 0 ? "hour" : ""}`}
                      style={{ top: (s.min - START_HOUR * 60) * PX_PER_MIN, height: SLOT_MIN * PX_PER_MIN }}
                    />
                  ))}

                  {(agenda.data ?? [])
                    .filter((a) => (a.professionalId ?? UNASSIGNED) === c.id)
                    .map((a, i) => {
                      const startMin = hhmmToMin(a.time);
                      if (startMin === null) return null;
                      const top = (startMin - START_HOUR * 60) * PX_PER_MIN;
                      const dur = a.durationMinutes ?? 30;
                      const height = Math.max(dur * PX_PER_MIN - 4, 26);
                      const st = a.paymentStatus;
                      return (
                        <article
                          key={i}
                          className={`cal-event ${st ? `ev-${st.toLowerCase()}` : ""}`}
                          style={{ top, height }}
                          title={`${a.time?.slice(0, 5)} · ${a.customerName} · ${a.service}`}
                        >
                          <span className="ev-time mono">{a.time?.slice(0, 5)}</span>
                          <span className="ev-name">
                            {a.customerName || "—"}
                            {a.subscriber && <span className="ev-gem" title="Assinante do clube">💎</span>}
                          </span>
                          {height > 46 && <span className="ev-service">{a.service}</span>}
                          {height > 68 && st && <span className="ev-pay">{PAYMENT_LABEL[st]}</span>}
                        </article>
                      );
                    })}
                </div>
              ))}

              {showNow && (
                <div className="cal-now" style={{ top: nowTop }}>
                  <span className="cal-now-dot" />
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}
