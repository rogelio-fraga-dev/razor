import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Switch } from "../components/Modal";
import { Skeleton } from "../components/Skeleton";
import { ConfigWizard, WIZARD_STEPS } from "../components/ConfigWizard";
import { ConfigChecklist } from "../components/ConfigChecklist";
import { Tabs, panelId, tabId, type TabDef } from "../components/Tabs";
import { evaluateConfig } from "../lib/configCompleteness";
import { fillBot } from "../lib/botSettings";
import { matchSpeechMode, SPEECH_MODES } from "../lib/speechModes";
import { WhatsAppPairing } from "../components/WhatsAppPairing";
import { AdminsEditor } from "../components/AdminsEditor";
import { SiteEditor } from "../components/SiteEditor";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { getOnce } from "../lib/dedupe";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { api, ApiError } from "../api/client";
import { money } from "../lib/format";
import type { AdminContact, Barbershop, BotSettings, BotStatus, Goals } from "../api/types";

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
          <div className="error" role="alert">{error}</div>
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

            {toggleError && <div className="error" role="alert">{toggleError}</div>}
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
          <div className="error" role="alert">{error}</div>
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
          <div className="error" role="alert">{error}</div>
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


const WEEKDAY_SHORT = ["", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

/** Primeira linha não vazia, encurtada — o resumo mostra o gosto do texto, não o texto. */
function preview(text: string | null | undefined, max = 60): string | null {
  const line = (text ?? "").split("\n").map((l) => l.trim()).find((l) => l !== "");
  if (!line) return null;
  return line.length > max ? `${line.slice(0, max - 1)}…` : line;
}

const filled = (v: string | null | undefined) => (v ?? "").trim() !== "";

/**
 * Resumo do que o bot fala, por assunto, com "Editar" que abre o modal no passo certo.
 *
 * Substitui o antigo formulário "O que o bot fala", que duplicava campo a campo o assistente.
 * A edição mora num lugar só (o modal); aqui a tela reflete o que está salvo — e, como recarrega
 * ao fechar o modal, mostra o resultado sem F5.
 */
function BotSummaryCard({
  data,
  loading,
  error,
  onEdit,
}: {
  data: BotSettings | null;
  loading: boolean;
  error: string | null;
  onEdit: (step: number) => void;
}) {
  const persona = data?.botPersona ?? "";
  const modeId = matchSpeechMode(persona);
  const modeLabel = modeId
    ? SPEECH_MODES.find((m) => m.id === modeId)?.label ?? null
    : filled(persona)
      ? "Personalizado"
      : null;
  const days = [...(data?.workingWeekdays ?? [])].sort((a, b) => a - b).map((d) => WEEKDAY_SHORT[d]).join(", ");
  const hours = data?.openingTime && data?.closingTime ? `${data.openingTime}–${data.closingTime}` : null;
  const messagesCustom = [data?.welcomeText, data?.reminderText, data?.reviewText, data?.fidelityRewardText]
    .filter(filled).length;

  const rows: { step: number; title: string; value: string | null; empty: string }[] = [
    {
      step: WIZARD_STEPS.identity,
      title: "Identidade",
      value: [data?.displayName, data?.addressText].filter(filled).join(" · ") || null,
      empty: "Sem nome — o bot não sabe como chamar a barbearia",
    },
    {
      step: WIZARD_STEPS.hours,
      title: "Funcionamento",
      value: [hours, days].filter(Boolean).join(" · ") || null,
      empty: "Padrão: 09:00–19:00, Seg a Sáb",
    },
    {
      step: WIZARD_STEPS.services,
      title: "Serviços e preços",
      value: preview(data?.servicesText),
      empty: "Sem tabela — o bot não informa preços",
    },
    { step: WIZARD_STEPS.speech, title: "Modo de fala", value: modeLabel, empty: "Padrão (Parceiro)" },
    {
      step: WIZARD_STEPS.payment,
      title: "Pagamento",
      value: filled(data?.pixKey) ? `PIX: ${data?.pixKey}` : preview(data?.paymentInfo),
      empty: "Sem PIX — o bot não envia cobrança",
    },
    {
      step: WIZARD_STEPS.messages,
      title: "Mensagens automáticas",
      value: messagesCustom ? `${messagesCustom} de 4 personalizadas` : null,
      empty: "Textos padrão do sistema",
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h3>O que o bot fala</h3>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => onEdit(WIZARD_STEPS.identity)}
          disabled={loading || !!error}
        >
          Editar tudo
        </button>
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="stack" style={{ gap: 10 }}>
            {[0, 1, 2].map((i) => <Skeleton key={i} h={38} />)}
          </div>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : (
          <ul className="cfg-checklist" style={{ marginTop: 0 }}>
            {rows.map((r) => (
              <li key={r.title}>
                <div className="cfg-check-main">
                  <span className="cfg-check-label">{r.title}</span>
                  <span className="cfg-check-hint" style={r.value ? { color: "var(--text-muted)" } : undefined}>
                    {r.value ?? r.empty}
                  </span>
                </div>
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => onEdit(r.step)}
                  aria-label={`Editar: ${r.title}`}
                >
                  Editar
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/** Marca que o modal de configuração já se abriu sozinho (ou foi dispensado) nesta sessão. */
const WIZARD_SEEN_KEY = "razor.config_wizard_autoopened";

// sessionStorage pode lançar (aba anônima, site data bloqueado): sem o try a tela inteira caía.
function wizardSeen(): boolean {
  try {
    return sessionStorage.getItem(WIZARD_SEEN_KEY) === "1";
  } catch {
    return false;
  }
}
function markWizardSeen() {
  try {
    sessionStorage.setItem(WIZARD_SEEN_KEY, "1");
  } catch {
    /* sem storage: o limite vale só enquanto a tela estiver montada */
  }
}

// Abas de Configurações (UX_BLUEPRINT §4.7): some o corredor de cards.
const TABS: TabDef[] = [
  { id: "status", label: "Status" },
  { id: "whatsapp", label: "WhatsApp" },
  { id: "bot", label: "Bot & IA" },
  { id: "equipe", label: "Equipe & acesso" },
  { id: "barbearia", label: "Barbearia" },
  { id: "site", label: "Site" },
];
const TAB_PREFIX = "settings";

export function Settings() {
  // O modal de configuração é o lugar único de edição do bot. Ao fechar, a tela inteira
  // recarrega — status, QR, admins e textos já aparecem com o que foi salvo, sem F5.
  const [configVersion, setConfigVersion] = useState(0);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState(0);
  const [autoOpened, setAutoOpened] = useState(false);
  // A aba vive na URL (?tab=): dá para linkar direto ("Conectar WhatsApp" → ?tab=whatsapp) e o
  // voltar do navegador não joga a pessoa de volta para a primeira aba.
  const [params, setParams] = useSearchParams();
  const requested = params.get("tab");
  const tab = TABS.some((t) => t.id === requested) ? (requested as string) : "status";
  const setTab = (id: string) => {
    const next = new URLSearchParams(params);
    if (id === "status") next.delete("tab");
    else next.set("tab", id);
    setParams(next, { replace: true });
  };

  const bot = useApi<BotSettings>(() => api.get("/api/admin/settings/bot"), [configVersion]);
  // O slug vem do perfil da barbearia — é o endereço público do site.
  const shop = useApi<Barbershop>(() => api.get("/api/admin/barbershop"), [configVersion]);
  const status = useApi<BotStatus>(() => api.get("/api/admin/settings/bot/status"), [configVersion]);
  const admins = useApi<AdminContact[]>(() => api.get("/api/admin/admins"), [configVersion]);

  const refreshAll = () => setConfigVersion((v) => v + 1);

  const config = evaluateConfig(bot.data, status.data, admins.data);
  const shopSlug = shop.data?.slug ?? null;
  // Só decide depois que as três consultas responderam: avaliar com dados pela metade abriria o
  // modal na cara de quem já configurou tudo.
  const carregado = !bot.loading && !status.loading && !admins.loading && !bot.error;

  // Falta algo OBRIGATÓRIO: o modal abre sozinho — uma vez por sessão do navegador. Quem o
  // dispensa não o leva na cara de novo a cada visita; a lista de pendências segue cobrando.
  useEffect(() => {
    if (autoOpened || !carregado || config.ready) return;
    setAutoOpened(true);
    if (wizardSeen()) return;
    markWizardSeen();
    // Abre no primeiro obrigatório pendente: quem já pareou o WhatsApp cai direto no que falta.
    const firstMissing = config.items.find((i) => i.required && !i.done && i.step >= 0);
    setWizardStep(firstMissing?.step ?? 0);
    setWizardOpen(true);
  }, [carregado, config.ready, config.items, autoOpened]);

  const openWizardAt = (step: number) => {
    setWizardStep(Math.max(0, step));
    setWizardOpen(true);
  };

  const closeWizard = () => {
    markWizardSeen();
    setWizardOpen(false);
    refreshAll();
  };

  const panel = (id: string, children: React.ReactNode) =>
    tab === id ? (
      <div
        role="tabpanel"
        id={panelId(TAB_PREFIX, id)}
        aria-labelledby={tabId(TAB_PREFIX, id)}
        className="stack"
        style={{ gap: 18, marginTop: 18 }}
      >
        {children}
      </div>
    ) : null;

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
      {/* O que falta vem ANTES das abas enquanto houver obrigatório pendente: quem abre esta
          tela quer saber o que falta. Pronto, o cartão fica só na aba Status. */}
      {carregado && (!config.ready || tab === "status") && (
        <ConfigChecklist status={config} onGoToStep={openWizardAt} />
      )}

      <div style={{ marginTop: 18 }}>
        <Tabs tabs={TABS} active={tab} onChange={setTab} idPrefix={TAB_PREFIX} label="Seções de configuração" />
      </div>

      {panel("status", <BotStatusCard reloadKey={configVersion} />)}
      {panel("whatsapp", <WhatsAppCard reloadKey={configVersion} />)}
      {panel(
        "bot",
        <BotSummaryCard data={bot.data} loading={bot.loading} error={bot.error} onEdit={openWizardAt} />,
      )}
      {panel("equipe", <AdminsCard reloadKey={configVersion} />)}
      {panel(
        "barbearia",
        <div className="cfg-body">
          <div className="cfg-lane">
            <ProfileCard reloadKey={configVersion} />
          </div>
          <div className="cfg-lane">
            <GoalsCard reloadKey={configVersion} />
          </div>
        </div>,
      )}
      {panel("site", <SiteEditor slug={shopSlug} />)}

      {wizardOpen && <ConfigWizard initial={fillBot(bot.data)} startAt={wizardStep} onClose={closeWizard} />}
    </Layout>
  );
}
