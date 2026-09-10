import { useEffect, useState } from "react";
import { api, qs } from "../api";
import type { Experiment, ExperimentStatus, Protocol } from "../types";
import { Card, EmptyState, Field, Modal, Page, StatusBadge } from "../components/ui";

const STATUSES: ExperimentStatus[] = ["planned", "in_progress", "completed", "failed", "on_hold"];

const emptyExp = (): Partial<Experiment> => ({
  title: "",
  status: "planned",
  protocol_id: null,
  start_date: new Date().toISOString().slice(0, 10),
  end_date: null,
  hypothesis: "",
  results: "",
  conclusion: "",
  tags: "",
});

export default function Experiments() {
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [editing, setEditing] = useState<Partial<Experiment> | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    api
      .get<Experiment[]>("/experiments" + qs({ status: statusFilter || undefined }))
      .then(setExperiments)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    api.get<Protocol[]>("/protocols").then(setProtocols);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  function save() {
    if (!editing) return;
    const req = editing.id
      ? api.put<Experiment>(`/experiments/${editing.id}`, editing)
      : api.post<Experiment>("/experiments", editing);
    req.then(() => {
      setEditing(null);
      load();
    });
  }

  function remove(id: number) {
    if (!confirm("Delete this experiment? Linked notebook entries will remain but lose their link.")) return;
    api.del(`/experiments/${id}`).then(load);
  }

  return (
    <Page
      title="Experiments"
      actions={<button className="btn btn-primary" onClick={() => setEditing(emptyExp())}>+ New Experiment</button>}
    >
      <div style={{ marginBottom: 14, display: "flex", gap: 8 }}>
        <select className="input" style={{ width: 200 }} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s.replace("_", " ")}</option>
          ))}
        </select>
      </div>

      {loading && <div>Loading…</div>}
      {!loading && experiments.length === 0 && <EmptyState text="No experiments recorded yet." />}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {experiments.map((e) => {
          const proto = protocols.find((p) => p.id === e.protocol_id);
          return (
            <Card key={e.id}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <span style={{ fontWeight: 700 }}>{e.title}</span>
                    <StatusBadge value={e.status} />
                  </div>
                  <div style={{ fontSize: 12, color: "var(--text-dim)", marginTop: 2 }}>
                    {e.start_date} {e.end_date ? `→ ${e.end_date}` : ""} {proto ? `· protocol: ${proto.name}` : ""}
                  </div>
                  {e.hypothesis && <div style={{ fontSize: 13, marginTop: 6 }}><b>Hypothesis:</b> {e.hypothesis}</div>}
                  {e.results && <div style={{ fontSize: 13, marginTop: 4 }}><b>Results:</b> {e.results}</div>}
                  {e.conclusion && <div style={{ fontSize: 13, marginTop: 4 }}><b>Conclusion:</b> {e.conclusion}</div>}
                </div>
                <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
                  <button className="btn" onClick={() => setEditing(e)}>Edit</button>
                  <button className="btn" onClick={() => remove(e.id)}>Delete</button>
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {editing && (
        <Modal title={editing.id ? "Edit Experiment" : "New Experiment"} onClose={() => setEditing(null)} wide>
          <Field label="Title">
            <input className="input" value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} />
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Status">
              <select className="input" value={editing.status} onChange={(e) => setEditing({ ...editing, status: e.target.value as ExperimentStatus })}>
                {STATUSES.map((s) => <option key={s} value={s}>{s.replace("_", " ")}</option>)}
              </select>
            </Field>
            <Field label="Protocol (optional)">
              <select
                className="input"
                value={editing.protocol_id ?? ""}
                onChange={(e) => setEditing({ ...editing, protocol_id: e.target.value ? Number(e.target.value) : null })}
              >
                <option value="">None</option>
                {protocols.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </Field>
            <Field label="Start Date">
              <input type="date" className="input" value={editing.start_date || ""} onChange={(e) => setEditing({ ...editing, start_date: e.target.value })} />
            </Field>
            <Field label="End Date">
              <input type="date" className="input" value={editing.end_date || ""} onChange={(e) => setEditing({ ...editing, end_date: e.target.value })} />
            </Field>
          </div>
          <Field label="Hypothesis">
            <textarea className="input" rows={2} value={editing.hypothesis} onChange={(e) => setEditing({ ...editing, hypothesis: e.target.value })} />
          </Field>
          <Field label="Results">
            <textarea className="input" rows={3} value={editing.results} onChange={(e) => setEditing({ ...editing, results: e.target.value })} />
          </Field>
          <Field label="Conclusion">
            <textarea className="input" rows={2} value={editing.conclusion} onChange={(e) => setEditing({ ...editing, conclusion: e.target.value })} />
          </Field>
          <Field label="Tags (comma separated)">
            <input className="input" value={editing.tags} onChange={(e) => setEditing({ ...editing, tags: e.target.value })} />
          </Field>
          <button className="btn btn-primary" onClick={save}>Save Experiment</button>
        </Modal>
      )}
    </Page>
  );
}
