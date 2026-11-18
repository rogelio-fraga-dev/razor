// Logins do painel ("Equipe & acesso" em Configurações). Só o dono vê e mexe — a API recusa o
// resto. As regras de verdade (não mexer em si mesmo, nunca ficar sem dono ativo, e-mail único)
// moram no backend (PanelUserService); aqui só se evita oferecer o botão que vai ser recusado.
import { useState } from "react";
import { ErrorState } from "./ErrorState";
import { Modal } from "./Modal";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useAuth } from "../auth/AuthContext";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { PanelUser, Professional, UserRole } from "../api/types";

/** Mesma regra da troca de senha em "Meu perfil" e do onboarding. */
const MIN_PASSWORD = 8;

const ROLES: { id: UserRole; label: string; hint: string }[] = [
  { id: "OWNER", label: "Dono", hint: "vê e configura tudo, inclusive financeiro e acessos" },
  { id: "RECEPTION", label: "Recepção", hint: "agenda, clientes, comandas e caixa — sem financeiro" },
  { id: "BARBER", label: "Barbeiro", hint: "a agenda e o próprio “Meu dia” (horários e comissão)" },
];
const roleLabel = (r: UserRole) => ROLES.find((x) => x.id === r)?.label ?? r;

interface Draft {
  name: string;
  email: string;
  role: UserRole;
  password: string;
  professionalId: string;
}
const EMPTY: Draft = { name: "", email: "", role: "BARBER", password: "", professionalId: "" };

