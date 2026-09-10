import { NavLink, Route, Routes } from "react-router-dom";
import Dashboard from "./pages/Dashboard";
import Notebook from "./pages/Notebook";
import Experiments from "./pages/Experiments";
import Protocols from "./pages/Protocols";
import Inventory from "./pages/Inventory";
import Tasks from "./pages/Tasks";
import Reports from "./pages/Reports";
import SearchPage from "./pages/Search";
import Settings from "./pages/Settings";
import GlobalSearchBox from "./components/GlobalSearchBox";

const NAV = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/notebook", label: "Notebook" },
  { to: "/experiments", label: "Experiments" },
  { to: "/protocols", label: "Protocols" },
  { to: "/inventory", label: "Inventory" },
  { to: "/tasks", label: "Tasks" },
  { to: "/reports", label: "Reports" },
  { to: "/settings", label: "Settings" },
];

export default function App() {
  return (
    <div style={{ display: "flex", minHeight: "100vh" }}>
      <aside
        style={{
          width: 210,
          flexShrink: 0,
          borderRight: "1px solid var(--border)",
          padding: "20px 14px",
          position: "sticky",
          top: 0,
          height: "100vh",
          overflowY: "auto",
        }}
      >
        <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 4, padding: "0 8px" }}>
          🧬 LabNotebook
        </div>
        <div style={{ fontSize: 11, color: "var(--text-dim)", padding: "0 8px", marginBottom: 18 }}>
          Personal research log
        </div>
        <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              style={({ isActive }) => ({
                padding: "8px 10px",
                borderRadius: 8,
                fontSize: 14,
                fontWeight: 500,
                textDecoration: "none",
                color: isActive ? "var(--accent)" : "var(--text)",
                background: isActive ? "var(--accent-soft)" : "transparent",
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <main style={{ flex: 1, minWidth: 0 }}>
        <header
          style={{
            borderBottom: "1px solid var(--border)",
            padding: "12px 24px",
            display: "flex",
            justifyContent: "flex-end",
          }}
        >
          <div style={{ width: 320, maxWidth: "100%" }}>
            <GlobalSearchBox />
          </div>
        </header>
        <div style={{ padding: "24px", maxWidth: 1100 }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/notebook" element={<Notebook />} />
            <Route path="/experiments" element={<Experiments />} />
            <Route path="/protocols" element={<Protocols />} />
            <Route path="/inventory" element={<Inventory />} />
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}
