import io
import os
import secrets
from datetime import datetime, timedelta
from functools import wraps

import pandas as pd
import re
from flask import Flask, jsonify, redirect, render_template, request, send_file, session, url_for
from sqlalchemy import and_, or_, func
from sqlalchemy.exc import IntegrityError

from db_new import engine, SessionLocal, get_db_session, Base
from models import Event, Student, Device, Session as DBSession, SessionAttendance

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


app = Flask(__name__, template_folder="templates")
app.secret_key = os.environ.get("FLASK_SECRET_KEY") or os.urandom(24)

# Development convenience: ensure an admin username/password exists for
# local testing when ADMIN_PASSWORD isn't explicitly set in the environment.
# This only provides a default for development; do NOT rely on this in
# production deployments.
if not os.environ.get("ADMIN_PASSWORD"):
    os.environ.setdefault("ADMIN_USERNAME", "admin")
    os.environ.setdefault("ADMIN_PASSWORD", "admin")
    print("Development: default admin credentials set -> username: 'admin', password: 'admin'")


def _now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _parse_dt(s: str) -> datetime | None:
    try:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S")
    except Exception:
        return None


def init_db() -> None:
    """Create tables using SQLAlchemy ORM. Idempotent."""
    Base.metadata.create_all(engine)
    
    # Ensure at least one event exists (helps older Android migrations that map to eventId=1).
    db = get_db_session()
    try:
        event_count = db.query(func.count(Event.event_id)).scalar()
        if event_count == 0:
            default_event = Event(
                event_name="Default Event",
                start_time="",
                end_time="",
                is_active=1,
                created_at=_now_str(),
            )
            db.add(default_event)
            db.flush()
            
            default_session = DBSession(
                event_id=default_event.event_id,
                session_name="Session 1",
                is_active=1,
                created_at=_now_str(),
            )
            db.add(default_session)
            db.commit()
    finally:
        db.close()


init_db()


def _touch_device(device_id: str, event_id: int | None = None) -> None:
    device_id = (device_id or "").strip()
    if not device_id:
        return
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    now = _now_str()
    
    db = get_db_session()
    try:
        device = db.query(Device).filter(Device.device_id == device_id).first()
        if device:
            device.last_seen = now
            device.last_event_id = event_id
            device.last_ip = ip
        else:
            device = Device(device_id=device_id, last_seen=now, last_event_id=event_id, last_ip=ip)
            db.add(device)
        db.commit()
    finally:
        db.close()


def _touch_device_orm(db, device_id: str, event_id: int | None = None) -> None:
    """Touch device within an active session context."""
    device_id = (device_id or "").strip()
    if not device_id:
        return
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    now = _now_str()
    
    device = db.query(Device).filter(Device.device_id == device_id).first()
    if device:
        device.last_seen = now
        device.last_event_id = event_id
        device.last_ip = ip
    else:
        device = Device(device_id=device_id, last_seen=now, last_event_id=event_id, last_ip=ip)
        db.add(device)


def _attendance_export_rows(event_id: int, present_only: bool) -> list[dict]:
    # Backwards-compatible wrapper (defaults to active session).
    active = _get_active_session(event_id)
    session_id = int(active.get("session_id") or 0) if active else 0
    return _attendance_export_rows_for_session(event_id=event_id, session_id=session_id, present_only=present_only)


def _attendance_export_rows_for_session(*, event_id: int, session_id: int, present_only: bool) -> list[dict]:
    session_id = int(session_id or 0)
    if session_id <= 0:
        session_id = _ensure_default_session(event_id)

    db = get_db_session()
    try:
        if present_only:
            rows = db.query(
                Student.uid, Student.name, Student.branch, Student.year,
                func.literal("Present").label("status"),
                SessionAttendance.timestamp, SessionAttendance.source, SessionAttendance.device_id
            ).join(
                SessionAttendance,
                and_(
                    SessionAttendance.event_id == Student.event_id,
                    SessionAttendance.uid == Student.uid
                )
            ).filter(
                SessionAttendance.event_id == event_id,
                SessionAttendance.session_id == session_id
            ).order_by(
                SessionAttendance.timestamp.desc(), Student.name, Student.uid
            ).all()
        else:
            # Left join: all students, with their session attendance (if any)
            rows = db.query(
                Student.uid, Student.name, Student.branch, Student.year,
                func.case(
                    (SessionAttendance.uid.is_(None), "Absent"),
                    else_="Present"
                ).label("status"),
                func.coalesce(SessionAttendance.timestamp, "").label("timestamp"),
                func.coalesce(SessionAttendance.source, "Imported").label("source"),
                func.coalesce(SessionAttendance.device_id, "").label("device_id")
            ).outerjoin(
                SessionAttendance,
                and_(
                    SessionAttendance.event_id == Student.event_id,
                    SessionAttendance.uid == Student.uid,
                    SessionAttendance.session_id == session_id
                )
            ).filter(
                Student.event_id == event_id
            ).order_by(
                Student.name, Student.uid
            ).all()

        return [dict(row._mapping) if hasattr(row, '_mapping') else dict(zip(['uid', 'name', 'branch', 'year', 'status', 'timestamp', 'source', 'device_id'], row)) for row in rows]
    finally:
        db.close()