export function PanelUsersEditor({ reloadKey = 0 }: { reloadKey?: number }) {
  const { session } = useAuth();
  const toast = useToast();
  const users = useApi<PanelUser[]>(() => api.get("/api/admin/panel-users"), [reloadKey]);
  const pros = useApi<Professional[]>(() => api.get("/api/admin/professionals"), [reloadKey]);

  const [draft, setDraft] = useState<Draft | null>(null);
  const [resetFor, setResetFor] = useState<PanelUser | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [deactivating, setDeactivating] = useState<PanelUser | null>(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);

  const list = users.data ?? [];
  const isMe = (u: PanelUser) => !!session && u.email.toLowerCase() === session.email.toLowerCase();
  const activeOwners = list.filter((u) => u.role === "OWNER" && u.active).length;
  // Último dono ativo: nem desativar nem rebaixar (a API recusa do mesmo jeito).
  const isLastOwner = (u: PanelUser) => u.role === "OWNER" && u.active && activeOwners <= 1;
  const proName = (id: string | null) => (id ? pros.data?.find((p) => p.id === id)?.name ?? "—" : null);
  const linkedIds = new Set(list.map((u) => u.professionalId).filter(Boolean) as string[]);
  const freePros = (pros.data ?? []).filter((p) => p.active && !linkedIds.has(p.id));

  const fail = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

  const create = async () => {
    if (!draft) return;
    setBusy(true);
    setFormError(null);
    try {
      await api.post("/api/admin/panel-users", {
        name: draft.name.trim(),
        email: draft.email.trim(),
        role: draft.role,
        password: draft.password,
        professionalId: draft.role !== "RECEPTION" && draft.professionalId ? draft.professionalId : null,
      });
      toast.success(`Acesso criado para ${draft.email.trim()} ✓`);
      setDraft(null);
      users.reload();
    } catch (e) {
      setFormError(fail(e, "Erro ao criar o acesso."));
    } finally {
      setBusy(false);
    }
  };

  const changeRole = async (u: PanelUser, role: UserRole) => {
    setRowError(null);
    try {
      await api.put(`/api/admin/panel-users/${u.id}/role`, { role });
      toast.success(`${u.name || u.email} agora é ${roleLabel(role).toLowerCase()} ✓`);
    } catch (e) {
      setRowError(fail(e, "Erro ao mudar o papel."));
    } finally {
      users.reload();
    }
  };

  const setActive = async (u: PanelUser, active: boolean) => {
    setBusy(true);
    setRowError(null);
    try {
      await api.put(`/api/admin/panel-users/${u.id}/active`, { active });
      toast.success(active ? `${u.name || u.email} reativado ✓` : `${u.name || u.email} desativado ✓`);
      setDeactivating(null);
    } catch (e) {
      setDeactivating(null);
      setRowError(fail(e, "Erro ao alterar o acesso."));
    } finally {
      setBusy(false);
      users.reload();
    }
  };

  const resetPassword = async () => {
    if (!resetFor) return;
    setBusy(true);
    setFormError(null);
    try {
      await api.put(`/api/admin/panel-users/${resetFor.id}/password`, { password: newPassword });
      toast.success(`Senha de ${resetFor.name || resetFor.email} redefinida ✓`);
      setResetFor(null);
      setNewPassword("");
    } catch (e) {
      setFormError(fail(e, "Erro ao redefinir a senha."));
    } finally {
      setBusy(false);
    }
  };

  const draftValid =
    !!draft &&
    draft.name.trim().length > 0 &&
    /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(draft.email.trim()) &&
    draft.password.length >= MIN_PASSWORD;

  return (
    <>
      <div className="row between wrap" style={{ gap: 12, marginBottom: 14 }}>
        <p className="muted" style={{ margin: 0, fontSize: 14, maxWidth: 560 }}>
          Quem entra no painel e o que cada um vê. Cada pessoa tem o próprio e-mail e senha —
          não compartilhe o login do dono.
        </p>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => {
            setFormError(null);
            setDraft({ ...EMPTY });
          }}
        >
          Novo acesso
        </button>
      </div>
      {rowError && (
        <div className="error" role="alert" style={{ marginBottom: 12 }}>{rowError}</div>
      )}

      {users.loading && !users.data ? (
        <SkeletonRows rows={3} />
      ) : users.error ? (
        <ErrorState compact message={users.error} onRetry={users.reload} />
      ) : list.length === 0 ? (
        <div className="faint">Nenhum acesso cadastrado.</div>
      ) : (
        <div className="table-bleed">
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Nome</th>
                <th>Papel</th>
                <th>Profissional</th>
                <th>Situação</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {list.map((u) => {
                const me = isMe(u);
                const lockedRole = me || isLastOwner(u);
                return (
                  <tr key={u.id} style={u.active ? undefined : { opacity: 0.6 }}>
                    <td className="tc-head">
                      <div style={{ fontWeight: 550 }}>
                        {u.name || "Sem nome"}
                        {me && <span className="badge badge-blue" style={{ marginLeft: 8 }}>você</span>}
                      </div>
                      <div className="faint mono" style={{ fontSize: 12.5 }}>{u.email}</div>
                    </td>
                    <td data-label="Papel">
                      <select
                        className="input"
                        style={{ padding: "6px 10px", fontSize: 13, maxWidth: 150 }}
                        value={u.role}
                        disabled={lockedRole || !u.active}
                        title={
                          me
                            ? "Você não pode mudar o seu próprio papel."
                            : isLastOwner(u)
                              ? "A barbearia precisa de pelo menos um dono ativo."
                              : undefined
                        }
                        onChange={(e) => changeRole(u, e.target.value as UserRole)}
                        aria-label={`Papel de ${u.name || u.email}`}
                      >
                        {ROLES.map((r) => (
                          <option key={r.id} value={r.id}>{r.label}</option>
                        ))}
                      </select>
                    </td>
                    <td data-label="Profissional">
                      {u.role === "RECEPTION" ? (
                        <span className="faint">—</span>
                      ) : u.professionalId ? (
                        proName(u.professionalId)
                      ) : (
                        <span className="faint" title="Ligue em Profissionais › editar › Login do painel">
                          sem vínculo
                        </span>
                      )}
                    </td>
                    <td data-label="Situação">
                      {u.active ? (
                        <span className="badge badge-green" style={{ alignSelf: "flex-start" }}>Ativo</span>
                      ) : (
                        <span className="badge badge-muted" style={{ alignSelf: "flex-start" }}>Desativado</span>
                      )}
                    </td>
                    <td className="right tc-actions">
                      {me ? (
                        <span className="faint" style={{ fontSize: 12.5 }}>Sua senha: em “Meu perfil”</span>
                      ) : (
                        <>
                          <button
                            className="btn btn-ghost btn-sm"
                            onClick={() => {
                              setFormError(null);
                              setNewPassword("");
                              setResetFor(u);
                            }}
                            aria-label={`Redefinir a senha de ${u.name || u.email}`}
                          >
                            Redefinir senha
                          </button>
                          {u.active ? (
                            <button
                              className="btn btn-danger btn-sm"
                              disabled={isLastOwner(u)}
                              title={isLastOwner(u) ? "A barbearia precisa de pelo menos um dono ativo." : undefined}
                              onClick={() => setDeactivating(u)}
                              aria-label={`Desativar ${u.name || u.email}`}
                            >
                              Desativar
                            </button>
                          ) : (
                            <button
                              className="btn btn-ghost btn-sm"
                              disabled={busy}
                              onClick={() => setActive(u, true)}
                              aria-label={`Reativar ${u.name || u.email}`}
                            >
                              Reativar
                            </button>
                          )}
                        </>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {draft && (
        <Modal
          title="Novo acesso ao painel"
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={busy}>Cancelar</button>
              <button className="btn btn-primary" onClick={create} disabled={busy || !draftValid}>
                {busy ? "Criando…" : "Criar acesso"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="form-grid">
              <div className="field">
                <label>Nome
                  <input className="input" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                </label>
              </div>
              <div className="field">
                <label>E-mail (login)
                  <input
                    className="input"
                    type="email"
                    autoComplete="off"
                    value={draft.email}
                    onChange={(e) => setDraft({ ...draft, email: e.target.value })}
                  />
                </label>
              </div>
            </div>
            <div className="field">
              <label>Papel
                <select
                  className="input"
                  value={draft.role}
                  onChange={(e) => setDraft({ ...draft, role: e.target.value as UserRole })}
                >
                  {ROLES.map((r) => (
                    <option key={r.id} value={r.id}>{r.label}</option>
                  ))}
                </select>
              </label>
              <span className="faint" style={{ fontSize: 12 }}>{ROLES.find((r) => r.id === draft.role)?.hint}</span>
            </div>
            {draft.role !== "RECEPTION" && (
              <div className="field">
                <label>Profissional na agenda
                  <select
                    className="input"
                    value={draft.professionalId}
                    onChange={(e) => setDraft({ ...draft, professionalId: e.target.value })}
                  >
                    <option value="">Nenhum</option>
                    {freePros.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </label>
                <span className="faint" style={{ fontSize: 12 }}>
                  {draft.role === "BARBER"
                    ? "Ligado ao cadastro dele, o barbeiro vê no “Meu dia” os próprios horários e a comissão."
                    : "Para o dono que também atende: liga o “Meu dia” à agenda dele."}
                  {pros.data && freePros.length === 0 && " Nenhum profissional livre — cadastre em Profissionais."}
                </span>
              </div>
            )}
            <div className="field">
              <label>Senha inicial
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={draft.password}
                  onChange={(e) => setDraft({ ...draft, password: e.target.value })}
                />
              </label>
              <span className="faint" style={{ fontSize: 12 }}>
                Mínimo de {MIN_PASSWORD} caracteres. Passe a senha à pessoa e peça para trocar em “Meu perfil”.
              </span>
            </div>
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}

      {resetFor && (
        <Modal
          title={`Redefinir a senha de ${resetFor.name || resetFor.email}`}
          onClose={() => setResetFor(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setResetFor(null)} disabled={busy}>Cancelar</button>
              <button
                className="btn btn-primary"
                onClick={resetPassword}
                disabled={busy || newPassword.length < MIN_PASSWORD}
              >
                {busy ? "Salvando…" : "Redefinir senha"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Nova senha provisória
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              </label>
              <span className="faint" style={{ fontSize: 12 }}>
                Mínimo de {MIN_PASSWORD} caracteres. As sessões abertas dessa pessoa caem na hora.
              </span>
            </div>
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}

      {deactivating && (
        <Modal
          title={`Desativar ${deactivating.name || deactivating.email}?`}
          onClose={() => setDeactivating(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDeactivating(null)} disabled={busy}>Cancelar</button>
              <button className="btn btn-danger" onClick={() => setActive(deactivating, false)} disabled={busy}>
                {busy ? "Desativando…" : "Desativar acesso"}
              </button>
            </>
          }
        >
          <p style={{ margin: 0 }}>
            A pessoa sai do painel na hora e não consegue mais entrar. O histórico (agenda, comandas,
            comissões) continua intacto e dá para reativar depois.
          </p>
        </Modal>
      )}
    </>
  );
}
