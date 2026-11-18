// Prévia ao vivo do RASCUNHO: um iframe da rota /site-previa (mesma origem), que recebe o
// rascunho por postMessage e desenha com o mesmo código do site publicado.
//
// Por que iframe e não o template direto aqui dentro: o site tem CSS próprio e media queries
// (celular x computador). Dentro do painel, o CSS de um vazaria no outro e a "largura de
// celular" seria fingida. No iframe a largura é real — 390px é um celular de verdade para o CSS.
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useFocusTrap } from "../../lib/useFocusTrap";
import { DRAFT_MSG, READY_MSG, type SiteDraft } from "../../site/draft";

type Device = "desktop" | "phone";
const WIDTH: Record<Device, number> = { desktop: 1280, phone: 390 };
/**
 * Abaixo desta largura de palco o "computador" vira uma miniatura ilegível (menos da metade do
 * tamanho): sem escolha do dono, a prévia abre no celular — e a "Tela cheia" mostra o computador.
 */
const DESKTOP_MIN_STAGE = 760;

function send(frame: HTMLIFrameElement | null, d: SiteDraft) {
  // targetOrigin explícito: o rascunho só sai para a nossa origem, nunca para "*".
  frame?.contentWindow?.postMessage({ type: DRAFT_MSG, draft: d }, window.location.origin);
}

export function SitePreviewPanel({
  draft,
  onClose,
  onFullscreen,
  initialDevice,
  title = "Prévia ao vivo",
}: {
  draft: SiteDraft;
  onClose?: () => void;
  /** Mostra o botão "Tela cheia". */
  onFullscreen?: () => void;
  initialDevice?: Device;
  title?: string;
}) {
  const frame = useRef<HTMLIFrameElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const latest = useRef(draft);
  latest.current = draft;
  const [ready, setReady] = useState(false);
  // null = o dono ainda não escolheu: decide pela largura do palco (ver DESKTOP_MIN_STAGE).
  const [chosen, setChosen] = useState<Device | null>(initialDevice ?? null);
  const [box, setBox] = useState({ w: 0, h: 0 });
  const device: Device = chosen ?? (box.w === 0 || box.w >= DESKTOP_MIN_STAGE ? "desktop" : "phone");
  const setDevice = (d: Device) => setChosen(d);

  // A prévia avisa quando está pronta (e de novo se recarregar); aí recebe o rascunho atual.
  useEffect(() => {
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== window.location.origin || e.source !== frame.current?.contentWindow) return;
      if ((e.data as { type?: unknown } | null)?.type !== READY_MSG) return;
      setReady(true);
      send(frame.current, latest.current);
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  // Cada mudança vai para a prévia com um respiro curto: digitar não redesenha o site a cada tecla.
  useEffect(() => {
    if (!ready) return;
    const t = window.setTimeout(() => send(frame.current, draft), 120);
    return () => window.clearTimeout(t);
  }, [draft, ready]);

  useLayoutEffect(() => {
    const el = stage.current;
    if (!el) return;
    const measure = () => setBox({ w: el.clientWidth, h: el.clientHeight });
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const width = WIDTH[device];
  // Encolhe para caber, nunca amplia: o computador vira miniatura fiel, o celular fica 1:1.
  const scale = box.w ? Math.min(1, (box.w - 2) / width) : 1;
  const height = box.h ? box.h / scale : 800;

  return (
    <div className="se-pv">
      <div className="se-pv-bar">
        <strong className="se-pv-title">{title}</strong>
        <div className="se-seg" role="group" aria-label="Largura da prévia">
          <button type="button" aria-pressed={device === "desktop"} onClick={() => setDevice("desktop")}>
            Computador
          </button>
          <button type="button" aria-pressed={device === "phone"} onClick={() => setDevice("phone")}>
            Celular
          </button>
        </div>
        {onFullscreen && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={onFullscreen} aria-haspopup="dialog">
            ⛶ Tela cheia
          </button>
        )}
        {onClose && (
          <button type="button" className="btn btn-ghost btn-sm se-pv-close" onClick={onClose}>
            Fechar
          </button>
        )}
      </div>
      <div className="se-pv-stage" ref={stage}>
        <div
          className={`se-pv-device ${device}`}
          style={{ width: width * scale, height: box.h || undefined }}
        >
          <iframe
            ref={frame}
            src="/site-previa"
            title="Prévia do site com as alterações ainda não salvas"
            style={{ width, height, transform: `scale(${scale})` }}
          />
        </div>
        {!ready && <div className="se-pv-loading">Carregando a prévia…</div>}
      </div>
      <p className="se-hint se-pv-foot">Mostra o rascunho, antes de salvar. Os links não abrem aqui.</p>
    </div>
  );
}

/**
 * A prévia em tela cheia, por cima do painel: o computador em tamanho de verdade (ou quase) e o
 * celular em 1:1. Fecha no botão ou no Esc; o foco fica preso aqui e volta para quem abriu.
 */
export function SitePreviewFullscreen({ draft, onClose }: { draft: SiteDraft; onClose: () => void }) {
  const { ref, onKeyDown } = useFocusTrap<HTMLDivElement>({ onEscape: onClose });

  // Trava a rolagem do painel atrás enquanto a tela cheia está aberta.
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  return (
    // onKeyDown no diálogo é o padrão WAI-ARIA de modal (Esc + Tab preso).
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <div
      ref={ref}
      className="se-full"
      role="dialog"
      aria-modal="true"
      aria-label="Prévia do site em tela cheia"
      tabIndex={-1}
      onKeyDown={onKeyDown}
    >
      <SitePreviewPanel draft={draft} onClose={onClose} initialDevice="desktop" title="Prévia em tela cheia" />
    </div>
  );
}
