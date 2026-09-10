from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from ..auth import require_auth
from ..database import get_session
from ..models import Protocol, ProtocolStep

router = APIRouter(prefix="/api/protocols", tags=["protocols"], dependencies=[Depends(require_auth)])


@router.get("", response_model=List[Protocol])
def list_protocols(q: Optional[str] = None, session: Session = Depends(get_session)):
    stmt = select(Protocol)
    protocols = session.exec(stmt).all()
    if q:
        ql = q.lower()
        protocols = [p for p in protocols if ql in p.name.lower() or ql in p.category.lower() or ql in p.tags.lower()]
    return sorted(protocols, key=lambda p: p.name.lower())


@router.post("", response_model=Protocol)
def create_protocol(protocol: Protocol, session: Session = Depends(get_session)):
    protocol.id = None
    session.add(protocol)
    session.commit()
    session.refresh(protocol)
    return protocol


@router.get("/{protocol_id}", response_model=Protocol)
def get_protocol(protocol_id: int, session: Session = Depends(get_session)):
    protocol = session.get(Protocol, protocol_id)
    if not protocol:
        raise HTTPException(404, "Protocol not found")
    return protocol


@router.put("/{protocol_id}", response_model=Protocol)
def update_protocol(protocol_id: int, data: Protocol, session: Session = Depends(get_session)):
    protocol = session.get(Protocol, protocol_id)
    if not protocol:
        raise HTTPException(404, "Protocol not found")
    update = data.dict(exclude_unset=True, exclude={"id", "created_at"})
    for k, v in update.items():
        setattr(protocol, k, v)
    protocol.updated_at = datetime.utcnow()
    session.add(protocol)
    session.commit()
    session.refresh(protocol)
    return protocol


@router.delete("/{protocol_id}")
def delete_protocol(protocol_id: int, session: Session = Depends(get_session)):
    protocol = session.get(Protocol, protocol_id)
    if not protocol:
        raise HTTPException(404, "Protocol not found")
    steps = session.exec(select(ProtocolStep).where(ProtocolStep.protocol_id == protocol_id)).all()
    for s in steps:
        session.delete(s)
    session.delete(protocol)
    session.commit()
    return {"ok": True}


@router.get("/{protocol_id}/steps", response_model=List[ProtocolStep])
def list_steps(protocol_id: int, session: Session = Depends(get_session)):
    steps = session.exec(select(ProtocolStep).where(ProtocolStep.protocol_id == protocol_id)).all()
    return sorted(steps, key=lambda s: s.order)


@router.post("/{protocol_id}/steps", response_model=ProtocolStep)
def add_step(protocol_id: int, step: ProtocolStep, session: Session = Depends(get_session)):
    if not session.get(Protocol, protocol_id):
        raise HTTPException(404, "Protocol not found")
    step.id = None
    step.protocol_id = protocol_id
    session.add(step)
    session.commit()
    session.refresh(step)
    return step


@router.put("/{protocol_id}/steps/{step_id}", response_model=ProtocolStep)
def update_step(protocol_id: int, step_id: int, data: ProtocolStep, session: Session = Depends(get_session)):
    step = session.get(ProtocolStep, step_id)
    if not step or step.protocol_id != protocol_id:
        raise HTTPException(404, "Step not found")
    update = data.dict(exclude_unset=True, exclude={"id", "protocol_id"})
    for k, v in update.items():
        setattr(step, k, v)
    session.add(step)
    session.commit()
    session.refresh(step)
    return step


@router.delete("/{protocol_id}/steps/{step_id}")
def delete_step(protocol_id: int, step_id: int, session: Session = Depends(get_session)):
    step = session.get(ProtocolStep, step_id)
    if not step or step.protocol_id != protocol_id:
        raise HTTPException(404, "Step not found")
    session.delete(step)
    session.commit()
    return {"ok": True}
