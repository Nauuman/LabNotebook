# LabNotebook

A personal, self-hosted lab notebook for molecular biology work: dated notebook
entries, protocols/SOPs, experiment tracking, reagent & sample inventory, a
task list, full-text search, and auto-generated **weekly / monthly / custom
progress reports** you can export as Markdown.

All data lives in a local SQLite database. Nothing is sent to any third
party — your notebook stays private. "Cloud backup" is handled by exporting
a JSON snapshot and pushing it to this git repository (see **Backups**
below), so GitHub becomes your off-site copy.

## Features

- **Notebook** — dated, tagged entries, optionally linked to an experiment.
- **Experiments** — status pipeline (planned → in progress → completed /
  failed / on hold), hypothesis, results, conclusion, linked protocol.
- **Protocols** — a reusable SOP library with ordered, timed steps.
- **Inventory & Samples** — reagents, antibodies, primers, plasmids, cell
  lines, samples and consumables in one table, with storage location, lot
  numbers, expiry dates, and low-stock alerts.
- **Tasks** — a simple to-do list with due dates, priority, and optional
  links to an experiment.
- **Reports** — one-click weekly, monthly, or custom-range progress reports
  summarizing everything logged in that period; downloadable as Markdown.
- **Global search** across entries, experiments, protocols, inventory, and
  tasks.
- **Export / Import** — full JSON backup/restore from the Settings page.

## Running locally

Requirements: Python 3.11+, Node 20+.

```bash
# Backend
cd backend
python3 -m venv .venv
./.venv/bin/pip install -r requirements.txt
./.venv/bin/python -m app.seed      # optional: adds a few example records
./.venv/bin/uvicorn app.main:app --reload --port 8000

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — the Vite dev server proxies `/api` requests to
the backend on port 8000.

For a single production build served directly by the backend (no separate
frontend server needed):

```bash
cd frontend && npm install && npm run build
cd ../backend && ./.venv/bin/uvicorn app.main:app --port 8000
```

Then open http://localhost:8000.

## Running with Docker

```bash
docker compose up --build
```

Open http://localhost:8000. Data persists in the `lab_data` Docker volume.

## Deploying so you can reach it from anywhere

This app has no built-in multi-user accounts — it's meant for one person.
If you deploy it somewhere reachable over the internet (Render, Fly.io,
Railway, a home server, etc.), set the `LABNOTEBOOK_PASSWORD` environment
variable to a strong secret. The app then requires that value as an
`X-Auth-Token` header on every API call; enter the same value under
**Settings → Access Password** in the web UI once, and it's remembered in
that browser. Leave the variable unset for local-only use.

## Backups (your "cloud backup")

Two options, both keep your data in this git repository on GitHub:

1. **From the app:** Settings → "Download JSON Backup", then commit the file
   yourself.
2. **Automated:** run `scripts/backup.sh` (optionally passing your deployed
   API URL as the first argument). It exports a full snapshot to
   `backups/`, commits it, and pushes. Set `LABNOTEBOOK_PASSWORD` in your
   shell environment first if the API is password-protected. Wire this into
   a cron job or a scheduled CI workflow if you want it fully automatic.

The SQLite database file itself (`backend/data/lab.db`) is intentionally
**not** committed to git (see `.gitignore`) since it can contain binary diffs
and grows over time — the JSON snapshots in `backups/` are the durable,
diffable backup format.

## Project layout

```
backend/    FastAPI + SQLite app (app/main.py, app/models.py, app/routers/*)
frontend/   React + TypeScript + Vite single-page app
scripts/    backup.sh — exports data and pushes it to git
backups/    JSON snapshots created by scripts/backup.sh
```

## Tech stack

- Backend: FastAPI, SQLModel (SQLAlchemy + Pydantic), SQLite
- Frontend: React, TypeScript, Vite, React Router, Tailwind CSS (via
  `@tailwindcss/postcss`)
