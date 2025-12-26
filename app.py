import io
import os
import secrets
import sqlite3
from datetime import datetime, timedelta
from functools import wraps

import pandas as pd
from flask import Flask, jsonify, redirect, render_template, request, send_file, session, url_for


DB_PATH = os.environ.get("DB_PATH", "attendance.db")
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


app = Flask(__name__, template_folder="templates")
app.secret_key = os.environ.get("FLASK_SECRET_KEY") or os.urandom(24)


def _now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _parse_dt(s: str) -> datetime | None:
    try:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S")
    except Exception:
        return None


def _db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with _db() as conn:
        c = conn.cursor()
        c.execute(
            """
            CREATE TABLE IF NOT EXISTS events (
                event_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                event_name TEXT NOT NULL,
                start_time TEXT,
                end_time   TEXT,
                is_active  INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )
            """
        )
        c.execute(
            """
            CREATE TABLE IF NOT EXISTS students (
                event_id        INTEGER NOT NULL,
                uid             TEXT NOT NULL,
                name            TEXT NOT NULL,
                branch          TEXT,
                year            TEXT,
                status          TEXT NOT NULL DEFAULT 'Absent',
                timestamp       TEXT NOT NULL DEFAULT '',
                source          TEXT NOT NULL DEFAULT 'Imported',
                device_id       TEXT NOT NULL DEFAULT '',
                device_timestamp TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (event_id, uid),
                FOREIGN KEY (event_id) REFERENCES events(event_id)
            )
            """
        )

        c.execute(
            """
            CREATE TABLE IF NOT EXISTS devices (
                device_id     TEXT PRIMARY KEY,
                last_seen     TEXT NOT NULL,
                last_event_id INTEGER,
                last_ip       TEXT NOT NULL DEFAULT ''
            )
            """
        )

        c.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                session_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id     INTEGER NOT NULL,
                session_name TEXT NOT NULL,
                is_active    INTEGER NOT NULL DEFAULT 0,
                created_at   TEXT NOT NULL,
                FOREIGN KEY (event_id) REFERENCES events(event_id)
            )
            """
        )

        c.execute(
            """
            CREATE TABLE IF NOT EXISTS session_attendance (
                session_id       INTEGER NOT NULL,
                event_id         INTEGER NOT NULL,
                uid              TEXT NOT NULL,
                timestamp        TEXT NOT NULL,
                source           TEXT NOT NULL,
                device_id        TEXT NOT NULL DEFAULT '',
                device_timestamp TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (session_id, uid),
                FOREIGN KEY (session_id) REFERENCES sessions(session_id),
                FOREIGN KEY (event_id) REFERENCES events(event_id)
            )
            """
        )
        # Ensure at least one event exists (helps older Android local migrations that map to eventId=1).
        c.execute("SELECT COUNT(*) AS n FROM events")
        if int(c.fetchone()[0]) == 0:
            c.execute(
                "INSERT INTO events (event_name, start_time, end_time, is_active, created_at) VALUES (?, ?, ?, ?, ?)",
                ("Default Event", "", "", 1, _now_str()),
            )
            # Default session for the seeded event.
            c.execute("SELECT event_id FROM events ORDER BY event_id ASC LIMIT 1")
            seeded_id = c.fetchone()[0]
            c.execute(
                "INSERT INTO sessions (event_id, session_name, is_active, created_at) VALUES (?, ?, ?, ?)",
                (int(seeded_id), "Session 1", 1, _now_str()),
            )
        conn.commit()


init_db()


def _touch_device(device_id: str, event_id: int | None = None) -> None:
    device_id = (device_id or "").strip()
    if not device_id:
        return
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    now = _now_str()
    with _db() as conn:
        conn.execute(
            """
            INSERT INTO devices (device_id, last_seen, last_event_id, last_ip)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                last_seen = excluded.last_seen,
                last_event_id = COALESCE(excluded.last_event_id, devices.last_event_id),
                last_ip = excluded.last_ip
            """,
            (device_id, now, event_id, ip),
        )
        conn.commit()


def _attendance_export_rows(event_id: int, present_only: bool) -> list[dict]:
    # Backwards-compatible wrapper (defaults to active session).
    active = _get_active_session(event_id)
    session_id = int(active.get("session_id") or 0) if active else 0
    return _attendance_export_rows_for_session(event_id=event_id, session_id=session_id, present_only=present_only)


def _attendance_export_rows_for_session(*, event_id: int, session_id: int, present_only: bool) -> list[dict]:
    session_id = int(session_id or 0)
    if session_id <= 0:
        session_id = _ensure_default_session(event_id)

    with _db() as conn:
        if present_only:
            rows = conn.execute(
                """
                SELECT s.uid, s.name, s.branch, s.year,
                       'Present' AS status,
                       sa.timestamp AS timestamp,
                       sa.source AS source,
                       sa.device_id AS device_id
                  FROM session_attendance sa
                  JOIN students s
                    ON s.event_id = sa.event_id AND s.uid = sa.uid
                 WHERE sa.event_id = ? AND sa.session_id = ?
                 ORDER BY sa.timestamp DESC, s.name, s.uid
                """,
                (event_id, session_id),
            ).fetchall()
        else:
            rows = conn.execute(
                """
                SELECT s.uid, s.name, s.branch, s.year,
                       CASE WHEN sa.uid IS NULL THEN 'Absent' ELSE 'Present' END AS status,
                       COALESCE(sa.timestamp, '') AS timestamp,
                       COALESCE(sa.source, 'Imported') AS source,
                       COALESCE(sa.device_id, '') AS device_id
                  FROM students s
                  LEFT JOIN session_attendance sa
                    ON sa.event_id = s.event_id AND sa.uid = s.uid AND sa.session_id = ?
                 WHERE s.event_id = ?
                 ORDER BY s.name, s.uid
                """,
                (session_id, event_id),
            ).fetchall()

    return [dict(r) for r in rows]


def _import_students_from_excel(*, event_id: int, file_storage) -> tuple[int, str | None]:
    """Import roster rows into students table.

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

    # Normalize column names.
    df.columns = [str(c).strip().lower() for c in df.columns]
    required = {"uid", "name"}
    if not required.issubset(set(df.columns)):
        return 0, "Excel must have uid and name columns"

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

    df.columns = [str(c).strip().lower() for c in df.columns]
    required = {"uid", "name"}
    if not required.issubset(set(df.columns)):
        return [], "Excel must have uid and name columns"

    rows: list[dict] = []
    for _, row in df.iterrows():
        uid = str(row.get("uid", "")).strip()
        name = str(row.get("name", "")).strip()
        branch = str(row.get("branch", "")).strip()
        year = str(row.get("year", "")).strip()
        if not uid or not name:
            continue
        rows.append({"uid": uid, "name": name, "branch": branch, "year": year})
    return rows, None


