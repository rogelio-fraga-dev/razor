import { WeekdayPicker } from "../components/WeekdayPicker";
import { useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { Switch } from "../components/Modal";
import { Skeleton } from "../components/Skeleton";
import { SpeechModePicker } from "../components/SpeechModePicker";
import { ConfigWizard } from "../components/ConfigWizard";
import { ConfigChecklist } from "../components/ConfigChecklist";
import { evaluateConfig } from "../lib/configCompleteness";
import { WhatsAppPairing } from "../components/WhatsAppPairing";
import { AdminsEditor } from "../components/AdminsEditor";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { getOnce } from "../lib/dedupe";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { api, ApiError } from "../api/client";
import { money } from "../lib/format";
import type { AdminContact, Barbershop, BotSettings, BotStatus, Goals, BotTextKey, Weekday } from "../api/types";

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
    () => getOnce("/api/admin/settings/bot/status"),
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
  const toast = useToast();
  const [name, setName] = useState("");
  const [instance, setInstance] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (data) {
      setName(data.name);
      setInstance(data.whatsappInstance ?? "");
    }
  }, [data]);

  // Sucesso é toast, como em todos os outros cards. Este era o único que deixava um "Perfil
  // salvo." fixo na tela — e, por herdar `.badge`, saía em CAIXA ALTA por acidente de cascata.
  const save = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      await api.put("/api/admin/barbershop", { name: name.trim(), whatsappInstance: instance.trim() || null });
      toast.success("Perfil salvo ✓");
      reload();
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : "Erro ao salvar.");
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
              <label>Nome da barbearia
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            </div>
            <div className="field">
              <label>Instância do WhatsApp
              <input className="input" value={instance} onChange={(e) => setInstance(e.target.value)} placeholder="ex.: minha-barbearia-wa" />
            </label>
              <span className="faint" style={{ fontSize: 12 }}>Nome da instância na Evolution API por onde o bot envia as mensagens.</span>
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving || !name.trim()}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
              {saveError && <span className="error" role="alert">{saveError}</span>}
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
  workingWeekdays: [1, 2, 3, 4, 5, 6],
  welcomeText: "",
  reminderText: "",
  reviewText: "",
  fidelityRewardText: "",
  cancellationPolicy: "",
  botPersona: "",
};

