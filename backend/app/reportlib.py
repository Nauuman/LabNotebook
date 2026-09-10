from datetime import date, timedelta
from typing import Optional

from sqlmodel import Session, select

from .models import Entry, Experiment, ExperimentStatus, InventoryItem, Task, TaskStatus


def week_range(anchor: date) -> tuple[date, date]:
    start = anchor - timedelta(days=anchor.weekday())  # Monday
    end = start + timedelta(days=6)
    return start, end


def month_range(year: int, month: int) -> tuple[date, date]:
    start = date(year, month, 1)
    if month == 12:
        end = date(year, 12, 31)
    else:
        end = date(year, month + 1, 1) - timedelta(days=1)
    return start, end


def build_report(session: Session, start: date, end: date, title: str) -> dict:
    entries = [e for e in session.exec(select(Entry)).all() if start <= e.date <= end]
    experiments = session.exec(select(Experiment)).all()
    started = [e for e in experiments if e.start_date and start <= e.start_date <= end]
    completed = [
        e for e in experiments
        if e.end_date and start <= e.end_date <= end and e.status == ExperimentStatus.completed
    ]
    failed = [
        e for e in experiments
        if e.end_date and start <= e.end_date <= end and e.status == ExperimentStatus.failed
    ]
    ongoing = [e for e in experiments if e.status == ExperimentStatus.in_progress]

    tasks = session.exec(select(Task)).all()
    tasks_completed = [
        t for t in tasks if t.status == TaskStatus.done and start <= t.updated_at.date() <= end
    ]
    upcoming_tasks = [
        t for t in tasks
        if t.status == TaskStatus.open and t.due_date and start <= t.due_date <= end + timedelta(days=14)
    ]

    inventory = session.exec(select(InventoryItem)).all()
    low_stock = [i for i in inventory if i.low_stock_threshold is not None and i.quantity <= i.low_stock_threshold]
    expiring = [i for i in inventory if i.expiry_date and start <= i.expiry_date <= end + timedelta(days=30)]

    entries.sort(key=lambda e: e.date)
    started.sort(key=lambda e: e.start_date)
    completed.sort(key=lambda e: e.end_date)
    failed.sort(key=lambda e: e.end_date)
    upcoming_tasks.sort(key=lambda t: t.due_date)

    return {
        "title": title,
        "range_start": start,
        "range_end": end,
        "entries": entries,
        "experiments_started": started,
        "experiments_completed": completed,
        "experiments_failed": failed,
        "experiments_ongoing": ongoing,
        "tasks_completed": tasks_completed,
        "upcoming_tasks": upcoming_tasks,
        "low_stock": low_stock,
        "expiring_soon": expiring,
        "summary": {
            "entries_logged": len(entries),
            "experiments_started": len(started),
            "experiments_completed": len(completed),
            "experiments_failed": len(failed),
            "experiments_ongoing": len(ongoing),
            "tasks_completed": len(tasks_completed),
        },
    }


def report_to_markdown(report: dict) -> str:
    lines = [f"# {report['title']}", "", f"**Period:** {report['range_start']} to {report['range_end']}", ""]

    s = report["summary"]
    lines += [
        "## Summary",
        "",
        f"- Notebook entries logged: **{s['entries_logged']}**",
        f"- Experiments started: **{s['experiments_started']}**",
        f"- Experiments completed: **{s['experiments_completed']}**",
        f"- Experiments failed: **{s['experiments_failed']}**",
        f"- Experiments currently in progress: **{s['experiments_ongoing']}**",
        f"- Tasks completed: **{s['tasks_completed']}**",
        "",
    ]

    def exp_line(e) -> str:
        d = e.end_date or e.start_date
        return f"- **{e.title}** ({d}) — {e.status.value}" + (f": {e.conclusion}" if e.conclusion else "")

    if report["experiments_completed"]:
        lines += ["## Completed Experiments", ""] + [exp_line(e) for e in report["experiments_completed"]] + [""]
    if report["experiments_failed"]:
        lines += ["## Failed / Troubleshooting", ""] + [exp_line(e) for e in report["experiments_failed"]] + [""]
    if report["experiments_started"]:
        lines += ["## Experiments Started", ""] + [
            f"- **{e.title}** ({e.start_date}) — {e.hypothesis}" for e in report["experiments_started"]
        ] + [""]
    if report["experiments_ongoing"]:
        lines += ["## Ongoing Experiments", ""] + [f"- **{e.title}**" for e in report["experiments_ongoing"]] + [""]

    if report["entries"]:
        lines += ["## Notebook Entries", ""]
        for e in report["entries"]:
            lines.append(f"- **{e.date}** — {e.title}")
        lines.append("")

    if report["tasks_completed"]:
        lines += ["## Tasks Completed", ""] + [f"- {t.title}" for t in report["tasks_completed"]] + [""]
    if report["upcoming_tasks"]:
        lines += ["## Upcoming Tasks", ""] + [
            f"- {t.title} (due {t.due_date}, priority: {t.priority.value})" for t in report["upcoming_tasks"]
        ] + [""]

    if report["low_stock"]:
        lines += ["## Low Stock Alerts", ""] + [
            f"- {i.name}: {i.quantity} {i.unit} remaining (threshold {i.low_stock_threshold})"
            for i in report["low_stock"]
        ] + [""]
    if report["expiring_soon"]:
        lines += ["## Expiring Soon", ""] + [
            f"- {i.name} — expires {i.expiry_date}" for i in report["expiring_soon"]
        ] + [""]

    return "\n".join(lines)
