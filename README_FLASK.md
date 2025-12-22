# Flask Attendance Server (Excel-Only, Offline-First)

## Features
- Import students from Excel (.xlsx) only (no Google Sheets)
- Export attendance to Excel (.xlsx)
- Mark attendance by UID (offline, atomic)
- Search students by name or UID
- Add students manually (flagged as 'Manual')
- Live stats: total, present, remaining
- SQLite is the only DB (source of truth)
- All file I/O is safe and atomic
- No Google Sheets logic anywhere
- Optional: Post-event sync endpoint (see TODO in code)

## Usage

### 1. Install requirements
```bash
pip install flask pandas openpyxl
```

### 2. Run the server
```bash
python flask_server.py
```

Note: `flask_server.py` is a thin wrapper around `app.py`.

### Admin Dashboard

- Set environment variables: `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `FLASK_SECRET_KEY`.
- Visit: `/admin/login` then `/admin`.
- Live updates are implemented via polling `/admin/api/dashboard` every 5 seconds.

### Audience View (No Login)

- Projector-friendly page: `/event/<event_id>/live`
- Polling JSON API (summary only): `/api/event/<event_id>/live`

### 3. API Endpoints
- `POST /import` (Excel file, .xlsx)
- `GET /events` (list events; `?active=1` supported)
- `POST /events` (create event; admin-only)
- `GET /export?event_id=1&present_only=1` (Excel download)
- `POST /mark` (JSON: `{uid, event_id, device_id, device_timestamp}`)
- `GET /search?q=...&event_id=1`
- `POST /add` (JSON: `{uid, name, branch, year, event_id}`)
- `GET /stats?event_id=1`

### 4. Security & Safety
- All endpoints validate input and handle errors
- No blocking or unsafe file I/O
- No network dependency for event use

---

**For post-event sync, see the TODO in TODO.md.**
