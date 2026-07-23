// Feedback assíncrono padronizado (UX_BLUEPRINT §5): toasts no canto inferior direito,
// auto-dismiss (3.5s sucesso / 6s erro), um lugar só para "salvou/deu errado".
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

interface ToastItem {
  id: number;
  kind: "success" | "error";
  text: string;
}

interface ToastApi {
  success: (text: string) => void;
  error: (text: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast precisa do <ToastProvider>.");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const push = useCallback((kind: ToastItem["kind"], text: string) => {
    const id = nextId.current++;
    setItems((list) => [...list, { id, kind, text }]);
    const ttl = kind === "error" ? 6000 : 3500;
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), ttl);
  }, []);

  const api: ToastApi = {
    success: (text) => push("success", text),
    error: (text) => push("error", text),
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-wrap" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`}>
            <span className="toast-ico" aria-hidden="true">{t.kind === "success" ? "✓" : "✕"}</span>
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
