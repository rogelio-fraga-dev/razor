// Conteúdo editável do site (V45) — a ÚNICA fonte dos textos padrão de cada seção.
//
// O dono pode trocar o "olho" (eyebrow), o título e o texto de cada seção, e esconder a seção.
// O que ele deixar em branco cai no padrão do template, definido aqui. O editor mostra estes
// mesmos padrões como placeholder — assim ele vê exatamente o que vai aparecer se não mexer.
//
// Regra para os templates: NENHUM texto visível de seção fica fixo no JSX. Todo bloco lê
// `section(site, "chave")` e usa `.eyebrow/.title/.text`, e respeita `.visible`.
import type { PublicSite, SiteContent, SiteMotion, SiteSectionKey, SiteTemplate } from "../api/types";

export interface SectionText {
  eyebrow: string;
  title: string;
  text: string;
}

export interface ResolvedSection extends SectionText {
  visible: boolean;
}

/** Metadados para o editor: nome da seção e o que cada campo significa nela. */
export const SECTION_META: { key: SiteSectionKey; label: string; hint: string; fields: (keyof SectionText)[] }[] = [
  { key: "hero", label: "Capa", hint: "Título e frase da capa ficam em “Textos principais”.", fields: ["eyebrow"] },
  { key: "stats", label: "Números da casa", hint: "Calculados do seu cadastro — nunca inventados.", fields: [] },
  { key: "services", label: "Serviços", hint: "A lista vem dos serviços cadastrados.", fields: ["eyebrow", "title", "text"] },
  { key: "gallery", label: "Galeria", hint: "As fotos vêm da galeria abaixo.", fields: ["eyebrow", "title", "text"] },
  { key: "story", label: "Sobre a casa", hint: "O texto vem de “Sobre a barbearia”.", fields: ["eyebrow", "title"] },
  { key: "team", label: "Equipe", hint: "Fotos e bios vêm de Profissionais.", fields: ["eyebrow", "title", "text"] },
  { key: "plans", label: "Planos", hint: "Os planos vêm do Clube de assinatura.", fields: ["eyebrow", "title", "text"] },
  { key: "how", label: "Como agendar", hint: "Só aparece quando o agendamento é pelo WhatsApp.", fields: ["eyebrow", "title"] },
  { key: "location", label: "Onde estamos", hint: "Endereço e horário vêm das configurações do bot.", fields: ["eyebrow", "title"] },
  { key: "cta", label: "Chamada final", hint: "O último convite antes do rodapé.", fields: ["eyebrow", "title", "text"] },
];

const BASE: Record<SiteSectionKey, SectionText> = {
  hero: { eyebrow: "Barbearia", title: "", text: "" },
  stats: { eyebrow: "", title: "", text: "" },
  services: { eyebrow: "O que fazemos", title: "Serviços", text: "" },
  gallery: { eyebrow: "Portfólio", title: "Nosso trabalho", text: "" },
  story: { eyebrow: "A casa", title: "", text: "" },
  team: { eyebrow: "Quem corta", title: "Nossa equipe", text: "" },
  plans: { eyebrow: "Clube", title: "Planos de assinatura", text: "Corte sempre em dia, por um valor fixo no mês." },
  how: { eyebrow: "Simples assim", title: "Como agendar", text: "" },
  location: { eyebrow: "Visite", title: "Onde estamos", text: "" },
  cta: {
    eyebrow: "",
    title: "Bora marcar seu horário?",
    text: "Escolha o horário em um minuto — sem app, sem cadastro.",
  },
};

/** Padrões por template: só o que difere da BASE. */
const BY_TEMPLATE: Record<SiteTemplate, Partial<Record<SiteSectionKey, Partial<SectionText>>>> = {
  CLASSICO: {
    hero: { eyebrow: "Barbearia · tradição e navalha" },
    services: { eyebrow: "Tabela da casa", title: "Serviços & valores" },
    gallery: { title: "Nosso trabalho" },
  },
  ESTUDIO: {
    hero: { eyebrow: "Estúdio de barbearia" },
    story: { eyebrow: "Manifesto" },
    services: { eyebrow: "A carta", title: "Serviços" },
    gallery: { eyebrow: "Estúdio", title: "Atmosfera" },
  },
  VITRINE: {
    hero: { eyebrow: "Barbearia" },
    services: { eyebrow: "Cardápio", title: "O que a gente faz" },
    gallery: { eyebrow: "Portfólio", title: "Feito aqui" },
  },
};

/** Texto padrão de uma seção num template — o que o editor mostra como placeholder. */
export function sectionDefaults(template: SiteTemplate, key: SiteSectionKey): SectionText {
  return { ...BASE[key], ...(BY_TEMPLATE[template]?.[key] ?? {}) };
}

const filled = (v: string | undefined | null): v is string => !!v && v.trim().length > 0;

/** A seção como o site deve mostrar: override do dono por cima do padrão do template. */
export function section(site: Pick<PublicSite, "template" | "content">, key: SiteSectionKey): ResolvedSection {
  const d = sectionDefaults(site.template, key);
  const o = site.content?.sections?.[key];
  return {
    visible: o?.visible !== false,
    eyebrow: filled(o?.eyebrow) ? o!.eyebrow!.trim() : d.eyebrow,
    title: filled(o?.title) ? o!.title!.trim() : d.title,
    text: filled(o?.text) ? o!.text!.trim() : d.text,
  };
}

/** Texto do botão de agendar: o do dono, ou o padrão conforme o canal. */
export function bookLabel(site: Pick<PublicSite, "content" | "onlineBooking">): string {
  const custom = site.content?.bookLabel;
  if (filled(custom)) return custom.trim();
  return site.onlineBooking ? "Agendar horário" : "Agendar pelo WhatsApp";
}

/** Intensidade das animações escolhida pelo dono (o visitante com movimento reduzido vence). */
export function motionOf(site: Pick<PublicSite, "content">): SiteMotion {
  const m = site.content?.motion;
  return m === "subtle" || m === "off" ? m : "full";
}

/** Limites de tamanho — os mesmos que o backend valida (SiteContentPolicy). */
export const CONTENT_LIMITS = { eyebrow: 60, title: 120, text: 600, bookLabel: 40 } as const;

export type { SiteContent };
