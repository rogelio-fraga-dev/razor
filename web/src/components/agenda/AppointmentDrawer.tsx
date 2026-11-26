// Detalhe de um agendamento (gaveta lateral; no celular, folha de baixo): remarcar, trocar de
// barbeiro, cancelar, marcar comparecimento e abrir a comanda do atendimento.
import { createPortal } from "react-dom";
import { useId, useState } from "react";
import { useNavigate } from "react-router";
import { Switch } from "../Modal";
import { FormError } from "../FormError";
import { api, ApiError } from "../../api/client";
import { useToast } from "../Toast";
import { dateBr } from "../../lib/format";
import { CONFIRMATION_INFO, hhmmToMin, phoneBr } from "../../lib/agenda";
import { useFocusTrap } from "../../lib/useFocusTrap";
import "../drawer.css";
import type { AgendaItem, AgendaPro, AttendanceStatus, PaymentStatus } from "../../api/types";

const PAYMENT_LABEL: Record<PaymentStatus, string> = { PIX: "PIX", PRESENCIAL: "Presencial", PENDENTE: "Pendente" };
const ATTENDANCE_LABEL: Record<AttendanceStatus, string> = { ATTENDED: "Compareceu", NO_SHOW: "Faltou" };

export function AppointmentDrawer({
  item,
  pros,
  canEdit,
  canManage = canEdit,
  ownProfessionalId = null,
  onClose,
  onMove,
  onChanged,
}: {
  item: AgendaItem;
  pros: AgendaPro[];
  /** Pode remarcar, cancelar e marcar comparecimento DESTE agendamento. */
  canEdit: boolean;
  /** Dono/recepção: também sinal recebido e comanda (o barbeiro não mexe em dinheiro). */
  canManage?: boolean;
  /** Barbeiro: remarca só para a própria agenda — a troca de barbeiro fica travada nele. */
  ownProfessionalId?: string | null;
  onClose: () => void;
  /** Remarcação otimista da página (a mesma do arrastar). Devolve se deu certo. */
  onMove: (item: AgendaItem, date: string, time: string, professionalId: string, notify: boolean) => Promise<boolean>;
  onChanged: () => void;
}) {
  const toast = useToast();
  const navigate = useNavigate();
  const titleId = useId();
  const [date, setDate] = useState(item.date);
  const [time, setTime] = useState(item.time?.slice(0, 5) ?? "");
  const [professionalId, setProfessionalId] = useState(item.professionalId ?? "");
  const [notify, setNotify] = useState(true);
  const [busy, setBusy] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Foco preso na gaveta, Esc fecha. Ao fechar, o foco volta para o CARTÃO deste agendamento —
  // procurado de novo pelo [data-id], porque depois de uma remarcação o cartão original foi
  // re-renderizado em outra coluna. Sem cartão (cancelado, outro dia), volta para quem abriu.
  const { ref: panelRef, onKeyDown } = useFocusTrap<HTMLElement>({
    onEscape: onClose,
    restoreFocus: (previous) => {
      const card = document.querySelector<HTMLElement>(`[data-id="${item.id}"]`);
      if (card) card.focus();
      else if (previous?.isConnected) previous.focus();
    },
  });

  const started = (() => {
    const start = hhmmToMin(item.time);
    if (start === null) return false;
    const at = new Date(`${item.date}T${item.time!.slice(0, 5)}:00`);
    return at.getTime() <= Date.now();
  })();

  const changed =
    date !== item.date || time !== (item.time?.slice(0, 5) ?? "") || professionalId !== (item.professionalId ?? "");

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Algo deu errado. Tente de novo.");
    } finally {
      setBusy(false);
    }
  };

  const reschedule = () =>
    run(async () => {
      const ok = await onMove(item, date, time, professionalId, notify);
      if (ok) onClose();
    });

  const cancel = () =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/cancel`, { notifyCustomer: notify });
      toast.success(notify ? "Agendamento cancelado — cliente avisado ✓" : "Agendamento cancelado ✓");
      onChanged();
      onClose();
    });

  const attendance = (status: AttendanceStatus | null) =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/attendance`, { status });
      toast.success(status ? `Marcado: ${ATTENDANCE_LABEL[status]} ✓` : "Comparecimento desfeito ✓");
      onChanged();
      onClose();
    });

  const depositPaid = () =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/deposit-paid`);
      toast.success("Sinal marcado como recebido ✓");
      onChanged();
      onClose();
    });

  const openComanda = () =>
    run(async () => {
      if (!item.comandaId) {
        await api.post("/api/admin/comandas", {
          customerName: item.customerName,
          customerPhone: item.customerPhone,
          professionalId: item.professionalId,
          appointmentId: item.id,
        });
        toast.success("Comanda aberta ✓");
      }
      navigate("/comandas");
    });

  // Portal no <body>: o .content animado prendia o "fixed" (cabeçalho escondido).
  return createPortal(
    <div
      className="drawer-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      {/* onKeyDown no diálogo é o padrão WAI-ARIA de modal (Esc + Tab preso). */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <aside
        ref={panelRef}
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <div className="drawer-head">
          <div>
            <h3 id={titleId}>
              {item.customerName || "Cliente sem nome"} {item.subscriber && <span title="Assinante do clube">💎</span>}
            </h3>
            <p className="muted">
              {dateBr(item.date)} · {item.time?.slice(0, 5)} · {item.durationMinutes} min
            </p>
          </div>
          <button className="iconbtn" onClick={onClose} aria-label="Fechar">✕</button>
        </div>

        <div className="drawer-body stack">
          <dl className="ag-dl">
            <dt>Serviço</dt>
            <dd>{item.service || "—"}</dd>
            <dt>Barbeiro</dt>
            <dd>{item.professionalName || "Sem barbeiro"}</dd>
            <dt>WhatsApp</dt>
            <dd className="mono">{phoneBr(item.customerPhone)}</dd>
            <dt>Pagamento</dt>
            <dd>{item.paymentStatus ? PAYMENT_LABEL[item.paymentStatus] : "—"}</dd>
            <dt>Situação</dt>
            <dd>
              {item.attendanceStatus ? (
                <span className={`badge ${item.attendanceStatus === "ATTENDED" ? "badge-green" : "badge-amber"}`}>
                  {item.attendanceStatus === "ATTENDED" ? "✓ " : "✕ "}
                  {ATTENDANCE_LABEL[item.attendanceStatus]}
                </span>
              ) : (
                <span className="badge badge-muted">Agendado</span>
              )}
            </dd>
            <dt>Confirmação</dt>
            <dd>
              {item.confirmationStatus ? (
                <span className={`confirm-chip ${CONFIRMATION_INFO[item.confirmationStatus].cls}`}>
                  {CONFIRMATION_INFO[item.confirmationStatus].icon} {CONFIRMATION_INFO[item.confirmationStatus].label}
                </span>
              ) : (
                <span className="muted" title="A pergunta sai na véspera; horários marcados em cima da hora não são perguntados">
                  Não perguntado
                </span>
              )}
            </dd>
            {item.depositStatus && (
              <>
                <dt>Sinal</dt>
                <dd>
                  {item.depositStatus === "PAID" ? (
                    <span className="confirm-chip is-confirmed">✓ Sinal recebido</span>
                  ) : (
                    <span className="row wrap" style={{ gap: 8 }}>
                      <span className="confirm-chip is-deposit">💰 Sinal pendente (PIX)</span>
                      {canManage && (
                        <button className="btn btn-ghost btn-sm" onClick={depositPaid} disabled={busy}>
                          Marcar como recebido
                        </button>
                      )}
                    </span>
                  )}
                </dd>
              </>
            )}
          </dl>

          {canEdit ? (
            <>
              <section className="stack ag-section">
                <h4>Comparecimento</h4>
                {started ? (
                  <div className="chip-row">
                    <button
                      className={`chip-btn ${item.attendanceStatus === "ATTENDED" ? "active" : ""}`}
                      onClick={() => attendance(item.attendanceStatus === "ATTENDED" ? null : "ATTENDED")}
                      disabled={busy}
                    >
                      ✓ Compareceu
                    </button>
                    <button
                      className={`chip-btn ${item.attendanceStatus === "NO_SHOW" ? "active" : ""}`}
                      onClick={() => attendance(item.attendanceStatus === "NO_SHOW" ? null : "NO_SHOW")}
                      disabled={busy}
                    >
                      ✕ Faltou
                    </button>
                  </div>
                ) : (
                  <p className="muted">Disponível quando o horário começar.</p>
                )}
                {canManage && (
                  <button className="btn btn-ghost btn-sm" onClick={openComanda} disabled={busy}>
                    {item.comandaId ? "Ver comanda" : "🧾 Abrir comanda deste atendimento"}
                  </button>
                )}
              </section>

              {item.attendanceStatus !== "ATTENDED" && (
                <section className="stack ag-section">
                  <h4>Remarcar</h4>
                  <div className="ag-grid3">
                    <div className="field">
                      <label>
                        Data
                        <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                      </label>
                    </div>
                    <div className="field">
                      <label>
                        Horário
                        <input className="input" type="time" step={1800} value={time} onChange={(e) => setTime(e.target.value)} />
                      </label>
                    </div>
                    <div className="field">
                      <label>
                        Barbeiro
                        <select
                          className="input"
                          value={professionalId}
                          onChange={(e) => setProfessionalId(e.target.value)}
                          disabled={!!ownProfessionalId}
                          title={ownProfessionalId ? "Você remarca só na sua agenda." : undefined}
                        >
                          {!professionalId && <option value="">Escolha…</option>}
                          {pros.filter((p) => p.active || p.id === ownProfessionalId).map((p) => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                          ))}
                        </select>
                      </label>
                    </div>
                  </div>
                </section>
              )}

              <Switch checked={notify} onChange={setNotify} label="Avisar o cliente pelo WhatsApp" />
              <FormError>{error}</FormError>

              <div className="drawer-actions">
                {confirmCancel ? (
                  <>
                    <span className="muted">Cancelar este horário?</span>
                    <button className="btn btn-ghost btn-sm" onClick={() => setConfirmCancel(false)} disabled={busy}>Não</button>
                    <button className="btn btn-danger btn-sm" onClick={cancel} disabled={busy}>Sim, cancelar</button>
                  </>
                ) : (
                  <>
                    <button className="btn btn-danger btn-sm" onClick={() => setConfirmCancel(true)} disabled={busy}>
                      Cancelar agendamento
                    </button>
                    {item.attendanceStatus !== "ATTENDED" && (
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={reschedule}
                        disabled={busy || !changed || !professionalId || !time}
                      >
                        {busy ? "Salvando…" : "Salvar remarcação"}
                      </button>
                    )}
                  </>
                )}
              </div>
            </>
          ) : (
            <p className="muted">
              {canManage
                ? "Este horário não pode ser alterado."
                : "Você altera só os seus próprios horários. Os dos colegas ficam com a recepção e o dono."}
            </p>
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}
