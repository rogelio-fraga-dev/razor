// Conexão do WhatsApp (pareamento por QR). Vive num hook porque duas telas precisam do
// mesmo fluxo: o card de Configurações e o passo de conexão do assistente.
import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import type { WhatsAppConnect, WhatsAppStatus } from "../api/types";

/** De quanto em quanto tempo checamos o pareamento enquanto o QR está na tela. */
const POLL_MS = 3000;

export interface WhatsAppConnection {
  status: WhatsAppStatus | null;
  connected: boolean;
  qr: string | null;
  pairingCode: string | null;
  loading: boolean;
  busy: boolean;
  error: string | null;
  connect: () => Promise<void>;
  disconnect: () => Promise<void>;
  refreshStatus: () => Promise<WhatsAppStatus | null>;
}

export function useWhatsAppConnection(): WhatsAppConnection {
  const [status, setStatus] = useState<WhatsAppStatus | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshStatus = useCallback(async (): Promise<WhatsAppStatus | null> => {
    try {
      const s = await api.get<WhatsAppStatus>("/api/admin/whatsapp/status");
      setStatus(s);
      if (s.connected) {
        setQr(null);
        setPairingCode(null);
      }
      return s;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao consultar o WhatsApp.");
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshStatus();
  }, [refreshStatus]);

  // Enquanto o QR está na tela, verifica a conexão até o WhatsApp parear.
  useEffect(() => {
    if (!qr) return;
    const id = setInterval(async () => {
      const s = await refreshStatus();
      if (s?.connected) clearInterval(id);
    }, POLL_MS);
    return () => clearInterval(id);
  }, [qr, refreshStatus]);

  const connect = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<WhatsAppConnect>("/api/admin/whatsapp/connect");
      setStatus({ instanceName: r.instanceName, state: r.state, connected: r.state === "open" });
      if (r.state === "open") {
        setQr(null);
        setPairingCode(null);
      } else {
        setQr(r.qrCodeBase64);
        setPairingCode(r.pairingCode);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao conectar.");
    } finally {
      setBusy(false);
    }
  }, []);

  const disconnect = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post("/api/admin/whatsapp/disconnect");
      setQr(null);
      setPairingCode(null);
      await refreshStatus();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao desconectar.");
    } finally {
      setBusy(false);
    }
  }, [refreshStatus]);

  return {
    status,
    connected: status?.connected ?? false,
    qr,
    pairingCode,
    loading,
    busy,
    error,
    connect,
    disconnect,
    refreshStatus,
  };
}
