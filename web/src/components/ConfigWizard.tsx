// Modal único de configuração do bot: leva o dono por TODAS as configurações, numa janela só —
// conexão do WhatsApp (QR), número(s) de admin, identidade, funcionamento, serviços e preços,
// modo de fala, pagamento e mensagens automáticas.
//
// Regras que valem para o fluxo inteiro:
//  1. Começa a partir das configs já salvas e sobrepõe as edições, então o PUT (que troca o
//     registro inteiro) nunca apaga campo que o modal não mostra.
//  2. Salva a cada passo: avançar, voltar, pular ou clicar no trilho grava o que mudou; fechar
//     com alteração pendente também grava antes de sair. Sair no meio não perde nada.
//  3. Os saves intermediários vão com `?welcome=false`; só o "Concluir" dispara o resumo de
//     boas-vindas no WhatsApp do dono (WelcomeBriefService) — "Tudo pronto!" no meio do fluxo,
//     antes de serviços e PIX, seria mentira.
//  4. Ao fechar, a tela de Configurações atrás recarrega e já mostra tudo que foi salvo.
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Modal } from "./Modal";
import { WhatsAppPairing } from "./WhatsAppPairing";
import { AdminsEditor } from "./AdminsEditor";
import {
  HoursFields,
  IdentityFields,
  MessagesFields,
  PaymentFields,
  ServicesFields,
  SpeechModeFields,
} from "./BotConfigFields";
import { useToast } from "./Toast";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { api, ApiError } from "../api/client";
import type { BotSettings } from "../api/types";

interface Step {
  title: string;
  hint: string;
  render: () => ReactNode;
  /** Bloqueia o "Próximo" enquanto falso (só o essencial). "Pular por agora" continua livre. */
  valid?: () => boolean;
}

/**
 * Índices dos passos — `configCompleteness` aponta para eles (whatsapp=0, admin=1, …), então a
 * ordem aqui é contrato com a lista de pendências.
 */
export const WIZARD_STEPS = {
  whatsapp: 0,
  admin: 1,
  identity: 2,
  hours: 3,
  services: 4,
  speech: 5,
  payment: 6,
  messages: 7,
} as const;

