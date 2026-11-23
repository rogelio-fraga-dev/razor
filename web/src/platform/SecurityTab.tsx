import { useState } from "react";
import { ApiError } from "../api/client";
import { useApi } from "../lib/useApi";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { papi, dt, type SecurityStatus } from "./api";
import { CodeInput, MfaSetup, RecoveryCodes } from "./MfaSetup";

interface SessionView {
  id: string;
  current: boolean;
  createdAt: string;
  lastSeenAt: string;
  ip: string | null;
  device: string;
  method: string;
  active: boolean;
}

interface DeviceView {
  id: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string;
  device: string;
  ip: string;
}

interface Overview {
  status: SecurityStatus;
  sessions: SessionView[];
  devices: DeviceView[];
}

const METHOD: Record<string, string> = {
  PASSWORD: "só senha", TOTP: "senha + app", RECOVERY: "código de recuperação", TRUSTED_DEVICE: "aparelho lembrado",
};

/** Pede um código do app e executa a ação (desativar, novos códigos de recuperação). */
function CodeAction({ label, danger, onSubmit }: { label: string; danger?: boolean; onSubmit: (code: string) => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!open) return <button className={`btn ${danger ? "btn-danger" : "btn-ghost"} btn-sm`} onClick={() => setOpen(true)}>{label}</button>;
  const go = async (c = code) => {
    if (c.length !== 6 || busy) return;
    setBusy(true);
    setError(null);
    try {
      await onSubmit(c);
      setOpen(false);
      setCode("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro.");
      setCode("");
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="stack" style={{ gap: 8, maxWidth: 360 }} onSubmit={(e) => { e.preventDefault(); go(); }}>
      <CodeInput value={code} onChange={setCode} autoSubmit={(v) => go(v)} label={`${label}: confirme com o código do app`} />
      {error && <div className="error" role="alert">{error}</div>}
      <div className="row" style={{ gap: 8 }}>
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setOpen(false); setError(null); }}>Cancelar</button>
        <button className={`btn ${danger ? "btn-danger" : "btn-primary"} btn-sm`} disabled={busy || code.length !== 6}>Confirmar</button>
      </div>
    </form>
  );
}

export function SecurityTab({ onChanged, onLoggedOutEverywhere }: { onChanged: () => void; onLoggedOutEverywhere: () => void }) {
  const { data, loading, error, reload } = useApi<Overview>(() => papi.get("/security"), []);
  const toast = useToast();
  const [codes, setCodes] = useState<string[] | null>(null);
  const [setup, setSetup] = useState(false);

  const refresh = () => { reload(); onChanged(); };

  const logoutAll = async () => {
    if (!window.confirm("Sair de todos os aparelhos, inclusive este, e esquecer os aparelhos lembrados?")) return;
    await papi.post("/security/logout-all").catch(() => {});
    onLoggedOutEverywhere();
  };

  const revokeSession = async (id: string) => {
    await papi.post(`/security/sessions/${id}/revoke`).catch(() => toast.error("Não consegui encerrar."));
    reload();
  };

  const revokeDevice = async (id: string) => {
    await papi.del(`/security/devices/${id}`).catch(() => toast.error("Não consegui remover."));
    reload();
  };

  if (!data) return error ? <div className="error" role="alert">{error}</div> : loading ? <SkeletonRows rows={4} /> : null;
  const s = data.status;

  return (
    <>
      <h2 style={{ margin: 0 }}>Segurança</h2>

      <div className="card">
        <div className="card-head">
          <h3>Verificação em duas etapas</h3>
          <span className={`badge ${s.mfaEnabled ? "badge-green" : "badge-amber"}`}>{s.mfaEnabled ? "ativa" : "desativada"}</span>
        </div>
        <div className="card-pad stack">
          {codes ? (
            <RecoveryCodes codes={codes} onDone={() => { setCodes(null); setSetup(false); refresh(); }} />
          ) : s.mfaEnabled ? (
            <>
              <p style={{ margin: 0 }}>
                O login pede o código do app autenticador. Códigos de recuperação restantes: <b>{s.recoveryCodesLeft}</b>
                {s.recoveryCodesLeft <= 3 && <span style={{ color: "var(--red)" }}> — gere novos</span>}.
              </p>
              <div className="row wrap" style={{ gap: 10, alignItems: "flex-start" }}>
                <CodeAction label="Gerar novos códigos de recuperação" onSubmit={async (c) => {
                  const r = await papi.post<{ recoveryCodes: string[] }>("/security/recovery-codes", { code: c });
                  setCodes(r.recoveryCodes);
                }} />
                <CodeAction label="Desativar (trocar de celular)" danger onSubmit={async (c) => {
                  await papi.post("/security/2fa/disable", { code: c });
                  toast.success("2FA desativado. Configure de novo no celular novo.");
                  refresh();
                }} />
              </div>
            </>
          ) : setup ? (
            <MfaSetup onEnabled={setCodes} />
          ) : (
            <>
              <p style={{ margin: 0 }}>
                Proteja o console com um código do celular além da senha.{" "}
                {s.mfaDeadline && <>Prazo: <b>{dt(s.mfaDeadline)}</b> ({s.mfaDaysLeft} dia{s.mfaDaysLeft === 1 ? "" : "s"}) — depois passa a ser obrigatório.</>}
              </p>
              <button className="btn btn-primary" style={{ justifySelf: "start" }} onClick={() => setSetup(true)}>Configurar agora</button>
            </>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-head"><h3>Sessões recentes</h3>
          <span className="muted" style={{ fontSize: 13 }}>expira após {s.idleMinutes} min sem uso</span></div>
        <div className="card-pad" style={{ overflowX: "auto" }}>
          <table className="table">
            <thead><tr><th>Aparelho</th><th>Entrou</th><th>Último uso</th><th>Como</th><th>IP</th><th></th></tr></thead>
            <tbody>
              {data.sessions.map((x) => (
                <tr key={x.id}>
                  <td>{x.device}{x.current && <span className="badge badge-blue" style={{ marginLeft: 6 }}>esta</span>}</td>
                  <td>{dt(x.createdAt)}</td>
                  <td>{dt(x.lastSeenAt)}</td>
                  <td>{METHOD[x.method] ?? x.method}</td>
                  <td>{x.ip ?? "—"}</td>
                  <td>{x.active && !x.current
                    ? <button className="btn btn-ghost btn-sm" onClick={() => revokeSession(x.id)}>Encerrar</button>
                    : !x.active ? <span className="muted">encerrada</span> : null}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <button className="btn btn-danger btn-sm" style={{ marginTop: 10 }} onClick={logoutAll}>Sair de todos os aparelhos</button>
        </div>
      </div>

      <div className="card">
        <div className="card-head"><h3>Aparelhos lembrados</h3></div>
        <div className="card-pad">
          {data.devices.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>Nenhum. Ao entrar, marque "Lembrar este aparelho" para não digitar o código por 30 dias.</p>
          ) : (
            <table className="table">
              <thead><tr><th>Aparelho</th><th>Lembrado em</th><th>Vale até</th><th>Último uso</th><th></th></tr></thead>
              <tbody>
                {data.devices.map((d) => (
                  <tr key={d.id}>
                    <td>{d.device}</td><td>{dt(d.createdAt)}</td><td>{dt(d.expiresAt)}</td><td>{dt(d.lastUsedAt || null)}</td>
                    <td><button className="btn btn-ghost btn-sm" onClick={() => revokeDevice(d.id)}>Esquecer</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
