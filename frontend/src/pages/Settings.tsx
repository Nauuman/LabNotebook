import { useState } from "react";
import { clearToken, getToken, setToken } from "../api";
import { Card, Field, Page } from "../components/ui";

export default function Settings() {
  const [token, setTokenState] = useState(getToken());
  const [status, setStatus] = useState("");

  function saveToken() {
    setToken(token);
    setStatus("Access token saved locally in this browser.");
  }

  function logout() {
    clearToken();
    setTokenState("");
    setStatus("Access token cleared.");
  }

  async function downloadBackup() {
    const res = await fetch("/api/backup/export", { headers: { "X-Auth-Token": getToken() } });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `labnotebook-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function importBackup(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!confirm("Import this backup? Existing records will be kept; imported rows are added as new records (duplicates possible if re-imported).")) return;
    const text = await file.text();
    const res = await fetch("/api/backup/import", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Auth-Token": getToken() },
      body: text,
    });
    const data = await res.json();
    setStatus(`Imported: ${JSON.stringify(data.imported)}`);
    e.target.value = "";
  }

  return (
    <Page title="Settings & Backup">
      <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 560 }}>
        <Card>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>Access Password</div>
          <p style={{ fontSize: 13, color: "var(--text-dim)", marginTop: 0 }}>
            If the server is started with <code>LABNOTEBOOK_PASSWORD</code> set (recommended once this app is deployed
            somewhere reachable over the internet), enter the matching password here so this browser can talk to the API.
            Leave blank for local-only use with no password set.
          </p>
          <Field label="Access Token / Password">
            <input className="input" type="password" value={token} onChange={(e) => setTokenState(e.target.value)} />
          </Field>
          <div style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-primary" onClick={saveToken}>Save</button>
            <button className="btn" onClick={logout}>Clear</button>
          </div>
        </Card>

        <Card>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>Cloud Backup</div>
          <p style={{ fontSize: 13, color: "var(--text-dim)", marginTop: 0 }}>
            All data lives in a local SQLite file on the server. To back it up to the cloud, export a full JSON snapshot
            here and commit it to this project's git repository (already hosted on GitHub) — or run{" "}
            <code>scripts/backup.sh</code> from the project root, which does this automatically and pushes it for you.
          </p>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button className="btn btn-primary" onClick={downloadBackup}>Download JSON Backup</button>
            <label className="btn" style={{ cursor: "pointer" }}>
              Import Backup
              <input type="file" accept="application/json" onChange={importBackup} style={{ display: "none" }} />
            </label>
          </div>
        </Card>

        {status && (
          <div style={{ fontSize: 13, color: "var(--accent)" }}>{status}</div>
        )}
      </div>
    </Page>
  );
}
