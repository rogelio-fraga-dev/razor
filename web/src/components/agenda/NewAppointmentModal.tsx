// Novo agendamento a partir de um horário vazio da grade: barbeiro, data e hora já vêm do clique.
// O cliente é buscado na base (nome ou telefone) ou cadastrado na hora pelo telefone.
import { useEffect, useState } from "react";
import { Modal, Switch } from "../Modal";
import { FormError } from "../FormError";
import { api, ApiError } from "../../api/client";
import { useApi } from "../../lib/useApi";
import { phoneBr } from "../../lib/agenda";
import type { AgendaPro, Customer, PageMeta, ServiceOffering } from "../../api/types";

export interface NewSlot {
  date: string;
  time: string;
  professionalId: string;
}

export function NewAppointmentModal({
  slot,
  pros,
  onClose,
  onCreated,
}: {
  slot: NewSlot;
  pros: AgendaPro[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const services = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Customer[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [professionalId, setProfessionalId] = useState(slot.professionalId);
  const [date, setDate] = useState(slot.date);
  const [time, setTime] = useState(slot.time);
  const [notify, setNotify] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeServices = (services.data ?? []).filter((s) => s.active);

  useEffect(() => {
    if (!serviceId && activeServices.length > 0) setServiceId(activeServices[0].id);
  }, [activeServices, serviceId]);

  // Busca na base com uma pausa curta entre teclas — não dispara uma requisição por letra.
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      return;
    }
    let alive = true;
    const t = setTimeout(() => {
      api
        .get<PageMeta<Customer>>(`/api/admin/customers?q=${encodeURIComponent(q)}&size=5`)
        .then((page) => alive && setResults(page.items))
        .catch(() => alive && setResults([]));
    }, 250);
    return () => {
      alive = false;
      clearTimeout(t);
    };
  }, [query]);

  const pick = (c: Customer) => {
    setName(c.name ?? "");
    setPhone(c.phone);
    setQuery("");
    setResults([]);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.post("/api/admin/agenda/appointments", {
        customerName: name.trim(),
        customerPhone: phone,
        serviceId: serviceId || null,
        professionalId,
        date,
        time,
        notifyCustomer: notify,
      });
      onCreated();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao agendar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Novo agendamento"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Cancelar</button>
          <button
            className="btn btn-primary"
            onClick={save}
            disabled={saving || !name.trim() || !phone.trim() || !serviceId}
          >
            {saving ? "Agendando…" : "Agendar"}
          </button>
        </>
      }
    >
      <div className="stack">
        <div className="field">
          <label>
            Buscar cliente
            <input
              className="input"
              value={query}
              placeholder="nome ou telefone de quem já é cliente"
              onChange={(e) => setQuery(e.target.value)}
              autoComplete="off"
            />
          </label>
          {results.length > 0 && (
            <ul className="ag-results" role="listbox" aria-label="Clientes encontrados">
              {results.map((c) => (
                <li key={c.id}>
                  <button type="button" className="ag-result" onClick={() => pick(c)}>
                    <span>{c.name || "Sem nome"}</span>
                    <span className="mono muted">{phoneBr(c.phone)}</span>
                    {c.planName && <span className="badge badge-plan">💎 {c.planName}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="ag-grid2">
          <div className="field">
            <label>
              Nome
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="ex.: João Souza" />
            </label>
          </div>
          <div className="field">
            <label>
              WhatsApp
              <input
                className="input"
                value={phone}
                inputMode="tel"
                onChange={(e) => setPhone(e.target.value)}
                placeholder="(11) 99999-0000"
              />
            </label>
          </div>
        </div>

        <div className="field">
          <label>
            Serviço
            <select className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
              {services.loading && <option value="">Carregando…</option>}
              {activeServices.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} · {s.durationMinutes} min
                </option>
              ))}
            </select>
          </label>
          {services.error && <span className="error">Não foi possível carregar os serviços ({services.error}).</span>}
        </div>

        <div className="ag-grid3">
          <div className="field">
            <label>
              Barbeiro
              <select className="input" value={professionalId} onChange={(e) => setProfessionalId(e.target.value)}>
                {pros.filter((p) => p.active).map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </label>
          </div>
          <div className="field">
            <label>
              Data
              <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </label>
          </div>
          <div className="field">
            <label>
              Horário
              <input className="input" type="time" step={1800} value={time} onChange={(e) => setTime(e.target.value)} />
            </label>
          </div>
        </div>

        <Switch checked={notify} onChange={setNotify} label="Confirmar para o cliente pelo WhatsApp" />
        <FormError>{error}</FormError>
      </div>
    </Modal>
  );
}
