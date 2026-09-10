import type { ReactNode } from "react";

export function Page({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>{title}</h1>
        <div style={{ display: "flex", gap: 8 }}>{actions}</div>
      </div>
      {children}
    </div>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: React.CSSProperties }) {
  return (
    <div className="card" style={{ padding: 16, ...style }}>
      {children}
    </div>
  );
}

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  planned: { bg: "#e5eaf2", fg: "#33507a" },
  in_progress: { bg: "#fdf1d8", fg: "#9a6700" },
  completed: { bg: "#dff3e6", fg: "#1f6f43" },
  failed: { bg: "#fbe3e1", fg: "#b3261e" },
  on_hold: { bg: "#eee", fg: "#666" },
  open: { bg: "#fdf1d8", fg: "#9a6700" },
  done: { bg: "#dff3e6", fg: "#1f6f43" },
  low: { bg: "#eee", fg: "#666" },
  medium: { bg: "#fdf1d8", fg: "#9a6700" },
  high: { bg: "#fbe3e1", fg: "#b3261e" },
};

export function StatusBadge({ value }: { value: string }) {
  const c = STATUS_COLORS[value] || { bg: "#eee", fg: "#555" };
  return (
    <span
      className="badge"
      style={{ background: c.bg, color: c.fg }}
    >
      {value.replace("_", " ")}
    </span>
  );
}

export function EmptyState({ text }: { text: string }) {
  return (
    <div style={{ padding: "40px 0", textAlign: "center", color: "var(--text-dim)", fontSize: 14 }}>
      {text}
    </div>
  );
}

export function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.4)",
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "center",
        padding: "5vh 20px",
        zIndex: 50,
        overflowY: "auto",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="card"
        style={{ width: wide ? 720 : 480, maxWidth: "100%", padding: 20 }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <h2 style={{ fontSize: 16, fontWeight: 700, margin: 0 }}>{title}</h2>
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: "block", marginBottom: 12 }}>
      <div className="label" style={{ marginBottom: 4 }}>
        {label}
      </div>
      {children}
    </label>
  );
}