def _import_students_from_excel(*, event_id: int, file_storage) -> tuple[int, str | None]:
    """Import roster rows into students table using ORM.

    Returns: (imported_count, error_message)
    """
    if not file_storage:
        return 0, "file is required"
    filename = (getattr(file_storage, "filename", "") or "").lower()
    if not filename.endswith(".xlsx"):
        return 0, "Excel (.xlsx) file required"

    try:
        df = pd.read_excel(file_storage)
    except Exception as e:
        return 0, f"Unable to read Excel: {e}"

    rows, err = _parse_rows_from_dataframe(df)
    if err:
        return 0, err

    inserted = 0
    db = get_db_session()
    try:
        for r in rows:
            uid = r.get("uid", "")
            name = r.get("name", "")
            branch = r.get("branch", "")
            year = r.get("year", "")
            if not uid or not name:
                continue
            
            # Use upsert pattern: find existing or create new
            student = db.query(Student).filter(
                Student.event_id == event_id,
                Student.uid == uid
            ).first()
            
            if student:
                student.name = name
                student.branch = branch
                student.year = year
            else:
                student = Student(
                    event_id=event_id,
                    uid=uid,
                    name=name,
                    branch=branch,
                    year=year,
                    status="Absent",
                    timestamp="",
                    source="Imported",
                    device_id="",
                    device_timestamp=""
                )
                db.add(student)
            inserted += 1
        db.commit()
    finally:
        db.close()

    return inserted, None


def _pending_import_path(token: str) -> str:
    safe = "".join(ch for ch in (token or "") if ch.isalnum() or ch in ("-", "_"))
    return os.path.join(UPLOAD_FOLDER, f"pending_import_{safe}.xlsx")


def _cleanup_pending_imports(*, max_age_minutes: int = 60) -> None:
    try:
        cutoff = datetime.now() - timedelta(minutes=max_age_minutes)
        for name in os.listdir(UPLOAD_FOLDER):
            if not name.startswith("pending_import_") or not name.endswith(".xlsx"):
                continue
            path = os.path.join(UPLOAD_FOLDER, name)
            try:
                mtime = datetime.fromtimestamp(os.path.getmtime(path))
                if mtime < cutoff:
                    os.remove(path)
            except Exception:
                continue
    except Exception:
        return


def _excel_roster_preview_from_path(path: str) -> tuple[list[dict], str | None]:
    try:
        df = pd.read_excel(path)
    except Exception as e:
        return [], f"Unable to read Excel: {e}"

    rows, err = _parse_rows_from_dataframe(df)
    return rows, err


def _normalize_header(h: str) -> str:
    return re.sub(r"[^0-9a-z]+", "_", (str(h or "")).strip().lower())


def _map_columns(headers: list[str]) -> dict:
    """Return mapping of logical fields to column names in the dataframe.

    Returns keys: uid_col (str or None), name_cols (list), branch_col, year_col
    """
    norm_to_orig = { _normalize_header(h): h for h in headers }
    norms = list(norm_to_orig.keys())

    def find_any(candidates:list[str]):
        for cand in candidates:
            for n in norms:
                if n == cand or cand in n:
                    return norm_to_orig[n]
        return None

    uid_candidates = ["uid","id","student_id","studentid","roll","roll_no","enrollment","registration","card","card_number","barcode"]
    uid_col = find_any(uid_candidates)

    # Name detection: prefer single "name" or "full_name", else combine first+last
    name_col = find_any(["name","full_name","fullname"])
    name_cols = []
    if name_col:
        name_cols = [name_col]
    else:
        # search for first/last/given/surname style columns
        first = find_any(["first","given","forename"])
        last = find_any(["last","surname","family"])
        if first and last:
            name_cols = [first, last]
        else:
            # fallback: any header containing 'name'
            name_like = [v for k,v in norm_to_orig.items() if 'name' in k]
            if name_like:
                name_cols = name_like

    branch_col = find_any(["branch","dept","department","program","programme","course"]) 
    year_col = find_any(["year","class","semester","batch"])

    return {"uid_col": uid_col, "name_cols": name_cols, "branch_col": branch_col, "year_col": year_col}


def _parse_rows_from_dataframe(df: pd.DataFrame) -> tuple[list[dict], str | None]:
    # Normalize headers but keep original names
    headers = [str(c) for c in df.columns]
    mapping = _map_columns(headers)

    if not mapping.get("uid_col"):
        return [], "Unable to detect UID column. Expected column names like UID, id, student_id, roll, enrollment, barcode"

    if not mapping.get("name_cols"):
        return [], "Unable to detect Name column(s). Expected column names like Name, Full Name, First/Last"

    rows: list[dict] = []
    seen = set()
    for _, row in df.iterrows():
        uid_raw = row.get(mapping["uid_col"]) if mapping.get("uid_col") else None
        uid = (str(uid_raw or "")).strip()
        if not uid:
            continue

        # build name
        name_parts = []
        for nc in mapping.get("name_cols", []):
            val = str(row.get(nc) or "").strip()
            if val:
                name_parts.append(val)
        name = " ".join(name_parts).strip()
        if not name:
            continue

        branch = str(row.get(mapping.get("branch_col")) or "").strip() if mapping.get("branch_col") else ""
        year = str(row.get(mapping.get("year_col")) or "").strip() if mapping.get("year_col") else ""

        # Normalize UID minimal: trim spaces
        uid_norm = uid
        if uid_norm in seen:
            # skip duplicate UID rows from the same sheet
            continue
        seen.add(uid_norm)

        rows.append({"uid": uid_norm, "name": name, "branch": branch, "year": year})

    if not rows:
        return [], "No valid rows with UID and Name found in the Excel file"
    return rows, None


