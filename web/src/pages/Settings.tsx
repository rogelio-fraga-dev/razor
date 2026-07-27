import { useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { Switch } from "../components/Modal";
import { Skeleton } from "../components/Skeleton";
import { SpeechModePicker } from "../components/SpeechModePicker";
import { ConfigWizard } from "../components/ConfigWizard";
import { WhatsAppPairing } from "../components/WhatsAppPairing";
import { AdminsEditor } from "../components/AdminsEditor";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { api, ApiError } from "../api/client";
import { money } from "../lib/format";
import type { Barbershop, BotSettings, BotStatus, Goals } from "../api/types";

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
function BotStatusCard({ reloadKey }: { reloadKey: number }) {
  const { data, loading, error, reload } = useApi<BotStatus>(
    () => api.get("/api/admin/settings/bot/status"),
    [reloadKey],
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

function WhatsAppCard({ reloadKey }: { reloadKey: number }) {
  const conn = useWhatsAppConnection();
  const { refreshStatus } = conn;

  // Reconsulta quando o assistente fecha — o pareamento pode ter sido feito lá dentro.
  useEffect(() => {
    if (reloadKey > 0) refreshStatus();
  }, [reloadKey, refreshStatus]);

  return (
    <div className="card">
      <div className="card-head">
        <h3>Conexão do WhatsApp</h3>
        {conn.status &&
          (conn.connected ? (
            <span className="badge badge-green">● Conectado</span>
          ) : (
            <span className="badge badge-amber">● Desconectado</span>
          ))}
      </div>
      <div className="card-pad">
        <WhatsAppPairing conn={conn} />
      </div>
    </div>
  );
}

function ProfileCard({ reloadKey }: { reloadKey: number }) {
  const { data, loading, error, reload } = useApi<Barbershop>(
    () => api.get("/api/admin/barbershop"),
    [reloadKey],
  );
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

function AdminsCard({ reloadKey }: { reloadKey: number }) {
  return (
    <div className="card">
      <div className="card-head">
        <h3>Administradores do WhatsApp</h3>
      </div>
      <div className="card-pad">
        <AdminsEditor reloadKey={reloadKey} />
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

/** Meta de faturamento do mês (V19). É o alvo que o painel usa para calcular o progresso. */
function GoalsCard({ reloadKey }: { reloadKey: number }) {
  const { data, loading, error, reload } = useApi<Goals>(
    () => api.get("/api/admin/settings/goals"),
    [reloadKey],
  );
  const toast = useToast();
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setValue(data?.monthlyRevenueGoal != null ? String(data.monthlyRevenueGoal) : "");
  }, [data]);

  const save = async () => {
    const parsed = Number(value.replace(",", "."));
    if (value.trim() !== "" && (!Number.isFinite(parsed) || parsed <= 0)) {
      toast.error("Informe um valor maior que zero — ou deixe vazio para não usar meta.");
      return;
    }
    setSaving(true);
    try {
      await api.put("/api/admin/settings/goals", {
        monthlyRevenueGoal: value.trim() === "" ? null : parsed,
      });
      toast.success(value.trim() === "" ? "Meta removida." : "Meta do mês salva ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar a meta.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Meta do mês</h3>
        {data?.monthlyRevenueGoal != null && (
          <span className="badge badge-green">{money(data.monthlyRevenueGoal)}</span>
        )}
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Quanto você quer faturar por mês. O painel compara com o faturamento real e mostra o
          progresso. Deixe vazio para não acompanhar meta.
        </p>
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack">
            <div className="field">
              <label>Meta de faturamento mensal (R$)</label>
              <input
                className="input"
                inputMode="decimal"
                value={value}
                placeholder="ex.: 20000"
                onChange={(e) => setValue(e.target.value)}
              />
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar meta"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/** Preenche o formulário a partir do que veio do backend (campo nulo vira string vazia). */
function fillBot(data: BotSettings | null): BotSettings {
  const filled = { ...EMPTY_BOT };
  if (!data) return filled;
  (Object.keys(EMPTY_BOT) as (keyof BotSettings)[]).forEach((k) => {
    filled[k] = data[k] ?? "";
  });
  return filled;
}

function BotCard({
  data,
  loading,
  error,
  onSaved,
  onOpenWizard,
}: {
  data: BotSettings | null;
  loading: boolean;
  error: string | null;
  onSaved: () => void;
  onOpenWizard: () => void;
}) {
  const toast = useToast();
  const [form, setForm] = useState<BotSettings>(EMPTY_BOT);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setForm(fillBot(data));
  }, [data]);

  const set = (k: keyof BotSettings, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setSaving(true);
    try {
      await api.put("/api/admin/settings/bot", form);
      toast.success("Configurações do bot salvas ✓");
      onSaved();
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
        <button className="btn btn-primary btn-sm" onClick={onOpenWizard} disabled={loading || !!error}>
          ✨ Abrir assistente
        </button>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ margin: "0 0 14px", fontSize: 14 }}>
          Use o <strong>assistente</strong> para configurar passo a passo, ou ajuste campo a
          campo abaixo. Campo vazio usa o padrão do sistema.
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
              <SpeechModePicker value={form.botPersona ?? ""} onChange={(v) => set("botPersona", v)} />
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
  // O assistente é o tutorial da tela: passa por todas as configurações e, ao fechar, faz a
  // tela inteira recarregar — os cards atrás já aparecem com o que foi preenchido.
  const [configVersion, setConfigVersion] = useState(0);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [autoOpened, setAutoOpened] = useState(false);

  const bot = useApi<BotSettings>(() => api.get("/api/admin/settings/bot"), [configVersion]);

  const refreshAll = () => setConfigVersion((v) => v + 1);

  // Barbearia ainda sem identidade configurada: o assistente abre sozinho, uma vez por visita.
  useEffect(() => {
    if (!autoOpened && bot.data && !(bot.data.displayName ?? "").trim()) {
      setWizardOpen(true);
      setAutoOpened(true);
    }
  }, [bot.data, autoOpened]);

  const closeWizard = () => {
    setWizardOpen(false);
    refreshAll();
  };

  return (
    <Layout title="Configurações">
      <div className="card cfg-block">
        <div className="card-head">
          <h3>Sua barbearia no automático</h3>
          <div className="row" style={{ gap: 10 }}>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => setWizardOpen(true)}
              disabled={bot.loading || !!bot.error}
            >
              ✨ Abrir assistente
            </button>
            <span className="badge badge-muted">tudo em um lugar</span>
          </div>
        </div>
        <div className="cfg-body">
          <div className="cfg-lane">
            <BotStatusCard reloadKey={configVersion} />
            <BotCard
              data={bot.data}
              loading={bot.loading}
              error={bot.error}
              onSaved={refreshAll}
              onOpenWizard={() => setWizardOpen(true)}
            />
          </div>
          <div className="cfg-lane">
            <WhatsAppCard reloadKey={configVersion} />
            <ProfileCard reloadKey={configVersion} />
            <GoalsCard reloadKey={configVersion} />
            <AdminsCard reloadKey={configVersion} />
          </div>
        </div>
      </div>

      {wizardOpen && (
        <ConfigWizard initial={fillBot(bot.data)} onClose={closeWizard} onSaved={refreshAll} />
      )}
    </Layout>
  );
}
