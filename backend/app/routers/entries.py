from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from ..auth import require_auth
from ..coerce import coerce_dates
from ..database import get_session
from ..models import Entry

router = APIRouter(prefix="/api/entries", tags=["entries"], dependencies=[Depends(require_auth)])


@router.get("", response_model=List[Entry])
def list_entries(
    q: Optional[str] = None,
    experiment_id: Optional[int] = None,
    session: Session = Depends(get_session),
):
    entries = session.exec(select(Entry)).all()
    if experiment_id is not None:
        entries = [e for e in entries if e.experiment_id == experiment_id]
    if q:
        ql = q.lower()
        entries = [e for e in entries if ql in e.title.lower() or ql in e.body.lower() or ql in e.tags.lower()]
    return sorted(entries, key=lambda e: (e.date, e.created_at), reverse=True)


@router.post("", response_model=Entry)
def create_entry(entry: Entry, session: Session = Depends(get_session)):
    data = coerce_dates(entry.model_dump(exclude={"id"}))
    obj = Entry(**data)
    session.add(obj)
    session.commit()
    session.refresh(obj)
    return obj


@router.get("/{entry_id}", response_model=Entry)
def get_entry(entry_id: int, session: Session = Depends(get_session)):
    entry = session.get(Entry, entry_id)
    if not entry:
        raise HTTPException(404, "Entry not found")
    return entry


@router.put("/{entry_id}", response_model=Entry)
def update_entry(entry_id: int, data: Entry, session: Session = Depends(get_session)):
    entry = session.get(Entry, entry_id)
    if not entry:
        raise HTTPException(404, "Entry not found")
    update = coerce_dates(data.model_dump(exclude_unset=True, exclude={"id", "created_at"}))
    for k, v in update.items():
        setattr(entry, k, v)
    entry.updated_at = datetime.utcnow()
    session.add(entry)
    session.commit()
    session.refresh(entry)
    return entry


@router.delete("/{entry_id}")
def delete_entry(entry_id: int, session: Session = Depends(get_session)):
    entry = session.get(Entry, entry_id)
    if not entry:
        raise HTTPException(404, "Entry not found")
    session.delete(entry)
    session.commit()
    return {"ok": True}
