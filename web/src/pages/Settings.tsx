import { useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { AdminContact, Barbershop, BotSettings } from "../api/types";

function ProfileCard() {
  const { data, loading, error, reload } = useApi<Barbershop>(() => api.get("/api/admin/barbershop"), []);
  const [name, setName] = useState("");
  const [instance, setInstance] = useState("");
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (data) {
      setName(data.name);
      setInstance(data.whatsappInstance ?? "");
    }
  }, [data]);

  const save = async () => {
    setSaving(true);
    setMsg(null);
    try {
      await api.put("/api/admin/barbershop", { name: name.trim(), whatsappInstance: instance.trim() || null });
      setMsg({ ok: true, text: "Perfil salvo." });
      reload();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : "Erro ao salvar." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Perfil da barbearia</h3>
        {data && <span className="badge badge-muted">/{data.slug}</span>}
      </div>
      <div className="card-pad">
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack" style={{ maxWidth: 460 }}>
            <div className="field">
              <label>Nome da barbearia</label>
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label>Instância do WhatsApp</label>
              <input className="input" value={instance} onChange={(e) => setInstance(e.target.value)} placeholder="ex.: minha-barbearia-wa" />
              <span className="faint" style={{ fontSize: 12 }}>Nome da instância na Evolution API por onde o bot envia as mensagens.</span>
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving || !name.trim()}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
              {msg && <span className={msg.ok ? "badge badge-green" : "error"}>{msg.text}</span>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function AdminsCard() {
  const { data, loading, error, reload } = useApi<AdminContact[]>(() => api.get("/api/admin/admins"), []);
  const [phone, setPhone] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const add = async () => {
    if (!phone.trim()) return;
    setBusy(true);
    setFormError(null);
    try {
      await api.post("/api/admin/admins", { phone: phone.trim(), name: name.trim() || null });
      setPhone("");
      setName("");
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao adicionar.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: string) => {
    try {
      await api.del(`/api/admin/admins/${id}`);
      reload();
    } catch {
      /* silencioso: recarrega estado real */
      reload();
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Administradores do WhatsApp</h3>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Telefones tratados como admin no WhatsApp (recebem avisos e usam os comandos de gestão).
        </p>
        <div className="row wrap" style={{ marginBottom: 16 }}>
          <input className="input grow" style={{ maxWidth: 220 }} placeholder="Telefone (com DDD)" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <input className="input grow" style={{ maxWidth: 200 }} placeholder="Nome (opcional)" value={name} onChange={(e) => setName(e.target.value)} />
          <button className="btn btn-primary btn-sm" onClick={add} disabled={busy || !phone.trim()}>Adicionar</button>
        </div>
        {formError && <div className="error" style={{ marginBottom: 12 }}>{formError}</div>}

        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : !data || data.length === 0 ? (
          <div className="faint">Nenhum admin cadastrado — o sistema usa os telefones da configuração global.</div>
        ) : (
          <div className="stack" style={{ gap: 8 }}>
            {data.map((a) => (
              <div key={a.id} className="row between" style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
                <div>
                  <div style={{ fontWeight: 550 }}>{a.phone}</div>
                  {a.name && <div className="faint" style={{ fontSize: 13 }}>{a.name}</div>}
                </div>
                <button className="btn btn-danger btn-sm" onClick={() => remove(a.id)}>Remover</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

const EMPTY_BOT: BotSettings = {
  addressText: "",
  addressMapsUrl: "",
  servicesText: "",
  scheduleUrl: "",
  instagramUrl: "",
};

function BotCard() {
  const { data, loading, error, reload } = useApi<BotSettings>(() => api.get("/api/admin/settings/bot"), []);
  const [form, setForm] = useState<BotSettings>(EMPTY_BOT);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (data) {
      setForm({
        addressText: data.addressText ?? "",
        addressMapsUrl: data.addressMapsUrl ?? "",
        servicesText: data.servicesText ?? "",
        scheduleUrl: data.scheduleUrl ?? "",
        instagramUrl: data.instagramUrl ?? "",
      });
    }
  }, [data]);

  const set = (k: keyof BotSettings, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setSaving(true);
    setMsg(null);
    try {
      await api.put("/api/admin/settings/bot", form);
      setMsg({ ok: true, text: "Configurações do bot salvas." });
      reload();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : "Erro ao salvar." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Textos do bot (WhatsApp)</h3>
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          O que o cliente recebe no WhatsApp. Campos vazios usam o texto padrão do sistema.
        </p>
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stack" style={{ maxWidth: 560 }}>
            <div className="field">
              <label>Endereço (texto)</label>
              <input className="input" value={form.addressText ?? ""} onChange={(e) => set("addressText", e.target.value)} />
            </div>
            <div className="field">
              <label>Link do mapa</label>
              <input className="input" value={form.addressMapsUrl ?? ""} onChange={(e) => set("addressMapsUrl", e.target.value)} />
            </div>
            <div className="field">
              <label>Tabela de serviços / preços</label>
              <textarea
                className="input"
                rows={5}
                value={form.servicesText ?? ""}
                onChange={(e) => set("servicesText", e.target.value)}
                style={{ resize: "vertical", fontFamily: "inherit" }}
              />
            </div>
            <div className="field">
              <label>Instagram (link)</label>
              <input className="input" value={form.instagramUrl ?? ""} onChange={(e) => set("instagramUrl", e.target.value)} />
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
              {msg && <span className={msg.ok ? "badge badge-green" : "error"}>{msg.text}</span>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function Settings() {
  return (
    <Layout title="Configurações">
      <ProfileCard />
      <BotCard />
      <AdminsCard />
    </Layout>
  );
}
