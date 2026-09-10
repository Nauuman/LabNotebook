from datetime import datetime

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlmodel import Session, select

from ..auth import require_auth
from ..coerce import coerce_dates
from ..database import get_session
from ..models import Entry, Experiment, InventoryItem, Protocol, ProtocolStep, Task

router = APIRouter(prefix="/api/backup", tags=["backup"], dependencies=[Depends(require_auth)])

TABLES = {
    "protocols": Protocol,
    "protocol_steps": ProtocolStep,
    "experiments": Experiment,
    "entries": Entry,
    "inventory": InventoryItem,
    "tasks": Task,
}


@router.get("/export")
def export_all(session: Session = Depends(get_session)):
    data = {"exported_at": datetime.utcnow().isoformat(), "version": 1}
    for key, model in TABLES.items():
        rows = session.exec(select(model)).all()
        data[key] = [row.dict() for row in rows]
    return JSONResponse(content=_jsonable(data))


def _jsonable(obj):
    import json
    from datetime import date as date_cls

    def default(o):
        if isinstance(o, (datetime, date_cls)):
            return o.isoformat()
        return str(o)

    return json.loads(json.dumps(obj, default=default))


@router.post("/import")
def import_all(payload: dict, session: Session = Depends(get_session), replace: bool = False):
    from ..models import ExperimentStatus, InventoryCategory, TaskPriority, TaskStatus  # noqa

    counts = {}
    if replace:
        for model in TABLES.values():
            for row in session.exec(select(model)).all():
                session.delete(row)
        session.commit()

    for key, model in TABLES.items():
        rows = payload.get(key, [])
        for row in rows:
            row = coerce_dates(dict(row))
            row.pop("id", None)
            obj = model(**row)
            session.add(obj)
        counts[key] = len(rows)

    session.commit()
    return {"imported": counts}
