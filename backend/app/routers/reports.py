from datetime import date

from fastapi import APIRouter, Depends
from fastapi.responses import PlainTextResponse
from sqlmodel import Session

from ..auth import require_auth
from ..database import get_session
from ..reportlib import build_report, month_range, report_to_markdown, week_range

router = APIRouter(prefix="/api/reports", tags=["reports"], dependencies=[Depends(require_auth)])


@router.get("/weekly")
def weekly_report(anchor: date = None, session: Session = Depends(get_session)):
    anchor = anchor or date.today()
    start, end = week_range(anchor)
    report = build_report(session, start, end, f"Weekly Progress Report — Week of {start}")
    return report


@router.get("/weekly.md", response_class=PlainTextResponse)
def weekly_report_md(anchor: date = None, session: Session = Depends(get_session)):
    anchor = anchor or date.today()
    start, end = week_range(anchor)
    report = build_report(session, start, end, f"Weekly Progress Report — Week of {start}")
    return report_to_markdown(report)


@router.get("/monthly")
def monthly_report(year: int, month: int, session: Session = Depends(get_session)):
    start, end = month_range(year, month)
    report = build_report(session, start, end, f"Monthly Progress Report — {start.strftime('%B %Y')}")
    return report


@router.get("/monthly.md", response_class=PlainTextResponse)
def monthly_report_md(year: int, month: int, session: Session = Depends(get_session)):
    start, end = month_range(year, month)
    report = build_report(session, start, end, f"Monthly Progress Report — {start.strftime('%B %Y')}")
    return report_to_markdown(report)


@router.get("/custom")
def custom_report(start: date, end: date, session: Session = Depends(get_session)):
    report = build_report(session, start, end, f"Progress Report — {start} to {end}")
    return report


@router.get("/custom.md", response_class=PlainTextResponse)
def custom_report_md(start: date, end: date, session: Session = Depends(get_session)):
    report = build_report(session, start, end, f"Progress Report — {start} to {end}")
    return report_to_markdown(report)