def _get_event(event_id: int) -> dict | None:
    db = get_db_session()
    try:
        event = db.query(Event).filter(Event.event_id == event_id).first()
        return dict(event.__dict__) if event else None
    finally:
        db.close()


def _require_event_id() -> int | None:
    raw = request.args.get("event_id") or request.form.get("event_id")
    if raw is None:
        return None
    try:
        eid = int(raw)
    except ValueError:
        return None
    return eid if eid > 0 else None


def _get_sessions(event_id: int) -> list[dict]:
    db = get_db_session()
    try:
        sessions = db.query(DBSession).filter(DBSession.event_id == event_id).order_by(DBSession.session_id.desc()).all()
        return [dict(s.__dict__) for s in sessions]
    finally:
        db.close()


def _get_active_session(event_id: int) -> dict | None:
    db = get_db_session()
    try:
        session = db.query(DBSession).filter(
            DBSession.event_id == event_id,
            DBSession.is_active == 1
        ).order_by(DBSession.session_id.desc()).first()
        return dict(session.__dict__) if session else None
    finally:
        db.close()


def _ensure_default_session(event_id: int) -> int:
    active = _get_active_session(event_id)
    if active:
        return int(active.get("session_id") or 0)

    db = get_db_session()
    try:
        # Find the most recent session for this event
        session = db.query(DBSession).filter(
            DBSession.event_id == event_id
        ).order_by(DBSession.session_id.desc()).first()
        
        if session:
            session.is_active = 1
            db.commit()
            return int(session.session_id)

        # Create a new default session if none exist
        new_session = DBSession(
            event_id=event_id,
            session_name="Session 1",
            is_active=1,
            created_at=_now_str()
        )
        db.add(new_session)
        db.flush()
        session_id = new_session.session_id
        db.commit()
        return session_id
    finally:
        db.close()


def _reset_event_roster_for_new_session(event_id: int) -> None:
    """Reset per-student attendance fields so a new session can be taken for the same event."""
    db = get_db_session()
    try:
        db.query(Student).filter(Student.event_id == event_id).update({
            Student.status: "Absent",
            Student.timestamp: "",
            Student.source: "Imported",
            Student.device_id: "",
            Student.device_timestamp: ""
        })
        db.commit()
    finally:
        db.close()


def _roster_counts(event_id: int) -> dict:
    # Backwards-compatible wrapper (defaults to active session).
    active = _get_active_session(event_id)
    session_id = int(active.get("session_id") or 0) if active else 0
    return _session_counts(event_id=event_id, session_id=session_id)


def _session_counts(*, event_id: int, session_id: int) -> dict:
    session_id = int(session_id or 0)
    if session_id <= 0:
        session_id = _ensure_default_session(event_id)

    db = get_db_session()
    try:
        total = db.query(func.count(Student.uid)).filter(Student.event_id == event_id).scalar() or 0
        present = db.query(func.count(SessionAttendance.uid)).filter(
            SessionAttendance.event_id == event_id,
            SessionAttendance.session_id == session_id
        ).scalar() or 0
        return {"total": int(total), "present": int(present), "remaining": max(int(total) - int(present), 0), "total_scanned": int(present)}
    finally:
        db.close()


def admin_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not session.get("is_admin"):
            if request.path.startswith("/admin/api/"):
                return jsonify({"error": "unauthorized"}), 401
            return redirect(url_for("admin_login"))
        return fn(*args, **kwargs)

    return wrapper


def _admin_password_configured() -> bool:
    return bool(os.environ.get("ADMIN_PASSWORD"))


def _check_admin(username: str, password: str) -> bool:
    expected_user = os.environ.get("ADMIN_USERNAME", "admin")
    expected_pass = os.environ.get("ADMIN_PASSWORD", "")
    return username == expected_user and password == expected_pass and expected_pass != ""


@app.get("/")
def home():
    return jsonify({"ok": True, "service": "attendance"})


@app.get("/events")
def list_events():
    active_only = request.args.get("active") == "1"
    db = get_db_session()
    try:
        if active_only:
            events = db.query(Event).filter(Event.is_active == 1).order_by(Event.event_id.desc()).all()
        else:
            events = db.query(Event).order_by(Event.event_id.desc()).all()
        return jsonify([dict(e.__dict__) for e in events])
    finally:
        db.close()


