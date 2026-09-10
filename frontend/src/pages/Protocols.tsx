import { useEffect, useState } from "react";
import { api, qs } from "../api";
import type { Protocol, ProtocolStep } from "../types";
import { Card, EmptyState, Field, Modal, Page } from "../components/ui";

const emptyProtocol = (): Partial<Protocol> => ({
  name: "",
  category: "",
  description: "",
  version: "1.0",
  tags: "",
});

export default function Protocols() {
  const [protocols, setProtocols] = useState<Protocol[]>([]);
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<Partial<Protocol> | null>(null);
  const [detail, setDetail] = useState<Protocol | null>(null);
  const [loading, setLoading] = useState(true);

  function load(query = "") {
    setLoading(true);
    api.get<Protocol[]>("/protocols" + qs({ q: query })).then(setProtocols).finally(() => setLoading(false));
  }

  useEffect(() => load(), []);

  function save() {
    if (!editing) return;
    const req = editing.id ? api.put<Protocol>(`/protocols/${editing.id}`, editing) : api.post<Protocol>("/protocols", editing);
    req.then(() => {
      setEditing(null);
      load(q);
    });
  }

  function remove(id: number) {
    if (!confirm("Delete this protocol and all its steps?")) return;
    api.del(`/protocols/${id}`).then(() => load(q));
  }

  return (
    <Page
      title="Protocols"
      actions={<button className="btn btn-primary" onClick={() => setEditing(emptyProtocol())}>+ New Protocol</button>}
    >
      <div style={{ marginBottom: 14, maxWidth: 320 }}>
        <input className="input" placeholder="Filter protocols…" value={q} onChange={(e) => { setQ(e.target.value); load(e.target.value); }} />
      </div>

      {loading && <div>Loading…</div>}
      {!loading && protocols.length === 0 && <EmptyState text="No protocols saved yet. Add your first SOP." />}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 12 }}>
        {protocols.map((p) => (
          <Card key={p.id}>
            <div style={{ fontWeight: 700 }}>{p.name}</div>
            <div style={{ fontSize: 12, color: "var(--text-dim)", marginBottom: 8 }}>
              {p.category || "Uncategorized"} · v{p.version}
            </div>
            <div style={{ fontSize: 13, marginBottom: 10, minHeight: 20 }}>{p.description}</div>
            <div style={{ display: "flex", gap: 6 }}>
              <button className="btn" onClick={() => setDetail(p)}>Steps</button>
              <button className="btn" onClick={() => setEditing(p)}>Edit</button>
              <button className="btn" onClick={() => remove(p.id)}>Delete</button>
            </div>
          </Card>
        ))}
      </div>

      {editing && (
        <Modal title={editing.id ? "Edit Protocol" : "New Protocol"} onClose={() => setEditing(null)}>
          <Field label="Name">
            <input className="input" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
          </Field>
          <Field label="Category">
            <input className="input" value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} />
          </Field>
          <Field label="Version">
            <input className="input" value={editing.version} onChange={(e) => setEditing({ ...editing, version: e.target.value })} />
          </Field>
          <Field label="Description">
            <textarea className="input" rows={3} value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
          </Field>
          <Field label="Tags (comma separated)">
            <input className="input" value={editing.tags} onChange={(e) => setEditing({ ...editing, tags: e.target.value })} />
          </Field>
          <button className="btn btn-primary" onClick={save}>Save Protocol</button>
        </Modal>
      )}

      {detail && <ProtocolStepsModal protocol={detail} onClose={() => setDetail(null)} />}
    </Page>
  );
}

function ProtocolStepsModal({ protocol, onClose }: { protocol: Protocol; onClose: () => void }) {
  const [steps, setSteps] = useState<ProtocolStep[]>([]);
  const [newStep, setNewStep] = useState({ instruction: "", duration_minutes: "", notes: "" });

  function load() {
    api.get<ProtocolStep[]>(`/protocols/${protocol.id}/steps`).then(setSteps);
  }

  useEffect(load, [protocol.id]);

  function addStep() {
    if (!newStep.instruction.trim()) return;
    api
      .post(`/protocols/${protocol.id}/steps`, {
        order: steps.length,
        instruction: newStep.instruction,
        duration_minutes: newStep.duration_minutes ? Number(newStep.duration_minutes) : null,
        notes: newStep.notes,
      })
      .then(() => {
        setNewStep({ instruction: "", duration_minutes: "", notes: "" });
        load();
      });
  }

  function removeStep(id: number) {
    api.del(`/protocols/${protocol.id}/steps/${id}`).then(load);
  }

  return (
    <Modal title={`Steps — ${protocol.name}`} onClose={onClose} wide>
      <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
        {steps.length === 0 && <EmptyState text="No steps yet." />}
        {steps.map((s, idx) => (
          <div key={s.id} style={{ display: "flex", gap: 10, alignItems: "flex-start", padding: "8px 0", borderBottom: "1px solid var(--border)" }}>
            <div style={{ fontWeight: 700, color: "var(--accent)", width: 22 }}>{idx + 1}.</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13 }}>{s.instruction}</div>
              {s.duration_minutes ? <div style={{ fontSize: 11, color: "var(--text-dim)" }}>~{s.duration_minutes} min</div> : null}
              {s.notes && <div style={{ fontSize: 12, color: "var(--text-dim)" }}>{s.notes}</div>}
            </div>
            <button className="btn" onClick={() => removeStep(s.id)}>Remove</button>
          </div>
        ))}
      </div>

      <div className="label" style={{ marginBottom: 6 }}>Add Step</div>
      <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
        <input
          className="input"
          placeholder="Instruction"
          value={newStep.instruction}
          onChange={(e) => setNewStep({ ...newStep, instruction: e.target.value })}
        />
        <input
          className="input"
          style={{ width: 110 }}
          placeholder="Minutes"
          type="number"
          value={newStep.duration_minutes}
          onChange={(e) => setNewStep({ ...newStep, duration_minutes: e.target.value })}
        />
      </div>
      <input
        className="input"
        placeholder="Notes (optional)"
        value={newStep.notes}
        onChange={(e) => setNewStep({ ...newStep, notes: e.target.value })}
        style={{ marginBottom: 8 }}
      />
      <button className="btn btn-primary" onClick={addStep}>Add Step</button>
    </Modal>
  );
}
