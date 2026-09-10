import { useEffect, useState } from "react";
import { api, qs } from "../api";
import type { Experiment, Task, TaskPriority } from "../types";
import { Card, EmptyState, Field, Modal, Page, StatusBadge } from "../components/ui";

const emptyTask = (): Partial<Task> => ({
  title: "",
  description: "",
  due_date: null,
  status: "open",
  priority: "medium",
  experiment_id: null,
});

export default function Tasks() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [showDone, setShowDone] = useState(false);
  const [editing, setEditing] = useState<Partial<Task> | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    api
      .get<Task[]>("/tasks" + qs({ status: showDone ? undefined : "open" }))
      .then(setTasks)
      .finally(() => setLoading(false));
  }

  useEffect(load, [showDone]);
  useEffect(() => {
    api.get<Experiment[]>("/experiments").then(setExperiments);
  }, []);

  function toggleDone(t: Task) {
    api.put(`/tasks/${t.id}`, { status: t.status === "done" ? "open" : "done" }).then(load);
  }

  function save() {
    if (!editing) return;
    const req = editing.id ? api.put<Task>(`/tasks/${editing.id}`, editing) : api.post<Task>("/tasks", editing);
    req.then(() => {
      setEditing(null);
      load();
    });
  }

  function remove(id: number) {
    if (!confirm("Delete this task?")) return;
    api.del(`/tasks/${id}`).then(load);
  }

  return (
    <Page
      title="Tasks"
      actions={<button className="btn btn-primary" onClick={() => setEditing(emptyTask())}>+ New Task</button>}
    >
      <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, marginBottom: 14 }}>
        <input type="checkbox" checked={showDone} onChange={(e) => setShowDone(e.target.checked)} />
        Show completed tasks
      </label>

      {loading && <div>Loading…</div>}
      {!loading && tasks.length === 0 && <EmptyState text="No tasks — you're all caught up." />}

      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {tasks.map((t) => {
          const exp = experiments.find((e) => e.id === t.experiment_id);
          return (
            <Card key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: 12 }}>
              <div style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
                <input type="checkbox" checked={t.status === "done"} onChange={() => toggleDone(t)} style={{ marginTop: 3 }} />
                <div>
                  <div style={{ fontWeight: 600, textDecoration: t.status === "done" ? "line-through" : "none" }}>{t.title}</div>
                  <div style={{ fontSize: 12, color: "var(--text-dim)" }}>
                    {t.due_date ? `due ${t.due_date}` : "no due date"} {exp ? `· ${exp.title}` : ""}
                  </div>
                  {t.description && <div style={{ fontSize: 12, marginTop: 4 }}>{t.description}</div>}
                </div>
              </div>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexShrink: 0 }}>
                <StatusBadge value={t.priority} />
                <button className="btn" onClick={() => setEditing(t)}>Edit</button>
                <button className="btn" onClick={() => remove(t.id)}>Delete</button>
              </div>
            </Card>
          );
        })}
      </div>

      {editing && (
        <Modal title={editing.id ? "Edit Task" : "New Task"} onClose={() => setEditing(null)}>
          <Field label="Title">
            <input className="input" value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} />
          </Field>
          <Field label="Description">
            <textarea className="input" rows={3} value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Due Date">
              <input type="date" className="input" value={editing.due_date || ""} onChange={(e) => setEditing({ ...editing, due_date: e.target.value })} />
            </Field>
            <Field label="Priority">
              <select className="input" value={editing.priority} onChange={(e) => setEditing({ ...editing, priority: e.target.value as TaskPriority })}>
                <option value="low">Low</option>
                <option value="medium">Medium</option>
                <option value="high">High</option>
              </select>
            </Field>
          </div>
          <Field label="Linked Experiment (optional)">
            <select
              className="input"
              value={editing.experiment_id ?? ""}
              onChange={(e) => setEditing({ ...editing, experiment_id: e.target.value ? Number(e.target.value) : null })}
            >
              <option value="">None</option>
              {experiments.map((x) => <option key={x.id} value={x.id}>{x.title}</option>)}
            </select>
          </Field>
          <button className="btn btn-primary" onClick={save}>Save Task</button>
        </Modal>
      )}
    </Page>
  );
}
