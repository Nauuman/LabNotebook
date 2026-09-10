from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from ..auth import require_auth
from ..coerce import coerce_dates
from ..database import get_session
from ..models import Experiment, ExperimentStatus

router = APIRouter(prefix="/api/experiments", tags=["experiments"], dependencies=[Depends(require_auth)])


@router.get("", response_model=List[Experiment])
def list_experiments(
    status: Optional[ExperimentStatus] = None,
    q: Optional[str] = None,
    session: Session = Depends(get_session),
):
    stmt = select(Experiment)
    experiments = session.exec(stmt).all()
    if status:
        experiments = [e for e in experiments if e.status == status]
    if q:
        ql = q.lower()
        experiments = [
            e for e in experiments
            if ql in e.title.lower() or ql in e.tags.lower() or ql in e.hypothesis.lower()
        ]
    return sorted(experiments, key=lambda e: e.created_at, reverse=True)


@router.post("", response_model=Experiment)
def create_experiment(experiment: Experiment, session: Session = Depends(get_session)):
    data = coerce_dates(experiment.model_dump(exclude={"id"}))
    obj = Experiment(**data)
    session.add(obj)
    session.commit()
    session.refresh(obj)
    return obj


@router.get("/{experiment_id}", response_model=Experiment)
def get_experiment(experiment_id: int, session: Session = Depends(get_session)):
    experiment = session.get(Experiment, experiment_id)
    if not experiment:
        raise HTTPException(404, "Experiment not found")
    return experiment


@router.put("/{experiment_id}", response_model=Experiment)
def update_experiment(experiment_id: int, data: Experiment, session: Session = Depends(get_session)):
    experiment = session.get(Experiment, experiment_id)
    if not experiment:
        raise HTTPException(404, "Experiment not found")
    update = coerce_dates(data.model_dump(exclude_unset=True, exclude={"id", "created_at"}))
    for k, v in update.items():
        setattr(experiment, k, v)
    experiment.updated_at = datetime.utcnow()
    session.add(experiment)
    session.commit()
    session.refresh(experiment)
    return experiment


@router.delete("/{experiment_id}")
def delete_experiment(experiment_id: int, session: Session = Depends(get_session)):
    experiment = session.get(Experiment, experiment_id)
    if not experiment:
        raise HTTPException(404, "Experiment not found")
    session.delete(experiment)
    session.commit()
    return {"ok": True}
