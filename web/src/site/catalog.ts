// Catálogo dos templates e temas do site white-label — a ÚNICA fonte desses textos.
//
// Antes os nomes e descrições viviam em três lugares (editor, enum do backend e comentários dos
// templates) e já tinham divergido: o enum prometia "avaliações", bloco que nenhum template tem.
// O editor lê daqui; a página pública lê daqui; o backend só guarda o id.
import type { SiteTemplate, SiteTheme } from "../api/types";

export interface TemplateInfo {
  id: SiteTemplate;
  label: string;
  /** Uma frase: o que o template prioriza. */
  pitch: string;
  /** Para que tipo de barbearia ele funciona melhor — ajuda o dono a escolher sem testar os três. */
  idealFor: string;
  /** O que o visitante vê primeiro, em ordem. Mostrado no editor como "estrutura". */
  firstBlocks: string[];
  /** O que o dono precisa ter cadastrado para o template brilhar. */
  needs: string;
}

export const TEMPLATE_CATALOG: TemplateInfo[] = [
  {
    id: "CLASSICO",
    label: "Clássico",
    pitch: "Preço e agendamento logo de cara.",
    idealFor: "Barbearia de bairro que compete por conveniência e preço claro.",
    firstBlocks: ["Foto de capa com botão de agendar", "Números da casa", "Serviços e preços"],
    needs: "Serviços com preço cadastrados. A foto de capa é opcional.",
  },
  {
    id: "ESTUDIO",
    label: "Estúdio",
    pitch: "Marca antes de preço: texto grande, muito respiro.",
    idealFor: "Barbearia premium, que vende experiência e atmosfera.",
    firstBlocks: ["Capa centralizada", "Texto de apresentação em destaque", "Fotos do ambiente", "Equipe"],
    needs: "Um bom texto \"sobre a casa\" e algumas fotos do espaço.",
  },
  {
    id: "VITRINE",
    label: "Vitrine",
    pitch: "A galeria abre a página: o trabalho é o argumento.",
    idealFor: "Barbeiro que já mostra os cortes no Instagram e vende pelo portfólio.",
    firstBlocks: ["Título curto", "Mosaico de fotos dos cortes", "Serviços"],
    needs: "Pelo menos 4 fotos de cortes na galeria.",
  },
];

export const templateInfo = (id: SiteTemplate): TemplateInfo =>
  TEMPLATE_CATALOG.find((t) => t.id === id) ?? TEMPLATE_CATALOG[0];

export const THEME_OPTIONS: { id: SiteTheme; label: string; hint: string }[] = [
  { id: "AUTO", label: "Ambos", hint: "Segue o aparelho do visitante e mostra um botão para alternar." },
  { id: "LIGHT", label: "Claro", hint: "Sempre claro." },
  { id: "DARK", label: "Escuro", hint: "Sempre escuro." },
];
