// Editor do site white-label (V33), dentro de Configurações.
//
// O dono escolhe template, tema e UMA cor, escreve os textos e cola os endereços das fotos.
// Publicar é um ato separado de salvar: dá para montar o site com calma sem que um rascunho
// pela metade vá ao ar com o endereço já impresso no cartão.
import { useEffect, useState } from "react";
import { Switch } from "./Modal";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { SiteTemplate, SiteTheme } from "../api/types";

interface SiteForm {
  templateId: SiteTemplate;
  themeMode: SiteTheme;
  accentColor: string;
  headline: string;
  subheadline: string;
  aboutText: string;
  logoUrl: string;
  heroImageUrl: string;
  galleryUrls: string;
  whatsappNumber: string;
  instagramUrl: string;
  mapsUrl: string;
  showTeam: boolean;
  showPlans: boolean;
  showPrices: boolean;
  published: boolean;
}

const TEMPLATES: { id: SiteTemplate; label: string; hint: string }[] = [
  { id: "CLASSICO", label: "Clássico", hint: "Preço e agendamento logo de cara. Para quem compete por conveniência." },
  { id: "ESTUDIO", label: "Estúdio", hint: "Marca antes de preço: texto grande, muito respiro, equipe em destaque." },
  { id: "VITRINE", label: "Vitrine", hint: "A galeria abre a página. Para quem vende pelo trabalho que aparece." },
];

const THEMES: { id: SiteTheme; label: string }[] = [
  { id: "AUTO", label: "Segue o aparelho" },
  { id: "LIGHT", label: "Claro" },
  { id: "DARK", label: "Escuro" },
];

/** Sugestões prontas: o dono escolhe uma cor boa sem abrir seletor. */
const PRESETS = ["#3E63FF", "#C9A227", "#1F9D55", "#E4572E", "#8A63FF", "#111827"];

const EMPTY: SiteForm = {
  templateId: "CLASSICO",
  themeMode: "AUTO",
  accentColor: "#3E63FF",
  headline: "",
  subheadline: "",
  aboutText: "",
  logoUrl: "",
  heroImageUrl: "",
  galleryUrls: "",
  whatsappNumber: "",
  instagramUrl: "",
  mapsUrl: "",
  showTeam: true,
  showPlans: true,
  showPrices: true,
  published: false,
};

