// Bloco de pareamento do WhatsApp (QR + estado). Usado no card de Configurações e no
// passo de conexão do assistente — o estado vem do hook, aqui é só a tela.
import type { WhatsAppConnection } from "../lib/useWhatsAppConnection";

export function WhatsAppPairing({ conn }: { conn: WhatsAppConnection }) {
  if (conn.loading) return <div className="muted">Carregando…</div>;

  return (
    <div className="stack">
      <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
        Conecte o número de WhatsApp <strong>da barbearia</strong> escaneando o QR code pelo
        app — sem sair daqui.
      </p>

      {conn.connected ? (
        <div className="row" style={{ gap: 12 }}>
          <span className="badge badge-green">WhatsApp conectado ✓</span>
          <button className="btn btn-danger btn-sm" onClick={conn.disconnect} disabled={conn.busy}>
            {conn.busy ? "…" : "Desconectar"}
          </button>
        </div>
      ) : conn.qr ? (
        <>
          <img
            src={conn.qr}
            alt="QR code do WhatsApp"
            width={260}
            height={260}
            style={{ borderRadius: 12, border: "1px solid var(--border)", background: "#fff", padding: 8 }}
          />
          <span className="faint" style={{ fontSize: 13 }}>
            No celular: WhatsApp → <b>Aparelhos conectados</b> → <b>Conectar um aparelho</b> →
            aponte para o QR. Ele expira em ~40s; gere um novo se precisar.
          </span>
          {conn.pairingCode && (
            <div className="faint" style={{ fontSize: 13 }}>
              Código de pareamento: <b>{conn.pairingCode}</b>
            </div>
          )}
          <button
            className="btn btn-ghost btn-sm"
            onClick={conn.connect}
            disabled={conn.busy}
            style={{ alignSelf: "flex-start" }}
          >
            {conn.busy ? "Gerando…" : "Gerar novo QR"}
          </button>
        </>
      ) : (
        <button
          className="btn btn-primary"
          onClick={conn.connect}
          disabled={conn.busy}
          style={{ alignSelf: "flex-start" }}
        >
          {conn.busy ? "Conectando…" : "Conectar WhatsApp"}
        </button>
      )}

      {conn.error && <div className="error">{conn.error}</div>}
    </div>
  );
}
