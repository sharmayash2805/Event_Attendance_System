from sqlalchemy import Column, Integer, Text, ForeignKey
from sqlalchemy.orm import relationship
from db import Base


class Event(Base):
    __tablename__ = "events"
    event_id = Column(Integer, primary_key=True, autoincrement=True)
    event_name = Column(Text, nullable=False)
    start_time = Column(Text)
    end_time = Column(Text)
    is_active = Column(Integer, nullable=False, default=0)
    created_at = Column(Text, nullable=False)


class Student(Base):
    __tablename__ = "students"
    event_id = Column(Integer, ForeignKey("events.event_id"), primary_key=True)
    uid = Column(Text, primary_key=True)
    name = Column(Text, nullable=False)
    branch = Column(Text)
    year = Column(Text)
    status = Column(Text, nullable=False, default="Absent")
    timestamp = Column(Text, nullable=False, default="")
    source = Column(Text, nullable=False, default="Imported")
    device_id = Column(Text, nullable=False, default="")
    device_timestamp = Column(Text, nullable=False, default="")


class Device(Base):
    __tablename__ = "devices"
    device_id = Column(Text, primary_key=True)
    last_seen = Column(Text, nullable=False)
    last_event_id = Column(Integer)
    last_ip = Column(Text, nullable=False, default="")


class Session(Base):
    __tablename__ = "sessions"
    session_id = Column(Integer, primary_key=True, autoincrement=True)
    event_id = Column(Integer, ForeignKey("events.event_id"), nullable=False)
    session_name = Column(Text, nullable=False)
    is_active = Column(Integer, nullable=False, default=0)
    created_at = Column(Text, nullable=False)


class SessionAttendance(Base):
    __tablename__ = "session_attendance"
    session_id = Column(Integer, ForeignKey("sessions.session_id"), primary_key=True)
    event_id = Column(Integer, ForeignKey("events.event_id"), primary_key=True)
    uid = Column(Text, primary_key=True)
    timestamp = Column(Text, nullable=False)
    source = Column(Text, nullable=False)
    device_id = Column(Text, nullable=False, default="")
    device_timestamp = Column(Text, nullable=False, default="")
