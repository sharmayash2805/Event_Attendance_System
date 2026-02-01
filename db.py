"""
Database layer: provides abstraction for both SQLite and Postgres using SQLAlchemy ORM.
Automatically detects which backend to use based on DATABASE_URL env var.
"""

import os
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, declarative_base

# Detect backend: Postgres if DATABASE_URL set, else SQLite (local dev)
DATABASE_URL = os.environ.get("DATABASE_URL")

if DATABASE_URL:
    # Postgres
    engine = create_engine(DATABASE_URL, future=True, pool_pre_ping=True)
else:
    # SQLite (local dev)
    DB_PATH = os.environ.get("DB_PATH", "attendance.db")
    DATABASE_URL = f"sqlite:///{DB_PATH}"
    engine = create_engine(DATABASE_URL, future=True)
    
    # Enable SQLite pragmas for better concurrency
    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        try:
            cursor.execute("PRAGMA journal_mode=WAL")
            cursor.execute("PRAGMA synchronous=NORMAL")
            cursor.execute("PRAGMA busy_timeout=30000")
        except Exception:
            pass
        cursor.close()

SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
Base = declarative_base()


def get_db_session():
    """Create and return a new DB session. Caller is responsible for closing."""
    return SessionLocal()
