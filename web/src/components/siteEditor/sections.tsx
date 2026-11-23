// "Seções da página": cada bloco do site com interruptor e os próprios textos (V45).
//
// O placeholder de cada campo é o texto PADRÃO do modelo escolhido (sectionDefaults): o dono vê
// exatamente o que vai aparecer se deixar em branco, e só escreve o que quiser trocar.
import type { SiteContent, SiteSectionContent, SiteSectionKey, SiteStepContent, SiteTemplate } from "../../api/types";
import { CONTENT_LIMITS, HOW_STEP_DEFAULTS, SECTION_META, STEP_LIMITS, sectionDefaults, type SectionText } from "../../site/content";
import { Switch } from "../Modal";
import { TextField } from "./fields";

const FIELD_LABEL: Record<keyof SectionText, string> = {
  eyebrow: "Texto pequeno acima do título",
  title: "Título",
  text: "Texto",
};

/**
 * Interruptores que já existiam antes da V45 e que, além de esconder, nem mandam o dado para a
 * página pública (equipe e planos). Para essas seções o interruptor da seção É o antigo — dois
 * botões para a mesma coisa só confundiriam.
 */
export interface LegacyToggles {
  showTeam: boolean;
  showPlans: boolean;
  showPrices: boolean;
}

export function SectionsEditor({
  template,
  content,
  onChange,
  toggles,
  onToggle,
}: {
  template: SiteTemplate;
  content: SiteContent;
  onChange: (c: SiteContent) => void;
  toggles: LegacyToggles;
  onToggle: (k: keyof LegacyToggles, v: boolean) => void;
}) {
  const sections = content.sections ?? {};

  const patch = (key: SiteSectionKey, next: SiteSectionContent | null) => {
    const all = { ...sections };
    if (next && Object.keys(next).length) all[key] = next;
    else delete all[key];
    onChange({ ...content, sections: all });
  };

  const setField = (key: SiteSectionKey, field: keyof SectionText, value: string) => {
    const cur = { ...(sections[key] ?? {}) };
    if (value) cur[field] = value;
    else delete cur[field];
    patch(key, cur);
  };

  // Passo a passo: a posição é o número do passo; os vazios do fim saem (o backend faz igual).
  const setStep = (key: SiteSectionKey, index: number, field: keyof SiteStepContent, value: string) => {
    const cur = { ...(sections[key] ?? {}) };
    const steps: SiteStepContent[] = [...(cur.steps ?? [])];
    while (steps.length <= index) steps.push({});
    const step = { ...steps[index] };
    if (value) step[field] = value;
    else delete step[field];
    steps[index] = step;
    while (steps.length && !steps[steps.length - 1].title && !steps[steps.length - 1].text) steps.pop();
    if (steps.length) cur.steps = steps;
    else delete cur.steps;
    patch(key, cur);
  };

  const isVisible = (key: SiteSectionKey) =>
    key === "team" ? toggles.showTeam : key === "plans" ? toggles.showPlans : sections[key]?.visible !== false;

  const setVisible = (key: SiteSectionKey, v: boolean) => {
    if (key === "team") return onToggle("showTeam", v);
    if (key === "plans") return onToggle("showPlans", v);
    const cur = { ...(sections[key] ?? {}) };
    if (v) delete cur.visible;
    else cur.visible = false;
    patch(key, cur);
  };

  // Volta os textos ao padrão do modelo, sem mexer na visibilidade.
  const restore = (key: SiteSectionKey) => {
    const cur = sections[key];
    patch(key, cur?.visible === false ? { visible: false } : null);
  };

  return (
    <div className="se-sections">
      {SECTION_META.map((meta) => {
        const defaults = sectionDefaults(template, meta.key);
        const cur = sections[meta.key] ?? {};
        const visible = isVisible(meta.key);
        const customized = meta.fields.some((f) => !!cur[f]) || !!cur.steps?.length;
        // A capa sempre aparece: sem ela o site não tem título nem botão de agendar no topo.
        const canHide = meta.key !== "hero";
        return (
          <section key={meta.key} className={`se-section${visible ? "" : " is-off"}`} aria-label={meta.label}>
            <div className="se-section-head">
              {canHide ? (
                <Switch checked={visible} onChange={(v) => setVisible(meta.key, v)} label={meta.label} />
              ) : (
                <strong>{meta.label}</strong>
              )}
              {customized && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => restore(meta.key)}>
                  Restaurar padrão
                </button>
              )}
            </div>
            <p className="se-hint" style={{ margin: 0 }}>
              {visible ? meta.hint : "Escondida no site."}
            </p>
            {visible &&
              meta.fields.map((f) => (
                <TextField
                  key={f}
                  label={FIELD_LABEL[f]}
                  value={cur[f] ?? ""}
                  onChange={(v) => setField(meta.key, f, v)}
                  max={CONTENT_LIMITS[f]}
                  placeholder={defaults[f] || "(sem texto — deixe em branco ou escreva o seu)"}
                  multiline={f === "text"}
                  rows={3}
                />
              ))}
            {visible && !!meta.steps && (
              <ol className="se-steps">
                {HOW_STEP_DEFAULTS.slice(0, meta.steps).map((d, i) => (
                  <li key={i} className="se-step">
                    <span className="se-step-n" aria-hidden="true">{i + 1}</span>
                    <div className="se-step-fields">
                      <TextField
                        label={`Passo ${i + 1} — título`}
                        value={cur.steps?.[i]?.title ?? ""}
                        onChange={(v) => setStep(meta.key, i, "title", v)}
                        max={STEP_LIMITS.title}
                        placeholder={d.title}
                      />
                      <TextField
                        label={`Passo ${i + 1} — texto`}
                        value={cur.steps?.[i]?.text ?? ""}
                        onChange={(v) => setStep(meta.key, i, "text", v)}
                        max={STEP_LIMITS.text}
                        placeholder={d.text}
                      />
                    </div>
                  </li>
                ))}
              </ol>
            )}
            {visible && meta.key === "services" && (
              <Switch checked={toggles.showPrices} onChange={(v) => onToggle("showPrices", v)} label="Mostrar os preços" />
            )}
          </section>
        );
      })}
    </div>
  );
}
