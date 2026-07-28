// Seletor dos 5 modos de fala. Escolher um modo preenche a persona; editar o texto
// livremente cai em "Personalizado". Mantém a IA soando como o dono quer sem ele ter
// que escrever a persona do zero.
import { SPEECH_MODES, matchSpeechMode } from "../lib/speechModes";

export function SpeechModePicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (persona: string) => void;
}) {
  const activeId = matchSpeechMode(value);
  const isCustom = value.trim() !== "" && activeId === null;

  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="mode-grid">
        {SPEECH_MODES.map((m) => (
          <button
            key={m.id}
            type="button"
            className={`mode-card ${activeId === m.id ? "active" : ""}`}
            onClick={() => onChange(m.persona)}
            aria-pressed={activeId === m.id}
          >
            <span className="mode-name">{m.label}</span>
            <span className="mode-tag">{m.tagline}</span>
          </button>
        ))}
        <div className={`mode-card mode-custom ${isCustom ? "active" : ""}`} aria-hidden="true">
          <span className="mode-name">Personalizado</span>
          <span className="mode-tag">Escreva do seu jeito abaixo</span>
        </div>
      </div>

      <div className="field">
        <label>Texto da personalidade
        <textarea
          className="input"
          rows={3}
          value={value}
          placeholder="Ex.: simpática e objetiva, trata o cliente por você, usa poucos emojis"
          onChange={(e) => onChange(e.target.value)}
          style={{ resize: "vertical", fontFamily: "inherit" }}
        />
            </label>
        <span className="faint" style={{ fontSize: 12.5 }}>
          Escolha um modo acima para começar — depois é só ajustar o texto se quiser.
        </span>
      </div>
    </div>
  );
}
