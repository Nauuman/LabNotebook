"""Populate the database with a few example records so the app isn't empty
on first run. Safe to run multiple times only when the DB is empty --
it skips seeding if any protocol already exists.

Run with: python -m app.seed
"""
from datetime import date, timedelta

from sqlmodel import Session, select

from .database import engine, init_db
from .models import (
    Entry,
    Experiment,
    ExperimentStatus,
    InventoryCategory,
    InventoryItem,
    Protocol,
    ProtocolStep,
    Task,
    TaskPriority,
    TaskStatus,
)


def seed() -> None:
    init_db()
    with Session(engine) as session:
        if session.exec(select(Protocol)).first():
            print("Database already has data, skipping seed.")
            return

        pcr = Protocol(
            name="Standard PCR Amplification",
            category="Molecular Cloning",
            description="Generic high-fidelity PCR protocol for amplifying inserts prior to cloning.",
            version="1.2",
            tags="pcr,cloning,dna",
        )
        session.add(pcr)
        session.commit()
        session.refresh(pcr)

        for i, (instr, dur) in enumerate([
            ("Initial denaturation at 98C", 2),
            ("30 cycles: 98C 10s / 60C 20s / 72C 30s per kb", 30),
            ("Final extension at 72C", 5),
            ("Hold at 4C", None),
        ]):
            session.add(ProtocolStep(protocol_id=pcr.id, order=i, instruction=instr, duration_minutes=dur))
        session.commit()

        exp = Experiment(
            title="Clone insert X into pUC19 backbone",
            protocol_id=pcr.id,
            status=ExperimentStatus.in_progress,
            start_date=date.today() - timedelta(days=2),
            hypothesis="Insert X will amplify cleanly with the designed primers and ligate into pUC19 at the EcoRI/HindIII sites.",
            tags="cloning,plasmid",
        )
        session.add(exp)
        session.commit()
        session.refresh(exp)

        session.add(Entry(
            title="PCR setup for insert X",
            date=date.today() - timedelta(days=2),
            body="Ran PCR per Standard PCR Amplification protocol. 50ng template, annealing at 60C. Ran gel afterward: single clean band at expected 1.2kb.",
            tags="pcr,gel",
            experiment_id=exp.id,
        ))
        session.add(Entry(
            title="Gel purification of insert X",
            date=date.today() - timedelta(days=1),
            body="Purified band from gel using column kit. Eluted in 30uL EB, nanodrop reads 45 ng/uL, 260/280 = 1.9.",
            tags="purification",
            experiment_id=exp.id,
        ))
        session.commit()

        session.add(InventoryItem(
            name="Q5 High-Fidelity DNA Polymerase",
            category=InventoryCategory.reagent,
            quantity=3,
            unit="tubes",
            location="Freezer -20 #1 / Shelf B",
            supplier="NEB",
            low_stock_threshold=2,
            notes="Keep frozen, avoid repeated freeze-thaw.",
        ))
        session.add(InventoryItem(
            name="pUC19 vector",
            category=InventoryCategory.plasmid,
            quantity=15,
            unit="uL aliquot",
            location="Freezer -20 #2 / Box PLASMIDS-A / A3",
            lot_number="PUC19-2024-01",
        ))
        session.add(InventoryItem(
            name="Forward primer - InsertX-Fwd",
            category=InventoryCategory.primer,
            quantity=40,
            unit="uL @ 10uM",
            location="Freezer -20 #1 / Primer Box 2",
            sequence="ATGGCTAGCAAAGGAGAAGAACTT",
            low_stock_threshold=10,
        ))
        session.commit()

        session.add(Task(
            title="Run diagnostic digest on miniprep colonies",
            description="Pick 6 colonies, miniprep, digest with EcoRI/HindIII, run on gel to confirm insert.",
            due_date=date.today() + timedelta(days=3),
            priority=TaskPriority.high,
            experiment_id=exp.id,
        ))
        session.add(Task(
            title="Order more Q5 polymerase",
            description="Stock is running low, order 2 more tubes from NEB.",
            due_date=date.today() + timedelta(days=7),
            priority=TaskPriority.medium,
        ))
        session.commit()

        print("Seeded example data.")


if __name__ == "__main__":
    seed()
