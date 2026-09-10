from datetime import date as date_type
from datetime import datetime

# SQLModel table models skip Pydantic validation/coercion in their __init__ and
# on attribute assignment (this is deliberate SQLModel behavior for ORM rows),
# so ISO date/datetime strings coming from JSON request bodies (or a JSON
# backup import) never get converted to Python date/datetime objects
# automatically. SQLite's column types then reject the raw str. This helper
# converts known date/datetime fields before they're used to construct or
# update a row.
DATE_FIELDS = {"date", "start_date", "end_date", "due_date", "expiry_date"}
DATETIME_FIELDS = {"created_at", "updated_at"}


def coerce_dates(data: dict) -> dict:
    out = dict(data)
    for key in DATE_FIELDS:
        value = out.get(key)
        if isinstance(value, str) and value:
            out[key] = date_type.fromisoformat(value)
    for key in DATETIME_FIELDS:
        value = out.get(key)
        if isinstance(value, str) and value:
            out[key] = datetime.fromisoformat(value)
    return out