export function SiteEditor({ slug }: { slug: string | null }) {
  const toast = useToast();
  const { data, loading, error, reload } = useApi<Partial<SiteForm>>(() => api.get("/api/admin/site"), []);
  const [form, setForm] = useState<SiteForm>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!data) return;
    // Campo nulo do backend vira string vazia; os booleanos mantêm o padrão do sistema quando
    // ausentes, senão a barbearia perderia equipe e planos por omissão.
    setForm({
      ...EMPTY,
      ...Object.fromEntries(Object.entries(data).filter(([, v]) => v !== null && v !== undefined)),
    } as SiteForm);
  }, [data]);

  const set = <K extends keyof SiteForm>(k: K, v: SiteForm[K]) => setForm((f) => ({ ...f, [k]: v }));

  const save = async (publicar?: boolean) => {
    setSaving(true);
    setFormError(null);
    const payload = { ...form, published: publicar ?? form.published };
    try {
      await api.put("/api/admin/site", payload);
      toast.success(publicar === true ? "Site publicado ✓" : publicar === false ? "Site despublicado" : "Site salvo ✓");
      setForm(payload);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const enderecoPublico = slug ? `/b/${slug}` : null;

  return (
    <div className="card">
      <div className="card-head">
        <h3>Seu site</h3>
        <span className={`badge ${form.published ? "badge-green" : "badge-muted"}`}>
          {form.published ? "no ar" : "rascunho"}
        </span>
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : (
          <div className="stack" style={{ gap: 18 }}>
            {/* ---------- Template ---------- */}
            <div className="field">
              <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
                <legend className="field-legend">Modelo do site</legend>
                <div className="site-tpl-grid">
                  {TEMPLATES.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      className={`site-tpl ${form.templateId === t.id ? "active" : ""}`}
                      onClick={() => set("templateId", t.id)}
                      aria-pressed={form.templateId === t.id}
                    >
                      {/* Miniatura desenhada em CSS: mostra a ESTRUTURA de cada modelo sem
                          depender de uma captura de tela que envelhece a cada mudança. */}
                      <span className={`site-thumb thumb-${t.id.toLowerCase()}`} aria-hidden="true" />
                      <strong>{t.label}</strong>
                      <span className="site-tpl-hint">{t.hint}</span>
                    </button>
                  ))}
                </div>
              </fieldset>
            </div>

            {/* ---------- Tema e cor ---------- */}
            <div className="form-grid">
              <div className="field">
                <label>Tema
                  <select className="input" value={form.themeMode} onChange={(e) => set("themeMode", e.target.value as SiteTheme)}>
                    {THEMES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
                  </select>
                </label>
              </div>
              <div className="field">
                <label>Cor da marca
                  <div className="row" style={{ gap: 8, marginTop: 6 }}>
                    <input
                      type="color"
                      className="site-color"
                      value={form.accentColor}
                      onChange={(e) => set("accentColor", e.target.value.toUpperCase())}
                      aria-label="Escolher cor da marca"
                    />
                    <input
                      className="input mono"
                      value={form.accentColor}
                      onChange={(e) => set("accentColor", e.target.value.toUpperCase())}
                      style={{ maxWidth: 120 }}
                    />
                  </div>
                </label>
                <div className="chip-row" style={{ marginTop: 8 }}>
                  {PRESETS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      className="site-swatch"
                      style={{ background: c }}
                      onClick={() => set("accentColor", c)}
                      aria-label={`Usar a cor ${c}`}
                    />
                  ))}
                </div>
              </div>
            </div>
            <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
              Você escolhe só a cor da marca. O resto da paleta o modelo calcula a partir dela —
              inclusive a cor do texto sobre os botões, para nada ficar ilegível.
            </p>

            {/* ---------- Textos ---------- */}
            <div className="field">
              <label>Título principal
                <input className="input" value={form.headline} placeholder={form.headline || "Ex.: Corte que resolve a sua semana"}
                  onChange={(e) => set("headline", e.target.value)} maxLength={120} />
              </label>
            </div>
            <div className="field">
              <label>Frase de apoio
                <input className="input" value={form.subheadline} placeholder="Ex.: Barbearia no centro, atendimento sem espera."
                  onChange={(e) => set("subheadline", e.target.value)} maxLength={255} />
              </label>
            </div>
            <div className="field">
              <label>Sobre a barbearia
                <textarea className="input" rows={4} value={form.aboutText}
                  placeholder="Conte a história da casa em duas ou três linhas."
                  onChange={(e) => set("aboutText", e.target.value)}
                  style={{ resize: "vertical", fontFamily: "inherit" }} />
              </label>
            </div>

            {/* ---------- Imagens ---------- */}
            <div className="field">
              <label>Foto de capa (endereço da imagem)
                <input className="input" value={form.heroImageUrl} placeholder="https://…"
                  onChange={(e) => set("heroImageUrl", e.target.value)} />
              </label>
            </div>
            <div className="field">
              <label>Logo (endereço da imagem, opcional)
                <input className="input" value={form.logoUrl} placeholder="https://…"
                  onChange={(e) => set("logoUrl", e.target.value)} />
              </label>
            </div>
            <div className="field">
              <label>Galeria — um endereço por linha
                <textarea className="input" rows={4} value={form.galleryUrls}
                  placeholder={"https://…/corte-1.jpg\nhttps://…/corte-2.jpg"}
                  onChange={(e) => set("galleryUrls", e.target.value)}
                  style={{ resize: "vertical", fontFamily: "var(--font-mono)", fontSize: 12.5 }} />
              </label>
            </div>
            <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
              Ainda não hospedamos imagens: cole o endereço de uma foto que já esteja na internet
              (Instagram, Google Fotos, Drive público). Sem foto de capa, o site usa um fundo com
              a sua cor em vez de deixar um espaço vazio.
            </p>

            {/* ---------- Contato ---------- */}
            <div className="form-grid">
              <div className="field">
                <label>WhatsApp do site (com DDD)
                  <input className="input" inputMode="tel" value={form.whatsappNumber} placeholder="5511987654321"
                    onChange={(e) => set("whatsappNumber", e.target.value)} />
                </label>
              </div>
              <div className="field">
                <label>Instagram
                  <input className="input" value={form.instagramUrl} placeholder="https://instagram.com/…"
                    onChange={(e) => set("instagramUrl", e.target.value)} />
                </label>
              </div>
            </div>
            <div className="field">
              <label>Link do mapa
                <input className="input" value={form.mapsUrl} placeholder="https://maps.google.com/…"
                  onChange={(e) => set("mapsUrl", e.target.value)} />
              </label>
            </div>

            {/* ---------- Seções ---------- */}
            <Switch checked={form.showPrices} onChange={(v) => set("showPrices", v)} label="Mostrar preços no site" />
            <Switch checked={form.showTeam} onChange={(v) => set("showTeam", v)} label="Mostrar a equipe" />
            <Switch checked={form.showPlans} onChange={(v) => set("showPlans", v)} label="Mostrar os planos" />

            {formError && <div className="error" role="alert">{formError}</div>}

            {/* ---------- Ações ---------- */}
            <div className="row wrap" style={{ gap: 10, marginTop: 4 }}>
              <button className="btn btn-ghost" onClick={() => save()} disabled={saving}>
                {saving ? "Salvando…" : "Salvar rascunho"}
              </button>
              {form.published ? (
                <button className="btn btn-ghost" onClick={() => save(false)} disabled={saving}>
                  Tirar do ar
                </button>
              ) : (
                <button className="btn btn-primary" onClick={() => save(true)} disabled={saving}>
                  Publicar site
                </button>
              )}
              {enderecoPublico && (
                // Prévia sempre disponível, publicado ou não: publicar às cegas para descobrir
                // como ficou seria o pior jeito de usar isto.
                <a className="btn btn-ghost" href={enderecoPublico} target="_blank" rel="noopener noreferrer">
                  Ver o site ↗
                </a>
              )}
            </div>
            {enderecoPublico && (
              <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
                Endereço: <span className="mono">{enderecoPublico}</span> — em breve
                <span className="mono"> {slug}.razor.com.br</span>.
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
