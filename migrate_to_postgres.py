"""
Migration helper: create Postgres schema and optionally copy data from an existing SQLite DB.

Usage:
  - Set `DATABASE_URL` env var to your Postgres URL.
  - Optionally set `SQLITE_DB_PATH` to an existing sqlite file to copy data.
  - Run: `python migrate_to_postgres.py`

This script will create the necessary tables in Postgres to match the current SQLite schema.
It attempts a best-effort copy of rows from the SQLite DB when `SQLITE_DB_PATH` is provided.
"""

import os
import sqlite3
from sqlalchemy import (
    MetaData,
    Table,
    Column,
    Integer,
    Text,
    String,
    ForeignKey,
    PrimaryKeyConstraint,
    Index,
    create_engine,
)
from sqlalchemy.exc import SQLAlchemyError

from db import engine

metadata = MetaData()

events = Table(
    "events",
    metadata,
    Column("event_id", Integer, primary_key=True, autoincrement=True),
    Column("event_name", Text, nullable=False),
    Column("start_time", Text),
    Column("end_time", Text),
    Column("is_active", Integer, nullable=False, server_default="0"),
    Column("created_at", Text, nullable=False),
)

students = Table(
    "students",
    metadata,
    Column("event_id", Integer, ForeignKey("events.event_id"), nullable=False),
    Column("uid", Text, nullable=False),
    Column("name", Text, nullable=False),
    Column("branch", Text),
    Column("year", Text),
    Column("status", Text, nullable=False, server_default="Absent"),
    Column("timestamp", Text, nullable=False, server_default=""),
    Column("source", Text, nullable=False, server_default="Imported"),
    Column("device_id", Text, nullable=False, server_default=""),
    Column("device_timestamp", Text, nullable=False, server_default=""),
    PrimaryKeyConstraint("event_id", "uid", name="pk_students_event_uid"),
)

devices = Table(
    "devices",
    metadata,
    Column("device_id", Text, primary_key=True),
    Column("last_seen", Text, nullable=False),
    Column("last_event_id", Integer),
    Column("last_ip", Text, nullable=False, server_default=""),
)

sessions = Table(
    "sessions",
    metadata,
    Column("session_id", Integer, primary_key=True, autoincrement=True),
    Column("event_id", Integer, ForeignKey("events.event_id"), nullable=False),
    Column("session_name", Text, nullable=False),
    Column("is_active", Integer, nullable=False, server_default="0"),
    Column("created_at", Text, nullable=False),
)

session_attendance = Table(
    "session_attendance",
    metadata,
    Column("session_id", Integer, ForeignKey("sessions.session_id"), nullable=False),
    Column("event_id", Integer, ForeignKey("events.event_id"), nullable=False),
    Column("uid", Text, nullable=False),
    Column("timestamp", Text, nullable=False),
    Column("source", Text, nullable=False),
    Column("device_id", Text, nullable=False, server_default=""),
    Column("device_timestamp", Text, nullable=False, server_default=""),
    PrimaryKeyConstraint("session_id", "uid", name="pk_session_attendance"),
)

# Indexes
Index("idx_session_attendance_event", session_attendance.c.event_id)
Index("idx_students_event_uid", students.c.event_id, students.c.uid)


def create_schema():
    print("Creating Postgres schema (if not exists)...")
    metadata.create_all(engine)
    print("Schema created or verified.")


def copy_from_sqlite(sqlite_path: str):
    if not os.path.exists(sqlite_path):
        print(f"SQLite file not found: {sqlite_path}")
        return

    print(f"Copying data from SQLite: {sqlite_path} -> Postgres")
    sconn = sqlite3.connect(sqlite_path)
    sconn.row_factory = sqlite3.Row

    with engine.connect() as conn:
        trans = conn.begin()
        try:
            # Events
            rows = sconn.execute("SELECT * FROM events").fetchall()
            if rows:
                for r in rows:
                    conn.execute(events.insert().prefix_with("ON CONFLICT DO NOTHING"), dict(r))

            # Sessions
            rows = sconn.execute("SELECT * FROM sessions").fetchall()
            if rows:
                for r in rows:
                    conn.execute(sessions.insert().prefix_with("ON CONFLICT DO NOTHING"), dict(r))

            # Devices
            rows = sconn.execute("SELECT * FROM devices").fetchall()
            if rows:
                for r in rows:
                    conn.execute(devices.insert().prefix_with("ON CONFLICT DO NOTHING"), dict(r))

            # Students
            rows = sconn.execute("SELECT * FROM students").fetchall()
            if rows:
                for r in rows:
                    conn.execute(students.insert().prefix_with("ON CONFLICT DO NOTHING"), dict(r))

            # Session attendance
            rows = sconn.execute("SELECT * FROM session_attendance").fetchall()
            if rows:
                for r in rows:
                    conn.execute(session_attendance.insert().prefix_with("ON CONFLICT DO NOTHING"), dict(r))

            trans.commit()
            print("Data copy complete.")
        except Exception as e:
            trans.rollback()
            print("Error during copy:", e)
        finally:
            sconn.close()


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Create Postgres schema and optionally copy from SQLite.")
    parser.add_argument("--copy", action="store_true", help="Copy data from SQLite when provided via --sqlite-path")
    parser.add_argument("--sqlite-path", dest="sqlite_path", help="Path to existing SQLite DB to copy from")
    args = parser.parse_args()

    try:
        create_schema()
    except SQLAlchemyError as e:
        print("Error creating schema:", e)
        raise

    if args.copy:
        if args.sqlite_path:
            copy_from_sqlite(args.sqlite_path)
        else:
            print("--copy specified but no --sqlite-path provided; skipping data copy.")
    else:
        print("Data copy disabled. To copy data explicitly, run with --copy --sqlite-path PATH")
