// Fotos do site: logo, capa e galeria por upload (V45), com "colar link" como opção avançada.
//
// O dono não precisa saber o que é URL: toca em "Enviar foto", escolhe da galeria do celular e
// pronto. A foto é reduzida no navegador antes de subir (lib/photo.ts) e volta como
// /api/site/img/<id>, que o site serve com cache longo.
import { useRef, useState, type DragEvent } from "react";
import { imageUrlError } from "../../site/contactRules";
import { safeHttpUrl } from "../../site/safeUrl";

export type UploadFn = (file: File, kind: "photo" | "logo") => Promise<string>;

export const MAX_GALLERY = 12;

/** Botão que abre o seletor de arquivo (o input fica escondido — o botão é o que se vê). */
function FileButton({
  label,
  multiple,
  disabled,
  onFiles,
  primary,
}: {
  label: string;
  multiple?: boolean;
  disabled?: boolean;
  onFiles: (files: File[]) => void;
  primary?: boolean;
}) {
  const ref = useRef<HTMLInputElement>(null);
  return (
    <>
      <button
        type="button"
        className={`btn btn-sm ${primary ? "btn-primary" : "btn-ghost"}`}
        disabled={disabled}
        onClick={() => ref.current?.click()}
      >
        {label}
      </button>
      <input
        ref={ref}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/*"
        multiple={multiple}
        hidden
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          e.target.value = ""; // permite escolher o mesmo arquivo de novo
          if (files.length) onFiles(files);
        }}
      />
    </>
  );
}

/** "Colar link" — escondido por padrão: é o caminho de quem já tem a foto hospedada. */
function PasteLink({ onAdd, cta }: { onAdd: (url: string) => void; cta: string }) {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const add = () => {
    const v = value.trim();
    const err = v ? imageUrlError(v) : "Cole o endereço da imagem.";
    setError(err);
    if (err) return;
    onAdd(v);
    setValue("");
  };
  return (
    <details className="se-adv">
      <summary>Colar link de uma foto</summary>
      <div className="row" style={{ gap: 8, marginTop: 8 }}>
        <input
          className={`input${error ? " input-invalid" : ""}`}
          value={value}
          placeholder="https://…"
          aria-label="Endereço da imagem"
          inputMode="url"
          onChange={(e) => {
            setValue(e.target.value);
            setError(null);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
        />
        <button type="button" className="btn btn-ghost btn-sm" onClick={add}>
          {cta}
        </button>
      </div>
      {error && (
        <span className="se-error" role="alert">
          {error}
        </span>
      )}
    </details>
  );
}

/** Uma foto (logo ou capa): miniatura + enviar/trocar/remover. */
export function PhotoPicker({
  label,
  hint,
  value,
  onChange,
  kind,
  upload,
}: {
  label: string;
  hint: string;
  value: string;
  onChange: (url: string) => void;
  kind: "photo" | "logo";
  upload: UploadFn;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const preview = safeHttpUrl(value);

  const send = async (file: File) => {
    setBusy(true);
    setError(null);
    try {
      onChange(await upload(file, kind));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível enviar a foto.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="se-photo" role="group" aria-label={label}>
      <div className={`se-photo-thumb ${kind === "logo" ? "is-logo" : ""}`}>
        {busy ? (
          <span className="se-photo-empty">Enviando…</span>
        ) : preview ? (
          <img src={preview} alt={`${label} atual`} loading="lazy" />
        ) : (
          <span className="se-photo-empty">Sem foto</span>
        )}
      </div>
      <div className="se-photo-side">
        <strong className="se-photo-label">{label}</strong>
        <span className="se-hint">{hint}</span>
        <div className="row wrap" style={{ gap: 8 }}>
          <FileButton label={value ? "Trocar foto" : "Enviar foto"} primary={!value} disabled={busy} onFiles={(f) => send(f[0])} />
          {value && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => onChange("")} disabled={busy}>
              Remover
            </button>
          )}
        </div>
        {error && (
          <span className="se-error" role="alert">
            {error}
          </span>
        )}
        <PasteLink cta="Usar" onAdd={onChange} />
      </div>
    </div>
  );
}

/** Galeria: até 12 fotos, reordenáveis (arrastar no computador, setas em qualquer tela). */
export function GalleryEditor({
  value,
  onChange,
  upload,
}: {
  value: string[];
  onChange: (v: string[]) => void;
  upload: UploadFn;
}) {
  const [pending, setPending] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  // Sempre a lista mais nova: vários envios terminam fora de ordem e cada um acrescenta a sua.
  const latest = useRef(value);
  latest.current = value;

  const room = MAX_GALLERY - value.length - pending;

  const addFiles = async (files: File[]) => {
    setError(null);
    const batch = files.slice(0, Math.max(0, room));
    if (files.length > batch.length) {
      setError(`A galeria guarda até ${MAX_GALLERY} fotos — ${files.length - batch.length} ficaram de fora.`);
    }
    setPending((p) => p + batch.length);
    for (const file of batch) {
      try {
        const url = await upload(file, "photo");
        latest.current = [...latest.current, url].slice(0, MAX_GALLERY);
        onChange(latest.current);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Não foi possível enviar uma das fotos.");
      } finally {
        setPending((p) => p - 1);
      }
    }
  };

  const move = (from: number, to: number) => {
    if (to < 0 || to >= value.length || from === to) return;
    const next = [...value];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  };

  const onDrop = (e: DragEvent, to: number) => {
    e.preventDefault();
    if (dragFrom !== null) move(dragFrom, to);
    setDragFrom(null);
  };

  return (
    <div className="se-gallery-wrap">
      <div className="se-label-row">
        <strong className="se-photo-label">Galeria</strong>
        <span className="se-count" aria-live="polite">
          {value.length}/{MAX_GALLERY} fotos
        </span>
      </div>
      <span className="se-hint">
        Cortes, ambiente, equipe. A primeira aparece em destaque — arraste ou use as setas para mudar a ordem.
      </span>
      <ol className="se-gallery">
        {value.map((url, i) => {
          const src = safeHttpUrl(url);
          return (
            <li
              key={`${url}-${i}`}
              className={`se-tile${dragFrom === i ? " dragging" : ""}`}
              draggable
              onDragStart={() => setDragFrom(i)}
              onDragEnd={() => setDragFrom(null)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => onDrop(e, i)}
            >
              {src ? <img src={src} alt={`Foto ${i + 1} da galeria`} loading="lazy" /> : <span className="se-photo-empty">Link inválido</span>}
              <span className="se-tile-n" aria-hidden="true">
                {i + 1}
              </span>
              <div className="se-tile-actions">
                <button type="button" onClick={() => move(i, i - 1)} disabled={i === 0} aria-label={`Mover a foto ${i + 1} para antes`}>
                  ↑
                </button>
                <button
                  type="button"
                  onClick={() => move(i, i + 1)}
                  disabled={i === value.length - 1}
                  aria-label={`Mover a foto ${i + 1} para depois`}
                >
                  ↓
                </button>
                <button type="button" onClick={() => onChange(value.filter((_, j) => j !== i))} aria-label={`Tirar a foto ${i + 1} da galeria`}>
                  ✕
                </button>
              </div>
            </li>
          );
        })}
        {Array.from({ length: pending }, (_, i) => (
          <li key={`pending-${i}`} className="se-tile is-pending" aria-hidden="true">
            <span className="se-photo-empty">Enviando…</span>
          </li>
        ))}
      </ol>
      <div className="row wrap" style={{ gap: 8 }}>
        <FileButton label="Adicionar fotos" multiple primary disabled={room <= 0} onFiles={addFiles} />
        {room <= 0 && <span className="se-hint">Galeria cheia: tire uma foto para pôr outra.</span>}
      </div>
      {error && (
        <span className="se-error" role="alert">
          {error}
        </span>
      )}
      {room > 0 && <PasteLink cta="Adicionar" onAdd={(url) => onChange([...value, url].slice(0, MAX_GALLERY))} />}
    </div>
  );
}