def _get_event(event_id: int) -> dict | None:
    with _db() as conn:
        row = conn.execute("SELECT * FROM events WHERE event_id = ?", (event_id,)).fetchone()
        return dict(row) if row else None


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
    with _db() as conn:
        rows = conn.execute(
            "SELECT * FROM sessions WHERE event_id = ? ORDER BY session_id DESC", (event_id,)
        ).fetchall()
    return [dict(r) for r in rows]


def _get_active_session(event_id: int) -> dict | None:
    with _db() as conn:
        row = conn.execute(
            "SELECT * FROM sessions WHERE event_id = ? AND is_active = 1 ORDER BY session_id DESC LIMIT 1",
            (event_id,),
        ).fetchone()
    return dict(row) if row else None


def _ensure_default_session(event_id: int) -> int:
    active = _get_active_session(event_id)
    if active:
        return int(active.get("session_id") or 0)

    with _db() as conn:
        row = conn.execute(
            "SELECT session_id FROM sessions WHERE event_id = ? ORDER BY session_id DESC LIMIT 1",
            (event_id,),
        ).fetchone()
        if row:
            sid = int(row[0])
            conn.execute("UPDATE sessions SET is_active = 1 WHERE session_id = ?", (sid,))
            conn.commit()
            return sid

        conn.execute(
            "INSERT INTO sessions (event_id, session_name, is_active, created_at) VALUES (?, ?, 1, ?)",
            (event_id, "Session 1", _now_str()),
        )
        sid = int(conn.execute("SELECT last_insert_rowid() AS id").fetchone()[0])
        conn.commit()
        return sid


