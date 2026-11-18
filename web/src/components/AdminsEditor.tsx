// Telefones de admin do WhatsApp. Usado no card de Configurações e no passo correspondente
// do assistente, para o dono não precisar sair do fluxo para cadastrar o próprio número.
import { useState } from "react";
import { ErrorState } from "./ErrorState";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { AdminContact } from "../api/types";

export function AdminsEditor({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error, reload } = useApi<AdminContact[]>(
    () => api.get("/api/admin/admins"),
    [reloadKey],
  );
  const [phone, setPhone] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const toast = useToast();

  const add = async () => {
    if (!phone.trim()) return;
    setBusy(true);
    setFormError(null);
    try {
      await api.post("/api/admin/admins", { phone: phone.trim(), name: name.trim() || null });
      setPhone("");
      setName("");
      toast.success("Admin adicionado ✓");
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao adicionar.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: string) => {
    try {
      await api.del(`/api/admin/admins/${id}`);
      toast.success("Admin removido ✓");
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao remover.");
    } finally {
      reload();
    }
  };

  return (
    <>
      <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
        Telefones tratados como admin no WhatsApp (recebem avisos e usam os comandos de gestão).
        Use o número <strong>pessoal</strong> do dono — não o número do bot.
      </p>
      {/* Rótulos de verdade, não placeholder.
          Estes dois eram os únicos campos do painel sem <label> nenhum — placeholder some ao
          digitar e nem todo leitor de tela o expõe como nome do campo. */}
      <div className="row wrap end" style={{ marginBottom: 16 }}>
        <div className="field grow" style={{ maxWidth: 220 }}>
          <label>Telefone (com DDD)
            <input
              className="input"
              inputMode="tel"
              placeholder="Ex.: 11987654321"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </label>
        </div>
        <div className="field grow" style={{ maxWidth: 200 }}>
          <label>Nome (opcional)
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
        </div>
        <button className="btn btn-primary" onClick={add} disabled={busy || !phone.trim()}>
          Adicionar
        </button>
      </div>
      {formError && (
        <div className="error" role="alert" style={{ marginBottom: 12 }}>{formError}</div>
      )}

      {loading ? (
        <SkeletonRows rows={2} />
      ) : error ? (
        <ErrorState compact message={error} onRetry={reload} />
      ) : !data || data.length === 0 ? (
        <div className="faint">Nenhum admin cadastrado — o sistema usa os telefones da configuração global.</div>
      ) : (
        <div className="stack" style={{ gap: 8 }}>
          {data.map((a) => (
            <div
              key={a.id}
              className="row between"
              style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}
            >
              <div>
                <div style={{ fontWeight: 550 }}>{a.phone}</div>
                {a.name && <div className="faint" style={{ fontSize: 13 }}>{a.name}</div>}
              </div>
              {/* Vários "Remover" idênticos na lista de botões do leitor de tela sem o número. */}
              <button
                className="btn btn-danger btn-sm"
                onClick={() => remove(a.id)}
                aria-label={`Remover admin ${a.phone}`}
              >
                Remover
              </button>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