@app.post("/import")
@admin_required
def import_excel():
    # Backwards-compatible endpoint.
    # Prefer using /admin/api/import from the admin dashboard.
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    imported, err = _import_students_from_excel(event_id=event_id, file_storage=request.files.get("file"))
    if err:
        return jsonify({"error": err}), 400
    return jsonify({"success": True, "event_id": event_id, "imported": imported})


@app.post("/admin/api/import")
@admin_required
def admin_api_import_excel():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Invalid event_id"}), 404

    imported, err = _import_students_from_excel(event_id=event_id, file_storage=request.files.get("file"))
    if err:
        return jsonify({"error": err}), 400

    return jsonify({"success": True, "event_id": event_id, "imported": imported})


@app.post("/admin/api/import/preview")
@admin_required
def admin_api_import_preview():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Invalid event_id"}), 404

    file = request.files.get("file")
    if not file:
        return jsonify({"error": "file is required"}), 400

    if not ((file.filename or "").lower().endswith(".xlsx")):
        return jsonify({"error": "Excel (.xlsx) file required"}), 400

    _cleanup_pending_imports(max_age_minutes=60)
    token = secrets.token_urlsafe(18)
    path = _pending_import_path(token)

    try:
        file.save(path)
    except Exception as e:
        return jsonify({"error": f"Unable to save upload: {e}"}), 400

    rows, err = _excel_roster_preview_from_path(path)
    if err:
        try:
            os.remove(path)
        except Exception:
            pass
        return jsonify({"error": err}), 400

    return jsonify({"success": True, "event_id": event_id, "token": token, "rows": rows, "count": len(rows)})


@app.post("/admin/api/import/confirm")
@admin_required
def admin_api_import_confirm():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    token = (request.form.get("token") or "").strip()
    if not token:
        return jsonify({"error": "token is required"}), 400

    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Invalid event_id"}), 404

    path = _pending_import_path(token)
    if not os.path.exists(path):
        return jsonify({"error": "Import token expired. Please upload again."}), 400

    try:
        with open(path, "rb") as f:
            df = pd.read_excel(f)
    except Exception as e:
        return jsonify({"error": f"Unable to read saved Excel: {e}"}), 400

    # Use the same normalization/import behavior.
    df.columns = [str(c).strip().lower() for c in df.columns]
    required = {"uid", "name"}
    if not required.issubset(set(df.columns)):
        return jsonify({"error": "Excel must have uid and name columns"}), 400

    inserted = 0
    with _db() as conn:
        for _, row in df.iterrows():
            uid = str(row.get("uid", "")).strip()
            name = str(row.get("name", "")).strip()
            branch = str(row.get("branch", "")).strip()
            year = str(row.get("year", "")).strip()
            if not uid or not name:
                continue
            conn.execute(
                """
                INSERT OR REPLACE INTO students
                    (event_id, uid, name, branch, year, status, timestamp, source, device_id, device_timestamp)
                VALUES
                    (?, ?, ?, ?, ?, 'Absent', '', 'Imported', '', '')
                """,
                (event_id, uid, name, branch, year),
            )
            inserted += 1
        conn.commit()

    try:
        os.remove(path)
    except Exception:
        pass

    return jsonify({"success": True, "event_id": event_id, "imported": inserted})


@app.get("/export")
def export_excel():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    present_only = request.args.get("present_only") == "1"
    raw_session_id = request.args.get("session_id")
    try:
        session_id = int(raw_session_id) if raw_session_id is not None and raw_session_id != "" else 0
    except Exception:
        session_id = 0

    rows = _attendance_export_rows_for_session(event_id=event_id, session_id=session_id, present_only=present_only)
    df = pd.DataFrame(rows)
    # Standardize export columns (same as live attendance table):
    # UID, Name, Branch, Year, Status, Time, Source, Device
    df = df.rename(
        columns={
            "uid": "UID",
            "name": "Name",
            "branch": "Branch",
            "year": "Year",
            "status": "Status",
            "timestamp": "Time",
            "source": "Source",
            "device_id": "Device",
        }
    )
    ordered = ["UID", "Name", "Branch", "Year", "Status", "Time", "Source", "Device"]
    df = df[[c for c in ordered if c in df.columns]]
    bio = io.BytesIO()
    with pd.ExcelWriter(bio, engine="openpyxl") as writer:
        df.to_excel(writer, index=False, sheet_name="Attendance")
    bio.seek(0)

    suffix = "present" if present_only else "full"
    session_part = f"_session_{session_id}" if int(session_id or 0) > 0 else ""
    filename = f"attendance_event_{event_id}{session_part}_{suffix}.xlsx"
    return send_file(
        bio,
        as_attachment=True,
        download_name=filename,
        mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )


