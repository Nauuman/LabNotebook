from datetime import date, datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from ..auth import require_auth
from ..coerce import coerce_dates
from ..database import get_session
from ..models import InventoryCategory, InventoryItem

router = APIRouter(prefix="/api/inventory", tags=["inventory"], dependencies=[Depends(require_auth)])


@router.get("", response_model=List[InventoryItem])
def list_inventory(
    q: Optional[str] = None,
    category: Optional[InventoryCategory] = None,
    low_stock: bool = False,
    expiring_soon_days: Optional[int] = None,
    session: Session = Depends(get_session),
):
    items = session.exec(select(InventoryItem)).all()
    if category:
        items = [i for i in items if i.category == category]
    if q:
        ql = q.lower()
        items = [
            i for i in items
            if ql in i.name.lower() or ql in i.location.lower() or ql in i.lot_number.lower()
            or ql in i.notes.lower() or ql in i.sequence.lower()
        ]
    if low_stock:
        items = [i for i in items if i.low_stock_threshold is not None and i.quantity <= i.low_stock_threshold]
    if expiring_soon_days is not None:
        cutoff = date.today().toordinal() + expiring_soon_days
        items = [i for i in items if i.expiry_date and i.expiry_date.toordinal() <= cutoff]
    return sorted(items, key=lambda i: i.name.lower())


@router.post("", response_model=InventoryItem)
def create_item(item: InventoryItem, session: Session = Depends(get_session)):
    data = coerce_dates(item.model_dump(exclude={"id"}))
    obj = InventoryItem(**data)
    session.add(obj)
    session.commit()
    session.refresh(obj)
    return obj


@router.get("/{item_id}", response_model=InventoryItem)
def get_item(item_id: int, session: Session = Depends(get_session)):
    item = session.get(InventoryItem, item_id)
    if not item:
        raise HTTPException(404, "Item not found")
    return item


@router.put("/{item_id}", response_model=InventoryItem)
def update_item(item_id: int, data: InventoryItem, session: Session = Depends(get_session)):
    item = session.get(InventoryItem, item_id)
    if not item:
        raise HTTPException(404, "Item not found")
    update = coerce_dates(data.model_dump(exclude_unset=True, exclude={"id", "created_at"}))
    for k, v in update.items():
        setattr(item, k, v)
    item.updated_at = datetime.utcnow()
    session.add(item)
    session.commit()
    session.refresh(item)
    return item


@router.delete("/{item_id}")
def delete_item(item_id: int, session: Session = Depends(get_session)):
    item = session.get(InventoryItem, item_id)
    if not item:
        raise HTTPException(404, "Item not found")
    session.delete(item)
    session.commit()
    return {"ok": True}
