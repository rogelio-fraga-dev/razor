// Editor do site white-label (V33, refeito na V45), dentro de Configurações.
//
// Feito para o dono que não é técnico, no celular ou no computador: tudo em grupos recolhíveis,
// fotos por upload (nada de colar endereço), os textos de cada seção com o padrão do modelo à
// vista e uma PRÉVIA AO VIVO do rascunho ao lado (no celular, num botão). Publicar continua sendo
// um ato separado de salvar: dá para montar o site com calma sem que um rascunho pela metade vá
// ao ar com o endereço já impresso no cartão.
import { useCallback, useEffect, useMemo, useState } from "react";
import { SkeletonRows } from "./Skeleton";
import { Switch } from "./Modal";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { photoForSite } from "../lib/photo";
import { api, ApiError } from "../api/client";
import type { SiteContent, SiteMotion, SiteTemplate, SiteTheme } from "../api/types";
import { TEMPLATE_CATALOG, THEME_OPTIONS } from "../site/catalog";
import { bookLabel, CONTENT_LIMITS } from "../site/content";
import type { SiteDraft } from "../site/draft";
import {
  instagramError,
  mapsError,
  normalizeInstagram,
  normalizeMaps,
  normalizeWhatsapp,
  whatsappError,
  imageUrlError,
} from "../site/contactRules";
import { Group, TextField } from "./siteEditor/fields";
import { GalleryEditor, MAX_GALLERY, PhotoPicker, type UploadFn } from "./siteEditor/photos";
import { PhotoLibrary } from "./siteEditor/library";
import { SectionsEditor } from "./siteEditor/sections";
import { SitePreviewPanel } from "./siteEditor/preview";
import "./siteEditor/site-editor.css";

/** O formulário é o próprio rascunho da prévia, mais o estado de publicação. */
interface SiteForm extends SiteDraft {
  published: boolean;
}

/** Como o backend devolve (SitePayload). */
type ServerSite = Partial<Omit<SiteForm, "gallery" | "content">> & {
  galleryUrls?: string | null;
  content?: SiteContent | null;
};

/** Sugestões prontas: o dono escolhe uma cor boa sem abrir seletor. */
const PRESETS = ["#3E63FF", "#C9A227", "#1F9D55", "#E4572E", "#8A63FF", "#111827"];

const MOTION_OPTIONS: { id: SiteMotion; label: string; hint: string }[] = [
  { id: "full", label: "Completas", hint: "Seções entram com movimento ao rolar a página." },
  { id: "subtle", label: "Suaves", hint: "Só um esmaecer discreto." },
  { id: "off", label: "Desligadas", hint: "Nada se mexe. Quem pede menos movimento no aparelho já vê assim." },
];

const EMPTY: SiteForm = {
  templateId: "CLASSICO",
  themeMode: "AUTO",
  accentColor: "#3E63FF",
  headline: "",
  subheadline: "",
  aboutText: "",
  logoUrl: "",
  heroImageUrl: "",
  gallery: [],
  whatsappNumber: "",
  instagramUrl: "",
  mapsUrl: "",
  showTeam: true,
  showPlans: true,
  showPrices: true,
  published: false,
  onlineBookingEnabled: false,
  content: {},
};

type GroupId = "modelo" | "textos" | "fotos" | "secoes" | "contato" | "agenda" | "publicar";

function fromServer(data: ServerSite): SiteForm {
  // Campo nulo do backend vira string vazia; os booleanos mantêm o padrão do sistema quando
  // ausentes, senão a barbearia perderia equipe e planos por omissão.
  const plain = Object.fromEntries(
    Object.entries(data).filter(([k, v]) => v !== null && v !== undefined && k !== "galleryUrls" && k !== "content"),
  );
  return {
    ...EMPTY,
    ...plain,
    gallery: (data.galleryUrls ?? "")
      .split("\n")
      .map((s) => s.trim())
      .filter(Boolean),
    content: data.content ?? {},
  } as SiteForm;
}

/** Conteúdo sem campos vazios — o mesmo que o backend guarda, para "há alterações?" não mentir. */
function cleanContent(c: SiteContent): SiteContent {
  const sections: NonNullable<SiteContent["sections"]> = {};
  for (const [k, s] of Object.entries(c.sections ?? {})) {
    if (!s) continue;
    const out: Record<string, unknown> = {};
    if (s.visible === false) out.visible = false;
    for (const f of ["eyebrow", "title", "text"] as const) if (s[f]?.trim()) out[f] = s[f]!.trim();
    if (Object.keys(out).length) sections[k as keyof typeof sections] = out;
  }
  const res: SiteContent = {};
  if (Object.keys(sections).length) res.sections = sections;
  if (c.bookLabel?.trim()) res.bookLabel = c.bookLabel.trim();
  if (c.motion && c.motion !== "full") res.motion = c.motion;
  return res;
}

