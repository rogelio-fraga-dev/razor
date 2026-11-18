import { useRef, useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { Avatar } from "../components/Avatar";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { PanelUser, Professional, ProfessionalInput, ServiceOffering } from "../api/types";
import { EmptyState } from "../components/EmptyState";
import { Hint } from "../components/Hint";
import { Skeleton } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { percent } from "../lib/format";
import { photoToAvatarDataUri } from "../lib/photo";

interface Draft {
  id: string | null;
  name: string;
  phone: string;
  commissionPercent: string;
  /** Vazio = herda o percentual dos serviços (V23). */
  productCommissionPercent: string;
  active: boolean;
  // ---- perfil (V37) ----
  photoUrl: string | null;
  nickname: string;
  bio: string;
  specialties: string[];
  instagramUrl: string;
  /** Vazio = atende todos os serviços. */
  serviceIds: string[];
  /** Login do painel ligado a este barbeiro (V44); vazio = nenhum. */
  panelUserId: string;
}

const EMPTY: Draft = {
  id: null,
  name: "",
  phone: "",
  commissionPercent: "0",
  productCommissionPercent: "",
  active: true,
  photoUrl: null,
  nickname: "",
  bio: "",
  specialties: [],
  instagramUrl: "",
  serviceIds: [],
  panelUserId: "",
};

const MAX_BIO = 500;
const MAX_SPECIALTIES = 8;

function instagramHandle(url: string | null): string | null {
  if (!url) return null;
  const m = url.match(/instagram\.com\/([A-Za-z0-9._]+)/i);
  return m ? `@${m[1]}` : "Instagram";
}

export function Professionals() {
  const { data, loading, error, reload } = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);
  const services = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  // Logins do painel (V44): ligar um login a um barbeiro dá a ele o "Meu dia" (horários e
  // comissão dele). Se falhar, a tela segue — só o seletor de login some.
  const panelUsers = useApi<PanelUser[]>(() => api.get("/api/admin/panel-users"), []);
  const toast = useToast();
  const loginOf = (professionalId: string) => panelUsers.data?.find((u) => u.professionalId === professionalId) ?? null;
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [tagInput, setTagInput] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  // Cada abertura do formulário ganha um número. A foto é processada de forma assíncrona: se a
  // pessoa fechar e abrir OUTRO barbeiro nesse meio-tempo, o recorte antigo não pode cair nele.
  const draftSeq = useRef(0);

  const serviceName = (id: string) => services.data?.find((s) => s.id === id)?.name;

  const openNew = () => {
    setFormError(null);
    setTagInput("");
    draftSeq.current++;
    setPhotoBusy(false);
    setDraft({ ...EMPTY });
  };
  const openEdit = (p: Professional) => {
    setFormError(null);
    setTagInput("");
    draftSeq.current++;
    setPhotoBusy(false);
    setDraft({
      id: p.id,
      name: p.name,
      phone: p.phone ?? "",
      commissionPercent: String(p.commissionPercent),
      productCommissionPercent: p.productCommissionPercent == null ? "" : String(p.productCommissionPercent),
      active: p.active,
      photoUrl: p.photoUrl,
      nickname: p.nickname ?? "",
      bio: p.bio ?? "",
      specialties: p.specialties ?? [],
      instagramUrl: p.instagramUrl ?? "",
      serviceIds: p.serviceIds ?? [],
      panelUserId: loginOf(p.id)?.id ?? "",
    });
  };

  const onPickPhoto = async (file: File | undefined) => {
    if (!file || !draft) return;
    const seq = draftSeq.current;
    const draftId = draft.id;
    const sameDraft = () => seq === draftSeq.current;
    setPhotoBusy(true);
    setFormError(null);
    try {
      const uri = await photoToAvatarDataUri(file);
      setDraft((d) => (d && sameDraft() && d.id === draftId ? { ...d, photoUrl: uri } : d));
    } catch (e) {
      if (sameDraft()) setFormError(e instanceof Error ? e.message : "Não consegui processar a foto.");
    } finally {
      if (sameDraft()) setPhotoBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const addTag = () => {
    if (!draft) return;
    const tag = tagInput.trim().slice(0, 40);
    if (!tag) return;
    const exists = draft.specialties.some((t) => t.toLowerCase() === tag.toLowerCase());
    if (!exists && draft.specialties.length < MAX_SPECIALTIES) {
      setDraft({ ...draft, specialties: [...draft.specialties, tag] });
    }
    setTagInput("");
  };

  const toggleService = (id: string) => {
    if (!draft) return;
    const has = draft.serviceIds.includes(id);
    setDraft({ ...draft, serviceIds: has ? draft.serviceIds.filter((s) => s !== id) : [...draft.serviceIds, id] });
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setFormError(null);
    // Tag digitada e não confirmada com Enter também entra — senão some em silêncio.
    const pending = tagInput.trim();
    const specialties =
      pending && !draft.specialties.some((t) => t.toLowerCase() === pending.toLowerCase())
        ? [...draft.specialties, pending.slice(0, 40)].slice(0, MAX_SPECIALTIES)
        : draft.specialties;
    // Perfil sempre vai completo: vazio = "limpa" (nulo seria "não mexe" no backend).
    const payload: ProfessionalInput = {
      name: draft.name.trim(),
      phone: draft.phone.trim() || null,
      commissionPercent: Number(draft.commissionPercent.replace(",", ".")),
      // Vazio vira null de propósito: "herda o percentual dos serviços", diferente de 0.
      productCommissionPercent:
        draft.productCommissionPercent.trim() === ""
          ? null
          : Number(draft.productCommissionPercent.replace(",", ".")),
      active: draft.active,
      photoUrl: draft.photoUrl ?? "",
      nickname: draft.nickname.trim(),
      bio: draft.bio.trim(),
      specialties,
      instagramUrl: draft.instagramUrl.trim(),
      serviceIds: draft.serviceIds,
    };
    try {
      let id = draft.id;
      if (id) await api.put(`/api/admin/professionals/${id}`, payload);
      else id = (await api.post<Professional>("/api/admin/professionals", payload)).id;

      // Vínculo do login: desfaz o antigo (se trocou) e liga o novo.
      const previous = draft.id ? loginOf(draft.id)?.id ?? "" : "";
      if (previous !== draft.panelUserId) {
        if (previous) await api.put(`/api/admin/panel-users/${previous}/professional`, { professionalId: null });
        if (draft.panelUserId) await api.put(`/api/admin/panel-users/${draft.panelUserId}/professional`, { professionalId: id });
        panelUsers.reload();
      }
      toast.success(draft.id ? "Barbeiro salvo ✓" : "Barbeiro cadastrado ✓");
      setDraft(null);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const activeServices = (services.data ?? []).filter((s) => s.active);

  return (
    <Layout title="Profissionais" actions={<button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo barbeiro</button>}>
      <div className="card">
        <div className="card-head">
          <h3>Equipe</h3>
          {data && <span className="badge badge-muted">{data.length} profissionais</span>}
        </div>
        {loading && !data ? (
          <div className="pro-grid" aria-busy="true" aria-label="Carregando equipe">
            {[0, 1, 2].map((i) => (
              <div key={i} className="pro-card">
                <div className="pro-card-top">
                  <Skeleton w={56} h={56} style={{ borderRadius: 16 }} />
                  <div style={{ flex: 1 }}>
                    <Skeleton w="60%" h={14} />
                    <Skeleton w="30%" h={18} style={{ display: "block", marginTop: 8, borderRadius: 999 }} />
                  </div>
                </div>
                <Skeleton w="90%" h={12} style={{ display: "block", marginTop: 14 }} />
              </div>
            ))}
          </div>
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="✂"
            title="Cadastre seu primeiro barbeiro."
            hint="A IA já começa a agendar para ele, e cada barbeiro vira uma coluna da agenda."
            action={<button className="btn btn-primary" onClick={openNew}>+ Novo barbeiro</button>}
          />
        ) : (
          <div className="pro-grid">
            {data.map((p) => (
              <article key={p.id} className={`pro-card ${p.active ? "" : "pro-card-inactive"}`}>
                <div className="pro-card-top">
                  <Avatar name={p.name} photoUrl={p.photoUrl} size={56} />
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div className="pro-card-name">{p.displayName || p.name}</div>
                    {p.nickname && <div className="faint pro-card-sub">{p.name}</div>}
                    <div style={{ marginTop: 4 }}>
                      {p.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Inativo</span>}
                    </div>
                  </div>
                  <button className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>Editar</button>
                </div>

                {p.bio && <p className="pro-card-bio">{p.bio}</p>}

                {p.specialties.length > 0 && (
                  <div className="pro-chips">
                    {p.specialties.map((t) => (
                      <span key={t} className="pro-chip">{t}</span>
                    ))}
                  </div>
                )}

                <dl className="pro-card-facts">
                  <div>
                    <dt>Serviços</dt>
                    <dd>{p.serviceIds.length === 0 ? "Todos" : p.serviceIds.map(serviceName).filter(Boolean).join(", ") || `${p.serviceIds.length} selecionados`}</dd>
                  </div>
                  <div>
                    <dt>Comissão</dt>
                    <dd>
                      {percent(p.commissionPercent)}
                      <span className="faint"> · produtos {percent(p.productCommissionPercent ?? p.commissionPercent)}</span>
                    </dd>
                  </div>
                  {panelUsers.data && (
                    <div>
                      <dt>Login</dt>
                      <dd>{loginOf(p.id) ? <span className="muted">{loginOf(p.id)!.email}</span> : <span className="faint">sem login no painel</span>}</dd>
                    </div>
                  )}
                  {(p.phone || p.instagramUrl) && (
                    <div>
                      <dt>Contato</dt>
                      <dd>
                        {p.phone && <span className="muted">{p.phone}</span>}
                        {p.phone && p.instagramUrl && " · "}
                        {p.instagramUrl && /^https?:\/\//i.test(p.instagramUrl) && (
                          <a href={p.instagramUrl} target="_blank" rel="noopener noreferrer">{instagramHandle(p.instagramUrl)}</a>
                        )}
                      </dd>
                    </div>
                  )}
                </dl>
              </article>
            ))}
          </div>
        )}
      </div>

      {draft && (
        <Modal
          title={draft.id ? "Editar barbeiro" : "Novo barbeiro"}
          size="wide"
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={save} disabled={saving || photoBusy || !draft.name.trim()}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="pro-photo-row">
              <Avatar name={draft.name || "?"} photoUrl={draft.photoUrl} size={88} />
              <div className="stack" style={{ gap: 8 }}>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => fileRef.current?.click()} disabled={photoBusy}>
                    {photoBusy ? "Processando…" : draft.photoUrl ? "Trocar foto" : "Enviar foto"}
                  </button>
                  {draft.photoUrl && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDraft({ ...draft, photoUrl: null })}>
                      Remover
                    </button>
                  )}
                </div>
                <span className="faint" style={{ fontSize: 12 }}>
                  Recortamos o centro em quadrado e reduzimos para 256px. Aparece na agenda, nas comissões e no site.
                </span>
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  hidden
                  onChange={(e) => onPickPhoto(e.target.files?.[0])}
                />
              </div>
            </div>

            <div className="form-grid">
              <div className="field">
                <label>Nome
                  <input className="input" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                </label>
              </div>
              <div className="field">
                <label>Apelido (como o bot chama)
                  <input
                    className="input"
                    value={draft.nickname}
                    maxLength={60}
                    placeholder={draft.name.split(" ")[0] || "Ex.: Diego"}
                    onChange={(e) => setDraft({ ...draft, nickname: e.target.value })}
                  />
                </label>
              </div>
            </div>

            <div className="field">
              <label>Bio curta
                <textarea
                  className="input"
                  rows={3}
                  maxLength={MAX_BIO}
                  value={draft.bio}
                  placeholder="10 anos de navalha, especialista em degradê."
                  onChange={(e) => setDraft({ ...draft, bio: e.target.value })}
                />
              </label>
              <span className="faint" style={{ fontSize: 12 }}>{draft.bio.length}/{MAX_BIO} · aparece na página da equipe do site</span>
            </div>

            <div className="field">
              <label>Especialidades
                <input
                  className="input"
                  value={tagInput}
                  maxLength={40}
                  placeholder={draft.specialties.length >= MAX_SPECIALTIES ? `Máximo de ${MAX_SPECIALTIES}` : "Digite e tecle Enter (ex.: Degradê)"}
                  disabled={draft.specialties.length >= MAX_SPECIALTIES}
                  onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === ",") {
                      e.preventDefault();
                      addTag();
                    } else if (e.key === "Backspace" && !tagInput && draft.specialties.length > 0) {
                      setDraft({ ...draft, specialties: draft.specialties.slice(0, -1) });
                    }
                  }}
                />
              </label>
              {draft.specialties.length > 0 && (
                <div className="pro-chips">
                  {draft.specialties.map((t) => (
                    <span key={t} className="pro-chip">
                      {t}
                      <button
                        type="button"
                        aria-label={`Remover ${t}`}
                        onClick={() => setDraft({ ...draft, specialties: draft.specialties.filter((x) => x !== t) })}
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div className="form-grid">
              <div className="field">
                <label>Instagram (opcional)
                  <input
                    className="input"
                    value={draft.instagramUrl}
                    placeholder="@perfil ou https://instagram.com/perfil"
                    onChange={(e) => setDraft({ ...draft, instagramUrl: e.target.value })}
                  />
                </label>
              </div>
              <div className="field">
                <label>Telefone (opcional)
                  <input className="input" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} />
                </label>
              </div>
            </div>

            <fieldset className="pro-services">
              <legend>Serviços que atende</legend>
              <span className="faint" style={{ fontSize: 12 }}>
                {draft.serviceIds.length === 0
                  ? "Nenhum marcado = atende todos. Marque só os que ele faz: quem não faz barba não recebe barba."
                  : `${draft.serviceIds.length} marcado(s) — a IA só agenda com ele estes serviços.`}
              </span>
              {services.loading ? (
                <span className="faint">Carregando serviços…</span>
              ) : activeServices.length === 0 ? (
                <span className="faint">Cadastre serviços em Serviços para restringir por barbeiro.</span>
              ) : (
                <div className="pro-services-list">
                  {activeServices.map((s) => (
                    <label key={s.id} className="pro-service-check">
                      <input type="checkbox" checked={draft.serviceIds.includes(s.id)} onChange={() => toggleService(s.id)} />
                      {s.name}
                    </label>
                  ))}
                </div>
              )}
            </fieldset>

            <div className="form-grid">
              <div className="field">
                <label>Comissão em serviços (%)
                  <input className="input" inputMode="decimal" value={draft.commissionPercent} onChange={(e) => setDraft({ ...draft, commissionPercent: e.target.value })} />
                </label>
              </div>
              <div className="field">
                <label>Comissão em produtos (%)
                  <input
                    className="input"
                    inputMode="decimal"
                    value={draft.productCommissionPercent}
                    placeholder={`Vazio = mesmo dos serviços (${draft.commissionPercent || 0}%)`}
                    onChange={(e) => setDraft({ ...draft, productCommissionPercent: e.target.value })}
                  />
                </label>
              </div>
            </div>
            <span className="faint" style={{ fontSize: 12, marginTop: -6 }}>
              <Hint term="comissaoProduto" label="comissão por produto" align="start" />{" "}
              Muitas barbearias pagam menos em produto. Deixe <strong>vazio</strong> para usar o
              mesmo percentual dos serviços — <strong>0</strong> é diferente: significa não pagar
              comissão de produto.
            </span>
            {panelUsers.data && panelUsers.data.length > 0 && (
              <div className="field">
                <label>Login do painel
                  <select
                    className="input"
                    value={draft.panelUserId}
                    onChange={(e) => setDraft({ ...draft, panelUserId: e.target.value })}
                  >
                    <option value="">Nenhum (ele não entra no painel)</option>
                    {panelUsers.data.map((u) => {
                      const takenBy = u.professionalId && u.professionalId !== draft.id;
                      return (
                        <option key={u.id} value={u.id} disabled={!!takenBy}>
                          {(u.name || u.email) + ` · ${u.email}`}
                          {u.role === "OWNER" ? " (dono)" : u.role === "RECEPTION" ? " (recepção)" : ""}
                          {takenBy ? " — já ligado a outro barbeiro" : ""}
                        </option>
                      );
                    })}
                  </select>
                </label>
                <span className="faint" style={{ fontSize: 12 }}>
                  Com o login ligado, ele entra no painel e vê o <strong>Meu dia</strong>: os próprios horários e
                  quanto ganhou hoje e na quinzena — nunca a comissão dos colegas.
                </span>
              </div>
            )}
            {draft.id && <Switch checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="Barbeiro ativo" />}
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
