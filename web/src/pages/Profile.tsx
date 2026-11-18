import { useEffect, useState } from "react";
import { SkeletonRows } from "../components/Skeleton";
import { Layout } from "../components/Layout";
import { useToast } from "../components/Toast";
import { ErrorState } from "../components/ErrorState";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Me } from "../api/types";

const ROLE_LABEL: Record<string, string> = {
  OWNER: "Dono",
  RECEPTION: "Recepção",
  BARBER: "Barbeiro",
};

export function Profile() {
  const { data, loading, error, reload } = useApi<Me>(() => api.get("/api/admin/me"), []);
  const toast = useToast();

  const [name, setName] = useState("");
  const [savingName, setSavingName] = useState(false);
  const [nameMsg, setNameMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [savingPass, setSavingPass] = useState(false);
  const [passMsg, setPassMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (data) setName(data.name ?? "");
  }, [data]);

  const saveName = async () => {
    setSavingName(true);
    setNameMsg(null);
    try {
      await api.put("/api/admin/me", { name: name.trim() });
      toast.success("Nome atualizado ✓");
      reload();
    } catch (e) {
      setNameMsg({ ok: false, text: e instanceof ApiError ? e.message : "Erro ao salvar." });
    } finally {
      setSavingName(false);
    }
  };

  const savePassword = async () => {
    if (newPassword !== confirm) {
      setPassMsg({ ok: false, text: "A confirmação não confere com a nova senha." });
      return;
    }
    setSavingPass(true);
    setPassMsg(null);
    try {
      await api.put("/api/admin/me", { currentPassword, newPassword });
      toast.success("Senha alterada ✓");
      setCurrentPassword("");
      setNewPassword("");
      setConfirm("");
    } catch (e) {
      setPassMsg({ ok: false, text: e instanceof ApiError ? e.message : "Erro ao alterar a senha." });
    } finally {
      setSavingPass(false);
    }
  };

  return (
    <Layout title="Meu perfil">
      <div className="card">
        <div className="card-head">
          <h3>Dados da conta</h3>
          {data && <span className="badge badge-muted">{ROLE_LABEL[data.role] ?? data.role}</span>}
        </div>
        <div className="card-pad">
          {loading ? (
            <SkeletonRows rows={2} />
          ) : error ? (
            <ErrorState message={error} onRetry={reload} />
          ) : data ? (
            <div className="stack" style={{ maxWidth: 460 }}>
              <div className="field">
                <label>E-mail (login)
                <input className="input" value={data.email} disabled />
            </label>
                <span className="faint" style={{ fontSize: 12 }}>O e-mail de acesso não pode ser alterado por aqui.</span>
              </div>
              <div className="field">
                <label htmlFor="p-name">Seu nome</label>
                <input id="p-name" className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Como devemos te chamar" />
              </div>
              <div className="row">
                <button className="btn btn-primary" onClick={saveName} disabled={savingName || !name.trim()}>
                  {savingName ? "Salvando…" : "Salvar nome"}
                </button>
                {nameMsg && <span className={nameMsg.ok ? "badge badge-green" : "error"} role="status">{nameMsg.text}</span>}
              </div>
            </div>
          ) : null}
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Alterar senha</h3>
        </div>
        <div className="card-pad">
          <div className="stack" style={{ maxWidth: 460 }}>
            <div className="field">
              <label htmlFor="p-cur">Senha atual</label>
              <input id="p-cur" className="input" type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="p-new">Nova senha</label>
              <input id="p-new" className="input" type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
              <span className="faint" style={{ fontSize: 12 }}>Mínimo de 8 caracteres.</span>
            </div>
            <div className="field">
              <label htmlFor="p-conf">Confirmar nova senha</label>
              <input id="p-conf" className="input" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </div>
            <div className="row">
              <button
                className="btn btn-primary"
                onClick={savePassword}
                disabled={savingPass || !currentPassword || newPassword.length < 8 || !confirm}
              >
                {savingPass ? "Alterando…" : "Alterar senha"}
              </button>
              {passMsg && <span className={passMsg.ok ? "badge badge-green" : "error"} role="status">{passMsg.text}</span>}
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
}