@app.post("/mark")
def mark_attendance():
    data = request.get_json(silent=True) or {}
    uid = str(data.get("uid", "")).strip()
    event_id = data.get("event_id")
    device_id = str(data.get("device_id", "")).strip()
    device_timestamp = str(data.get("device_timestamp", "")).strip()

    try:
        event_id = int(event_id)
    except Exception:
        event_id = 0

    if not uid or event_id <= 0:
        return jsonify({"error": "uid and event_id are required"}), 400

    db = get_db_session()
    try:
        # Validate event exists and is active
        event = db.query(Event).filter(Event.event_id == event_id).first()
        if not event:
            return jsonify({"error": "Invalid event_id"}), 404
        if not event.is_active:
            return jsonify({"error": "Event is closed"}), 403

        # Ensure active session exists (create default if needed)
        active_session = db.query(DBSession).filter(
            DBSession.event_id == event_id,
            DBSession.is_active == 1
        ).order_by(DBSession.session_id.desc()).first()
        
        if not active_session:
            active_session = DBSession(
                event_id=event_id,
                session_name="Session 1",
                is_active=1,
                created_at=_now_str()
            )
            db.add(active_session)
            db.flush()
        
        session_id = active_session.session_id

        # Touch device
        _touch_device_orm(db, device_id, event_id)

        # Check if student exists
        student = db.query(Student).filter(
            Student.event_id == event_id,
            Student.uid == uid
        ).first()
        
        if not student:
            db.close()
            return jsonify({"error": "Invalid UID"}), 404

        # Check if already marked present
        if student.status and student.status.lower() == "present":
            student_dict = dict(student.__dict__)
            db.close()
            return jsonify({"error": "Already marked", "student": student_dict}), 409

        # Mark present
        now = _now_str()
        student.status = "Present"
        student.timestamp = now
        student.source = "Scanned"
        student.device_id = device_id
        student.device_timestamp = device_timestamp

        # Record session attendance
        session_att = db.query(SessionAttendance).filter(
            SessionAttendance.session_id == session_id,
            SessionAttendance.uid == uid
        ).first()
        
        if not session_att:
            session_att = SessionAttendance(
                session_id=session_id,
                event_id=event_id,
                uid=uid,
                timestamp=now,
                source="Scanned",
                device_id=device_id,
                device_timestamp=device_timestamp
            )
            db.add(session_att)
        else:
            session_att.timestamp = now
            session_att.source = "Scanned"
            session_att.device_id = device_id
            session_att.device_timestamp = device_timestamp

        db.commit()
        student_dict = dict(student.__dict__)
        return jsonify({"success": True, "timestamp": now, "student": student_dict})
    except Exception as e:
        db.rollback()
        return jsonify({"error": "Database error", "detail": str(e)}), 500
    finally:
        db.close()


@app.get("/search")
def search_students():
    event_id = _require_event_id()
    if not event_id:
        return jsonify([])

    q = (request.args.get("q") or "").strip()
    if not q:
        return jsonify([])

    db = get_db_session()
    try:
        students = db.query(Student).filter(
            Student.event_id == event_id,
            or_(Student.name.ilike(f"%{q}%"), Student.uid.ilike(f"%{q}%"))
        ).order_by(Student.name, Student.uid).all()
        return jsonify([dict(s.__dict__) for s in students])
    finally:
        db.close()


@app.post("/add")
def add_student():
    data = request.get_json(silent=True) or {}
    uid = str(data.get("uid", "")).strip()
    name = str(data.get("name", "")).strip()
    branch = str(data.get("branch", "")).strip()
    year = str(data.get("year", "")).strip()
    try:
        event_id = int(data.get("event_id") or 0)
    except Exception:
        event_id = 0

    if not uid or not name or event_id <= 0:
        return jsonify({"error": "event_id, uid and name are required"}), 400

    active_session = _get_active_session(event_id)
    if not active_session:
        _ensure_default_session(event_id)
        active_session = _get_active_session(event_id)
    session_id = int(active_session.get("session_id") or 0) if active_session else 0
    if session_id <= 0:
        return jsonify({"error": "No active session. Please open a session in admin dashboard."}), 403

    now = _now_str()
    db = get_db_session()
    try:
        # Find or create student
        student = db.query(Student).filter(
            Student.event_id == event_id,
            Student.uid == uid
        ).first()
        
        if student and student.status and student.status.lower() == "present":
            student_dict = dict(student.__dict__)
            return jsonify({"error": "Already marked", "student": student_dict}), 409

        if student:
            student.name = name
            student.branch = branch
            student.year = year
            student.status = "Present"
            student.timestamp = now
            student.source = "Manual"
            student.device_id = ""
            student.device_timestamp = ""
        else:
            student = Student(
                event_id=event_id,
                uid=uid,
                name=name,
                branch=branch,
                year=year,
                status="Present",
                timestamp=now,
                source="Manual",
                device_id="",
                device_timestamp=""
            )
            db.add(student)
        
        db.flush()

        # Record session attendance
        session_att = db.query(SessionAttendance).filter(
            SessionAttendance.session_id == session_id,
            SessionAttendance.uid == uid
        ).first()
        
        if not session_att:
            session_att = SessionAttendance(
                session_id=session_id,
                event_id=event_id,
                uid=uid,
                timestamp=now,
                source="Manual",
                device_id="",
                device_timestamp=""
            )
            db.add(session_att)
        else:
            session_att.timestamp = now
            session_att.source = "Manual"

        db.commit()
        student_dict = dict(student.__dict__)
        return jsonify({"success": True, "timestamp": now, "student": student_dict})
    finally:
        db.close()


