import { useEffect, useState } from "react";
import { api, getToken } from "../api";
import type { ReportData } from "../types";
import { Card, Page } from "../components/ui";

type Mode = "weekly" | "monthly" | "custom";

function todayISO() {
  return new Date().toISOString().slice(0, 10);
}

export default function Reports() {
  const [mode, setMode] = useState<Mode>("weekly");
  const [anchor, setAnchor] = useState(todayISO());
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [start, setStart] = useState(todayISO());
  const [end, setEnd] = useState(todayISO());
  const [report, setReport] = useState<ReportData | null>(null);
  const [loading, setLoading] = useState(false);

  function endpointPath(): string {
    if (mode === "weekly") return `/reports/weekly?anchor=${anchor}`;
    if (mode === "monthly") return `/reports/monthly?year=${year}&month=${month}`;
    return `/reports/custom?start=${start}&end=${end}`;
  }

  function mdPath(): string {
    return endpointPath().replace(/^\/reports\/(\w+)/, "/reports/$1.md");
  }

  function load() {
    setLoading(true);
    api.get<ReportData>(endpointPath()).then(setReport).finally(() => setLoading(false));
  }

  useEffect(load, [mode]);

  async function downloadMarkdown() {
    const res = await fetch(`/api${mdPath()}`, { headers: { "X-Auth-Token": getToken() } });
    const text = await res.text();
    const blob = new Blob([text], { type: "text/markdown" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${report?.title.replace(/[^\w-]+/g, "_") || "report"}.md`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Page
      title="Progress Reports"
      actions={report ? <button className="btn btn-primary" onClick={downloadMarkdown}>Download Markdown</button> : undefined}
    >
      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap", alignItems: "center" }}>
        {(["weekly", "monthly", "custom"] as Mode[]).map((m) => (
          <button
            key={m}
            className="btn"
            style={mode === m ? { borderColor: "var(--accent)", color: "var(--accent)" } : undefined}
            onClick={() => setMode(m)}
          >
            {m[0].toUpperCase() + m.slice(1)}
          </button>
        ))}

        {mode === "weekly" && (
          <>
            <input type="date" className="input" style={{ width: 160 }} value={anchor} onChange={(e) => setAnchor(e.target.value)} />
            <button className="btn" onClick={load}>Generate</button>
          </>
        )}
        {mode === "monthly" && (
          <>
            <input type="number" className="input" style={{ width: 90 }} value={year} onChange={(e) => setYear(Number(e.target.value))} />
            <select className="input" style={{ width: 130 }} value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                <option key={m} value={m}>{new Date(2000, m - 1, 1).toLocaleString("default", { month: "long" })}</option>
              ))}
            </select>
            <button className="btn" onClick={load}>Generate</button>
          </>
        )}
        {mode === "custom" && (
          <>
            <input type="date" className="input" style={{ width: 160 }} value={start} onChange={(e) => setStart(e.target.value)} />
            <span>to</span>
            <input type="date" className="input" style={{ width: 160 }} value={end} onChange={(e) => setEnd(e.target.value)} />
            <button className="btn" onClick={load}>Generate</button>
          </>
        )}
      </div>

      {loading && <div>Generating…</div>}
      {!loading && report && <ReportView report={report} />}
    </Page>
  );
}

function ReportView({ report }: { report: ReportData }) {
  const s = report.summary;
  return (
    <div>
      <h2 style={{ fontSize: 18, marginBottom: 2 }}>{report.title}</h2>
      <div style={{ fontSize: 12, color: "var(--text-dim)", marginBottom: 16 }}>
        {report.range_start} to {report.range_end}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 10, marginBottom: 20 }}>
        <StatBox label="Entries logged" value={s.entries_logged} />
        <StatBox label="Started" value={s.experiments_started} />
        <StatBox label="Completed" value={s.experiments_completed} />
        <StatBox label="Failed" value={s.experiments_failed} />
        <StatBox label="In progress" value={s.experiments_ongoing} />
        <StatBox label="Tasks done" value={s.tasks_completed} />
      </div>

      <ReportSection title="Completed Experiments">
        {report.experiments_completed.map((e) => (
          <li key={e.id}><b>{e.title}</b> ({e.end_date}) — {e.conclusion || "no conclusion noted"}</li>
        ))}
      </ReportSection>
      <ReportSection title="Failed / Troubleshooting">
        {report.experiments_failed.map((e) => (
          <li key={e.id}><b>{e.title}</b> ({e.end_date}) — {e.conclusion || e.results}</li>
        ))}
      </ReportSection>
      <ReportSection title="Experiments Started">
        {report.experiments_started.map((e) => (
          <li key={e.id}><b>{e.title}</b> ({e.start_date}) — {e.hypothesis}</li>
        ))}
      </ReportSection>
      <ReportSection title="Notebook Entries">
        {report.entries.map((e) => (
          <li key={e.id}>{e.date} — {e.title}</li>
        ))}
      </ReportSection>
      <ReportSection title="Upcoming Tasks">
        {report.upcoming_tasks.map((t) => (
          <li key={t.id}>{t.title} (due {t.due_date}, {t.priority})</li>
        ))}
      </ReportSection>
      <ReportSection title="Low Stock Alerts">
        {report.low_stock.map((i) => (
          <li key={i.id}>{i.name}: {i.quantity} {i.unit} remaining</li>
        ))}
      </ReportSection>
      <ReportSection title="Expiring Soon">
        {report.expiring_soon.map((i) => (
          <li key={i.id}>{i.name} — expires {i.expiry_date}</li>
        ))}
      </ReportSection>
    </div>
  );
}

function StatBox({ label, value }: { label: string; value: number }) {
  return (
    <Card style={{ textAlign: "center", padding: 12 }}>
      <div style={{ fontSize: 22, fontWeight: 700, color: "var(--accent)" }}>{value}</div>
      <div style={{ fontSize: 11, color: "var(--text-dim)" }}>{label}</div>
    </Card>
  );
}

function ReportSection({ title, children }: { title: string; children: React.ReactNode }) {
  const items = children as React.ReactNode[];
  const list = Array.isArray(items) ? items : [items];
  if (list.length === 0) return null;
  return (
    <Card style={{ marginBottom: 12 }}>
      <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>{title}</div>
      <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: "flex", flexDirection: "column", gap: 4 }}>{children}</ul>
    </Card>
  );
}
