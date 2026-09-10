import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, qs } from "../api";
import type { Entry, Experiment, InventoryItem, Task } from "../types";
import { Card, EmptyState, Page, StatusBadge } from "../components/ui";

export default function Dashboard() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [lowStock, setLowStock] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<Entry[]>("/entries"),
      api.get<Experiment[]>("/experiments"),
      api.get<Task[]>("/tasks" + qs({ status: "open" })),
      api.get<InventoryItem[]>("/inventory" + qs({ low_stock: true })),
    ])
      .then(([e, x, t, i]) => {
        setEntries(e.slice(0, 5));
        setExperiments(x);
        setTasks(t.slice(0, 6));
        setLowStock(i);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Page title="Dashboard">Loading…</Page>;
  if (error) return <Page title="Dashboard">Error: {error}</Page>;

  const ongoing = experiments.filter((e) => e.status === "in_progress");
  const completedCount = experiments.filter((e) => e.status === "completed").length;

  return (
    <Page title="Dashboard">
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12, marginBottom: 24 }}>
        <Stat label="Ongoing experiments" value={ongoing.length} />
        <Stat label="Completed experiments" value={completedCount} />
        <Stat label="Open tasks" value={tasks.length} />
        <Stat label="Low stock alerts" value={lowStock.length} warn={lowStock.length > 0} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <Card>
          <SectionTitle>Ongoing Experiments</SectionTitle>
          {ongoing.length === 0 && <EmptyState text="Nothing in progress right now." />}
          {ongoing.map((e) => (
            <Row key={e.id}>
              <Link to="/experiments">{e.title}</Link>
              <StatusBadge value={e.status} />
            </Row>
          ))}
        </Card>

        <Card>
          <SectionTitle>Upcoming / Open Tasks</SectionTitle>
          {tasks.length === 0 && <EmptyState text="No open tasks." />}
          {tasks.map((t) => (
            <Row key={t.id}>
              <Link to="/tasks">{t.title}</Link>
              <span style={{ fontSize: 12, color: "var(--text-dim)" }}>{t.due_date || "no due date"}</span>
            </Row>
          ))}
        </Card>

        <Card>
          <SectionTitle>Recent Notebook Entries</SectionTitle>
          {entries.length === 0 && <EmptyState text="No entries logged yet." />}
          {entries.map((e) => (
            <Row key={e.id}>
              <Link to="/notebook">{e.title}</Link>
              <span style={{ fontSize: 12, color: "var(--text-dim)" }}>{e.date}</span>
            </Row>
          ))}
        </Card>

        <Card>
          <SectionTitle>Low Stock Alerts</SectionTitle>
          {lowStock.length === 0 && <EmptyState text="Everything is well stocked." />}
          {lowStock.map((i) => (
            <Row key={i.id}>
              <Link to="/inventory">{i.name}</Link>
              <span style={{ fontSize: 12, color: "var(--danger)" }}>
                {i.quantity} {i.unit} left
              </span>
            </Row>
          ))}
        </Card>
      </div>
    </Page>
  );
}

function Stat({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
  return (
    <Card style={{ textAlign: "center" }}>
      <div style={{ fontSize: 26, fontWeight: 700, color: warn ? "var(--danger)" : "var(--accent)" }}>{value}</div>
      <div style={{ fontSize: 12, color: "var(--text-dim)" }}>{label}</div>
    </Card>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10 }}>{children}</div>;
}

function Row({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "7px 0",
        borderBottom: "1px solid var(--border)",
        fontSize: 13,
      }}
    >
      {children}
    </div>
  );
}
