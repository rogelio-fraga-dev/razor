// Relógio ao vivo da topbar. Útil de verdade num sistema de agenda: o dono confere a hora
// no mesmo lugar em que vê o próximo corte (a Agenda desenha a linha do "agora" com esta
// mesma referência — a hora local do aparelho).
import { useEffect, useState } from "react";

const TIME = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});
const DAY = new Intl.DateTimeFormat("pt-BR", { weekday: "short", day: "2-digit", month: "2-digit" });

export function Clock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    // Alinha o primeiro tique à virada do segundo para o relógio não "pular".
    let interval: number | undefined;
    const align = window.setTimeout(() => {
      setNow(new Date());
      interval = window.setInterval(() => setNow(new Date()), 1000);
    }, 1000 - (Date.now() % 1000));

    return () => {
      window.clearTimeout(align);
      if (interval) window.clearInterval(interval);
    };
  }, []);

  const [hh, mm, ss] = TIME.format(now).split(":");

  return (
    <div className="clock" title={DAY.format(now)} aria-label={`Agora: ${hh}:${mm}`}>
      <span className="clk-day mono">{DAY.format(now).replace(".", "").toUpperCase()}</span>
      <span className="clk-time mono">
        {hh}:{mm}
        <span className="clk-sec">:{ss}</span>
      </span>
    </div>
  );
}