@app.get("/stats")
def stats():
    event_id = _require_event_id()
    device_id = (request.args.get("device_id") or "").strip()
    try:
        last_event_id = int(request.args.get("event_id") or 0) if request.args.get("event_id") else None
    except Exception:
        last_event_id = None

    if device_id:
        _touch_device(device_id, event_id=last_event_id)
    if event_id:
        return jsonify(_roster_counts(event_id))

    db = get_db_session()
    try:
        total = db.query(func.count(Student.uid)).scalar() or 0
        present = db.query(func.count(Student.uid)).filter(Student.status == "Present").scalar() or 0
        return jsonify({"total": int(total), "present": int(present), "remaining": max(int(total) - int(present), 0)})
    finally:
        db.close()


@app.get("/admin/login")
def admin_login():
    return render_template(
        "admin_login.html",
        error=None,
        password_configured=_admin_password_configured(),
    )


@app.post("/admin/login")
def admin_login_post():
    username = (request.form.get("username") or "").strip()
    password = (request.form.get("password") or "").strip()

    if not _admin_password_configured():
        return render_template(
            "admin_login.html",
            error="Admin password is not configured.",
            password_configured=False,
        )

    if not _check_admin(username, password):
        return render_template(
            "admin_login.html",
            error="Invalid username or password.",
            password_configured=True,
        )

    session["is_admin"] = True
    return redirect(url_for("admin_dashboard"))


@app.post("/admin/logout")
def admin_logout():
    session.clear()
    return redirect(url_for("admin_login"))


@app.get("/admin")
@admin_required
def admin_dashboard():
    db = get_db_session()
    try:
        events = [dict(e.__dict__) for e in db.query(Event).order_by(Event.event_id.desc()).all()]

        selected_event_id = request.args.get("event_id")
        try:
            selected_event_id = int(selected_event_id) if selected_event_id is not None else None
        except ValueError:
            selected_event_id = None

        if not selected_event_id and events:
            active = next((e for e in events if int(e.get("is_active") or 0) == 1), None)
            selected_event_id = int((active or events[0])["event_id"])

        sessions = _get_sessions(int(selected_event_id)) if selected_event_id else []
        active_session = _get_active_session(int(selected_event_id)) if selected_event_id else None
        if selected_event_id and not active_session:
            _ensure_default_session(int(selected_event_id))
            sessions = _get_sessions(int(selected_event_id))
            active_session = _get_active_session(int(selected_event_id))

        return render_template(
            "admin_dashboard.html",
            events=events,
            selected_event_id=selected_event_id,
            sessions=sessions,
            active_session=active_session,
        )
    finally:
        db.close()


@app.get("/admin/api/dashboard")
@admin_required
def admin_api_dashboard():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    raw_session_id = request.args.get("session_id")
    try:
        session_id = int(raw_session_id) if raw_session_id is not None and raw_session_id != "" else 0
    except Exception:
        session_id = 0

    if session_id <= 0:
        active = _get_active_session(event_id)
        session_id = int(active.get("session_id") or 0) if active else _ensure_default_session(event_id)

    summary = _session_counts(event_id=event_id, session_id=session_id)

    online_window_seconds = int(os.environ.get("DEVICE_ONLINE_SECONDS", "120") or "120")
    online_cutoff = datetime.now() - timedelta(seconds=online_window_seconds)

    db = get_db_session()
    try:
        # Get device attendance counts
        device_att_counts = db.query(
            SessionAttendance.device_id,
            func.count(SessionAttendance.uid).label("present_count")
        ).filter(
            SessionAttendance.event_id == event_id,
            SessionAttendance.session_id == session_id,
            SessionAttendance.device_id != ''
        ).group_by(SessionAttendance.device_id).all()
        
        present_by_device = {row[0]: row[1] for row in device_att_counts}

        # Get device info
        devices = db.query(Device).filter(Device.last_event_id == event_id).all()
        device_info = {d.device_id: {"device_id": d.device_id, "last_seen": d.last_seen, "last_ip": d.last_ip} for d in devices}

        # Live attendance for the selected session
        attendance = db.query(
            Student.uid, Student.name, Student.branch, Student.year,
            func.literal("Present").label("status"),
            SessionAttendance.timestamp, SessionAttendance.source, SessionAttendance.device_id
        ).join(
            SessionAttendance,
            and_(
                SessionAttendance.event_id == Student.event_id,
                SessionAttendance.uid == Student.uid
            )
        ).filter(
            SessionAttendance.event_id == event_id,
            SessionAttendance.session_id == session_id
        ).order_by(
            SessionAttendance.timestamp.desc(), Student.name, Student.uid
        ).all()

        device_ids = set(present_by_device.keys()) | set(device_info.keys())
        device_stats: list[dict] = []
        for device_id in device_ids:
            info = device_info.get(device_id, {})
            last_seen = str(info.get("last_seen", "") or "")
            last_seen_dt = _parse_dt(last_seen)
            online = bool(last_seen_dt and last_seen_dt >= online_cutoff)
            device_stats.append(
                {
                    "device_id": device_id,
                    "present_count": int(present_by_device.get(device_id, 0)),
                    "last_seen": last_seen,
                    "last_ip": str(info.get("last_ip", "") or ""),
                    "online": online,
                }
            )
        device_stats.sort(key=lambda d: (not bool(d.get("online")), -(int(d.get("present_count") or 0)), str(d.get("device_id") or "")))

        # Convert attendance rows to dicts
        attendance_list = []
        for row in attendance:
            if hasattr(row, '_mapping'):
                attendance_list.append(dict(row._mapping))
            else:
                attendance_list.append(dict(zip(['uid', 'name', 'branch', 'year', 'status', 'timestamp', 'source', 'device_id'], row)))

        return jsonify(
            {
                "server_time": _now_str(),
                "summary": summary,
                "device_stats": device_stats,
                "session_id": session_id,
                "attendance": attendance_list,
            }
        )
    finally:
        db.close()


