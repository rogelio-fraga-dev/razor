// Folgas e bloqueios de agenda (V16). Reachable pela toolbar da Agenda — sem isso a API
// existe mas o dono não consegue cadastrar folga, e a IA marcaria em cima de folga/almoço.
import { useState } from "react";
import { Modal, Switch } from "./Modal";
import { EmptyState } from "./EmptyState";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Professional, ScheduleBlock } from "../api/types";

const WEEKDAYS = ["Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"];
const ALL = "__all__";

interface Draft {
  professionalId: string; // "" = barbearia toda (mapeado para null)
  kind: "date" | "weekday";
  date: string;
  weekday: number;
  allDay: boolean;
  startTime: string;
  endTime: string;
  reason: string;
}

function emptyDraft(): Draft {
  return {
    professionalId: "",
    kind: "date",
    date: new Date().toISOString().slice(0, 10),
    weekday: 1,
    allDay: true,
    startTime: "12:00",
    endTime: "13:00",
    reason: "",
  };
}

function describe(b: ScheduleBlock): string {
  const quando =
    b.date != null
      ? new Date(`${b.date}T12:00:00`).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" })
      : `Toda ${WEEKDAYS[b.weekday ?? 0].toLowerCase()}`;
  const faixa = b.startTime && b.endTime ? `${b.startTime.slice(0, 5)}–${b.endTime.slice(0, 5)}` : "dia inteiro";
  return `${quando} · ${faixa}`;
}

export function ScheduleBlocksModal({ onClose, onChanged }: { onClose: () => void; onChanged: () => void }) {
  const blocks = useApi<ScheduleBlock[]>(() => api.get("/api/admin/schedule-blocks"), []);
  const pros = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);
  const toast = useToast();

  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const activePros = (pros.data ?? []).filter((p) => p.active);

  const create = async () => {
    setSaving(true);
    const payload = {
      professionalId: draft.professionalId || null,
      date: draft.kind === "date" ? draft.date : null,
      weekday: draft.kind === "weekday" ? draft.weekday : null,
      startTime: draft.allDay ? null : `${draft.startTime}:00`,
      endTime: draft.allDay ? null : `${draft.endTime}:00`,
      reason: draft.reason.trim() || null,
    };
    try {
      await api.post("/api/admin/schedule-blocks", payload);
      toast.success("Folga cadastrada ✓");
      setDraft(emptyDraft());
      blocks.reload();
      onChanged();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar a folga.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (b: ScheduleBlock) => {
    setBusyId(b.id);
    try {
      await api.del(`/api/admin/schedule-blocks/${b.id}`);
      toast.success("Folga removida ✓");
      blocks.reload();
      onChanged();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao remover.");
    } finally {
      setBusyId(null);
    }
  };

  const invalidRange = !draft.allDay && draft.endTime <= draft.startTime;

  return (
    <Modal
      title="Folgas e bloqueios"
      onClose={onClose}
      footer={
        <button className="btn btn-primary" onClick={create} disabled={saving || invalidRange}>
          {saving ? "Salvando…" : "Adicionar folga"}
        </button>
      }
    >
      <div className="stack" style={{ gap: 18 }}>
        <p className="muted" style={{ margin: 0, fontSize: 13.5 }}>
          A IA não oferece nem marca horário dentro de uma folga — de um barbeiro ou da barbearia toda.
        </p>

        {/* Formulário */}
        <div className="stack" style={{ gap: 12 }}>
          <div className="field">
            <label>Quem
            <select
              className="input"
              value={draft.professionalId || ALL}
              onChange={(e) => setDraft({ ...draft, professionalId: e.target.value === ALL ? "" : e.target.value })}
            >
              <option value={ALL}>Barbearia inteira</option>
              {activePros.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            </label>
          </div>

          <div className="field">
            <label>Quando</label>
            <div className="chip-row">
              <button
                type="button"
                className={`chip-btn ${draft.kind === "date" ? "active" : ""}`}
                onClick={() => setDraft({ ...draft, kind: "date" })}
              >
                Uma data
              </button>
              <button
                type="button"
                className={`chip-btn ${draft.kind === "weekday" ? "active" : ""}`}
                onClick={() => setDraft({ ...draft, kind: "weekday" })}
              >
                Toda semana
              </button>
            </div>
          </div>

          {draft.kind === "date" ? (
            <div className="field">
              <label>Data
              <input className="input" type="date" value={draft.date} onChange={(e) => setDraft({ ...draft, date: e.target.value })} />
            </label>
            </div>
          ) : (
            <div className="field">
              <label>Dia da semana
              <select className="input" value={draft.weekday} onChange={(e) => setDraft({ ...draft, weekday: Number(e.target.value) })}>
                {WEEKDAYS.map((d, i) => (
                  <option key={i} value={i}>{d}</option>
                ))}
              </select>
            </label>
            </div>
          )}

          <Switch checked={draft.allDay} onChange={(v) => setDraft({ ...draft, allDay: v })} label="Dia inteiro" />

          {!draft.allDay && (
            <div className="form-grid">
              <div className="field">
                <label>Início
                <input className="input" type="time" value={draft.startTime} onChange={(e) => setDraft({ ...draft, startTime: e.target.value })} />
            </label>
              </div>
              <div className="field">
                <label>Fim
                <input className="input" type="time" value={draft.endTime} onChange={(e) => setDraft({ ...draft, endTime: e.target.value })} />
            </label>
              </div>
            </div>
          )}
          {invalidRange && <div className="error">O fim precisa ser depois do início.</div>}

          <div className="field">
            <label>Motivo (opcional)
            <input className="input" value={draft.reason} placeholder="Ex.: almoço, folga, feriado" onChange={(e) => setDraft({ ...draft, reason: e.target.value })} />
            </label>
          </div>
        </div>

        {/* Lista */}
        <div>
          <div className="mono" style={{ fontSize: 11, letterSpacing: "0.1em", color: "var(--text-faint)", marginBottom: 8 }}>
            FOLGAS CADASTRADAS
          </div>
          {blocks.loading ? (
            <SkeletonRows rows={2} />
          ) : blocks.error ? (
            <div className="error">{blocks.error}</div>
          ) : !blocks.data || blocks.data.length === 0 ? (
            <EmptyState icon="🌴" title="Nenhuma folga cadastrada." hint="A agenda está aberta em todo o expediente." />
          ) : (
            <div className="stack" style={{ gap: 8 }}>
              {blocks.data.map((b) => (
                <div
                  key={b.id}
                  className="row between"
                  style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 550, fontSize: 14 }}>
                      {b.professionalName ?? "Barbearia inteira"}
                      {b.reason && <span className="muted" style={{ fontWeight: 400 }}> · {b.reason}</span>}
                    </div>
                    <div className="mono" style={{ fontSize: 12, color: "var(--text-faint)" }}>{describe(b)}</div>
                  </div>
                  <button className="btn btn-danger btn-sm" onClick={() => remove(b)} disabled={busyId === b.id}>
                    {busyId === b.id ? "…" : "Remover"}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
