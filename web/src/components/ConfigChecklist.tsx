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
    <div className="card">
      <div className="card-head">
        <h3>{status.ready ? "Sua barbearia está no ar" : "Falta configurar"}</h3>
        <span className={`badge ${status.ready ? "badge-green" : "badge-amber"} mono`}>
          {status.percent}% pronto
        </span>
      </div>

      <div className="cfg-progress" aria-hidden="true">
        <div className="cfg-progress-fill" style={{ width: `${status.percent}%` }} />
      </div>

      {status.ready && pendentes.length === 0 ? (
        <p className="muted" style={{ margin: "14px 0 0", fontSize: 14 }}>
          Tudo configurado. O bot atende, agenda e cobra sozinho — mande um <b>oi</b> de outro
          número para ver funcionando.
        </p>
      ) : (
        <>
          {status.ready && (
            <p className="muted" style={{ margin: "14px 0 0", fontSize: 14 }}>
              O essencial está pronto e o bot já atende. O que resta abaixo melhora a experiência,
              mas não impede de operar.
            </p>
          )}
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
                  <button className="btn btn-ghost btn-sm" onClick={() => onGoToStep(item.step)}>
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
  );
}