export function ConfigWizard({
  initial,
  onClose,
  startAt = 0,
}: {
  /** Config salva (já normalizada por `fillBot`). */
  initial: BotSettings;
  /** Chamado ao fechar — por concluir, pelo ✕, Esc ou clique fora. A tela de trás recarrega. */
  onClose: () => void;
  /** Passo inicial — a lista de pendências abre o modal direto no item que falta. */
  startAt?: number;
}) {
  const toast = useToast();
  const whatsapp = useWhatsAppConnection();
  const [draft, setDraft] = useState<BotSettings>(initial);
  const [step, setStep] = useState(startAt);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [savedOnce, setSavedOnce] = useState(false);
  // Conta edições: um save só limpa o "dirty" se nada mudou enquanto a requisição estava no ar.
  const editVersion = useRef(0);
  const headingRef = useRef<HTMLHeadingElement>(null);

  // Ao trocar de passo, o foco vai para o título do passo novo — quem usa leitor de tela ouve
  // o título em vez de ficar parado num botão "Próximo" enquanto o conteúdo troca em silêncio.
  useEffect(() => {
    headingRef.current?.focus();
  }, [step]);

  const change = (patch: Partial<BotSettings>) => {
    editVersion.current += 1;
    setDirty(true);
    setDraft((d) => ({ ...d, ...patch }));
  };

  /** Grava o rascunho inteiro. `welcome` = este é o "Concluir" (pode disparar o resumo). */
  const persist = async (welcome: boolean) => {
    const version = editVersion.current;
    setSaving(true);
    try {
      await api.put(`/api/admin/settings/bot${welcome ? "" : "?welcome=false"}`, draft);
      if (editVersion.current === version) setDirty(false);
      setSavedOnce(true);
    } finally {
      setSaving(false);
    }
  };

  const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : "Erro ao salvar. Tente de novo.");

  const steps: Step[] = [
    {
      title: "Conectar o WhatsApp",
      hint: "O número da barbearia — é ele que o cliente vê e que o bot usa para responder.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          {whatsapp.status && (
            // Estado ao vivo: o hook consulta a conexão enquanto o QR está na tela, então o
            // selo vira "Conectado" sozinho assim que o celular pareia.
            <span
              className={`badge ${whatsapp.connected ? "badge-green" : "badge-amber"}`}
              role="status"
              style={{ alignSelf: "flex-start" }}
            >
              ● {whatsapp.connected ? "Conectado" : "Desconectado"}
            </span>
          )}
          <WhatsAppPairing conn={whatsapp} />
        </div>
      ),
    },
    {
      title: "Seu número de admin",
      hint: "O seu número pessoal, por onde você comanda o bot e recebe os avisos.",
      render: () => (
        <div className="stack" style={{ gap: 12 }}>
          <div
            className="muted"
            style={{
              fontSize: 13.5,
              padding: "12px 14px",
              border: "1px solid var(--border)",
              borderRadius: "var(--radius-sm)",
              background: "var(--surface-2)",
            }}
          >
            <b>A regra dos dois números.</b> O número <b>do bot</b> é o da barbearia — o que você
            pareou no passo anterior e que atende os clientes. O número <b>de admin</b> é o{" "}
            <b>seu</b>, pessoal: por ele você dá comandos e recebe os avisos. Precisam ser
            diferentes: se você usar o mesmo número nos dois, o WhatsApp trata suas mensagens como
            enviadas pelo próprio bot e ele não recebe seus comandos.
          </div>
          <AdminsEditor />
        </div>
      ),
    },
    {
      title: "Identidade",
      hint: "Como sua barbearia se apresenta ao cliente.",
      valid: () => (draft.displayName ?? "").trim() !== "",
      render: () => <IdentityFields draft={draft} onChange={change} />,
    },
    {
      title: "Funcionamento",
      hint: "Os dias e horários que a IA pode oferecer.",
      render: () => <HoursFields draft={draft} onChange={change} />,
    },
    {
      title: "Serviços e preços",
      hint: "A tabela que o bot mostra no menu e usa para responder “quanto custa?”.",
      render: () => <ServicesFields draft={draft} onChange={change} />,
    },
    {
      title: "Modo de fala",
      hint: "O tom de voz do seu assistente.",
      render: () => <SpeechModeFields draft={draft} onChange={change} />,
    },
    {
      title: "Pagamento",
      hint: "O PIX que o bot envia ao cliente.",
      render: () => <PaymentFields draft={draft} onChange={change} />,
    },
    {
      title: "Mensagens automáticas",
      hint: "O que o bot manda sozinho. Campo vazio usa o texto padrão do sistema.",
      render: () => <MessagesFields draft={draft} onChange={change} />,
    },
  ];

  // `startAt` vem de fora e pode apontar para um passo que não existe; limitar evita tela em branco.
  const index = Math.min(Math.max(step, 0), steps.length - 1);
  const current = steps[index];
  const isLast = index >= steps.length - 1;
  const canAdvance = current.valid ? current.valid() : true;
  const percent = Math.round(((index + 1) / steps.length) * 100);

  /** Vai para outro passo gravando antes o que mudou. Se o save falhar, fica onde está. */
  const goTo = async (target: number) => {
    if (saving) return;
    if (dirty) {
      try {
        await persist(false);
      } catch (e) {
        toast.error(errorMessage(e));
        return;
      }
    }
    setStep(Math.min(Math.max(target, 0), steps.length - 1));
  };

  const finish = async () => {
    try {
      // Sempre grava, mesmo sem mudança: é este save que pede o resumo de boas-vindas.
      await persist(true);
      toast.success("Configuração concluída ✓ Na primeira vez, um resumo chega no seu WhatsApp.");
      onClose();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  /**
   * Fechar (✕, Esc, clique fora) com alteração pendente grava antes de sair, em vez de
   * descartar ou perguntar. Se o save falhar, o modal fica aberto com o erro.
   */
  const requestClose = () => {
    if (!dirty) return true;
    if (saving) return false;
    void (async () => {
      try {
        await persist(false);
        toast.success("Alterações salvas ✓");
        onClose();
      } catch (e) {
        toast.error(errorMessage(e));
      }
    })();
    return false;
  };

  return (
    <Modal
      title="Configurar sua barbearia"
      size="wide"
      onClose={onClose}
      onRequestClose={requestClose}
      footer={
        <div className="row between" style={{ width: "100%" }}>
          <button className="btn btn-ghost" onClick={() => goTo(index - 1)} disabled={index === 0 || saving}>
            Voltar
          </button>
          <div className="row" style={{ gap: 10 }}>
            <span className="faint" role="status" style={{ fontSize: 12.5 }}>
              {saving ? "Salvando…" : dirty ? "Alterações não salvas" : savedOnce ? "✓ Tudo salvo" : ""}
            </span>
            {!isLast && (
              // "Pular por agora": nenhum passo pode aprisionar o dono. A lista de pendências na
              // tela de Configurações continua cobrando o que ficou para trás.
              <button className="btn btn-ghost" onClick={() => goTo(index + 1)} disabled={saving}>
                Pular por agora
              </button>
            )}
            <button
              className="btn btn-primary"
              onClick={() => (isLast ? finish() : goTo(index + 1))}
              disabled={!canAdvance || saving}
            >
              {saving ? "Salvando…" : isLast ? "Concluir" : dirty ? "Salvar e continuar" : "Próximo"}
            </button>
          </div>
        </div>
      }
    >
      <div
        className="cfg-progress"
        role="progressbar"
        aria-label="Progresso da configuração"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        style={{ marginBottom: 18 }}
      >
        <div className="cfg-progress-fill" style={{ width: `${percent}%` }} />
      </div>

      <div className="wiz">
        {/* Trilho de passos: rótulos, não pontinhos — com 8 passos e entrada direta pela lista
            de pendências, é preciso dizer onde a pessoa está e quanto falta. */}
        <nav className="wiz-rail" aria-label="Etapas da configuração">
          {steps.map((s, i) => (
            <button
              key={s.title}
              type="button"
              className={`wiz-step ${i === index ? "active" : ""} ${i < index ? "done" : ""}`}
              onClick={() => goTo(i)}
              disabled={saving}
              aria-current={i === index ? "step" : undefined}
            >
              <span className="wiz-step-n mono">{String(i + 1).padStart(2, "0")}</span>
              <span className="wiz-step-label">{s.title}</span>
            </button>
          ))}
        </nav>

        <div className="wiz-pane">
          <div className="wiz-progress mono" aria-live="polite">
            PASSO {index + 1} DE {steps.length} · {current.title}
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
