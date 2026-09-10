import { useEffect, useState } from "react";
import { api, qs } from "../api";
import type { Entry, Experiment } from "../types";
import { Card, EmptyState, Field, Modal, Page } from "../components/ui";

const emptyEntry = (): Partial<Entry> => ({
  title: "",
  date: new Date().toISOString().slice(0, 10),
  body: "",
  tags: "",
  experiment_id: null,
});

export default function Notebook() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<Partial<Entry> | null>(null);
  const [loading, setLoading] = useState(true);

  function load(query = "") {
    setLoading(true);
    api
      .get<Entry[]>("/entries" + qs({ q: query }))
      .then(setEntries)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    api.get<Experiment[]>("/experiments").then(setExperiments);
  }, []);

  function save() {
    if (!editing) return;
    const payload = { ...editing };
    const req = editing.id ? api.put<Entry>(`/entries/${editing.id}`, payload) : api.post<Entry>("/entries", payload);
    req.then(() => {
      setEditing(null);
      load(q);
    });
  }

  function remove(id: number) {
    if (!confirm("Delete this entry?")) return;
    api.del(`/entries/${id}`).then(() => load(q));
  }

  return (
    <Page
      title="Lab Notebook"
      actions={<button className="btn btn-primary" onClick={() => setEditing(emptyEntry())}>+ New Entry</button>}
    >
      <div style={{ marginBottom: 14, maxWidth: 320 }}>
        <input
          className="input"
          placeholder="Filter entries…"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            load(e.target.value);
          }}
        />
      </div>

      {loading && <div>Loading…</div>}
      {!loading && entries.length === 0 && <EmptyState text="No notebook entries yet. Log your first observation." />}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {entries.map((e) => {
          const exp = experiments.find((x) => x.id === e.experiment_id);
          return (
            <Card key={e.id}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <div>
                  <div style={{ fontWeight: 700 }}>{e.title}</div>
                  <div style={{ fontSize: 12, color: "var(--text-dim)" }}>
                    {e.date} {exp ? `· linked to ${exp.title}` : ""} {e.tags ? `· ${e.tags}` : ""}
                  </div>
                </div>
                <div style={{ display: "flex", gap: 6 }}>
                  <button className="btn" onClick={() => setEditing(e)}>Edit</button>
                  <button className="btn" onClick={() => remove(e.id)}>Delete</button>
                </div>
              </div>
              {e.body && <div style={{ marginTop: 8, fontSize: 13, whiteSpace: "pre-wrap" }}>{e.body}</div>}
            </Card>
          );
        })}
      </div>

      {editing && (
        <Modal title={editing.id ? "Edit Entry" : "New Entry"} onClose={() => setEditing(null)} wide>
          <Field label="Title">
            <input className="input" value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} />
          </Field>
          <Field label="Date">
            <input type="date" className="input" value={editing.date as string} onChange={(e) => setEditing({ ...editing, date: e.target.value })} />
          </Field>
          <Field label="Linked Experiment (optional)">
            <select
              className="input"
              value={editing.experiment_id ?? ""}
              onChange={(e) => setEditing({ ...editing, experiment_id: e.target.value ? Number(e.target.value) : null })}
            >
              <option value="">None</option>
              {experiments.map((x) => (
                <option key={x.id} value={x.id}>{x.title}</option>
              ))}
            </select>
          </Field>
          <Field label="Notes / Observations">
            <textarea
              className="input"
              rows={8}
              value={editing.body}
              onChange={(e) => setEditing({ ...editing, body: e.target.value })}
            />
          </Field>
          <Field label="Tags (comma separated)">
            <input className="input" value={editing.tags} onChange={(e) => setEditing({ ...editing, tags: e.target.value })} />
          </Field>
          <button className="btn btn-primary" onClick={save}>Save Entry</button>
        </Modal>
      )}
    </Page>
  );
}
