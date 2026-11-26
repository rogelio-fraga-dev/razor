// Sobre o Razor: o que é, a versão, contato, links legais e as Novidades de cada versão
// (lidas de web/CHANGELOG.md — a mesma fonte que vai para o GitHub).
import type { ReactNode } from "react";
import changelog from "../../CHANGELOG.md?raw";
import { Layout } from "../components/Layout";
import { RazorLogo } from "../components/Logo";

const SITE_URL = (import.meta.env.VITE_SITE_URL ?? "").trim().replace(/\/$/, "");
const SUPPORT_EMAIL = "rogelio.claro@gmail.com";
const INSTAGRAM = "https://www.instagram.com/rogeliofraga.dev/";

/** Markdown simples do CHANGELOG: ## versão, ### seção, - item, **negrito**. */
function renderChangelog(md: string): ReactNode[] {
  const out: ReactNode[] = [];
  let list: string[] = [];
  const flush = (k: number) => {
    if (list.length) out.push(<ul key={`ul${k}`} className="about-list">{list.map((l, i) => <li key={i}>{inline(l)}</li>)}</ul>);
    list = [];
  };
  md.split(/\r?\n/).forEach((line, k) => {
    if (line.startsWith("- ")) { list.push(line.slice(2)); return; }
    flush(k);
    if (line.startsWith("# ")) return; // título do arquivo
    if (line.startsWith("## ")) out.push(<h2 key={k} className="about-version">{line.slice(3)}</h2>);
    else if (line.startsWith("### ")) out.push(<h3 key={k} className="about-sec">{line.slice(4)}</h3>);
    else if (line.trim()) out.push(<p key={k} className="muted">{inline(line)}</p>);
  });
  flush(-1);
  return out;
}

function inline(s: string): ReactNode[] {
  return s.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? <b key={i}>{part.slice(2, -2)}</b> : <span key={i}>{part}</span>,
  );
}

export function About() {
  return (
    <Layout title="Sobre o Razor">
      <div className="about">
        <section className="card about-card">
          <RazorLogo size={30} />
          <p className="about-lead">Gestão e atendimento por IA para barbearias: a IA atende o WhatsApp e marca os horários; o painel cuida da agenda, das comandas, do caixa e dos clientes.</p>
          <dl className="about-facts">
            <div><dt>Versão</dt><dd className="mono">{__APP_VERSION__}</dd></div>
            <div><dt>Suporte</dt><dd><a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a></dd></div>
            <div><dt>Instagram</dt><dd><a href={INSTAGRAM} target="_blank" rel="noreferrer">@rogeliofraga.dev</a></dd></div>
          </dl>
          {SITE_URL && (
            <p className="about-links">
              <a href={`${SITE_URL}/termos.html`} target="_blank" rel="noreferrer">Termos de uso</a> ·{" "}
              <a href={`${SITE_URL}/privacidade.html`} target="_blank" rel="noreferrer">Privacidade</a> ·{" "}
              <a href={`${SITE_URL}/cancelamento.html`} target="_blank" rel="noreferrer">Cancelamento</a>
            </p>
          )}
        </section>
        <section className="card about-card">
          <h2 className="about-title">Novidades</h2>
          {renderChangelog(changelog)}
        </section>
      </div>
    </Layout>
  );
}
