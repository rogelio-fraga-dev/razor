// Prévia ao vivo do RASCUNHO: um iframe da rota /site-previa (mesma origem), que recebe o
// rascunho por postMessage e desenha com o mesmo código do site publicado.
//
// Por que iframe e não o template direto aqui dentro: o site tem CSS próprio e media queries
// (celular x computador). Dentro do painel, o CSS de um vazaria no outro e a "largura de
// celular" seria fingida. No iframe a largura é real — 390px é um celular de verdade para o CSS.
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { DRAFT_MSG, READY_MSG, type SiteDraft } from "../../site/draft";

type Device = "desktop" | "phone";
const WIDTH: Record<Device, number> = { desktop: 1280, phone: 390 };

function send(frame: HTMLIFrameElement | null, d: SiteDraft) {
  // targetOrigin explícito: o rascunho só sai para a nossa origem, nunca para "*".
  frame?.contentWindow?.postMessage({ type: DRAFT_MSG, draft: d }, window.location.origin);
}

export function SitePreviewPanel({ draft, onClose }: { draft: SiteDraft; onClose?: () => void }) {
  const frame = useRef<HTMLIFrameElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const latest = useRef(draft);
  latest.current = draft;
  const [ready, setReady] = useState(false);
  const [device, setDevice] = useState<Device>(() =>
    window.matchMedia?.("(min-width: 1180px)").matches ? "desktop" : "phone",
  );
  const [box, setBox] = useState({ w: 0, h: 0 });

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
        <strong className="se-pv-title">Prévia ao vivo</strong>
        <div className="se-seg" role="group" aria-label="Largura da prévia">
          <button type="button" aria-pressed={device === "desktop"} onClick={() => setDevice("desktop")}>
            Computador
          </button>
          <button type="button" aria-pressed={device === "phone"} onClick={() => setDevice("phone")}>
            Celular
          </button>
        </div>
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
