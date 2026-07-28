// Assistente de configuração do bot: o tutorial que leva o dono por TODAS as configurações,
// numa janela só — conexão do WhatsApp, número do admin, identidade, funcionamento, serviços,
// modo de fala, pagamento e mensagens automáticas.
//
// Duas regras que valem para o fluxo inteiro:
//  1. Começa a partir das configs já salvas e sobrepõe as edições, então o PUT (que troca o
//     registro inteiro) nunca apaga campo que o assistente não mostra.
//  2. Ao concluir, o back manda o resumo de boas-vindas ao dono (WelcomeBriefService) e a tela
//     de Configurações atrás recarrega, já mostrando tudo que foi preenchido.
import { WeekdayPicker } from "./WeekdayPicker";
import { useState, type ReactNode } from "react";
import { Modal } from "./Modal";
import { SpeechModePicker } from "./SpeechModePicker";
import { WhatsAppPairing } from "./WhatsAppPairing";
import { AdminsEditor } from "./AdminsEditor";
import { useToast } from "./Toast";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { api, ApiError } from "../api/client";
import type { BotSettings, BotTextKey } from "../api/types";

interface Step {
  title: string;
  hint: string;
  render: () => ReactNode;
  /** Bloqueia o "Próximo" enquanto falso (só o essencial). */
  valid?: () => boolean;
}

export function ConfigWizard({
  initial,
  onClose,
  onSaved,
}: {
  initial: BotSettings;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const whatsapp = useWhatsAppConnection();
  const [draft, setDraft] = useState<BotSettings>(initial);
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);

  const set = (k: BotTextKey, v: string) => setDraft((d) => ({ ...d, [k]: v }));

  const field = (k: BotTextKey, label: string, placeholder?: string) => (
    <div className="field">
      <label>{label}
      <input className="input" value={draft[k] ?? ""} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} />
            </label>
    </div>
  );

  const area = (k: BotTextKey, label: string, rows = 3, placeholder?: string) => (
    <div className="field">
      <label>{label}
      <textarea
        className="input"
        rows={rows}
        value={draft[k] ?? ""}
        placeholder={placeholder}
        onChange={(e) => set(k, e.target.value)}
        style={{ resize: "vertical", fontFamily: "inherit" }}
      />
            </label>
    </div>
  );

  const steps: Step[] = [
    {
      title: "Conectar o WhatsApp",
      hint: "O número da barbearia — é ele que o cliente vê e que o bot usa para responder.",
      render: () => <WhatsAppPairing conn={whatsapp} />,
    },
    {
      title: "Seu número de admin",
      hint: "O seu número pessoal, por onde você comanda o bot e recebe os avisos.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          <AdminsEditor />
          <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
            São dois números diferentes: o <b>do bot</b> (o da barbearia, que você acabou de parear)
            e o <b>seu</b>, cadastrado aqui. Se você usar o mesmo número nos dois, o WhatsApp marca
            suas mensagens como próprias e o bot não consegue receber seus comandos.
          </p>
        </div>
      ),
    },
    {
      title: "Identidade",
      hint: "Como sua barbearia se apresenta ao cliente.",
      valid: () => (draft.displayName ?? "").trim() !== "",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          {field("displayName", "Nome da barbearia", "Como o bot chama sua barbearia")}
          {field("addressText", "Endereço")}
          {field("addressMapsUrl", "Link do mapa (opcional)")}
          {field("instagramUrl", "Instagram (opcional)")}
        </div>
      ),
    },
    {
      title: "Funcionamento",
      hint: "Os horários que a IA pode oferecer.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          <div className="form-grid">
            {field("openingTime", "Abre às", "09:00")}
            {field("closingTime", "Fecha às", "19:00")}
          </div>
          <WeekdayPicker
            value={draft.workingWeekdays ?? []}
            onChange={(days) => setDraft((d) => ({ ...d, workingWeekdays: days }))}
          />
          {area("cancellationPolicy", "Política de cancelamento/atraso", 2, "Ex.: cancele com 2h de antecedência")}
        </div>
      ),
    },
    {
      title: "Serviços e preços",
      hint: "A tabela que o bot mostra no menu.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          {area("servicesText", "Tabela de serviços / preços", 5, "Ex.:\nCorte — R$ 40\nBarba — R$ 30\nCorte + Barba — R$ 60")}
          {field("scheduleUrl", "Link de agendamento (opcional)")}
        </div>
      ),
    },
    {
      title: "Modo de fala",
      hint: "O tom de voz do seu assistente.",
      render: () => <SpeechModePicker value={draft.botPersona ?? ""} onChange={(v) => set("botPersona", v)} />,
    },
    {
      title: "Pagamento",
      hint: "O PIX que o bot envia ao cliente.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          {field("pixKey", "Chave PIX", "CNPJ, e-mail, telefone ou aleatória")}
          {area("pixCopyPaste", "PIX copia-e-cola (opcional)", 3, "Cole aqui o código do seu banco")}
          {area("paymentInfo", "Formas de pagamento", 2, "Ex.: PIX, dinheiro, débito e crédito")}
        </div>
      ),
    },
    {
      title: "Mensagens automáticas",
      hint: "O que o bot manda sozinho. Campo vazio usa o texto padrão do sistema.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          {area("welcomeText", "Saudação inicial", 2, "Primeira mensagem ao cliente novo")}
          {area("reminderText", "Lembrete (1h antes)", 2)}
          {area("reviewText", "Pedido de avaliação (após o corte)", 2)}
          {area("fidelityRewardText", "Recompensa de fidelidade (10 pontos)", 2)}
        </div>
      ),
    },
  ];

  const current = steps[step];
  const isLast = step === steps.length - 1;
  const canAdvance = current.valid ? current.valid() : true;

  const finish = async () => {
    setSaving(true);
    try {
      await api.put("/api/admin/settings/bot", draft);
      toast.success("Configuração concluída ✓ Um resumo foi enviado ao seu WhatsApp.");
      onSaved();
      onClose();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar. Tente de novo.");
    } finally {
      setSaving(false);
    }
  };

  const next = () => (isLast ? finish() : setStep((s) => s + 1));

  return (
    <Modal
      title="Assistente de configuração"
      onClose={onClose}
      footer={
        <div className="row between" style={{ width: "100%" }}>
          <button className="btn" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0 || saving}>
            Voltar
          </button>
          <button className="btn btn-primary" onClick={next} disabled={!canAdvance || saving}>
            {saving ? "Salvando…" : isLast ? "Concluir" : "Próximo"}
          </button>
        </div>
      }
    >
      <div className="stack" style={{ gap: 16 }}>
        {/* Progresso */}
        <div>
          <div className="wiz-dots" aria-hidden="true">
            {steps.map((_, i) => (
              <span key={i} className={`wiz-dot ${i === step ? "active" : ""} ${i < step ? "done" : ""}`} />
            ))}
          </div>
          <div className="mono" style={{ fontSize: 11, letterSpacing: "0.08em", color: "var(--text-faint)", marginTop: 8 }}>
            PASSO {step + 1} DE {steps.length}
          </div>
        </div>

        <div>
          <h4 style={{ margin: "0 0 2px", fontSize: 17 }}>{current.title}</h4>
          <p className="muted" style={{ margin: "0 0 14px", fontSize: 13.5 }}>{current.hint}</p>
          {current.render()}
        </div>

        {isLast && (
          <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
            Ao concluir, esta janela fecha e a tela de Configurações mostra tudo que você preencheu —
            onde dá para ajustar campo a campo quando quiser.
          </p>
        )}
      </div>
    </Modal>
  );
}
