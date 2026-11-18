// Prévia ao vivo do site: /site-previa — só faz sentido dentro do iframe do editor.
//
// Carrega os dados reais da barbearia (GET /api/admin/site/preview?completo=true, autenticado
// pelo cookie do dono, mesma origem) e espera o RASCUNHO do editor por postMessage. Desenha com
// o mesmo SiteView do site publicado — o dono vê o que vai ao ar antes de salvar.
//
// Segurança da mensagem: só aceita o que vem da MESMA origem E do próprio pai (o editor). Outra
// aba, um popup ou um iframe de terceiro não conseguem injetar conteúdo aqui; e mesmo o que
// passa é tratado como dado (applyDraft confere tipos e passa as URLs pela barreira).
import { useEffect, useMemo, useState, type MouseEvent } from "react";
import { api, ApiError } from "../api/client";
import type { PublicSite } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { SiteView } from "./SitePage";
import { applyDraft, DRAFT_MSG, READY_MSG } from "./draft";
import { sanitizeSite } from "./safeUrl";
import "./site.css";

export function SitePreviewPage() {
  const { session } = useAuth();
  const [base, setBase] = useState<PublicSite | null>(null);
  const [draft, setDraft] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const isOwner = session?.role === "OWNER";

  useEffect(() => {
    if (!isOwner) return;
    let vivo = true;
    api
      .get<PublicSite>("/api/admin/site/preview?completo=true")
      .then((s) => vivo && setBase(sanitizeSite(s)))
      .catch((e) => vivo && setError(e instanceof ApiError ? e.message : "Não foi possível carregar a prévia."));
    return () => {
      vivo = false;
    };
  }, [isOwner]);

  useEffect(() => {
    // Fora de um iframe não há editor para conversar.
    if (window.parent === window) return;
    const onMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin || event.source !== window.parent) return;
      const msg = event.data as { type?: unknown; draft?: unknown } | null;
      if (!msg || msg.type !== DRAFT_MSG) return;
      setDraft(msg.draft);
    };
    window.addEventListener("message", onMessage);
    // Avisa o editor que está pronta: ele responde com o rascunho atual (sem corrida de carga).
    window.parent.postMessage({ type: READY_MSG }, window.location.origin);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  useEffect(() => {
    document.title = "Prévia do site";
  }, []);

  const site = useMemo(() => (base ? (draft ? applyDraft(base, draft) : base) : null), [base, draft]);

  // Na prévia, link não navega: clicar em "Agendar" trocaria a prévia por outra página dentro do
  // iframe e o dono ficaria preso nela. Âncora interna (#servicos) continua rolando a página.
  const blockLinks = (e: MouseEvent<HTMLDivElement>) => {
    const a = (e.target as HTMLElement).closest("a");
    if (a && !(a.getAttribute("href") ?? "").startsWith("#")) e.preventDefault();
  };

  if (!isOwner || error) {
    return (
      <div className="rzsite theme-auto" style={{ display: "grid", placeItems: "center", padding: 40, minHeight: "100vh" }}>
        <p className="rz-lead" style={{ textAlign: "center", maxWidth: "40ch" }}>
          {error ?? "A prévia do site é só para o dono, dentro do painel."}
        </p>
      </div>
    );
  }

  if (!site) return <div className="rzsite theme-auto" style={{ minHeight: "100vh" }} />;

  return (
    <div onClickCapture={blockLinks}>
      <SiteView site={site} />
    </div>
  );
}
