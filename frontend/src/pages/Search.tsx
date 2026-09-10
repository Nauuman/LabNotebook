import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, qs } from "../api";
import type { SearchResults } from "../types";
import { Card, EmptyState, Page } from "../components/ui";

export default function SearchPage() {
  const [params] = useSearchParams();
  const q = params.get("q") || "";
  const [results, setResults] = useState<SearchResults | null>(null);

  useEffect(() => {
    if (!q) return;
    api.get<SearchResults>("/search" + qs({ q })).then(setResults);
  }, [q]);

  const total = results
    ? results.entries.length + results.experiments.length + results.protocols.length + results.inventory.length + results.tasks.length
    : 0;

  return (
    <Page title={`Search results for "${q}"`}>
      {!results && <div>Searching…</div>}
      {results && total === 0 && <EmptyState text="No matches found." />}
      {results && total > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <ResultGroup title="Notebook Entries" items={results.entries.map((e) => `${e.date} — ${e.title}`)} />
          <ResultGroup title="Experiments" items={results.experiments.map((e) => `${e.title} (${e.status})`)} />
          <ResultGroup title="Protocols" items={results.protocols.map((p) => p.name)} />
          <ResultGroup title="Inventory" items={results.inventory.map((i) => `${i.name} — ${i.location}`)} />
          <ResultGroup title="Tasks" items={results.tasks.map((t) => t.title)} />
        </div>
      )}
    </Page>
  );
}

function ResultGroup({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <Card>
      <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>{title} ({items.length})</div>
      <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: "flex", flexDirection: "column", gap: 4 }}>
        {items.map((t, i) => <li key={i}>{t}</li>)}
      </ul>
    </Card>
  );
}
