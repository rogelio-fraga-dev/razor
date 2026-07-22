import { useMemo, useState } from "react";
import { Layout } from "../components/Layout";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { Customer } from "../api/types";

function dateShort(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("pt-BR");
}

export function Customers() {
  const { data, loading, error } = useApi<Customer[]>(() => api.get("/api/admin/customers"), []);
  const [q, setQ] = useState("");

  const filtered = useMemo(() => {
    if (!data) return [];
    const term = q.trim().toLowerCase();
    if (!term) return data;
    return data.filter(
      (c) => (c.name ?? "").toLowerCase().includes(term) || c.phone.includes(term),
    );
  }, [data, q]);

  return (
    <Layout
      title="Clientes"
      actions={
        <input
          className="input"
          placeholder="Buscar por nome ou telefone"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{ width: 240 }}
        />
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Base de clientes</h3>
          {data && <span className="badge badge-muted">{data.length} clientes</span>}
        </div>
        {loading ? (
          <div className="empty">Carregando…</div>
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : filtered.length === 0 ? (
          <div className="empty">{data && data.length > 0 ? "Nenhum cliente encontrado." : "Nenhum cliente na base ainda."}</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Telefone</th>
                  <th>Fidelidade</th>
                  <th>Último atendimento</th>
                  <th>Cadastro</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((c) => (
                  <tr key={c.id}>
                    <td style={{ fontWeight: 550 }}>{c.name || "—"}</td>
                    <td className="muted">{c.phone}</td>
                    <td>
                      <span className="badge badge-muted">{c.fidelityPoints} / 10 pts</span>
                    </td>
                    <td className="muted">{dateShort(c.lastServiceDate)}</td>
                    <td className="muted">{dateShort(c.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
