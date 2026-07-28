// Página pública do site white-label: /b/:slug
//
// Fica FORA do bloco autenticado do App — é um site, e site não pede login. O tenant vem do slug
// da URL e o backend recusa slug inexistente ou site não publicado com o mesmo 404.
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { PublicSite } from "../api/types";
import { TEMPLATES } from "./templates";
import "./site.css";

const THEME_CLASS = { LIGHT: "theme-light", DARK: "theme-dark", AUTO: "theme-auto" } as const;

export function SitePage() {
  const { slug = "" } = useParams();
  const [site, setSite] = useState<PublicSite | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    api
      .get<PublicSite>(`/api/site/${encodeURIComponent(slug)}`)
      .then((s) => vivo && setSite(s))
      .catch((e) => vivo && setError(e instanceof ApiError ? e.message : "Não foi possível carregar."));
    return () => {
      vivo = false;
    };
  }, [slug]);

  // O título da aba é do estabelecimento, não do Razor: o site é da barbearia.
  useEffect(() => {
    if (site) document.title = site.barbershopName;
  }, [site]);

  if (error) {
    return (
      <div className="rzsite theme-auto" style={{ display: "grid", placeItems: "center", padding: 40 }}>
        <div style={{ textAlign: "center", maxWidth: "42ch" }}>
          <h1 className="rz-h2">Página não encontrada</h1>
          <p className="rz-lead">Confira o endereço. Se você é o dono, publique o site em Configurações.</p>
        </div>
      </div>
    );
  }

  // Sem esqueleto aqui de propósito: um site em branco por meio segundo é melhor que um
  // esqueleto que pisca com a cor errada — o acento e o tema só chegam junto com os dados.
  if (!site) return <div className="rzsite theme-auto" style={{ minHeight: "100vh" }} />;

  const Template = TEMPLATES[site.template] ?? TEMPLATES.CLASSICO;

  return (
    <div
      className={`rzsite ${THEME_CLASS[site.theme] ?? "theme-auto"}`}
      // A única cor do dono entra aqui; o resto da paleta o CSS deriva.
      style={{ ["--accent" as string]: site.accentColor }}
    >
      <Template site={site} />
    </div>
  );
}
