import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

DATABASE_URL = os.environ.get("DATABASE_URL")

if not DATABASE_URL:
    # Do not raise here to allow running some local scripts without DATABASE_URL,
    # but print a helpful message when missing.
    raise RuntimeError("DATABASE_URL not set. Set to e.g. postgresql://user:pass@host:5432/dbname")

# Use SQLAlchemy 2.0 style engine
engine = create_engine(DATABASE_URL, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
Base = declarative_base()
