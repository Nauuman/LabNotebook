from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from ..auth import require_auth
from ..coerce import coerce_dates
from ..database import get_session
from ..models import Task, TaskStatus

router = APIRouter(prefix="/api/tasks", tags=["tasks"], dependencies=[Depends(require_auth)])


@router.get("", response_model=List[Task])
def list_tasks(status: Optional[TaskStatus] = None, session: Session = Depends(get_session)):
    tasks = session.exec(select(Task)).all()
    if status:
        tasks = [t for t in tasks if t.status == status]
    def sort_key(t: Task):
        return (t.due_date is None, t.due_date or t.created_at.date())
    return sorted(tasks, key=sort_key)


@router.post("", response_model=Task)
def create_task(task: Task, session: Session = Depends(get_session)):
    data = coerce_dates(task.model_dump(exclude={"id"}))
    obj = Task(**data)
    session.add(obj)
    session.commit()
    session.refresh(obj)
    return obj


@router.put("/{task_id}", response_model=Task)
def update_task(task_id: int, data: Task, session: Session = Depends(get_session)):
    task = session.get(Task, task_id)
    if not task:
        raise HTTPException(404, "Task not found")
    update = coerce_dates(data.model_dump(exclude_unset=True, exclude={"id", "created_at"}))
    for k, v in update.items():
        setattr(task, k, v)
    task.updated_at = datetime.utcnow()
    session.add(task)
    session.commit()
    session.refresh(task)
    return task


@router.delete("/{task_id}")
def delete_task(task_id: int, session: Session = Depends(get_session)):
    task = session.get(Task, task_id)
    if not task:
        raise HTTPException(404, "Task not found")
    session.delete(task)
    session.commit()
    return {"ok": True}
