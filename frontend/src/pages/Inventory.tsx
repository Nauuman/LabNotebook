import { useEffect, useState } from "react";
import { api, qs } from "../api";
import type { InventoryCategory, InventoryItem } from "../types";
import { EmptyState, Field, Modal, Page } from "../components/ui";

const CATEGORIES: InventoryCategory[] = ["reagent", "antibody", "primer", "plasmid", "cell_line", "sample", "consumable", "other"];

const emptyItem = (): Partial<InventoryItem> => ({
  name: "",
  category: "reagent",
  quantity: 0,
  unit: "",
  location: "",
  lot_number: "",
  supplier: "",
  expiry_date: null,
  low_stock_threshold: null,
  notes: "",
  sequence: "",
});

function isExpiringSoon(dateStr?: string | null): boolean {
  if (!dateStr) return false;
  const days = (new Date(dateStr).getTime() - Date.now()) / 86400000;
  return days <= 30;
}

export default function Inventory() {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [editing, setEditing] = useState<Partial<InventoryItem> | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    api
      .get<InventoryItem[]>("/inventory" + qs({ q, category: category || undefined, low_stock: lowStockOnly || undefined }))
      .then(setItems)
      .finally(() => setLoading(false));
  }

  useEffect(load, [category, lowStockOnly]);

  function save() {
    if (!editing) return;
    const req = editing.id ? api.put<InventoryItem>(`/inventory/${editing.id}`, editing) : api.post<InventoryItem>("/inventory", editing);
    req.then(() => {
      setEditing(null);
      load();
    });
  }

  function remove(id: number) {
    if (!confirm("Delete this inventory item?")) return;
    api.del(`/inventory/${id}`).then(load);
  }

  return (
    <Page
      title="Inventory & Samples"
      actions={<button className="btn btn-primary" onClick={() => setEditing(emptyItem())}>+ New Item</button>}
    >
      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        <input
          className="input"
          style={{ width: 240 }}
          placeholder="Search name, location, lot, sequence…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && load()}
          onBlur={load}
        />
        <select className="input" style={{ width: 160 }} value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace("_", " ")}</option>)}
        </select>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
          <input type="checkbox" checked={lowStockOnly} onChange={(e) => setLowStockOnly(e.target.checked)} />
          Low stock only
        </label>
      </div>

      {loading && <div>Loading…</div>}
      {!loading && items.length === 0 && <EmptyState text="No inventory items match." />}

      {items.length > 0 && (
        <div className="card" style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ textAlign: "left", borderBottom: "1px solid var(--border)" }}>
                {["Name", "Category", "Qty", "Location", "Lot #", "Expiry", "", ""].map((h) => (
                  <th key={h} style={{ padding: "8px 10px", color: "var(--text-dim)", fontWeight: 600, fontSize: 11, textTransform: "uppercase" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((i) => {
                const low = i.low_stock_threshold != null && i.quantity <= i.low_stock_threshold;
                const expiring = isExpiringSoon(i.expiry_date);
                return (
                  <tr key={i.id} style={{ borderBottom: "1px solid var(--border)" }}>
                    <td style={{ padding: "8px 10px", fontWeight: 600 }}>{i.name}</td>
                    <td style={{ padding: "8px 10px" }}>{i.category.replace("_", " ")}</td>
                    <td style={{ padding: "8px 10px", color: low ? "var(--danger)" : undefined }}>
                      {i.quantity} {i.unit}
                    </td>
                    <td style={{ padding: "8px 10px" }}>{i.location}</td>
                    <td style={{ padding: "8px 10px" }}>{i.lot_number}</td>
                    <td style={{ padding: "8px 10px", color: expiring ? "var(--warning)" : undefined }}>{i.expiry_date || "—"}</td>
                    <td style={{ padding: "8px 10px" }}>
                      <button className="btn" onClick={() => setEditing(i)}>Edit</button>
                    </td>
                    <td style={{ padding: "8px 10px" }}>
                      <button className="btn" onClick={() => remove(i.id)}>Delete</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? "Edit Item" : "New Inventory Item"} onClose={() => setEditing(null)} wide>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Name">
              <input className="input" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
            </Field>
            <Field label="Category">
              <select className="input" value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value as InventoryCategory })}>
                {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace("_", " ")}</option>)}
              </select>
            </Field>
            <Field label="Quantity">
              <input type="number" className="input" value={editing.quantity} onChange={(e) => setEditing({ ...editing, quantity: Number(e.target.value) })} />
            </Field>
            <Field label="Unit">
              <input className="input" value={editing.unit} onChange={(e) => setEditing({ ...editing, unit: e.target.value })} placeholder="tubes, mL, uL@10uM…" />
            </Field>
            <Field label="Location (freezer/box/position)">
              <input className="input" value={editing.location} onChange={(e) => setEditing({ ...editing, location: e.target.value })} />
            </Field>
            <Field label="Lot Number">
              <input className="input" value={editing.lot_number} onChange={(e) => setEditing({ ...editing, lot_number: e.target.value })} />
            </Field>
            <Field label="Supplier">
              <input className="input" value={editing.supplier} onChange={(e) => setEditing({ ...editing, supplier: e.target.value })} />
            </Field>
            <Field label="Expiry Date">
              <input type="date" className="input" value={editing.expiry_date || ""} onChange={(e) => setEditing({ ...editing, expiry_date: e.target.value })} />
            </Field>
            <Field label="Low Stock Threshold (optional)">
              <input
                type="number"
                className="input"
                value={editing.low_stock_threshold ?? ""}
                onChange={(e) => setEditing({ ...editing, low_stock_threshold: e.target.value ? Number(e.target.value) : null })}
              />
            </Field>
          </div>
          <Field label="Sequence (primers/plasmids, optional)">
            <input className="input" value={editing.sequence} onChange={(e) => setEditing({ ...editing, sequence: e.target.value })} />
          </Field>
          <Field label="Notes">
            <textarea className="input" rows={2} value={editing.notes} onChange={(e) => setEditing({ ...editing, notes: e.target.value })} />
          </Field>
          <button className="btn btn-primary" onClick={save}>Save Item</button>
        </Modal>
      )}
    </Page>
  );
}
