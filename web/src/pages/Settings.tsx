import { useCallback, useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { Switch } from "../components/Modal";
import { Skeleton } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type {
  AdminContact,
  Barbershop,
  BotSettings,
  BotStatus,
  WhatsAppConnect,
  WhatsAppStatus,
} from "../api/types";

function StatusDot({ ok, warn }: { ok: boolean; warn?: boolean }) {
  const color = ok ? "var(--green)" : warn ? "var(--amber)" : "var(--red)";
  return (
    <span
      aria-hidden="true"
      style={{
        width: 9,
        height: 9,
        borderRadius: "50%",
        background: color,
        boxShadow: `0 0 10px ${color}`,
        flexShrink: 0,
      }}
    />
  );
}

/** Saúde do bot: a resposta imediata de "está tudo funcionando?" + o liga/desliga geral. */
function BotStatusCard() {
  const { data, loading, error, reload } = useApi<BotStatus>(
    () => api.get("/api/admin/settings/bot/status"),
    [],
  );
  const [toggling, setToggling] = useState(false);
  const [toggleError, setToggleError] = useState<string | null>(null);

  const setEnabled = async (enabled: boolean) => {
    setToggling(true);
    setToggleError(null);
    try {
      await api.put("/api/admin/settings/bot/enabled", { enabled });
      reload();
    } catch (e) {
      setToggleError(e instanceof ApiError ? e.message : "Erro ao alterar.");
    } finally {
      setToggling(false);
    }
  };

  const allOk = !!data && data.whatsappConnected && data.aiConfigured && data.botEnabled;

  const rowStyle: React.CSSProperties = {
    display: "flex",
    alignItems: "center",
    gap: 10,
    padding: "12px 14px",
    border: "1px solid var(--border)",
    borderRadius: "var(--radius-sm)",
    fontSize: 14,
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Status do bot</h3>
        {data &&
          (allOk ? (
            <span className="badge badge-green">● Operacional</span>
          ) : (
            <span className="badge badge-amber">● Atenção</span>
          ))}
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="muted">Verificando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : data ? (
          <div className="stack" style={{ gap: 10 }}>
            <div style={rowStyle}>
              <StatusDot ok={data.whatsappConnected} warn={data.whatsappState === "connecting"} />
              <div className="grow">
                <b>WhatsApp</b>{" "}
                <span className="muted">
                  {data.whatsappConnected
                    ? "conectado — o bot recebe e envia mensagens"
                    : data.whatsappState === "connecting"
                      ? "aguardando pareamento — escaneie o QR ao lado"
                      : "desconectado — use o card “Conexão do WhatsApp”"}
                </span>
              </div>
              <span className="mono faint" style={{ fontSize: 11 }}>{data.instanceName}</span>
            </div>

            <div style={rowStyle}>
              <StatusDot ok={data.aiConfigured} />
              <div className="grow">
                <b>Inteligência artificial</b>{" "}
                <span className="muted">
                  {data.aiConfigured ? "funcionando" : "indisponível no momento"}
                </span>
              </div>
            </div>

            <div style={rowStyle}>
              <StatusDot ok={data.botEnabled} />
              <div className="grow">
                <b>Atendimento automático</b>{" "}
                <span className="muted">
                  {data.botEnabled ? "ligado — clientes são atendidos pela IA" : "desligado — mensagens de clientes são ignoradas"}
                </span>
              </div>
              <Switch
                checked={data.botEnabled}
                onChange={(v) => setEnabled(v)}
                label={toggling ? "…" : data.botEnabled ? "Ligado" : "Desligado"}
              />
            </div>

            {toggleError && <div className="error">{toggleError}</div>}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function WhatsAppCard() {
  const [status, setStatus] = useState<WhatsAppStatus | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [pairing, setPairing] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshStatus = useCallback(async (): Promise<WhatsAppStatus | null> => {
    try {
      const s = await api.get<WhatsAppStatus>("/api/admin/whatsapp/status");
      setStatus(s);
      if (s.connected) {
        setQr(null);
        setPairing(null);
      }
      return s;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao consultar o WhatsApp.");
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshStatus();
  }, [refreshStatus]);

  // Enquanto o QR está na tela, verifica a conexão a cada 3s até o WhatsApp parear.
  useEffect(() => {
    if (!qr) return;
    const id = setInterval(async () => {
      const s = await refreshStatus();
      if (s?.connected) clearInterval(id);
    }, 3000);
    return () => clearInterval(id);
  }, [qr, refreshStatus]);

  const connect = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<WhatsAppConnect>("/api/admin/whatsapp/connect");
      setStatus({ instanceName: r.instanceName, state: r.state, connected: r.state === "open" });
      if (r.state === "open") {
        setQr(null);
        setPairing(null);
      } else {
        setQr(r.qrCodeBase64);
        setPairing(r.pairingCode);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao conectar.");
    } finally {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post("/api/admin/whatsapp/disconnect");
      setQr(null);
      setPairing(null);
      await refreshStatus();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao desconectar.");
    } finally {
      setBusy(false);
    }
  };

  const connected = status?.connected ?? false;

  return (
    <div className="card">
      <div className="card-head">
        <h3>Conexão do WhatsApp</h3>
        {status &&
          (connected ? (
            <span className="badge badge-green">● Conectado</span>
          ) : (
            <span className="badge badge-amber">● Desconectado</span>
          ))}
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : (
          <div className="stack">
            <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
              Conecte o número de WhatsApp da barbearia escaneando o QR code pelo app — sem sair daqui.
            </p>

            {connected ? (
              <div className="row" style={{ gap: 12 }}>
                <span className="badge badge-green">WhatsApp conectado ✓</span>
                <button className="btn btn-danger btn-sm" onClick={disconnect} disabled={busy}>
                  {busy ? "…" : "Desconectar"}
                </button>
              </div>
            ) : qr ? (
              <>
                <img
                  src={qr}
                  alt="QR code do WhatsApp"
                  width={260}
                  height={260}
                  style={{ borderRadius: 12, border: "1px solid var(--border)", background: "#fff", padding: 8 }}
                />
                <span className="faint" style={{ fontSize: 13 }}>
                  No celular: WhatsApp → <b>Aparelhos conectados</b> → <b>Conectar um aparelho</b> → aponte para o QR.
                  Ele expira em ~40s; gere um novo se precisar.
                </span>
                {pairing && (
                  <div className="faint" style={{ fontSize: 13 }}>
                    Código de pareamento: <b>{pairing}</b>
                  </div>
                )}
                <button className="btn btn-ghost btn-sm" onClick={connect} disabled={busy} style={{ alignSelf: "flex-start" }}>
                  {busy ? "Gerando…" : "Gerar novo QR"}
                </button>
              </>
            ) : (
              <button className="btn btn-primary" onClick={connect} disabled={busy} style={{ alignSelf: "flex-start" }}>
                {busy ? "Conectando…" : "Conectar WhatsApp"}
              </button>
            )}

            {error && <div className="error">{error}</div>}
          </div>
        )}
      </div>
    </div>
  );
}

function ProfileCard() {
  const { data, loading, error, reload } = useApi<Barbershop>(() => api.get("/api/admin/barbershop"), []);
  const [name, setName] = useState("");
  const [instance, setInstance] = useState("");
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (data) {
      setName(data.name);
      setInstance(data.whatsappInstance ?? "");
    }
  }, [data]);

  const save = async () => {
    setSaving(true);
    setMsg(null);
    try {
      await api.put("/api/admin/barbershop", { name: name.trim(), whatsappInstance: instance.trim() || null });
      setMsg({ ok: true, text: "Perfil salvo." });
      reload();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : "Erro ao salvar." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Perfil da barbearia</h3>
        {data && <span className="badge badge-muted">/{data.slug}</span>}
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack">
            <div className="field">
              <label>Nome da barbearia</label>
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label>Instância do WhatsApp</label>
              <input className="input" value={instance} onChange={(e) => setInstance(e.target.value)} placeholder="ex.: minha-barbearia-wa" />
              <span className="faint" style={{ fontSize: 12 }}>Nome da instância na Evolution API por onde o bot envia as mensagens.</span>
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving || !name.trim()}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
              {msg && <span className={msg.ok ? "badge badge-green" : "error"}>{msg.text}</span>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function AdminsCard() {
  const { data, loading, error, reload } = useApi<AdminContact[]>(() => api.get("/api/admin/admins"), []);
  const [phone, setPhone] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const add = async () => {
    if (!phone.trim()) return;
    setBusy(true);
    setFormError(null);
    try {
      await api.post("/api/admin/admins", { phone: phone.trim(), name: name.trim() || null });
      setPhone("");
      setName("");
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
      reload();
    } catch {
      /* silencioso: recarrega estado real */
      reload();
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Administradores do WhatsApp</h3>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Telefones tratados como admin no WhatsApp (recebem avisos e usam os comandos de gestão).
        </p>
        <div className="row wrap" style={{ marginBottom: 16 }}>
          <input className="input grow" style={{ maxWidth: 220 }} placeholder="Telefone (com DDD)" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <input className="input grow" style={{ maxWidth: 200 }} placeholder="Nome (opcional)" value={name} onChange={(e) => setName(e.target.value)} />
          <button className="btn btn-primary btn-sm" onClick={add} disabled={busy || !phone.trim()}>Adicionar</button>
        </div>
        {formError && <div className="error" style={{ marginBottom: 12 }}>{formError}</div>}

        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : !data || data.length === 0 ? (
          <div className="faint">Nenhum admin cadastrado — o sistema usa os telefones da configuração global.</div>
        ) : (
          <div className="stack" style={{ gap: 8 }}>
            {data.map((a) => (
              <div key={a.id} className="row between" style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
                <div>
                  <div style={{ fontWeight: 550 }}>{a.phone}</div>
                  {a.name && <div className="faint" style={{ fontSize: 13 }}>{a.name}</div>}
                </div>
                <button className="btn btn-danger btn-sm" onClick={() => remove(a.id)}>Remover</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

const EMPTY_BOT: BotSettings = {
  displayName: "",
  addressText: "",
  addressMapsUrl: "",
  servicesText: "",
  scheduleUrl: "",
  instagramUrl: "",
  pixKey: "",
  pixCopyPaste: "",
  paymentInfo: "",
  openingTime: "",
  closingTime: "",
  workingDays: "",
  welcomeText: "",
  reminderText: "",
  reviewText: "",
  fidelityRewardText: "",
  cancellationPolicy: "",
  botPersona: "",
};

/** Bloco colapsável — progressive disclosure: só "Identidade" e "Pagamento" abrem por padrão. */
function Section({
  title,
  hint,
  open,
  children,
}: {
  title: string;
  hint?: string;
  open?: boolean;
  children: React.ReactNode;
}) {
  return (
    <details className="cfg-section" open={open}>
      <summary>
        <span>{title}</span>
        {hint && <span className="faint">{hint}</span>}
      </summary>
      <div className="stack" style={{ paddingTop: 14 }}>{children}</div>
    </details>
  );
}

function BotCard() {
  const { data, loading, error, reload } = useApi<BotSettings>(() => api.get("/api/admin/settings/bot"), []);
  const toast = useToast();
  const [form, setForm] = useState<BotSettings>(EMPTY_BOT);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (data) {
      const filled = { ...EMPTY_BOT };
      (Object.keys(EMPTY_BOT) as (keyof BotSettings)[]).forEach((k) => {
        filled[k] = data[k] ?? "";
      });
      setForm(filled);
    }
  }, [data]);

  const set = (k: keyof BotSettings, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setSaving(true);
    try {
      await api.put("/api/admin/settings/bot", form);
      toast.success("Configurações do bot salvas ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const field = (k: keyof BotSettings, label: string, placeholder?: string) => (
    <div className="field">
      <label>{label}</label>
      <input className="input" value={form[k] ?? ""} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} />
    </div>
  );

  const area = (k: keyof BotSettings, label: string, rows = 4, placeholder?: string) => (
    <div className="field">
      <label>{label}</label>
      <textarea
        className="input"
        rows={rows}
        value={form[k] ?? ""}
        placeholder={placeholder}
        onChange={(e) => set(k, e.target.value)}
        style={{ resize: "vertical", fontFamily: "inherit" }}
      />
    </div>
  );

  return (
    <div className="card">
      <div className="card-head">
        <h3>O que o bot fala</h3>
        <span className="badge badge-muted">WhatsApp</span>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ margin: "0 0 14px", fontSize: 14 }}>
          Tudo o que o cliente recebe. Campo vazio usa o padrão do sistema.
        </p>
        {loading ? (
          <div className="stack" style={{ gap: 10 }}>
            {[0, 1, 2].map((i) => <Skeleton key={i} h={38} />)}
          </div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack" style={{ gap: 10 }}>
            <Section title="Identidade" hint="como a barbearia se apresenta" open>
              {field("displayName", "Nome da barbearia", "Como o bot chama sua barbearia")}
              {field("addressText", "Endereço")}
              {field("addressMapsUrl", "Link do mapa")}
              {field("instagramUrl", "Instagram")}
            </Section>

            <Section title="Pagamento" hint="PIX enviado ao cliente" open>
              {field("pixKey", "Chave PIX", "CNPJ, e-mail, telefone ou aleatória")}
              {area("pixCopyPaste", "PIX copia-e-cola (opcional)", 3, "Cole aqui o código do seu banco")}
              {area("paymentInfo", "Formas de pagamento", 2, "Ex.: PIX, dinheiro, débito e crédito")}
            </Section>

            <Section title="Funcionamento" hint="horários que a IA oferece">
              <div className="form-grid">
                {field("openingTime", "Abre às", "09:00")}
                {field("closingTime", "Fecha às", "19:00")}
              </div>
              {field("workingDays", "Dias de funcionamento", "Seg a Sáb")}
              {area("cancellationPolicy", "Política de cancelamento/atraso", 2, "Ex.: cancele com 2h de antecedência")}
            </Section>

            <Section title="Serviços e preços" hint="tabela enviada no menu">
              {area("servicesText", "Tabela de serviços / preços", 5)}
              {field("scheduleUrl", "Link de agendamento (opcional)")}
            </Section>

            <Section title="Mensagens automáticas" hint="saudação, lembrete, avaliação">
              {area("welcomeText", "Saudação inicial", 2, "Primeira mensagem ao cliente novo")}
              {area("reminderText", "Lembrete (1h antes)", 2)}
              {area("reviewText", "Pedido de avaliação (após o corte)", 2)}
              {area("fidelityRewardText", "Recompensa de fidelidade (10 pontos)", 2)}
            </Section>

            <Section title="Personalidade da IA" hint="tom de voz">
              {area("botPersona", "Como a IA deve falar", 3, "Ex.: simpática e objetiva, trata o cliente por você, usa poucos emojis")}
            </Section>

            <div className="row" style={{ marginTop: 4 }}>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function Settings() {
  // Bloco único: um card só, com as caixas por assunto lado a lado.
  return (
    <Layout title="Configurações">
      <div className="card cfg-block">
        <div className="card-head">
          <h3>Sua barbearia no automático</h3>
          <span className="badge badge-muted">tudo em um lugar</span>
        </div>
        <div className="cfg-body">
          <div className="cfg-lane">
            <BotStatusCard />
            <BotCard />
          </div>
          <div className="cfg-lane">
            <WhatsAppCard />
            <ProfileCard />
            <AdminsCard />
          </div>
        </div>
      </div>
    </Layout>
  );
}