const snapshot = (f: SiteForm) => JSON.stringify({ ...f, content: cleanContent(f.content) });

/** Largura de "computador" do editor: prévia ao lado em vez de num botão. */
function useWide(query = "(min-width: 1180px)") {
  const [wide, setWide] = useState(() => window.matchMedia?.(query).matches ?? true);
  useEffect(() => {
    const mq = window.matchMedia?.(query);
    if (!mq) return;
    const on = () => setWide(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, [query]);
  return wide;
}

export function SiteEditor({ slug }: { slug: string | null }) {
  const toast = useToast();
  const { data, loading, error } = useApi<ServerSite>(() => api.get("/api/admin/site"), []);
  const [form, setForm] = useState<SiteForm>(EMPTY);
  const [saved, setSaved] = useState<string>(snapshot(EMPTY));
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [open, setOpen] = useState<Record<GroupId, boolean>>({
    modelo: true,
    textos: false,
    fotos: false,
    secoes: false,
    contato: false,
    agenda: false,
    publicar: false,
  });
  const [libraryKey, setLibraryKey] = useState(0);
  const wide = useWide();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewMounted, setPreviewMounted] = useState(false);

  useEffect(() => {
    if (!data) return;
    const f = fromServer(data);
    setForm(f);
    setSaved(snapshot(f));
  }, [data]);

  const set = <K extends keyof SiteForm>(k: K, v: SiteForm[K]) => setForm((f) => ({ ...f, [k]: v }));
  const setContent = (c: SiteContent) => set("content", c);
  const toggleGroup = (id: GroupId, v: boolean) => setOpen((o) => (o[id] === v ? o : { ...o, [id]: v }));
  const touch = (k: string) => setTouched((t) => (t[k] ? t : { ...t, [k]: true }));

  const dirty = !loading && !error && snapshot(form) !== saved;

  // Sair da página (fechar aba, recarregar) com alteração não salva pede confirmação.
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  // Prévia no celular: tela cheia por cima do editor; trava a rolagem de trás e fecha no Esc.
  useEffect(() => {
    if (wide || !previewOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setPreviewOpen(false);
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [wide, previewOpen]);

  const upload: UploadFn = useCallback(async (file, kind) => {
    const dataUri = await photoForSite(file, kind);
    try {
      const res = await api.post<{ url: string }>("/api/admin/site/images", { data: dataUri });
      setLibraryKey((k) => k + 1);
      return res.url;
    } catch (e) {
      throw new Error(e instanceof ApiError ? e.message : "Não foi possível enviar a foto.");
    }
  }, []);

  // Erros inline: aparecem depois que o dono sai do campo (não enquanto ainda digita).
  const errors = {
    whatsappNumber: whatsappError(form.whatsappNumber),
    instagramUrl: instagramError(form.instagramUrl),
    mapsUrl: mapsError(form.mapsUrl),
    logoUrl: imageUrlError(form.logoUrl),
    heroImageUrl: imageUrlError(form.heroImageUrl),
  };
  const shown = (k: keyof typeof errors) => (touched[k] ? errors[k] : null);

  const draft: SiteDraft = useMemo(
    () => ({
      ...form,
      whatsappNumber: normalizeWhatsapp(form.whatsappNumber),
      instagramUrl: normalizeInstagram(form.instagramUrl),
      mapsUrl: normalizeMaps(form.mapsUrl),
    }),
    [form],
  );

  const save = async (publicar?: boolean) => {
    setFormError(null);
    if (Object.values(errors).some(Boolean)) {
      setTouched((t) => ({ ...t, whatsappNumber: true, instagramUrl: true, mapsUrl: true, logoUrl: true, heroImageUrl: true }));
      if (errors.whatsappNumber || errors.instagramUrl || errors.mapsUrl) toggleGroup("contato", true);
      if (errors.logoUrl || errors.heroImageUrl) toggleGroup("fotos", true);
      setFormError("Confira os campos destacados antes de salvar.");
      return;
    }
    setSaving(true);
    const { gallery, ...rest } = draft;
    const payload = {
      ...rest,
      galleryUrls: gallery.join("\n"),
      content: cleanContent(form.content),
      published: publicar ?? form.published,
    };
    try {
      const res = await api.put<ServerSite>("/api/admin/site", payload);
      const f = fromServer(res);
      setForm(f);
      setSaved(snapshot(f));
      setLibraryKey((k) => k + 1);
      toast.success(publicar === true ? "Site publicado ✓" : publicar === false ? "Site tirado do ar" : "Site salvo ✓");
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const enderecoPublico = slug ? `/b/${slug}` : null;
  const inDraft = useMemo(
    () => new Set([form.logoUrl, form.heroImageUrl, ...form.gallery].filter(Boolean)),
    [form.logoUrl, form.heroImageUrl, form.gallery],
  );
  const tpl = TEMPLATE_CATALOG.find((t) => t.id === form.templateId);
  const motion = form.content.motion ?? "full";
  const hiddenCount = Object.values(form.content.sections ?? {}).filter((s) => s?.visible === false).length
    + (form.showTeam ? 0 : 1)
    + (form.showPlans ? 0 : 1);
  const contactCount = [form.whatsappNumber, form.instagramUrl, form.mapsUrl].filter((v) => v.trim()).length;

  if (loading) {
    return (
      <div className="card card-pad">
        <SkeletonRows rows={3} />
      </div>
    );
  }
  if (error) {
    return (
      <div className="card card-pad">
        <div className="error" role="alert">{error}</div>
      </div>
    );
  }

  const openPreview = () => {
    setPreviewMounted(true);
    setPreviewOpen(true);
  };

  return (
    <div className="se-layout">
      <div className="se-editor">
        <div className="se-head">
          <div>
            <h3 className="se-title">Seu site</h3>
            <p className="se-hint" style={{ margin: 0 }}>
              {form.published ? "No ar — o que você salvar aparece para os clientes." : "Rascunho — só você vê até publicar."}
            </p>
          </div>
          <span className={`badge ${form.published ? "badge-green" : "badge-muted"}`}>{form.published ? "no ar" : "rascunho"}</span>
        </div>

        {/* ---------- Modelo & cores ---------- */}
        <Group title="Modelo & cores" summary={`${tpl?.label ?? ""} · ${THEME_OPTIONS.find((t) => t.id === form.themeMode)?.label ?? ""}`} open={open.modelo} onToggle={(v) => toggleGroup("modelo", v)}>
          <fieldset className="se-fieldset">
            <legend className="field-legend">Modelo do site</legend>
            <div className="site-tpl-grid">
              {TEMPLATE_CATALOG.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  className={`site-tpl ${form.templateId === t.id ? "active" : ""}`}
                  onClick={() => set("templateId", t.id as SiteTemplate)}
                  aria-pressed={form.templateId === t.id}
                >
                  {/* Miniatura desenhada em CSS: mostra a ESTRUTURA de cada modelo sem
                      depender de uma captura de tela que envelhece a cada mudança. */}
                  <span className={`site-thumb thumb-${t.id.toLowerCase()}`} aria-hidden="true" />
                  <strong>{t.label}</strong>
                  <span className="site-tpl-hint">{t.pitch}</span>
                  <span className="site-tpl-hint"><em>Ideal para:</em> {t.idealFor}</span>
                </button>
              ))}
            </div>
          </fieldset>
          <div className="form-grid">
            <div className="field">
              <label>Tema
                <select className="input" value={form.themeMode} onChange={(e) => set("themeMode", e.target.value as SiteTheme)}>
                  {THEME_OPTIONS.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
                </select>
              </label>
              <span className="se-hint">{THEME_OPTIONS.find((t) => t.id === form.themeMode)?.hint}</span>
            </div>
            <div className="field">
              <label>Animações
                <select
                  className="input"
                  value={motion}
                  onChange={(e) => setContent({ ...form.content, motion: e.target.value as SiteMotion })}
                >
                  {MOTION_OPTIONS.map((m) => <option key={m.id} value={m.id}>{m.label}</option>)}
                </select>
              </label>
              <span className="se-hint">{MOTION_OPTIONS.find((m) => m.id === motion)?.hint}</span>
            </div>
          </div>
          <div className="field">
            <label>Cor da marca
              <div className="row" style={{ gap: 8 }}>
                <input
                  type="color"
                  className="site-color"
                  value={/^#[0-9a-fA-F]{6}$/.test(form.accentColor) ? form.accentColor : "#3E63FF"}
                  onChange={(e) => set("accentColor", e.target.value.toUpperCase())}
                  aria-label="Escolher cor da marca"
                />
                <input
                  className="input mono"
                  value={form.accentColor}
                  onChange={(e) => set("accentColor", e.target.value.toUpperCase())}
                  style={{ maxWidth: 120 }}
                  aria-label="Cor da marca em hexadecimal"
                />
              </div>
            </label>
            <div className="chip-row">
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
            <span className="se-hint">
              Você escolhe só a cor da marca. O resto da paleta o modelo calcula a partir dela — inclusive a cor do
              texto sobre os botões, para nada ficar ilegível.
            </span>
          </div>
        </Group>

        {/* ---------- Textos principais ---------- */}
        <Group
          title="Textos principais"
          summary={form.headline ? `“${form.headline.slice(0, 40)}${form.headline.length > 40 ? "…" : ""}”` : "Título, frase e história da casa"}
          open={open.textos}
          onToggle={(v) => toggleGroup("textos", v)}
        >
          <TextField label="Título principal" value={form.headline} onChange={(v) => set("headline", v)} max={120}
            placeholder="Ex.: Corte que resolve a sua semana" hint="O que aparece grande na capa." />
          <TextField label="Frase de apoio" value={form.subheadline} onChange={(v) => set("subheadline", v)} max={255}
            placeholder="Ex.: Barbearia no centro, atendimento sem espera." />
          <TextField label="Sobre a barbearia" value={form.aboutText} onChange={(v) => set("aboutText", v)} max={5000}
            multiline rows={5} placeholder="Conte a história da casa em duas ou três linhas." />
        </Group>

        {/* ---------- Fotos ---------- */}
        <Group
          title="Fotos"
          summary={`${form.logoUrl ? "Logo" : "Sem logo"} · ${form.heroImageUrl ? "capa" : "sem capa"} · ${form.gallery.length}/${MAX_GALLERY} na galeria`}
          open={open.fotos}
          onToggle={(v) => toggleGroup("fotos", v)}
        >
          <PhotoPicker
            label="Foto de capa"
            hint="A primeira coisa que o cliente vê. Sem capa, o site usa um fundo com a sua cor."
            value={form.heroImageUrl}
            onChange={(v) => { set("heroImageUrl", v); touch("heroImageUrl"); }}
            kind="photo"
            upload={upload}
          />
          {shown("heroImageUrl") && <span className="se-error" role="alert">{errors.heroImageUrl}</span>}
          <PhotoPicker
            label="Logo"
            hint="Opcional. PNG com fundo transparente fica melhor."
            value={form.logoUrl}
            onChange={(v) => { set("logoUrl", v); touch("logoUrl"); }}
            kind="logo"
            upload={upload}
          />
          {shown("logoUrl") && <span className="se-error" role="alert">{errors.logoUrl}</span>}
          <GalleryEditor value={form.gallery} onChange={(v) => set("gallery", v)} upload={upload} />
          <PhotoLibrary
            reloadKey={libraryKey}
            inDraft={inDraft}
            galleryFull={form.gallery.length >= MAX_GALLERY}
            onUseInGallery={(url) => set("gallery", [...form.gallery, url].slice(0, MAX_GALLERY))}
          />
        </Group>

        {/* ---------- Seções da página ---------- */}
        <Group
          title="Seções da página"
          summary={hiddenCount ? `${hiddenCount} escondida(s)` : "Todas visíveis"}
          open={open.secoes}
          onToggle={(v) => toggleGroup("secoes", v)}
        >
          <p className="se-hint" style={{ margin: 0 }}>
            Deixe em branco para usar o texto do modelo (aparece em cinza). Escreva só o que quiser trocar.
          </p>
          <SectionsEditor
            template={form.templateId}
            content={form.content}
            onChange={setContent}
            toggles={{ showTeam: form.showTeam, showPlans: form.showPlans, showPrices: form.showPrices }}
            onToggle={(k, v) => set(k, v)}
          />
        </Group>

        {/* ---------- Contato ---------- */}
        <Group
          title="Contato"
          summary={contactCount ? `${contactCount} de 3 preenchidos` : "WhatsApp, Instagram e mapa"}
          open={open.contato}
          onToggle={(v) => toggleGroup("contato", v)}
        >
          <TextField
            label="WhatsApp do site (com DDD)"
            value={form.whatsappNumber}
            onChange={(v) => set("whatsappNumber", v)}
            onBlur={() => touch("whatsappNumber")}
            inputMode="tel"
            placeholder="11 98765-4321"
            hint="Pode digitar com espaço ou traço. O 55 do Brasil a gente põe sozinho."
            error={shown("whatsappNumber")}
          />
          <TextField
            label="Instagram"
            value={form.instagramUrl}
            onChange={(v) => set("instagramUrl", v)}
            onBlur={() => touch("instagramUrl")}
            inputMode="url"
            placeholder="@suabarbearia ou https://instagram.com/…"
            error={shown("instagramUrl")}
          />
          <TextField
            label="Link do mapa"
            value={form.mapsUrl}
            onChange={(v) => set("mapsUrl", v)}
            onBlur={() => touch("mapsUrl")}
            inputMode="url"
            placeholder="https://maps.app.goo.gl/…"
            hint="No Google Maps: abra a barbearia → Compartilhar → Copiar link."
            error={shown("mapsUrl")}
          />
        </Group>

        {/* ---------- Agendamento ---------- */}
        <Group
          title="Agendamento"
          summary={form.onlineBookingEnabled ? "Online pelo site" : "Pelo WhatsApp"}
          open={open.agenda}
          onToggle={(v) => toggleGroup("agenda", v)}
        >
          <Switch
            checked={form.onlineBookingEnabled}
            onChange={(v) => set("onlineBookingEnabled", v)}
            label="Agendamento online pelo site"
          />
          <span className="se-hint">
            Ligado, o botão “Agendar” abre uma página onde o cliente escolhe serviço, barbeiro e horário livre, sem
            login. Ele recebe a confirmação no WhatsApp e você recebe o aviso. Desligado, o botão abre o WhatsApp.
          </span>
          <TextField
            label="Texto do botão de agendar"
            value={form.content.bookLabel ?? ""}
            onChange={(v) => setContent({ ...form.content, bookLabel: v })}
            max={CONTENT_LIMITS.bookLabel}
            placeholder={bookLabel({ content: null, onlineBooking: form.onlineBookingEnabled })}
          />
        </Group>

        {/* ---------- Publicação ---------- */}
        <Group
          title="Publicação"
          summary={form.published ? "No ar" : "Rascunho"}
          open={open.publicar}
          onToggle={(v) => toggleGroup("publicar", v)}
        >
          <p className="se-hint" style={{ margin: 0 }}>
            {form.published
              ? "Seu site está no ar. Tirar do ar faz o endereço mostrar “página não encontrada” até você publicar de novo — nada se perde."
              : "Publique quando estiver tudo certo. Até lá, só você vê — aqui na prévia ao vivo."}
          </p>
          <div className="row wrap" style={{ gap: 10 }}>
            {form.published ? (
              <button type="button" className="btn btn-ghost" onClick={() => save(false)} disabled={saving}>
                Tirar do ar
              </button>
            ) : (
              <button type="button" className="btn btn-primary" onClick={() => save(true)} disabled={saving}>
                Publicar site
              </button>
            )}
            {enderecoPublico && (
              // O endereço público: antes de publicar ele responde "não encontrado" — por isso a
              // prévia ao vivo existe.
              <a className="btn btn-ghost" href={enderecoPublico} target="_blank" rel="noopener noreferrer">
                Ver o site ↗
              </a>
            )}
          </div>
          {enderecoPublico && (
            <p className="se-hint" style={{ margin: 0 }}>
              Endereço: <span className="mono">{enderecoPublico}</span> — em breve
              <span className="mono"> {slug}.razor.com.br</span>.
            </p>
          )}
        </Group>

        {/* ---------- Barra de ações (sempre à mão) ---------- */}
        <div className="se-actions" role="region" aria-label="Salvar o site">
          {formError ? (
            <div className="se-error" role="alert">{formError}</div>
          ) : (
            <span className={`se-status${dirty ? " dirty" : ""}`} aria-live="polite">
              {dirty ? "Alterações não salvas" : "Tudo salvo"}
            </span>
          )}
          <div className="row" style={{ gap: 8, marginLeft: "auto" }}>
            {!wide && (
              <button type="button" className="btn btn-ghost btn-sm" onClick={openPreview}>
                Ver prévia
              </button>
            )}
            <button type="button" className={`btn btn-sm ${form.published ? "btn-primary" : "btn-ghost"}`} onClick={() => save()} disabled={saving || !dirty}>
              {saving ? "Salvando…" : form.published ? "Salvar e atualizar" : "Salvar rascunho"}
            </button>
            {!form.published && (
              <button type="button" className="btn btn-primary btn-sm" onClick={() => save(true)} disabled={saving}>
                Publicar
              </button>
            )}
          </div>
        </div>
      </div>

      {(wide || previewMounted) && (
        <aside className={`se-preview${!wide && previewOpen ? " is-open" : ""}`} aria-label="Prévia do site" hidden={!wide && !previewOpen}>
          <SitePreviewPanel draft={draft} onClose={wide ? undefined : () => setPreviewOpen(false)} />
        </aside>
      )}
    </div>
  );
}