@app.post("/admin/events/create")
@admin_required
def admin_create_event():
    name = (request.form.get("event_name") or "").strip()
    start_time = (request.form.get("start_time") or "").strip()
    end_time = (request.form.get("end_time") or "").strip()
    is_active = 1 if (request.form.get("is_active") == "1") else 0
    if not name:
        return redirect(url_for("admin_dashboard"))

    db = get_db_session()
    try:
        event = Event(
            event_name=name,
            start_time=start_time,
            end_time=end_time,
            is_active=is_active,
            created_at=_now_str()
        )
        db.add(event)
        db.flush()

        # Create a default session for the event
        session = DBSession(
            event_id=event.event_id,
            session_name="Session 1",
            is_active=1 if is_active else 0,
            created_at=_now_str()
        )
        db.add(session)
        db.commit()
    finally:
        db.close()

    return redirect(url_for("admin_dashboard"))


@app.get("/admin/api/sessions")
@admin_required
def admin_api_sessions():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    if not _get_event(event_id):
        return jsonify({"error": "Invalid event_id"}), 404

    return jsonify({"event_id": event_id, "sessions": _get_sessions(event_id), "active": _get_active_session(event_id)})


@app.post("/admin/events/<int:event_id>/sessions/create")
@admin_required
def admin_create_session(event_id: int):
    db = get_db_session()
    try:
        event = db.query(Event).filter(Event.event_id == event_id).first()
        if not event:
            return redirect(url_for("admin_dashboard"))

        name = (request.form.get("session_name") or "").strip() or f"Session {_now_str()}"

        # Creating a new session implies we want to take attendance again.
        db.query(Student).filter(Student.event_id == event_id).update({
            Student.status: "Absent",
            Student.timestamp: "",
            Student.source: "Imported",
            Student.device_id: "",
            Student.device_timestamp: ""
        })

        # Deactivate all sessions for this event
        db.query(DBSession).filter(DBSession.event_id == event_id).update({DBSession.is_active: 0})

        # Create new active session
        new_session = DBSession(
            event_id=event_id,
            session_name=name,
            is_active=1,
            created_at=_now_str()
        )
        db.add(new_session)
        db.commit()
    finally:
        db.close()

    return redirect(url_for("admin_dashboard", event_id=event_id))


@app.post("/admin/sessions/<int:session_id>/open")
@admin_required
def admin_open_session(session_id: int):
    db = get_db_session()
    try:
        session = db.query(DBSession).filter(DBSession.session_id == session_id).first()
        if not session:
            return ("", 404)
        
        event_id = session.event_id

        # Check if this is the newest session
        newest = db.query(DBSession).filter(
            DBSession.event_id == event_id
        ).order_by(DBSession.session_id.desc()).first()
        
        if newest and newest.session_id != session_id:
            return (jsonify({"error": "Older sessions are view-only. Create a new session to take attendance again."}), 409)

        # Deactivate all sessions for this event
        db.query(DBSession).filter(DBSession.event_id == event_id).update({DBSession.is_active: 0})

        # Activate this session
        session.is_active = 1
        db.commit()
    finally:
        db.close()

    return ("", 204)


@app.post("/admin/sessions/<int:session_id>/close")
@admin_required
def admin_close_session(session_id: int):
    db = get_db_session()
    try:
        db.query(DBSession).filter(DBSession.session_id == session_id).update({DBSession.is_active: 0})
        db.commit()
    finally:
        db.close()
    return ("", 204)


@app.post("/admin/events/<int:event_id>/close")
@admin_required
def admin_close_event(event_id: int):
    db = get_db_session()
    try:
        db.query(Event).filter(Event.event_id == event_id).update({Event.is_active: 0})
        db.commit()
    finally:
        db.close()
    return ("", 204)


@app.post("/admin/events/<int:event_id>/open")
@admin_required
def admin_open_event(event_id: int):
    db = get_db_session()
    try:
        db.query(Event).filter(Event.event_id == event_id).update({Event.is_active: 1})
        db.commit()
    finally:
        db.close()
    _ensure_default_session(event_id)
    return ("", 204)


