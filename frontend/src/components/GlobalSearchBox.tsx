import { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function GlobalSearchBox() {
  const [q, setQ] = useState("");
  const navigate = useNavigate();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (q.trim()) navigate(`/search?q=${encodeURIComponent(q.trim())}`);
  }

  return (
    <form onSubmit={submit}>
      <input
        className="input"
        placeholder="Search everything… (entries, experiments, protocols, inventory)"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
    </form>
  );
}