def _reset_event_roster_for_new_session(event_id: int) -> None:
    """Reset per-student attendance fields so a new session can be taken for the same event.

    Note: This does not preserve historical sessions; it simply starts a fresh run.
    """
    with _db() as conn:
        conn.execute(
            """
            UPDATE students
               SET status = 'Absent',
                   timestamp = '',
                   source = 'Imported',
                   device_id = '',
                   device_timestamp = ''
             WHERE event_id = ?
            """,
            (event_id,),
        )
        conn.commit()


def _roster_counts(event_id: int) -> dict:
    # Backwards-compatible wrapper (defaults to active session).
    active = _get_active_session(event_id)
    session_id = int(active.get("session_id") or 0) if active else 0
    return _session_counts(event_id=event_id, session_id=session_id)


def _session_counts(*, event_id: int, session_id: int) -> dict:
    session_id = int(session_id or 0)
    if session_id <= 0:
        session_id = _ensure_default_session(event_id)

    with _db() as conn:
        total = int(conn.execute("SELECT COUNT(*) AS n FROM students WHERE event_id = ?", (event_id,)).fetchone()[0])
        present = int(
            conn.execute(
                "SELECT COUNT(*) AS n FROM session_attendance WHERE event_id = ? AND session_id = ?",
                (event_id, session_id),
            ).fetchone()[0]
        )
    return {"total": total, "present": present, "remaining": max(total - present, 0), "total_scanned": present}


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
    with _db() as conn:
        if active_only:
            rows = conn.execute("SELECT * FROM events WHERE is_active = 1 ORDER BY event_id DESC").fetchall()
        else:
            rows = conn.execute("SELECT * FROM events ORDER BY event_id DESC").fetchall()
    return jsonify([dict(r) for r in rows])


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

    active_session = _get_active_session(event_id)
    if not active_session:
        _ensure_default_session(event_id)
        active_session = _get_active_session(event_id)
    session_id = int(active_session.get("session_id") or 0) if active_session else 0
    if session_id <= 0:
        return jsonify({"error": "No active session. Please open a session in admin dashboard."}), 403

    _touch_device(device_id, event_id=event_id)

    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Invalid event_id"}), 404
    if not bool(event.get("is_active")):
        return jsonify({"error": "Event is closed"}), 403

    now = _now_str()
    with _db() as conn:
        row = conn.execute(
            "SELECT * FROM students WHERE event_id = ? AND uid = ? LIMIT 1", (event_id, uid)
        ).fetchone()
        if not row:
            return jsonify({"error": "Invalid UID"}), 404

        existing = dict(row)
        if (existing.get("status") or "").lower() == "present":
            return jsonify({"error": "Already marked", "student": existing}), 409

        conn.execute(
            """
            UPDATE students
               SET status = 'Present',
                   timestamp = ?,
                   source = 'Scanned',
                   device_id = ?,
                   device_timestamp = ?
             WHERE event_id = ? AND uid = ?
            """,
            (now, device_id, device_timestamp, event_id, uid),
        )

        # Record per-session attendance (history).
        conn.execute(
            """
            INSERT OR REPLACE INTO session_attendance
                (session_id, event_id, uid, timestamp, source, device_id, device_timestamp)
            VALUES
                (?, ?, ?, ?, 'Scanned', ?, ?)
            """,
            (session_id, event_id, uid, now, device_id, device_timestamp),
        )
        conn.commit()

        updated = conn.execute(
            "SELECT * FROM students WHERE event_id = ? AND uid = ? LIMIT 1", (event_id, uid)
        ).fetchone()
        student = dict(updated) if updated else existing

    return jsonify({"success": True, "timestamp": now, "student": student})