@app.post("/admin/events/<int:event_id>/clear")
@admin_required
def admin_clear_event(event_id: int):
    """Clear roster, sessions and attendance for an event so it can be reused."""
    db = get_db_session()
    try:
        event = db.query(Event).filter(Event.event_id == event_id).first()
        if not event:
            return (jsonify({"error": "Invalid event_id"}), 404)

        # Remove per-session attendance and roster entries for this event
        db.query(SessionAttendance).filter(SessionAttendance.event_id == event_id).delete()
        db.query(Student).filter(Student.event_id == event_id).delete()
        db.query(DBSession).filter(DBSession.event_id == event_id).delete()

        # Recreate a default session for this event
        new_session = DBSession(
            event_id=event_id,
            session_name="Session 1",
            is_active=1,
            created_at=_now_str()
        )
        db.add(new_session)
        db.commit()
    finally:
        db.close()

    return ("", 204)


@app.post("/admin/sessions/<int:session_id>/clear")
@admin_required
def admin_clear_session(session_id: int):
    """Clear attendance for a specific session."""
    db = get_db_session()
    try:
        session = db.query(DBSession).filter(DBSession.session_id == session_id).first()
        if not session:
            return (jsonify({"error": "Invalid session_id"}), 404)

        event_id = session.event_id

        # Delete session attendance rows
        db.query(SessionAttendance).filter(SessionAttendance.session_id == session_id).delete()

        # If this session is the active session for the event, reset per-student attendance fields
        active = db.query(DBSession).filter(
            DBSession.event_id == event_id,
            DBSession.is_active == 1
        ).first()
        
        if active and active.session_id == session_id:
            db.query(Student).filter(Student.event_id == event_id).update({
                Student.status: "Absent",
                Student.timestamp: "",
                Student.source: "Imported",
                Student.device_id: "",
                Student.device_timestamp: ""
            })

        db.commit()
    finally:
        db.close()

    return ("", 204)


@app.post("/admin/clear_all")
@admin_required
def admin_clear_all():
    """Clear all app data and recreate a default event + session."""
    db = get_db_session()
    try:
        db.query(SessionAttendance).delete()
        db.query(Student).delete()
        db.query(DBSession).delete()
        db.query(Device).delete()
        db.query(Event).delete()
        db.commit()

        # Seed a default event and session
        default_event = Event(
            event_name="Default Event",
            start_time="",
            end_time="",
            is_active=1,
            created_at=_now_str()
        )
        db.add(default_event)
        db.flush()

        default_session = DBSession(
            event_id=default_event.event_id,
            session_name="Session 1",
            is_active=1,
            created_at=_now_str()
        )
        db.add(default_session)
        db.commit()
    finally:
        db.close()

    return ("", 204)


@app.get("/event/<int:event_id>/live")
def event_live(event_id: int):
    event = _get_event(event_id)
    if not event:
        return ("Not found", 404)
    return render_template("event_live.html", event_id=event_id, event_name=event.get("event_name", f"Event {event_id}"))


@app.get("/api/event/<int:event_id>/live")
def api_event_live(event_id: int):
    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Not found"}), 404
    active = _get_active_session(event_id)
    session_id = int(active.get("session_id") or 0) if active else _ensure_default_session(event_id)
    summary = _session_counts(event_id=event_id, session_id=session_id)
    
    db = get_db_session()
    try:
        recent = db.query(
            Student.uid, Student.name, Student.branch, Student.year,
            func.literal("Present").label("status"),
            SessionAttendance.timestamp, SessionAttendance.source, SessionAttendance.device_id
        ).join(
            SessionAttendance,
            and_(
                SessionAttendance.event_id == Student.event_id,
                SessionAttendance.uid == Student.uid
            )
        ).filter(
            SessionAttendance.event_id == event_id,
            SessionAttendance.session_id == session_id
        ).order_by(
            SessionAttendance.timestamp.desc()
        ).limit(50).all()

        attendance_list = []
        for row in recent:
            if hasattr(row, '_mapping'):
                attendance_list.append(dict(row._mapping))
            else:
                attendance_list.append(dict(zip(['uid', 'name', 'branch', 'year', 'status', 'timestamp', 'source', 'device_id'], row)))

        return jsonify(
            {
                "server_time": _now_str(),
                "event": event,
                "session_id": session_id,
                "attendance": attendance_list,
                **summary,
            }
        )
    finally:
        db.close()


@app.get("/api/event/<int:event_id>/session")
def api_event_session(event_id: int):
    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Not found"}), 404

    active = _get_active_session(event_id)
    if not active:
        _ensure_default_session(event_id)
        active = _get_active_session(event_id)
        if not active:
            return jsonify({"error": "No open session"}), 404

    return jsonify(
        {
            "event_id": event_id,
            "session_id": int(active.get("session_id") or 0),
            "session_name": str(active.get("session_name") or ""),
            "is_open": bool(int(active.get("is_active") or 0) == 1),
        }
    )


@app.get("/api/event/<int:event_id>/roster")
def api_event_roster(event_id: int):
    """Public roster endpoint for Android clients."""
    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Not found"}), 404

    db = get_db_session()
    try:
        students = db.query(Student.uid, Student.name, Student.branch, Student.year).filter(
            Student.event_id == event_id
        ).order_by(Student.name, Student.uid).all()
        
        roster = [dict(zip(['uid', 'name', 'branch', 'year'], s)) for s in students]
        return jsonify(roster)
    finally:
        db.close()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
