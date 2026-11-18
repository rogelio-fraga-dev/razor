import { initials, isSafePhoto } from "../lib/photo";

/**
 * Avatar do barbeiro: a foto do perfil (V37) ou as iniciais num disco de acento.
 * Usado em Profissionais e Comissões; a Agenda pode usar o mesmo com `photoUrl` do DTO.
 */
export function Avatar({ name, photoUrl, size = 40 }: { name: string; photoUrl?: string | null; size?: number }) {
  const style = { width: size, height: size, fontSize: Math.round(size * 0.38) };
  if (isSafePhoto(photoUrl)) {
    return <img className="avatar" src={photoUrl} alt="" style={style} />;
  }
  return (
    <span className="avatar avatar-initials" style={style} aria-hidden="true">
      {initials(name)}
    </span>
  );
}