@app.get("/search")
def search_students():
    event_id = _require_event_id()
    if not event_id:
        return jsonify([])

    q = (request.args.get("q") or "").strip()
    if not q:
        return jsonify([])

    with _db() as conn:
        rows = conn.execute(
            """
            SELECT * FROM students
             WHERE event_id = ?
               AND (name LIKE ? OR uid LIKE ?)
             ORDER BY name, uid
            """,
            (event_id, f"%{q}%", f"%{q}%"),
        ).fetchall()
    return jsonify([dict(r) for r in rows])


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
    with _db() as conn:
        row = conn.execute(
            "SELECT * FROM students WHERE event_id = ? AND uid = ? LIMIT 1", (event_id, uid)
        ).fetchone()
        if row and (row["status"] or "").lower() == "present":
            return jsonify({"error": "Already marked", "student": dict(row)}), 409

        conn.execute(
            """
            INSERT OR REPLACE INTO students
                (event_id, uid, name, branch, year, status, timestamp, source, device_id, device_timestamp)
            VALUES
                (?, ?, ?, ?, ?, 'Present', ?, 'Manual', '', '')
            """,
            (event_id, uid, name, branch, year, now),
        )

        conn.execute(
            """
            INSERT OR REPLACE INTO session_attendance
                (session_id, event_id, uid, timestamp, source, device_id, device_timestamp)
            VALUES
                (?, ?, ?, ?, 'Manual', '', '')
            """,
            (session_id, event_id, uid, now),
        )
        conn.commit()

        updated = conn.execute(
            "SELECT * FROM students WHERE event_id = ? AND uid = ? LIMIT 1", (event_id, uid)
        ).fetchone()
        student = dict(updated) if updated else {"event_id": event_id, "uid": uid, "name": name}

    return jsonify({"success": True, "timestamp": now, "student": student})


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

    with _db() as conn:
        total = conn.execute("SELECT COUNT(*) AS n FROM students").fetchone()[0]
        present = conn.execute("SELECT COUNT(*) AS n FROM students WHERE status = 'Present'").fetchone()[0]
    total = int(total)
    present = int(present)
    return jsonify({"total": total, "present": present, "remaining": max(total - present, 0)})


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
    with _db() as conn:
        events = [dict(r) for r in conn.execute("SELECT * FROM events ORDER BY event_id DESC").fetchall()]

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

    with _db() as conn:
        present_by_device = {
            r["device_id"]: int(r["present_count"])
            for r in conn.execute(
                """
                SELECT device_id, COUNT(*) AS present_count
                  FROM session_attendance
                 WHERE event_id = ? AND session_id = ? AND device_id != ''
                 GROUP BY device_id
                """,
                (event_id, session_id),
            ).fetchall()
        }

        device_info = {
            r["device_id"]: dict(r)
            for r in conn.execute(
                """
                SELECT device_id, last_seen, last_ip
                  FROM devices
                 WHERE last_event_id = ?
                """,
                (event_id,),
            ).fetchall()
        }

        # Live attendance for the selected session.
        attendance_rows = conn.execute(
            """
            SELECT s.uid, s.name, s.branch, s.year,
                   'Present' AS status,
                   sa.timestamp AS timestamp,
                   sa.source AS source,
                   sa.device_id AS device_id
              FROM session_attendance sa
              JOIN students s
                ON s.event_id = sa.event_id AND s.uid = sa.uid
             WHERE sa.event_id = ? AND sa.session_id = ?
             ORDER BY sa.timestamp DESC, s.name, s.uid
            """,
            (event_id, session_id),
        ).fetchall()

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

    return jsonify(
        {
            "server_time": _now_str(),
            "summary": summary,
            "device_stats": device_stats,
            "session_id": session_id,
            "attendance": [dict(r) for r in attendance_rows],
        }
    )


