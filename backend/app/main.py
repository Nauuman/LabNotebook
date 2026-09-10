import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from .database import init_db
from .routers import backup, entries, experiments, inventory, protocols, reports, search, tasks

app = FastAPI(title="LabNotebook", description="Personal molecular biology lab notebook & reporting tool")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(entries.router)
app.include_router(experiments.router)
app.include_router(protocols.router)
app.include_router(inventory.router)
app.include_router(tasks.router)
app.include_router(reports.router)
app.include_router(search.router)
app.include_router(backup.router)


@app.on_event("startup")
def on_startup():
    init_db()


@app.get("/api/health")
def health():
    return {"status": "ok"}


BACKEND_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_candidates = [
    os.environ.get("FRONTEND_DIST", ""),
    os.path.join(BACKEND_ROOT, "frontend", "dist"),  # docker image layout
    os.path.join(BACKEND_ROOT, "..", "frontend", "dist"),  # local dev sibling layout
]
FRONTEND_DIST = next((os.path.abspath(p) for p in _candidates if p and os.path.isdir(p)), "")

if os.path.isdir(FRONTEND_DIST):
    app.mount("/assets", StaticFiles(directory=os.path.join(FRONTEND_DIST, "assets")), name="assets")

    @app.get("/{full_path:path}")
    def spa(full_path: str):
        candidate = os.path.join(FRONTEND_DIST, full_path)
        if full_path and os.path.isfile(candidate):
            return FileResponse(candidate)
        return FileResponse(os.path.join(FRONTEND_DIST, "index.html"))
