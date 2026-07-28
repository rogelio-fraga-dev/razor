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
import { useEffect, useRef, useState, type ReactNode } from "react";
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
  startAt = 0,
}: {
  initial: BotSettings;
  onClose: () => void;
  onSaved: () => void;
  /** Passo inicial — a lista de pendências abre o assistente direto no item que falta. */
  startAt?: number;
}) {
  const toast = useToast();
  const whatsapp = useWhatsAppConnection();
  const [draft, setDraft] = useState<BotSettings>(initial);
  const [step, setStep] = useState(startAt);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);

  // Ao trocar de passo, o foco vai para o título do passo novo.
  //
  // Sem isso o foco ficava no botão "Próximo" (que não é remontado) e o conteúdo inteiro trocava
  // sem aviso: num assistente de 8 passos, quem usa leitor de tela não percebia que a tela mudou.
  // Focar um heading também faz o leitor anunciar o título do passo sem precisar de live region
  // para o conteúdo principal.
  useEffect(() => {
    headingRef.current?.focus();
  }, [step]);

  const set = (k: BotTextKey, v: string) => {
    setDirty(true);
    setDraft((d) => ({ ...d, [k]: v }));
  };

  /**
   * Fechar com preenchimento não salvo pede confirmação.
   *
   * O assistente só grava no último passo, e o clique fora do diálogo fechava tudo em silêncio —
   * sete passos preenchidos evaporavam. É o que o UX_BLUEPRINT chama de "sair no meio não perde
   * nada"; enquanto o salvamento por passo não existe, confirmar é o mínimo honesto.
   */
  const confirmClose = () => {
    if (!dirty) return true;
    return window.confirm(
      "Você preencheu campos que ainda não foram salvos. Fechar o assistente agora descarta essas alterações. Deseja sair mesmo assim?",
    );
  };

  const field = (k: BotTextKey, label: string, placeholder?: string, required = false) => (
    <div className="field">
      <label>
        {label}
        {/* Obrigatório precisa estar dito no campo. Antes o único sinal era o "Próximo"
            desabilitado — o usuário via um botão morto sem saber o que faltava. */}
        {required && <span className="field-required"> · obrigatório</span>}
        <input
          className="input"
          value={draft[k] ?? ""}
          placeholder={placeholder}
          onChange={(e) => set(k, e.target.value)}
          aria-required={required || undefined}
        />
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
          {field("displayName", "Nome da barbearia", "Como o bot chama sua barbearia", true)}
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

  // `startAt` vem de fora e pode apontar para um passo que não existe mais se a lista mudar;
  // limitar aqui evita a tela em branco por índice fora da faixa.
  const current = steps[Math.min(Math.max(step, 0), steps.length - 1)];
  const isLast = step >= steps.length - 1;
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
      title="Configurar sua barbearia"
      size="wide"
      onClose={onClose}
      onRequestClose={confirmClose}
      footer={
        <div className="row between" style={{ width: "100%" }}>
          <button className="btn" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0 || saving}>
            Voltar
          </button>
          <div className="row" style={{ gap: 10 }}>
            {!isLast && (
              // "Pular por agora": nenhum passo pode aprisionar o dono. A lista de pendências na
              // tela de Configurações continua cobrando o que ficou para trás.
              <button className="btn btn-ghost" onClick={() => setStep((s) => s + 1)} disabled={saving}>
                Pular por agora
              </button>
            )}
            <button className="btn btn-primary" onClick={next} disabled={!canAdvance || saving}>
              {saving ? "Salvando…" : isLast ? "Concluir" : "Próximo"}
            </button>
          </div>
        </div>
      }
    >
      <div className="wiz">
        {/* Trilho de passos: rótulos, não pontinhos.
            Com 8 passos e entrada direta a partir da lista de pendências, oito bolinhas mudas
            não dizem onde a pessoa está nem quanto falta. */}
        <nav className="wiz-rail" aria-label="Etapas da configuração">
          {steps.map((s, i) => (
            <button
              key={s.title}
              type="button"
              className={`wiz-step ${i === step ? "active" : ""} ${i < step ? "done" : ""}`}
              onClick={() => setStep(i)}
              aria-current={i === step ? "step" : undefined}
            >
              <span className="wiz-step-n mono">{String(i + 1).padStart(2, "0")}</span>
              <span className="wiz-step-label">{s.title}</span>
            </button>
          ))}
        </nav>

        <div className="wiz-pane">
          {/* role="status" garante o anúncio mesmo para quem lê em modo virtual, sem depender
              da mudança de foco. */}
          <div className="wiz-progress mono" role="status" aria-live="polite">
            PASSO {step + 1} DE {steps.length} · {current.title}
          </div>

          <h4 ref={headingRef} tabIndex={-1} className="wiz-title">
            {current.title}
          </h4>
          <p className="wiz-hint">{current.hint}</p>
          {current.render()}
        </div>
      </div>
    </Modal>
  );
}
