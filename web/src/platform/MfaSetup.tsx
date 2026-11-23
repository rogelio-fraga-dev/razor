// Configuração do 2FA do console (V56): QR code (gerado no nosso servidor), chave manual com
// copiar, link para abrir no app, a mesma chave em todas as tentativas e "gerar novo QR".
// Usado no login (prazo vencido, com o desafio) e na aba Segurança (com a sessão).
import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { papi, type SetupInfo } from "./api";

export function CodeInput({ value, onChange, label = "Código de 6 dígitos do app", autoSubmit }: {
  value: string; onChange: (v: string) => void; label?: string; autoSubmit?: (v: string) => void;
}) {
  const [left, setLeft] = useState(30 - (Math.floor(Date.now() / 1000) % 30));
  useEffect(() => {
    const t = window.setInterval(() => setLeft(30 - (Math.floor(Date.now() / 1000) % 30)), 1000);
    return () => window.clearInterval(t);
  }, []);
  return (
    <div className="field">
      <label>{label}
        <input className="input plat-code" inputMode="numeric" autoComplete="one-time-code" placeholder="000 000"
          value={value.length > 3 ? `${value.slice(0, 3)} ${value.slice(3)}` : value}
          onChange={(e) => {
            // Aceita colar "123 456" ou "123-456": fica só com os números.
            const v = e.target.value.replace(/\D/g, "").slice(0, 6);
            onChange(v);
            if (v.length === 6) autoSubmit?.(v);
          }} />
      </label>
      <span className="muted" style={{ fontSize: 12.5 }}>
        Os números mudam a cada 30 s — o atual troca em {left}s. Digite o que o app mostrar na hora.
      </span>
    </div>
  );
}

export function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [saved, setSaved] = useState(false);
  const text = `Razor Plataforma — códigos de recuperação\n(cada um vale uma vez; use se perder o celular)\n\n${codes.join("\n")}\n`;
  const download = () => {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
    a.download = "razor-codigos-de-recuperacao.txt";
    a.click();
    URL.revokeObjectURL(a.href);
  };
  return (
    <div className="stack">
      <p style={{ margin: 0 }}><b>✅ Verificação em duas etapas ativada.</b></p>
      <p style={{ margin: 0 }}>
        Guarde estes <b>códigos de recuperação</b> num lugar seguro (gerenciador de senhas, papel guardado). Cada um
        entra uma vez, se você perder o celular. <b>Eles não aparecem de novo.</b>
      </p>
      <pre className="plat-secret" style={{ columns: 2, whiteSpace: "pre" }}>{codes.join("\n")}</pre>
      <div className="row wrap" style={{ gap: 8 }}>
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => navigator.clipboard?.writeText(text)}>Copiar</button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={download}>Baixar .txt</button>
      </div>
      <label className="row" style={{ gap: 8 }}>
        <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} /> Guardei os códigos
      </label>
      <button className="btn btn-primary" disabled={!saved} onClick={onDone}>Continuar</button>
    </div>
  );
}

/** {@code challenge} = configuração obrigatória no login; sem ele, usa a sessão (aba Segurança). */
export function MfaSetup({ challenge, onEnabled }: {
  challenge?: string;
  onEnabled: (recoveryCodes: string[]) => void;
}) {
  const [info, setInfo] = useState<SetupInfo | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const base = challenge ? "/auth/setup" : "/security/2fa/setup";

  const load = async (regenerate: boolean) => {
    setError(null);
    try {
      setInfo(await papi.post<SetupInfo>(`${base}/start`, challenge ? { challenge, regenerate } : { regenerate }));
      setCode("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não consegui gerar o QR.");
    }
  };

  useEffect(() => {
    load(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const confirm = async (c = code) => {
    if (c.length !== 6 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const r = await papi.post<{ recoveryCodes: string[] }>(`${base}/confirm`, challenge ? { challenge, code: c } : { code: c });
      onEnabled(r.recoveryCodes);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Código inválido.");
      setCode("");
    } finally {
      setBusy(false);
    }
  };

  const regenerate = () => {
    if (window.confirm("Gerar um QR novo? O que você já cadastrou no app deixa de valer — apague essa conta no app e leia o novo.")) {
      load(true);
    }
  };

  if (!info) return error ? <div className="error" role="alert">{error}</div> : <span className="muted">Gerando o QR…</span>;

  return (
    <div className="stack">
      <ol className="plat-steps">
        <li>
          Abra o app autenticador no celular — <b>Google Authenticator</b>, <b>Microsoft Authenticator</b>, <b>Authy</b>,
          <b> 1Password</b> ou o <b>Senhas</b> do iPhone (Ajustes → Senhas → item → Configurar código de verificação).
        </li>
        <li>Adicione uma conta e <b>leia o QR code</b> com a câmera:</li>
      </ol>
      {/* SVG gerado no nosso servidor (zxing) — a chave não passa por serviço de terceiros. */}
      <div className="plat-qr" dangerouslySetInnerHTML={{ __html: info.qrSvg }} />
      <details>
        <summary className="muted" style={{ cursor: "pointer" }}>Não consegue ler? Digite a chave no app</summary>
        <div className="stack" style={{ gap: 6, marginTop: 8 }}>
          <code className="plat-secret">{info.secret.match(/.{1,4}/g)?.join(" ")}</code>
          <div className="row wrap" style={{ gap: 8 }}>
            <button type="button" className="btn btn-ghost btn-sm"
              onClick={() => { navigator.clipboard?.writeText(info.secret); setCopied(true); }}>
              {copied ? "Copiada ✓" : "Copiar chave"}
            </button>
            <a className="btn btn-ghost btn-sm" href={info.otpauthUri}>Abrir no app (celular)</a>
          </div>
          <span className="muted" style={{ fontSize: 12.5 }}>Tipo: baseado em tempo (TOTP) · 6 dígitos · 30 segundos.</span>
        </div>
      </details>
      <ol className="plat-steps" start={3}>
        <li>Digite os <b>6 números</b> que o app mostra para "Razor Plataforma" (não a chave):</li>
      </ol>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); confirm(); }}>
        <CodeInput value={code} onChange={setCode} autoSubmit={(v) => confirm(v)} />
        {error && <div className="error" role="alert">{error}</div>}
        <div className="row wrap" style={{ gap: 8 }}>
          <button className="btn btn-primary" disabled={busy || code.length !== 6}>{busy ? "Conferindo…" : "Ativar"}</button>
          <button type="button" className="btn btn-ghost" onClick={regenerate} disabled={busy}>Gerar novo QR</button>
        </div>
      </form>
    </div>
  );
}
