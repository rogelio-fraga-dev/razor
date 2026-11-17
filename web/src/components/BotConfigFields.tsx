// Grupos de campos da config do bot — identidade, funcionamento, serviços, modo de fala,
// pagamento e mensagens automáticas.
//
// Viviam duplicados no assistente (ConfigWizard) e no antigo card "O que o bot fala" de
// Configurações: ~150 linhas quase idênticas que já divergiam em rótulo, placeholder e
// "obrigatório". Agora existe um grupo por assunto e o modal de configuração só os monta.
import { SpeechModePicker } from "./SpeechModePicker";
import { WeekdayPicker } from "./WeekdayPicker";
import type { BotSettings, BotTextKey } from "../api/types";

export interface BotFieldsProps {
  draft: BotSettings;
  /** Aplica uma alteração parcial ao rascunho. */
  onChange: (patch: Partial<BotSettings>) => void;
}

function TextField({
  draft,
  onChange,
  k,
  label,
  placeholder,
  required = false,
}: BotFieldsProps & { k: BotTextKey; label: string; placeholder?: string; required?: boolean }) {
  return (
    <div className="field">
      <label>
        {label}
        {/* Obrigatório precisa estar dito no campo, não só no botão "Próximo" desabilitado. */}
        {required && <span className="field-required"> · obrigatório</span>}
        <input
          className="input"
          value={draft[k] ?? ""}
          placeholder={placeholder}
          onChange={(e) => onChange({ [k]: e.target.value } as Partial<BotSettings>)}
          aria-required={required || undefined}
        />
      </label>
    </div>
  );
}

function AreaField({
  draft,
  onChange,
  k,
  label,
  rows = 3,
  placeholder,
}: BotFieldsProps & { k: BotTextKey; label: string; rows?: number; placeholder?: string }) {
  return (
    <div className="field">
      <label>
        {label}
        <textarea
          className="input"
          rows={rows}
          value={draft[k] ?? ""}
          placeholder={placeholder}
          onChange={(e) => onChange({ [k]: e.target.value } as Partial<BotSettings>)}
          style={{ resize: "vertical", fontFamily: "inherit" }}
        />
      </label>
    </div>
  );
}

export function IdentityFields(p: BotFieldsProps) {
  return (
    <div className="stack" style={{ gap: 12 }}>
      <TextField {...p} k="displayName" label="Nome da barbearia" placeholder="Como o bot chama sua barbearia" required />
      <TextField {...p} k="addressText" label="Endereço" />
      <TextField {...p} k="addressMapsUrl" label="Link do mapa (opcional)" />
      <TextField {...p} k="instagramUrl" label="Instagram (opcional)" />
    </div>
  );
}

export function HoursFields(p: BotFieldsProps) {
  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="form-grid">
        <TextField {...p} k="openingTime" label="Abre às" placeholder="09:00" />
        <TextField {...p} k="closingTime" label="Fecha às" placeholder="19:00" />
      </div>
      <WeekdayPicker
        value={p.draft.workingWeekdays ?? []}
        onChange={(days) => p.onChange({ workingWeekdays: days })}
      />
      <AreaField {...p} k="cancellationPolicy" label="Política de cancelamento/atraso" rows={2} placeholder="Ex.: cancele com 2h de antecedência" />
    </div>
  );
}

export function ServicesFields(p: BotFieldsProps) {
  return (
    <div className="stack" style={{ gap: 12 }}>
      <AreaField
        {...p}
        k="servicesText"
        label="Tabela de serviços / preços"
        rows={5}
        placeholder={"Ex.:\nCorte — R$ 40\nBarba — R$ 30\nCorte + Barba — R$ 60"}
      />
      <TextField {...p} k="scheduleUrl" label="Link de agendamento (opcional)" />
    </div>
  );
}

export function SpeechModeFields({ draft, onChange }: BotFieldsProps) {
  return <SpeechModePicker value={draft.botPersona ?? ""} onChange={(v) => onChange({ botPersona: v })} />;
}

export function PaymentFields(p: BotFieldsProps) {
  return (
    <div className="stack" style={{ gap: 12 }}>
      <TextField {...p} k="pixKey" label="Chave PIX" placeholder="CNPJ, e-mail, telefone ou aleatória" />
      <AreaField {...p} k="pixCopyPaste" label="PIX copia-e-cola (opcional)" rows={3} placeholder="Cole aqui o código do seu banco" />
      <AreaField {...p} k="paymentInfo" label="Formas de pagamento" rows={2} placeholder="Ex.: PIX, dinheiro, débito e crédito" />
    </div>
  );
}

export function MessagesFields(p: BotFieldsProps) {
  return (
    <div className="stack" style={{ gap: 12 }}>
      <AreaField {...p} k="welcomeText" label="Saudação inicial" rows={2} placeholder="Primeira mensagem ao cliente novo" />
      <AreaField {...p} k="reminderText" label="Lembrete (1h antes)" rows={2} />
      <AreaField {...p} k="reviewText" label="Pedido de avaliação (após o corte)" rows={2} />
      <AreaField {...p} k="fidelityRewardText" label="Recompensa de fidelidade (10 pontos)" rows={2} />
    </div>
  );
}
