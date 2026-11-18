// O que ainda falta configurar — e o caminho para resolver.
//
// Fica visível DEPOIS que o assistente fecha, de propósito: quem sai no meio precisa saber o que
// ficou pendente sem ter que reabrir tudo e clicar "próximo" oito vezes. Cada item pendente leva
// direto ao passo que o resolve.
import type { ConfigStatus } from "../lib/configCompleteness";

interface Props {
  status: ConfigStatus;
  /** Abre o assistente no passo indicado (-1 = item que o dono não resolve sozinho). */
  onGoToStep: (step: number) => void;
}

export function ConfigChecklist({ status, onGoToStep }: Props) {
  const pendentes = status.items.filter((i) => !i.done);

  return (
    // `checklist` ativa o fio de 68° no topo do card — a assinatura da marca, que já existia
    // pronta em CSS e não era usada justamente na tela de configuração.
    <div className="card checklist">
      {/* Tudo dentro do .card-pad: o .card não traz padding próprio, e sem ele a barra de
          progresso (uma pílula) ia de borda a borda e os textos encostavam na lateral. */}
      <div className="card-pad">
      <div className="cfg-hero">
        <div className="cfg-hero-main">
          <h3 className="cfg-hero-title">
            {status.ready ? "Sua barbearia está no ar" : "Falta configurar"}
          </h3>
          <p className="cfg-hero-sub">
            {status.ready
              ? "O essencial está pronto — o bot atende, agenda e cobra sozinho."
              : `${status.missingRequired} item(ns) obrigatório(s) impedem o bot de operar.`}
          </p>
        </div>
        {/* O único número da tela vira KPI de verdade. Como badge de 12px ele competia com
            rótulo de campo; é o dado que mede o progresso do onboarding inteiro. */}
        <div className="cfg-hero-kpi">
          <span className="cfg-hero-pct mono">{status.percent}%</span>
          <span className="cfg-hero-pct-label">pronto</span>
        </div>
      </div>

      <div className="cfg-progress" aria-hidden="true">
        <div className="cfg-progress-fill" style={{ width: `${status.percent}%` }} />
      </div>

      {status.ready && pendentes.length === 0 ? (
        <p className="muted" style={{ margin: "14px 0 0", fontSize: 14 }}>
          Mande um <b>oi</b> de outro número para ver o bot atendendo.
        </p>
      ) : (
        <>
          <ul className="cfg-checklist">
            {pendentes.map((item) => (
              <li key={item.id}>
                <div className="cfg-check-main">
                  <span className="cfg-check-label">
                    {item.label}
                    {item.required && <span className="badge badge-amber">obrigatório</span>}
                  </span>
                  <span className="cfg-check-hint">{item.hint}</span>
                </div>
                {item.step >= 0 ? (
                  // aria-label completo: quem navega pela lista de botões do leitor de tela
                  // ouviria seis "Configurar" idênticos sem o contexto do item.
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => onGoToStep(item.step)}
                    aria-label={`Configurar: ${item.label}`}
                  >
                    Configurar
                  </button>
                ) : (
                  // Chave da OpenAI é variável de ambiente do servidor: oferecer um botão que
                  // não resolve nada seria pior que não oferecer nenhum.
                  <span className="faint" style={{ fontSize: 12 }}>no servidor</span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {/* Itens já resolvidos ficam recolhidos: a tela é sobre o que falta. */}
      {status.items.some((i) => i.done) && (
        <details className="cfg-done">
          <summary>{status.items.filter((i) => i.done).length} item(ns) já configurado(s)</summary>
          <ul>
            {status.items.filter((i) => i.done).map((i) => (
              <li key={i.id}>✓ {i.label}</li>
            ))}
          </ul>
        </details>
      )}
      </div>
    </div>
  );
}
