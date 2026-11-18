// Foto de perfil do barbeiro (V37).
//
// Não há storage de objetos: a foto vai como data URI numa coluna TEXT. Para caber (~20-40KB) e
// ficar bonita em qualquer avatar redondo, o recorte é feito AQUI, no navegador: quadrado
// central, 256px, JPEG. O backend recusa acima de 60KB e qualquer coisa que não seja
// `data:image/jpeg|png|webp;base64,` — então este passo não é opcional.

export const PHOTO_SIZE = 256;
/** Mesmo teto do backend (ProfessionalProfileRules.MAX_PHOTO_BYTES). */
export const MAX_PHOTO_BYTES = 60 * 1024;

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("Não consegui abrir essa imagem. Tente um JPG ou PNG."));
    };
    img.src = url;
  });
}

/** Bytes reais de um data URI base64 (o que o backend mede). */
export function dataUriBytes(uri: string): number {
  const b64 = uri.slice(uri.indexOf(",") + 1);
  const pad = b64.endsWith("==") ? 2 : b64.endsWith("=") ? 1 : 0;
  return Math.floor((b64.length * 3) / 4) - pad;
}

/**
 * Recorta o quadrado central da imagem, reduz para 256×256 e devolve um data URI JPEG.
 * Se mesmo assim passar do limite (foto muito "ruidosa"), baixa a qualidade até caber.
 */
export async function photoToAvatarDataUri(file: File): Promise<string> {
  if (!file.type.startsWith("image/")) throw new Error("Escolha um arquivo de imagem.");
  const img = await loadImage(file);
  const side = Math.min(img.naturalWidth, img.naturalHeight);
  if (!side) throw new Error("Imagem vazia.");
  const sx = (img.naturalWidth - side) / 2;
  const sy = (img.naturalHeight - side) / 2;

  const canvas = document.createElement("canvas");
  canvas.width = PHOTO_SIZE;
  canvas.height = PHOTO_SIZE;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Seu navegador não conseguiu processar a imagem.");
  // Fundo branco: PNG com transparência vira JPEG sem "buraco preto".
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, PHOTO_SIZE, PHOTO_SIZE);
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(img, sx, sy, side, side, 0, 0, PHOTO_SIZE, PHOTO_SIZE);

  for (const quality of [0.85, 0.75, 0.65, 0.5, 0.4]) {
    const uri = canvas.toDataURL("image/jpeg", quality);
    if (dataUriBytes(uri) <= MAX_PHOTO_BYTES) return uri;
  }
  throw new Error("Não consegui deixar a foto abaixo de 60KB. Tente outra imagem.");
}

/** Só renderiza como foto o que é data URI de imagem — defesa extra além do backend. */
export function isSafePhoto(uri: string | null | undefined): uri is string {
  return !!uri && /^data:image\/(jpeg|png|webp);base64,/.test(uri);
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0].charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}
