// Assistente de configuração do bot: passo a passo guiado que substitui a "lista seca".
// Começa a partir das configs já salvas e sobrepõe as edições, então o PUT (que troca o
// registro inteiro) nunca apaga campos fora do wizard (ex.: mensagens automáticas). Ao
// concluir, o back manda o resumo de boas-vindas ao dono (WelcomeBriefService).
import { useState, type ReactNode } from "react";
import { Modal } from "./Modal";
import { SpeechModePicker } from "./SpeechModePicker";
import { useToast } from "./Toast";
import { api, ApiError } from "../api/client";
import type { BotSettings } from "../api/types";

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
  const [draft, setDraft] = useState<BotSettings>(initial);
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);

  const set = (k: keyof BotSettings, v: string) => setDraft((d) => ({ ...d, [k]: v }));

  const field = (k: keyof BotSettings, label: string, placeholder?: string) => (
    <div className="field">
      <label>{label}</label>
      <input className="input" value={draft[k] ?? ""} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} />
    </div>
  );

  const area = (k: keyof BotSettings, label: string, rows = 3, placeholder?: string) => (
    <div className="field">
      <label>{label}</label>
      <textarea
        className="input"
        rows={rows}
        value={draft[k] ?? ""}
        placeholder={placeholder}
        onChange={(e) => set(k, e.target.value)}
        style={{ resize: "vertical", fontFamily: "inherit" }}
      />
    </div>
  );

  const steps: Step[] = [
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
          {field("workingDays", "Dias de funcionamento", "Seg a Sáb")}
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
            Campos deixados em branco usam o padrão do sistema. Você pode ajustar tudo depois,
            campo a campo, aqui nas Configurações.
          </p>
        )}
      </div>
    </Modal>
  );
}
