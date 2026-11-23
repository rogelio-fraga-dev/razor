import { useEffect, useState } from "react";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { AutomationKind, AutomationSettings } from "../api/types";

/**
 * Automações (V47): lista de espera automática, campanhas (sumiu, aniversário, retorno do corte)
 * e relatório semanal do dono. Card autocontido — busca e salva em
 * `/api/admin/settings/automations`, como o anti-falta.
 */

const DIAS_SEMANA = ["", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"];
const VARIAVEIS: Record<AutomationKind, string> = {
  LAPSED: "{nome} {dias} {barbearia} {link}",
  BIRTHDAY: "{nome} {cupom} {desconto} {validade} {barbearia} {link}",
  RETURN: "{nome} {dias} {servico} {barbearia} {link}",
};

const onlyDigits = (v: string) => v.replace(/\D/g, "");

/** Converte e confere um inteiro entre min e max; null = inválido. */
function intIn(v: string, min: number, max: number): number | null {
  const n = Number(v);
  return v.trim() !== "" && Number.isInteger(n) && n >= min && n <= max ? n : null;
}

function TemplateEditor({
  kind,
  value,
  onChange,
  defaultText,
  disabled,
}: {
  kind: AutomationKind;
  value: string;
  onChange: (v: string) => void;
  defaultText?: string;
  disabled: boolean;
}) {
  const toast = useToast();
  const [preview, setPreview] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const showPreview = async () => {
    setLoading(true);
    try {
      const r = await api.post<{ text: string }>("/api/admin/settings/automations/preview", { kind, template: value });
      setPreview(r.text);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Não consegui gerar a prévia.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="stack" style={{ gap: 6 }}>
      <div className="field">
        <label>
          Mensagem
          <textarea
            className="input"
            rows={5}
            maxLength={1000}
            value={value}
            disabled={disabled}
            onChange={(e) => {
              onChange(e.target.value);
              setPreview(null);
            }}
            style={{ resize: "vertical", fontFamily: "inherit" }}
          />
        </label>
      </div>
      <div className="row wrap" style={{ gap: 8, alignItems: "center" }}>
        <span className="muted" style={{ fontSize: 12 }}>Variáveis: {VARIAVEIS[kind]}</span>
        <span style={{ flex: 1 }} />
        {defaultText && value.trim() !== defaultText.trim() && (
          <button type="button" className="btn btn-ghost btn-sm" disabled={disabled}
            onClick={() => { onChange(defaultText); setPreview(null); }}>
            Restaurar padrão
          </button>
        )}
        <button type="button" className="btn btn-ghost btn-sm" onClick={showPreview} disabled={loading}>
          {loading ? "Gerando…" : "Ver prévia"}
        </button>
      </div>
      {preview != null && (
        <div
          className="muted"
          style={{ whiteSpace: "pre-wrap", fontSize: 13, background: "var(--surface-2)", borderRadius: 8, padding: 10 }}
        >
          {preview}
        </div>
      )}
    </div>
  );
}

export function AutomationSettingsCard({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error, reload } = useApi<AutomationSettings>(
    () => api.get("/api/admin/settings/automations"),
    [reloadKey],
  );
  const toast = useToast();
  const [d, setD] = useState<AutomationSettings | null>(null);
  // Campos numéricos como texto: o dono pode apagar e digitar de novo sem o campo "pular".
  const [num, setNum] = useState<Record<string, string>>({});
  const [returnDays, setReturnDays] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!data) return;
    setD(data);
    setNum({
      waitlistWindowMinutes: String(data.waitlistWindowMinutes),
      lapsedDays: String(data.lapsedDays),
      birthdayDiscountPercent: String(data.birthdayDiscountPercent).replace(".", ","),
      birthdayCouponValidDays: String(data.birthdayCouponValidDays),
      frequencyCapDays: String(data.frequencyCapDays),
      weeklyReportHour: String(data.weeklyReportHour),
    });
    setReturnDays(Object.fromEntries(data.services.map((s) => [s.id, s.returnReminderDays != null ? String(s.returnReminderDays) : ""])));
  }, [data]);

  const set = (patch: Partial<AutomationSettings>) => setD((cur) => (cur ? { ...cur, ...patch } : cur));
  const setN = (k: string, v: string) => setNum((cur) => ({ ...cur, [k]: v }));

  const save = async () => {
    if (!d) return;
    const window = intIn(num.waitlistWindowMinutes, 5, 240);
    const lapsedDays = intIn(num.lapsedDays, 7, 365);
    const discount = Number((num.birthdayDiscountPercent ?? "").replace(",", "."));
    const validDays = intIn(num.birthdayCouponValidDays, 1, 90);
    const cap = intIn(num.frequencyCapDays, 1, 60);
    const hour = intIn(num.weeklyReportHour, 0, 23);
    if (window == null) return toast.error("Prazo para responder a vaga: de 5 a 240 minutos.");
    if (lapsedDays == null) return toast.error("“Sumiu há”: de 7 a 365 dias.");
    if (!Number.isFinite(discount) || discount < 1 || discount > 100) return toast.error("Desconto de aniversário: de 1% a 100%.");
    if (validDays == null) return toast.error("Validade do cupom: de 1 a 90 dias.");
    if (cap == null) return toast.error("Intervalo entre mensagens: de 1 a 60 dias.");
    if (hour == null) return toast.error("Hora do relatório: de 0 a 23.");
    const services = [];
    for (const s of d.services) {
      const raw = (returnDays[s.id] ?? "").trim();
      const days = raw === "" ? null : intIn(raw, 7, 180);
      if (raw !== "" && days == null) return toast.error(`Retorno de “${s.name}”: de 7 a 180 dias — ou vazio para não lembrar.`);
      services.push({ id: s.id, name: s.name, returnReminderDays: days });
    }
    setSaving(true);
    try {
      await api.put("/api/admin/settings/automations", {
        ...d,
        waitlistWindowMinutes: window,
        lapsedDays,
        birthdayDiscountPercent: discount,
        birthdayCouponValidDays: validDays,
        frequencyCapDays: cap,
        weeklyReportHour: hour,
        services,
        defaultTemplates: undefined,
        stats: undefined,
      });
      toast.success("Automações salvas ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const ligadas = d ? [d.waitlistEnabled, d.lapsedEnabled, d.birthdayEnabled, d.returnEnabled, d.weeklyReportEnabled].filter(Boolean).length : 0;
  const hr = <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "6px 0" }} />;

  return (
    <div className="card">
      <div className="card-head">
        <h3>Automações</h3>
        {d && <span className={`badge ${ligadas ? "badge-green" : "badge-muted"}`}>{ligadas} de 5 ligadas</span>}
      </div>
      <div className="card-pad">
        {loading && !d ? (
          <SkeletonRows rows={4} />
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : d ? (
          <div className="stack">
            {/* ---------------- lista de espera */}
            <h4 style={{ margin: 0 }}>Lista de espera automática</h4>
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              Quando um horário é desmarcado, o bot oferece a vaga ao primeiro da lista que combina (dia, período e
              barbeiro). Ele responde <em>SIM</em> para marcar; sem resposta no prazo, a vaga vai para o próximo.
              {d.stats && <> Hoje há <strong>{d.stats.waitlistWaiting}</strong> cliente(s) esperando.</>}
            </p>
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={d.waitlistEnabled} onChange={(e) => set({ waitlistEnabled: e.target.checked })} />
              Oferecer vagas liberadas para a lista de espera
            </label>
            <div className="field">
              <label>Prazo para o cliente responder (minutos)
                <input className="input" inputMode="numeric" value={num.waitlistWindowMinutes ?? ""} disabled={!d.waitlistEnabled}
                  onChange={(e) => setN("waitlistWindowMinutes", onlyDigits(e.target.value))} style={{ maxWidth: 120 }} />
              </label>
            </div>

            {hr}
            {/* ---------------- campanhas */}
            <h4 style={{ margin: 0 }}>Campanhas automáticas</h4>
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              Saem só com a barbearia aberta, no máximo uma mensagem por cliente a cada N dias (somando as três), e
              nunca para quem respondeu <em>parar</em>. Toda mensagem leva o aviso de como parar de receber.
            </p>
            <div className="field">
              <label>Intervalo mínimo entre mensagens para o mesmo cliente (dias)
                <input className="input" inputMode="numeric" value={num.frequencyCapDays ?? ""}
                  onChange={(e) => setN("frequencyCapDays", onlyDigits(e.target.value))} style={{ maxWidth: 120 }} />
              </label>
            </div>

            <label className="row" style={{ gap: 8, fontWeight: 600 }}>
              <input type="checkbox" checked={d.lapsedEnabled} onChange={(e) => set({ lapsedEnabled: e.target.checked })} />
              Cliente sumido
              {d.stats && <span className="muted" style={{ fontWeight: 400, fontSize: 13 }}>· {d.stats.lapsedSent30d} enviada(s) em 30 dias</span>}
            </label>
            <div className="field">
              <label>Mandar quando a última visita passar de (dias)
                <input className="input" inputMode="numeric" value={num.lapsedDays ?? ""} disabled={!d.lapsedEnabled}
                  onChange={(e) => setN("lapsedDays", onlyDigits(e.target.value))} style={{ maxWidth: 120 }} />
              </label>
            </div>
            <TemplateEditor kind="LAPSED" value={d.lapsedTemplate} onChange={(v) => set({ lapsedTemplate: v })}
              defaultText={d.defaultTemplates?.LAPSED} disabled={!d.lapsedEnabled} />

            <label className="row" style={{ gap: 8, fontWeight: 600, marginTop: 8 }}>
              <input type="checkbox" checked={d.birthdayEnabled} onChange={(e) => set({ birthdayEnabled: e.target.checked })} />
              Aniversário com cupom
              {d.stats && <span className="muted" style={{ fontWeight: 400, fontSize: 13 }}>· {d.stats.birthdaySent30d} enviada(s) em 30 dias</span>}
            </label>
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              Ligado, o bot pergunta o aniversário uma vez, no fim de um agendamento. O cupom é criado na hora do envio.
            </p>
            <div className="row wrap" style={{ gap: 12 }}>
              <div className="field">
                <label>Desconto (%)
                  <input className="input" inputMode="decimal" value={num.birthdayDiscountPercent ?? ""} disabled={!d.birthdayEnabled}
                    onChange={(e) => setN("birthdayDiscountPercent", e.target.value.replace(/[^\d,.]/g, ""))} style={{ maxWidth: 120 }} />
                </label>
              </div>
              <div className="field">
                <label>Validade do cupom (dias)
                  <input className="input" inputMode="numeric" value={num.birthdayCouponValidDays ?? ""} disabled={!d.birthdayEnabled}
                    onChange={(e) => setN("birthdayCouponValidDays", onlyDigits(e.target.value))} style={{ maxWidth: 120 }} />
                </label>
              </div>
            </div>
            <TemplateEditor kind="BIRTHDAY" value={d.birthdayTemplate} onChange={(v) => set({ birthdayTemplate: v })}
              defaultText={d.defaultTemplates?.BIRTHDAY} disabled={!d.birthdayEnabled} />

            <label className="row" style={{ gap: 8, fontWeight: 600, marginTop: 8 }}>
              <input type="checkbox" checked={d.returnEnabled} onChange={(e) => set({ returnEnabled: e.target.checked })} />
              Lembrete de retorno do corte
              {d.stats && <span className="muted" style={{ fontWeight: 400, fontSize: 13 }}>· {d.stats.returnSent30d} enviada(s) em 30 dias</span>}
            </label>
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              Para cada serviço, depois de quantos dias lembrar o cliente de voltar. Vazio = não lembrar.
            </p>
            <div className="row wrap" style={{ gap: 12 }}>
              {d.services.map((s) => (
                <div className="field" key={s.id}>
                  <label>{s.name}
                    <input className="input" inputMode="numeric" placeholder="—" value={returnDays[s.id] ?? ""}
                      disabled={!d.returnEnabled}
                      onChange={(e) => setReturnDays((cur) => ({ ...cur, [s.id]: onlyDigits(e.target.value) }))}
                      style={{ maxWidth: 110 }} />
                  </label>
                </div>
              ))}
              {d.services.length === 0 && <span className="muted" style={{ fontSize: 13 }}>Nenhum serviço ativo no catálogo.</span>}
            </div>
            <TemplateEditor kind="RETURN" value={d.returnTemplate} onChange={(v) => set({ returnTemplate: v })}
              defaultText={d.defaultTemplates?.RETURN} disabled={!d.returnEnabled} />

            {hr}
            {/* ---------------- relatorio semanal */}
            <h4 style={{ margin: 0 }}>Relatório semanal no WhatsApp</h4>
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              Faturamento da semana contra a anterior, atendimentos, faltas, destaque da equipe, horários mais vazios
              da próxima semana e o que a IA fez. Vai para os números de administrador.
            </p>
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={d.weeklyReportEnabled} onChange={(e) => set({ weeklyReportEnabled: e.target.checked })} />
              Enviar o relatório semanal
            </label>
            <div className="row wrap" style={{ gap: 12 }}>
              <div className="field">
                <label>Dia
                  <select className="input" value={d.weeklyReportDay} disabled={!d.weeklyReportEnabled}
                    onChange={(e) => set({ weeklyReportDay: Number(e.target.value) })} style={{ maxWidth: 160 }}>
                    {DIAS_SEMANA.slice(1).map((nome, i) => <option key={nome} value={i + 1}>{nome}</option>)}
                  </select>
                </label>
              </div>
              <div className="field">
                <label>Hora
                  <input className="input" inputMode="numeric" value={num.weeklyReportHour ?? ""} disabled={!d.weeklyReportEnabled}
                    onChange={(e) => setN("weeklyReportHour", onlyDigits(e.target.value))} style={{ maxWidth: 100 }} />
                </label>
              </div>
            </div>

            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar automações"}
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
