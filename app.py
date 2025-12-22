import io
import os
import sqlite3
from datetime import datetime
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
        # Ensure at least one event exists (helps older Android local migrations that map to eventId=1).
        c.execute("SELECT COUNT(*) AS n FROM events")
        if int(c.fetchone()[0]) == 0:
            c.execute(
                "INSERT INTO events (event_name, start_time, end_time, is_active, created_at) VALUES (?, ?, ?, ?, ?)",
                ("Default Event", "", "", 1, _now_str()),
            )
        conn.commit()


init_db()


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


def _roster_counts(event_id: int) -> dict:
    with _db() as conn:
        total = conn.execute("SELECT COUNT(*) AS n FROM students WHERE event_id = ?", (event_id,)).fetchone()[0]
        present = conn.execute(
            "SELECT COUNT(*) AS n FROM students WHERE event_id = ? AND status = 'Present'", (event_id,)
        ).fetchone()[0]
    total = int(total)
    present = int(present)
    return {
        "total": total,
        "present": present,
        "remaining": max(total - present, 0),
        "total_scanned": present,
    }


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
def import_excel():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    file = request.files.get("file")
    if not file:
        return jsonify({"error": "file is required"}), 400
    if not (file.filename or "").lower().endswith(".xlsx"):
        return jsonify({"error": "Excel (.xlsx) file required"}), 400

    try:
        df = pd.read_excel(file)
    except Exception as e:
        return jsonify({"error": f"Unable to read Excel: {e}"}), 400

    # Normalize column names.
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

    return jsonify({"success": True, "event_id": event_id, "imported": inserted})


@app.get("/export")
def export_excel():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    present_only = request.args.get("present_only") == "1"

    with _db() as conn:
        if present_only:
            rows = conn.execute(
                "SELECT * FROM students WHERE event_id = ? AND status = 'Present' ORDER BY name, uid", (event_id,)
            ).fetchall()
        else:
            rows = conn.execute("SELECT * FROM students WHERE event_id = ? ORDER BY name, uid", (event_id,)).fetchall()

    df = pd.DataFrame([dict(r) for r in rows])
    bio = io.BytesIO()
    with pd.ExcelWriter(bio, engine="openpyxl") as writer:
        df.to_excel(writer, index=False, sheet_name="Attendance")
    bio.seek(0)

    suffix = "present" if present_only else "full"
    filename = f"attendance_event_{event_id}_{suffix}.xlsx"
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
        conn.commit()

        updated = conn.execute(
            "SELECT * FROM students WHERE event_id = ? AND uid = ? LIMIT 1", (event_id, uid)
        ).fetchone()
        student = dict(updated) if updated else {"event_id": event_id, "uid": uid, "name": name}

    return jsonify({"success": True, "timestamp": now, "student": student})


@app.get("/stats")
def stats():
    event_id = _require_event_id()
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
        selected_event_id = int(events[0]["event_id"])

    return render_template(
        "admin_dashboard.html",
        events=events,
        selected_event_id=selected_event_id,
    )


@app.get("/admin/api/dashboard")
@admin_required
def admin_api_dashboard():
    event_id = _require_event_id()
    if not event_id:
        return jsonify({"error": "event_id is required"}), 400

    summary = _roster_counts(event_id)

    with _db() as conn:
        device_rows = conn.execute(
            """
            SELECT device_id, COUNT(*) AS present_count
              FROM students
             WHERE event_id = ? AND status = 'Present' AND device_id != ''
             GROUP BY device_id
             ORDER BY present_count DESC
            """,
            (event_id,),
        ).fetchall()

        attendance_rows = conn.execute(
            "SELECT * FROM students WHERE event_id = ? ORDER BY name, uid",
            (event_id,),
        ).fetchall()

    return jsonify(
        {
            "server_time": _now_str(),
            "summary": summary,
            "device_stats": [dict(r) for r in device_rows],
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
        conn.commit()

    return redirect(url_for("admin_dashboard"))


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
    summary = _roster_counts(event_id)
    return jsonify({"server_time": _now_str(), "event": event, **summary})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
