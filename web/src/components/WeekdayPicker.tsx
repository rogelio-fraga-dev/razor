// Seletor de dias de funcionamento (V32).
//
// Substitui o campo de texto livre "Dias de funcionamento". O texto parecia inofensivo mas era a
// causa de um bug real: ninguém interpretava a frase, então a agenda ignorava o dia da semana e o
// bot oferecia horário no domingo enquanto dizia ao cliente que abre de segunda a sábado.
import type { Weekday } from "../api/types";

/** ISO: 1=segunda … 7=domingo, igual a java.time.DayOfWeek. */
const DAYS: { value: Weekday; short: string; full: string }[] = [
  { value: 1, short: "Seg", full: "segunda-feira" },
  { value: 2, short: "Ter", full: "terça-feira" },
  { value: 3, short: "Qua", full: "quarta-feira" },
  { value: 4, short: "Qui", full: "quinta-feira" },
  { value: 5, short: "Sex", full: "sexta-feira" },
  { value: 6, short: "Sáb", full: "sábado" },
  { value: 7, short: "Dom", full: "domingo" },
];

interface Props {
  value: Weekday[];
  onChange: (days: Weekday[]) => void;
  label?: string;
}

export function WeekdayPicker({ value, onChange, label = "Dias de funcionamento" }: Props) {
  const toggle = (day: Weekday) => {
    const next = value.includes(day) ? value.filter((d) => d !== day) : [...value, day];
    onChange([...next].sort((a, b) => a - b));
  };

  return (
    <div className="field">
      {/* fieldset/legend em vez de <label>: um rótulo só não pode descrever sete controles. */}
      <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="field-legend">{label}</legend>
        <div className="chip-row" role="group">
          {DAYS.map((d) => {
            const on = value.includes(d.value);
            return (
              <button
                key={d.value}
                type="button"
                className={`chip-btn ${on ? "active" : ""}`}
                onClick={() => toggle(d.value)}
                aria-pressed={on}
                aria-label={`${d.full}: ${on ? "aberto" : "fechado"}`}
              >
                {d.short}
              </button>
            );
          })}
        </div>
      </fieldset>
      {value.length === 0 && (
        <p className="error" style={{ marginTop: 8 }}>
          Sem nenhum dia marcado o bot não oferece horário nenhum. Marque ao menos um.
        </p>
      )}
    </div>
  );
}
