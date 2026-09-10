from datetime import date as date_type
from datetime import datetime
from enum import Enum
from typing import Optional

from sqlmodel import SQLModel, Field


def now() -> datetime:
    return datetime.utcnow()


class ExperimentStatus(str, Enum):
    planned = "planned"
    in_progress = "in_progress"
    completed = "completed"
    failed = "failed"
    on_hold = "on_hold"


class TaskStatus(str, Enum):
    open = "open"
    done = "done"


class TaskPriority(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"


class InventoryCategory(str, Enum):
    reagent = "reagent"
    antibody = "antibody"
    primer = "primer"
    plasmid = "plasmid"
    cell_line = "cell_line"
    sample = "sample"
    consumable = "consumable"
    other = "other"


class Protocol(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str
    category: str = ""
    description: str = ""
    version: str = "1.0"
    tags: str = ""
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)


class ProtocolStep(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    protocol_id: int = Field(foreign_key="protocol.id")
    order: int = 0
    instruction: str
    duration_minutes: Optional[float] = None
    notes: str = ""


class Experiment(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    title: str
    protocol_id: Optional[int] = Field(default=None, foreign_key="protocol.id")
    status: ExperimentStatus = ExperimentStatus.planned
    start_date: Optional[date_type] = None
    end_date: Optional[date_type] = None
    hypothesis: str = ""
    results: str = ""
    conclusion: str = ""
    tags: str = ""
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)


class Entry(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    title: str
    date: date_type = Field(default_factory=date_type.today)
    body: str = ""
    tags: str = ""
    experiment_id: Optional[int] = Field(default=None, foreign_key="experiment.id")
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)


class InventoryItem(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str
    category: InventoryCategory = InventoryCategory.reagent
    quantity: float = 0
    unit: str = ""
    location: str = ""
    lot_number: str = ""
    supplier: str = ""
    expiry_date: Optional[date_type] = None
    low_stock_threshold: Optional[float] = None
    notes: str = ""
    sequence: str = ""
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)


class Task(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    title: str
    description: str = ""
    due_date: Optional[date_type] = None
    status: TaskStatus = TaskStatus.open
    priority: TaskPriority = TaskPriority.medium
    experiment_id: Optional[int] = Field(default=None, foreign_key="experiment.id")
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)
