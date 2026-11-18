// "Fotos enviadas": tudo que o dono já subiu, para reaproveitar ou apagar.
//
// A foto que está no site SALVO não pode ser apagada (o backend recusa com 409): apagaria um
// pedaço do site que está no ar. A que está só no rascunho também fica protegida aqui — o dono
// tira do rascunho primeiro. O resto some sozinho um dia depois de sair do site (faxina no
// salvamento), então isto é para quem quer arrumar antes ou bateu no teto de 40.
import { useState } from "react";
import { api, ApiError } from "../../api/client";
import { useApi } from "../../lib/useApi";
import { useToast } from "../Toast";

export interface SiteImageInfo {
  id: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  /** Está no site salvo (logo, capa ou galeria). */
  inUse: boolean;
}

const MAX_IMAGES = 40;
const kb = (n: number) => (n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.round(n / 1024)} KB`);

export function PhotoLibrary({
  reloadKey,
  inDraft,
  onUseInGallery,
  galleryFull,
}: {
  reloadKey: number;
  /** URLs presentes no rascunho atual. */
  inDraft: Set<string>;
  onUseInGallery: (url: string) => void;
  galleryFull: boolean;
}) {
  const toast = useToast();
  const { data, loading, error, reload } = useApi<SiteImageInfo[]>(() => api.get("/api/admin/site/images"), [reloadKey]);
  const [busy, setBusy] = useState<string | null>(null);

  const remove = async (img: SiteImageInfo) => {
    setBusy(img.id);
    try {
      await api.del(`/api/admin/site/images/${img.id}`);
      toast.success("Foto apagada");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Não foi possível apagar.");
    } finally {
      setBusy(null);
    }
  };

  const list = data ?? [];
  return (
    <details className="se-adv se-library">
      <summary>
        Fotos enviadas {data ? `(${list.length}/${MAX_IMAGES})` : ""}
      </summary>
      {loading && !data ? (
        <p className="se-hint">Carregando…</p>
      ) : error ? (
        <p className="se-error">{error}</p>
      ) : list.length === 0 ? (
        <p className="se-hint">Nenhuma foto enviada ainda.</p>
      ) : (
        <ul className="se-lib-grid">
          {list.map((img) => {
            const draft = inDraft.has(img.url);
            const locked = img.inUse || draft;
            return (
              <li key={img.id} className="se-lib-item">
                <img src={img.url} alt="" loading="lazy" />
                <span className="se-lib-meta">
                  {img.inUse ? "no site" : draft ? "no rascunho" : "sem uso"} · {kb(img.sizeBytes)}
                </span>
                <div className="row wrap" style={{ gap: 6 }}>
                  {!draft && !galleryFull && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => onUseInGallery(img.url)}>
                      Pôr na galeria
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    disabled={locked || busy === img.id}
                    title={locked ? "Tire a foto do site e salve antes de apagar." : undefined}
                    onClick={() => remove(img)}
                  >
                    {busy === img.id ? "Apagando…" : "Apagar"}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
}
