from typing import List

from fastapi import APIRouter, Depends
from sqlmodel import Session, select

from ..auth import require_auth
from ..database import get_session
from ..models import Entry, Experiment, InventoryItem, Protocol, Task

router = APIRouter(prefix="/api/search", tags=["search"], dependencies=[Depends(require_auth)])


@router.get("")
def search(q: str, session: Session = Depends(get_session)) -> dict:
    ql = q.lower().strip()
    if not ql:
        return {"entries": [], "experiments": [], "protocols": [], "inventory": [], "tasks": []}

    entries = [
        e for e in session.exec(select(Entry)).all()
        if ql in e.title.lower() or ql in e.body.lower() or ql in e.tags.lower()
    ]
    experiments = [
        e for e in session.exec(select(Experiment)).all()
        if ql in e.title.lower() or ql in e.hypothesis.lower() or ql in e.results.lower()
        or ql in e.conclusion.lower() or ql in e.tags.lower()
    ]
    protocols = [
        p for p in session.exec(select(Protocol)).all()
        if ql in p.name.lower() or ql in p.description.lower() or ql in p.category.lower() or ql in p.tags.lower()
    ]
    inventory = [
        i for i in session.exec(select(InventoryItem)).all()
        if ql in i.name.lower() or ql in i.location.lower() or ql in i.lot_number.lower()
        or ql in i.notes.lower() or ql in i.sequence.lower()
    ]
    tasks = [
        t for t in session.exec(select(Task)).all()
        if ql in t.title.lower() or ql in t.description.lower()
    ]

    return {
        "entries": entries[:25],
        "experiments": experiments[:25],
        "protocols": protocols[:25],
        "inventory": inventory[:25],
        "tasks": tasks[:25],
    }