@app.post("/admin/events/create")
@admin_required
def admin_create_event():
    name = (request.form.get("event_name") or "").strip()
    start_time = (request.form.get("start_time") or "").strip()
    end_time = (request.form.get("end_time") or "").strip()
    is_active = 1 if (request.form.get("is_active") == "1") else 0
    if not name:
        return redirect(url_for("admin_dashboard"))

    with _db() as conn:
        conn.execute(
            "INSERT INTO events (event_name, start_time, end_time, is_active, created_at) VALUES (?, ?, ?, ?, ?)",
            (name, start_time, end_time, is_active, _now_str()),
        )
        event_id = int(conn.execute("SELECT last_insert_rowid() AS id").fetchone()[0])

        # Create a default session for the event.
        conn.execute(
            "INSERT INTO sessions (event_id, session_name, is_active, created_at) VALUES (?, ?, ?, ?)",
            (event_id, "Session 1", 1 if is_active else 0, _now_str()),
        )
        conn.commit()

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
    if not _get_event(event_id):
        return redirect(url_for("admin_dashboard"))

    name = (request.form.get("session_name") or "").strip() or f"Session {_now_str()}"

    # Creating a new session implies we want to take attendance again.
    _reset_event_roster_for_new_session(event_id)

    with _db() as conn:
        conn.execute("UPDATE sessions SET is_active = 0 WHERE event_id = ?", (event_id,))
        conn.execute(
            "INSERT INTO sessions (event_id, session_name, is_active, created_at) VALUES (?, ?, 1, ?)",
            (event_id, name, _now_str()),
        )
        conn.commit()

    return redirect(url_for("admin_dashboard", event_id=event_id))


@app.post("/admin/sessions/<int:session_id>/open")
@admin_required
def admin_open_session(session_id: int):
    with _db() as conn:
        row = conn.execute("SELECT * FROM sessions WHERE session_id = ?", (session_id,)).fetchone()
        if not row:
            return ("", 404)
        session_row = dict(row)
        event_id = int(session_row["event_id"])

        newest = conn.execute(
            "SELECT session_id FROM sessions WHERE event_id = ? ORDER BY session_id DESC LIMIT 1",
            (event_id,),
        ).fetchone()
        newest_id = int(newest[0]) if newest else 0
        if newest_id and int(session_id) != newest_id:
            return (jsonify({"error": "Older sessions are view-only. Create a new session to take attendance again."}), 409)

        conn.execute("UPDATE sessions SET is_active = 0 WHERE event_id = ?", (event_id,))
        conn.execute("UPDATE sessions SET is_active = 1 WHERE session_id = ?", (session_id,))
        conn.commit()

    return ("", 204)


@app.post("/admin/sessions/<int:session_id>/close")
@admin_required
def admin_close_session(session_id: int):
    with _db() as conn:
        conn.execute("UPDATE sessions SET is_active = 0 WHERE session_id = ?", (session_id,))
        conn.commit()
    return ("", 204)


@app.post("/admin/events/<int:event_id>/close")
@admin_required
def admin_close_event(event_id: int):
    with _db() as conn:
        conn.execute("UPDATE events SET is_active = 0 WHERE event_id = ?", (event_id,))
        conn.commit()
    return ("", 204)


@app.post("/admin/events/<int:event_id>/open")
@admin_required
def admin_open_event(event_id: int):
    with _db() as conn:
        conn.execute("UPDATE events SET is_active = 1 WHERE event_id = ?", (event_id,))
        conn.commit()
    _ensure_default_session(event_id)
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
    with _db() as conn:
        recent = conn.execute(
            """
            SELECT s.uid, s.name, s.branch, s.year,
                   'Present' AS status,
                   sa.timestamp AS timestamp,
                   sa.source AS source,
                   sa.device_id AS device_id
              FROM session_attendance sa
              JOIN students s
                ON s.event_id = sa.event_id AND s.uid = sa.uid
             WHERE sa.event_id = ? AND sa.session_id = ?
             ORDER BY sa.timestamp DESC
             LIMIT 50
            """,
            (event_id, session_id),
        ).fetchall()

    return jsonify(
        {
            "server_time": _now_str(),
            "event": event,
            "session_id": session_id,
            "attendance": [dict(r) for r in recent],
            **summary,
        }
    )


@app.get("/api/event/<int:event_id>/session")
def api_event_session(event_id: int):
    event = _get_event(event_id)
    if not event:
        return jsonify({"error": "Not found"}), 404

    active = _get_active_session(event_id)
    if not active:
        sid = _ensure_default_session(event_id)
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


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
