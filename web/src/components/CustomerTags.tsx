import { useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { api, ApiError } from "../api/client";
import type { Customer, CustomerStatus, CustomerTag } from "../api/types";

/** Rótulo e cor de cada status (V36). Espelha o enum CustomerStatus do backend. */
export const STATUS_META: Record<CustomerStatus, { label: string; className: string; hint: string }> = {
  NOVO: { label: "Novo", className: "badge-blue", hint: "Cadastrado há pouco, sem segunda visita ainda" },
  ATIVO: { label: "Ativo", className: "badge-green", hint: "Voltando dentro do próprio ritmo" },
  EM_RISCO: { label: "Em risco", className: "badge-amber", hint: "Passou do intervalo de costume" },
  SUMIDO: { label: "Sumido", className: "badge-muted", hint: "Passou do dobro do intervalo de costume" },
};

/** Paleta curta para o dono escolher sem abrir um seletor de cor. */
const COLORS = ["#6B7280", "#DC2626", "#D97706", "#16A34A", "#2563EB", "#7C3AED", "#DB2777"];

/** Chip de tag com a cor do dono. O texto fica escuro/claro pelo contraste do fundo. */
export function TagChip({ tag }: { tag: CustomerTag }) {
  return (
    <span
      className="badge"
      style={{ background: `${tag.color}22`, color: tag.color, border: `1px solid ${tag.color}55` }}
    >
      {tag.name}
    </span>
  );
}

/**
 * Atribuir/remover tags de um cliente — e criar tag nova ali mesmo, sem trocar de tela. Salva o
 * conjunto inteiro (PUT /customers/{id}/tags), então desmarcar e salvar remove.
 */
export function CustomerTagsModal({
  customer,
  tags,
  onClose,
  onSaved,
  onTagsChanged,
}: {
  customer: Customer;
  tags: CustomerTag[];
  onClose: () => void;
  onSaved: () => void;
  onTagsChanged: () => void;
}) {
  const toast = useToast();
  const [selected, setSelected] = useState<Set<string>>(new Set(customer.tags.map((t) => t.id)));
  const [newName, setNewName] = useState("");
  const [newColor, setNewColor] = useState(COLORS[0]);
  const [saving, setSaving] = useState(false);
  const [creating, setCreating] = useState(false);
  const [extra, setExtra] = useState<CustomerTag[]>([]);

  const all = [...tags, ...extra.filter((e) => !tags.some((t) => t.id === e.id))];

  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const createTag = async () => {
    if (!newName.trim()) return;
    setCreating(true);
    try {
      const tag = await api.post<CustomerTag>("/api/admin/customers/tags", { name: newName.trim(), color: newColor });
      setExtra((prev) => [...prev, tag]);
      setSelected((prev) => new Set(prev).add(tag.id));
      setNewName("");
      onTagsChanged();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao criar a tag.");
    } finally {
      setCreating(false);
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      await api.put(`/api/admin/customers/${customer.id}/tags`, { tagIds: [...selected] });
      toast.success("Tags atualizadas ✓");
      onSaved();
      onClose();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar as tags.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={`Tags de ${customer.name || "cliente"}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Cancelar</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? "Salvando…" : "Salvar tags"}
          </button>
        </>
      }
    >
      <div className="stack">
        {all.length === 0 ? (
          <span className="muted" style={{ fontSize: 14 }}>
            Nenhuma tag ainda. Crie a primeira abaixo — ex.: <em>VIP</em>, <em>Barba</em>, <em>Indicou amigo</em>.
          </span>
        ) : (
          <div className="chip-row" role="group" aria-label="Tags disponíveis">
            {all.map((t) => (
              <button
                key={t.id}
                type="button"
                className={`chip-btn ${selected.has(t.id) ? "active" : ""}`}
                aria-pressed={selected.has(t.id)}
                onClick={() => toggle(t.id)}
                style={selected.has(t.id) ? { background: t.color, borderColor: t.color } : undefined}
              >
                {selected.has(t.id) ? "✓ " : ""}{t.name}
              </button>
            ))}
          </div>
        )}

        <div className="field">
          <label>Nova tag
            <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
              <input
                className="input"
                value={newName}
                maxLength={40}
                placeholder="ex.: VIP"
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); createTag(); } }}
                style={{ maxWidth: 200 }}
              />
              <div className="chip-row" role="radiogroup" aria-label="Cor da tag">
                {COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    role="radio"
                    aria-checked={newColor === c}
                    aria-label={`Cor ${c}`}
                    onClick={() => setNewColor(c)}
                    style={{
                      width: 22, height: 22, borderRadius: "50%", background: c, cursor: "pointer",
                      border: newColor === c ? "3px solid var(--text)" : "1px solid transparent",
                    }}
                  />
                ))}
              </div>
              <button className="btn btn-ghost" type="button" onClick={createTag} disabled={creating || !newName.trim()}>
                {creating ? "Criando…" : "Criar"}
              </button>
            </div>
          </label>
        </div>
      </div>
    </Modal>
  );
}

/**
 * "Enviar mensagem para este grupo" (V36): disparo para os clientes do filtro atual (tag e/ou
 * status) pelo mesmo mecanismo do disparo do WhatsApp do dono — um por vez, com pausa entre envios.
 */
export function BroadcastModal({
  tag,
  status,
  recipients,
  onClose,
}: {
  tag: CustomerTag | null;
  status: CustomerStatus | null;
  recipients: number | null;
  onClose: () => void;
}) {
  const toast = useToast();
  const [message, setMessage] = useState("");
  const [promo, setPromo] = useState(false);
  const [sending, setSending] = useState(false);

  const grupo = [tag ? `tag “${tag.name}”` : null, status ? STATUS_META[status].label.toLowerCase() : null]
    .filter(Boolean)
    .join(" + ") || "toda a base";

  const send = async () => {
    setSending(true);
    try {
      const res = await api.post<{ recipients: number }>("/api/admin/broadcasts", {
        message: message.trim(),
        tagId: tag?.id ?? null,
        status,
        promo,
      });
      toast.success(`Envio iniciado para ${res.recipients} cliente(s) ✓`);
      onClose();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao iniciar o envio.");
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      title="Enviar mensagem para este grupo"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={sending}>Cancelar</button>
          <button
            className="btn btn-primary"
            onClick={send}
            disabled={sending || !message.trim() || recipients === 0}
          >
            {sending ? "Enviando…" : recipients != null ? `Enviar para ${recipients}` : "Enviar"}
          </button>
        </>
      }
    >
      <div className="stack">
        <span className="muted" style={{ fontSize: 14 }}>
          Grupo: <strong>{grupo}</strong>
          {recipients != null && <> · {recipients} cliente(s) com WhatsApp (administradores ficam de fora)</>}
        </span>
        <div className="field">
          <label>Mensagem
            <textarea
              className="input"
              rows={5}
              maxLength={1000}
              value={message}
              placeholder="ex.: Sentimos sua falta! Essa semana o corte sai com 10% off 💈"
              onChange={(e) => setMessage(e.target.value)}
            />
          </label>
        </div>
        <label className="row" style={{ gap: 8, fontSize: 14 }}>
          <input type="checkbox" checked={promo} onChange={(e) => setPromo(e.target.checked)} />
          É uma promoção (cabeçalho “Promoção” em vez de “Aviso”)
        </label>
        <span className="faint" style={{ fontSize: 12 }}>
          As mensagens saem uma por vez, com alguns segundos entre cada uma — rajada de envios é o
          jeito mais rápido de ter o número bloqueado pelo WhatsApp. Grupos grandes levam alguns minutos.
        </span>
      </div>
    </Modal>
  );
}