/** Bloco colapsável — progressive disclosure: só "Identidade" e "Pagamento" abrem por padrão. */
function Section({
  index,
  title,
  hint,
  open,
  done,
  children,
}: {
  /** Numeração editorial (01/, 02/…) — dá ritmo a seis seções hoje indistinguíveis. */
  index?: number;
  title: string;
  hint?: string;
  open?: boolean;
  /** Preenchida? Sem isto, "Pagamento" cheio e "Pagamento" vazio são visualmente idênticos. */
  done?: boolean;
  children: React.ReactNode;
}) {
  return (
    <details className="cfg-section" open={open}>
      <summary>
        {index !== undefined && (
          <span className="cfg-section-n mono" aria-hidden="true">
            {String(index).padStart(2, "0")}/
          </span>
        )}
        <span>{title}</span>
        {done !== undefined && (
          <span
            className={`cfg-section-dot ${done ? "done" : ""}`}
            aria-label={done ? "preenchida" : "em branco"}
          />
        )}
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
              <label>Meta de faturamento mensal (R$)
              <input
                className="input"
                inputMode="decimal"
                value={value}
                placeholder="ex.: 20000"
                onChange={(e) => setValue(e.target.value)}
              />
            </label>
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
  (Object.keys(EMPTY_BOT) as BotTextKey[]).forEach((k) => {
    filled[k] = data[k] ?? "";
  });
  // A lista de dias nao pode virar string vazia: vazia significaria "fechado todo dia".
  filled.workingWeekdays = data.workingWeekdays?.length ? data.workingWeekdays : EMPTY_BOT.workingWeekdays;
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

  // workingWeekdays e a unica chave que nao e string: separar evita que os helpers de texto
  // (field/area) aceitem uma lista de dias por engano.
  const set = (k: BotTextKey, v: string) => setForm((f) => ({ ...f, [k]: v }));
  const setWeekdays = (days: Weekday[]) => setForm((f) => ({ ...f, workingWeekdays: days }));

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

  const field = (k: BotTextKey, label: string, placeholder?: string) => (
    <div className="field">
      <label>{label}
      <input className="input" value={form[k] ?? ""} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} />
            </label>
    </div>
  );

  const area = (k: BotTextKey, label: string, rows = 4, placeholder?: string) => (
    <div className="field">
      <label>{label}
      <textarea
        className="input"
        rows={rows}
        value={form[k] ?? ""}
        placeholder={placeholder}
        onChange={(e) => set(k, e.target.value)}
        style={{ resize: "vertical", fontFamily: "inherit" }}
      />
            </label>
    </div>
  );

  return (
    <div className="card">
      <div className="card-head">
        <h3>O que o bot fala</h3>
        <button className="btn btn-ghost btn-sm" onClick={onOpenWizard} disabled={loading || !!error}>
          Abrir assistente
        </button>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ margin: "0 0 14px", fontSize: 14 }}>
          Campo vazio usa o padrão do sistema.
        </p>
        {loading ? (
          <div className="stack" style={{ gap: 10 }}>
            {[0, 1, 2].map((i) => <Skeleton key={i} h={38} />)}
          </div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack" style={{ gap: 10 }}>
            <Section index={1} title="Identidade" hint="como a barbearia se apresenta" open done={!!(form.displayName ?? "").trim()}>
              {field("displayName", "Nome da barbearia", "Como o bot chama sua barbearia")}
              {field("addressText", "Endereço")}
              {field("addressMapsUrl", "Link do mapa")}
              {field("instagramUrl", "Instagram")}
            </Section>

            <Section index={2} title="Pagamento" hint="PIX enviado ao cliente" open done={!!(form.pixKey ?? "").trim()}>
              {field("pixKey", "Chave PIX", "CNPJ, e-mail, telefone ou aleatória")}
              {area("pixCopyPaste", "PIX copia-e-cola (opcional)", 3, "Cole aqui o código do seu banco")}
              {area("paymentInfo", "Formas de pagamento", 2, "Ex.: PIX, dinheiro, débito e crédito")}
            </Section>

            <Section index={3} title="Funcionamento" hint="horários que a IA oferece" done={(form.workingWeekdays ?? []).length > 0}>
              <div className="form-grid">
                {field("openingTime", "Abre às", "09:00")}
                {field("closingTime", "Fecha às", "19:00")}
              </div>
              <WeekdayPicker
                value={form.workingWeekdays ?? []}
                onChange={setWeekdays}
              />
              {area("cancellationPolicy", "Política de cancelamento/atraso", 2, "Ex.: cancele com 2h de antecedência")}
            </Section>

            <Section index={4} title="Serviços e preços" hint="tabela enviada no menu" done={!!(form.servicesText ?? "").trim()}>
              {area("servicesText", "Tabela de serviços / preços", 5)}
              {field("scheduleUrl", "Link de agendamento (opcional)")}
            </Section>

            <Section index={5} title="Mensagens automáticas" hint="saudação, lembrete, avaliação" done={!!(form.welcomeText ?? "").trim()}>
              {area("welcomeText", "Saudação inicial", 2, "Primeira mensagem ao cliente novo")}
              {area("reminderText", "Lembrete (1h antes)", 2)}
              {area("reviewText", "Pedido de avaliação (após o corte)", 2)}
              {area("fidelityRewardText", "Recompensa de fidelidade (10 pontos)", 2)}
            </Section>

            <Section index={6} title="Personalidade da IA" hint="tom de voz" done={!!(form.botPersona ?? "").trim()}>
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

/** Marca que o assistente já se abriu sozinho nesta sessão do navegador. */
const WIZARD_SEEN_KEY = "razor.config_wizard_autoopened";

export function Settings() {
  // Bloco único: um card só, com as caixas por assunto lado a lado.
  // O assistente é o tutorial da tela: passa por todas as configurações e, ao fechar, faz a
  // tela inteira recarregar — os cards atrás já aparecem com o que foi preenchido.
  const [configVersion, setConfigVersion] = useState(0);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState(0);
  const [autoOpened, setAutoOpened] = useState(false);

  const bot = useApi<BotSettings>(() => api.get("/api/admin/settings/bot"), [configVersion]);
  const status = useApi<BotStatus>(() => api.get("/api/admin/settings/bot/status"), [configVersion]);
  const admins = useApi<AdminContact[]>(() => api.get("/api/admin/admins"), [configVersion]);

  const refreshAll = () => setConfigVersion((v) => v + 1);

  const config = evaluateConfig(bot.data, status.data, admins.data);
  // Só decide depois que as três consultas responderam: avaliar com dados pela metade abriria o
  // assistente na cara de quem já configurou tudo.
  const carregado = !bot.loading && !status.loading && !admins.loading && !bot.error;

  // Falta algo OBRIGATÓRIO: o assistente abre sozinho — uma vez por sessão do navegador.
  //
  // O gatilho antigo era "displayName vazio": uma barbearia com nome preenchido e o WhatsApp
  // desconectado, sem PIX e sem tabela de preços passava por pronta, e ninguém avisava o dono.
  //
  // O limite por sessão importa tanto quanto o gatilho. Sem ele, quem entra em Configurações
  // para mexer numa coisa só levaria o assistente na cara toda vez — e um tutorial que insiste
  // vira obstáculo. Depois da primeira vez, a lista de pendências acima já diz o que falta.
  useEffect(() => {
    if (autoOpened || !carregado || config.ready) return;
    if (sessionStorage.getItem(WIZARD_SEEN_KEY) === "1") {
      setAutoOpened(true);
      return;
    }
    sessionStorage.setItem(WIZARD_SEEN_KEY, "1");
    setWizardStep(0);
    setWizardOpen(true);
    setAutoOpened(true);
  }, [carregado, config.ready, autoOpened]);

  const openWizardAt = (step: number) => {
    setWizardStep(Math.max(0, step));
    setWizardOpen(true);
  };

  const closeWizard = () => {
    setWizardOpen(false);
    refreshAll();
  };

  return (
    <Layout
      title="Configurações"
      actions={
        <button
          className="btn btn-primary btn-sm"
          onClick={() => openWizardAt(0)}
          disabled={bot.loading || !!bot.error}
        >
          {config.ready ? "Revisar configuração" : "Continuar configuração"}
        </button>
      }
    >
      {/* O que falta vem ANTES dos campos: quem abre esta tela quer saber o que está pendente,
          não caçar o campo vazio entre trinta preenchidos. */}
      {carregado && <ConfigChecklist status={config} onGoToStep={openWizardAt} />}

      {/* Sem o card externo: os cards abaixo já são caixas, e envolvê-los em mais uma criava
          quatro níveis de moldura — o input acabava a 112px da borda da tela, atravessando cinco
          bordas de 1px. O CTA subiu para a barra de ações da tela, onde ele é procurado. */}
      <div className="cfg-body">
        <div className="cfg-lane">
          <BotStatusCard reloadKey={configVersion} />
          <BotCard
            data={bot.data}
            loading={bot.loading}
            error={bot.error}
            onSaved={refreshAll}
            onOpenWizard={() => openWizardAt(0)}
          />
        </div>
        <div className="cfg-lane">
          <WhatsAppCard reloadKey={configVersion} />
          <ProfileCard reloadKey={configVersion} />
          <GoalsCard reloadKey={configVersion} />
          <AdminsCard reloadKey={configVersion} />
        </div>
      </div>

      {wizardOpen && (
        <ConfigWizard initial={fillBot(bot.data)} startAt={wizardStep} onClose={closeWizard} onSaved={refreshAll} />
      )}
    </Layout>
  );
}
