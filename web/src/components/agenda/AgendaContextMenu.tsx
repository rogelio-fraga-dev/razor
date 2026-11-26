import { createPortal } from "react-dom";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { api, ApiError } from "../../api/client";
import { useToast } from "../Toast";
import { CloseComandaModal } from "../CloseComandaModal";
import type { AgendaItem, AttendanceStatus, Comanda } from "../../api/types";

/**
 * Menu do botão direito num horário da agenda (26/11): o que a recepção e o dono fazem toda hora
 * sem abrir o painel do agendamento — fechar a comanda (abre se ainda não tem), marcar
 * comparecimento, sinal, WhatsApp do cliente, cancelar. O barbeiro só vê o que pode mexer.
 */
export function AgendaContextMenu({
  item,
  x,
  y,
  canManage,
  canEdit,
  onClose,
  onDetails,
  onChanged,
}: {
  item: AgendaItem;
  x: number;
  y: number;
  /** Dono/recepção: comanda, sinal e cancelamento. */
  canManage: boolean;
  /** Pode mexer neste horário (dono/recepção, ou o barbeiro dono do horário). */
  canEdit: boolean;
  onClose: () => void;
  onDetails: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const navigate = useNavigate();
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ left: x, top: y });
  const [busy, setBusy] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [closing, setClosing] = useState<Comanda | null>(null);

  // Mantém o menu dentro da tela (perto da borda direita/de baixo ele abre para o outro lado).
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    setPos({
      left: Math.max(8, Math.min(x, window.innerWidth - r.width - 8)),
      top: Math.max(8, Math.min(y, window.innerHeight - r.height - 8)),
    });
  }, [x, y]);

  useEffect(() => {
    if (closing) return;
    const away = (e: Event) => {
      if (ref.current && e.target instanceof Node && ref.current.contains(e.target)) return;
      onClose();
    };
    const key = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    // A rolagem suave que leva o horário para a tela pode terminar depois do clique: ignora os
    // primeiros instantes, senão o menu fecha sozinho.
    const openedAt = Date.now();
    const scrolled = () => Date.now() - openedAt > 400 && onClose();
    window.addEventListener("pointerdown", away, true);
    window.addEventListener("scroll", scrolled, true);
    window.addEventListener("resize", onClose);
    window.addEventListener("keydown", key);
    ref.current?.querySelector<HTMLButtonElement>("button")?.focus();
    return () => {
      window.removeEventListener("pointerdown", away, true);
      window.removeEventListener("scroll", scrolled, true);
      window.removeEventListener("resize", onClose);
      window.removeEventListener("keydown", key);
    };
  }, [onClose, closing]);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Algo deu errado. Tente de novo.");
    } finally {
      setBusy(false);
    }
  };

  const done = () => {
    onChanged();
    onClose();
  };

  const attendance = (status: AttendanceStatus | null) =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/attendance`, { status });
      toast.success(status === "ATTENDED" ? "Compareceu ✓" : status === "NO_SHOW" ? "Marcado: faltou" : "Comparecimento desfeito ✓");
      done();
    });

  /** Abre a comanda (com os serviços do horário) se ainda não existe e já mostra o fechamento. */
  const closeComanda = () =>
    run(async () => {
      let id = item.comandaId;
      if (!id) {
        const created = await api.post<Comanda>("/api/admin/comandas", {
          customerName: item.customerName,
          customerPhone: item.customerPhone,
          professionalId: item.professionalId,
          appointmentId: item.id,
        });
        id = created?.id ?? null;
        onChanged();
      }
      const open = await api.get<Comanda[]>("/api/admin/comandas");
      const comanda = open?.find((c) => c.id === id);
      if (!comanda) {
        navigate("/comandas");
        return;
      }
      setClosing(comanda);
    });

  const goComanda = () =>
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

  const depositPaid = () =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/deposit-paid`);
      toast.success("Sinal marcado como recebido ✓");
      done();
    });

  const cancel = () =>
    run(async () => {
      await api.post(`/api/admin/agenda/appointments/${item.id}/cancel`, { notifyCustomer: true });
      toast.success("Agendamento cancelado — cliente avisado ✓");
      done();
    });

  const phone = item.customerPhone?.replace(/\D/g, "");
  const att = item.attendanceStatus;
  const comandaClosed = !!item.comandaStatus && item.comandaStatus !== "ABERTA";

  if (closing) {
    return (
      <CloseComandaModal
        comanda={closing}
        onClose={onClose}
        onClosed={() => {
          toast.success("Comanda fechada ✓");
          done();
        }}
      />
    );
  }

  return createPortal(
    <div
      ref={ref}
      className="ctx-menu"
      role="menu"
      aria-label={`Ações de ${item.customerName ?? "cliente"} às ${item.time?.slice(0, 5) ?? ""}`}
      style={{ left: pos.left, top: pos.top }}
      onContextMenu={(e) => e.preventDefault()}
    >
      <div className="ctx-head">
        <b>{item.customerName || "Cliente"}</b>
        <span className="faint">{item.time?.slice(0, 5)} · {item.service || "—"}</span>
      </div>
      <button role="menuitem" onClick={() => { onDetails(); onClose(); }}>📋 Abrir detalhes</button>
      {canManage && att !== "NO_SHOW" && !comandaClosed && (
        <button role="menuitem" disabled={busy} onClick={closeComanda}>💳 Fechar comanda</button>
      )}
      {canManage && (
        <button role="menuitem" disabled={busy} onClick={goComanda}>
          🧾 {item.comandaId ? "Ir para a comanda" : "Abrir comanda"}
        </button>
      )}
      {canEdit && (
        <>
          <div className="ctx-sep" />
          {att !== "ATTENDED" && <button role="menuitem" disabled={busy} onClick={() => attendance("ATTENDED")}>✓ Compareceu</button>}
          {att !== "NO_SHOW" && <button role="menuitem" disabled={busy} onClick={() => attendance("NO_SHOW")}>✗ Faltou</button>}
          {att && <button role="menuitem" disabled={busy} onClick={() => attendance(null)}>↺ Desfazer comparecimento</button>}
        </>
      )}
      {canManage && item.depositStatus === "PENDING" && (
        <button role="menuitem" disabled={busy} onClick={depositPaid}>💰 Sinal recebido</button>
      )}
      {phone && (
        <a role="menuitem" className="ctx-link" href={`https://wa.me/${phone.startsWith("55") ? phone : "55" + phone}`} target="_blank" rel="noreferrer" onClick={onClose}>
          💬 WhatsApp do cliente
        </a>
      )}
      {canManage && !att && !comandaClosed && (
        <>
          <div className="ctx-sep" />
          {confirmCancel ? (
            <button role="menuitem" className="ctx-danger" disabled={busy} onClick={cancel}>Confirmar: cancelar e avisar</button>
          ) : (
            <button role="menuitem" className="ctx-danger" disabled={busy} onClick={() => setConfirmCancel(true)}>✕ Cancelar horário</button>
          )}
        </>
      )}
    </div>,
    document.body,
  );
}
