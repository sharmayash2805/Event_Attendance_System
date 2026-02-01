# Pre-Deployment Connection Checklist

Use this checklist to verify all connections are correct before pushing to Render.

## Backend Connections ✅

### Database
- [x] `db_new.py` auto-detects Postgres via `DATABASE_URL` env var
- [x] Falls back to SQLite (`DB_PATH`) if `DATABASE_URL` not set
- [x] ORM models in `models.py` work with both backends
- [x] Migration script: `migrate_to_postgres.py` (schema creation)

### Entry Point
- [x] `wsgi.py` imports Flask app from `app.py`
- [x] `render.yaml` start command uses `gunicorn wsgi:app`
- [x] Port binding: `0.0.0.0:$PORT` (Render injects PORT)

### Flask App (`app.py`)
- [x] Uses SQLAlchemy ORM for all DB operations
- [x] No hardcoded localhost/127.0.0.1
- [x] Supports dynamic host/port via environment
- [x] Admin authentication via `ADMIN_PASSWORD` env var

### Dependencies
- [x] `requirements.txt` includes:
  - Flask 3.0.3
  - gunicorn 22.0.0
  - SQLAlchemy 2.0+
  - psycopg-binary 3.1+ (for Postgres)
  - pandas, openpyxl (for Excel import)

---

## Android App Connections

### Server URL Configuration
- [ ] Update `ServerUrlDialog.kt` with Render URL
  - Current placeholder: `https://event-attendance-system-fslz.onrender.com`
  - **Change to:** Your actual Render service URL (e.g., `https://attendance-flask.onrender.com`)
- [ ] Rebuild Android app with new URL
- [ ] Test connection before production release

### API Endpoints (tested):
- [x] GET `/events` — List events
- [x] GET `/api/event/<id>/live` — Live attendance feed
- [x] GET `/api/event/<id>/session` — Active session info
- [x] GET `/api/event/<id>/roster` — Imported student list
- [x] POST `/mark` — Mark attendance
- [x] GET `/test_connection` — Health check (if implemented)

---

## Admin Dashboard

### Routes
- [x] GET `/admin/login` — Login page
- [x] GET `/admin` — Main dashboard
- [x] POST `/admin/api/dashboard` — API for live stats
- [x] POST `/admin/events/create` — Create event
- [x] POST `/admin/events/<id>/sessions/create` — Create session
- [x] POST `/admin/sessions/<id>/open` — Activate session
- [x] POST `/admin/api/import` — Bulk import students

### Authentication
- [x] Session-based auth via `ADMIN_PASSWORD` env var
- [x] No hardcoded credentials (dev defaults only locally)

---

## Environment Variables Checklist

Before deploying to Render, set these in the dashboard:

### Required
- [ ] `FLASK_SECRET_KEY=<random-hex-string>` (for session encryption)
- [ ] `ADMIN_PASSWORD=<strong-password>` (admin access)

### For Postgres (Option B only)
- [ ] `DATABASE_URL=postgresql://user:pass@host:5432/db`

### For SQLite (Option A only)
- [ ] `DB_PATH=/data/attendance.db` (if using persistent disk)

### Optional
- [ ] `ADMIN_USERNAME=admin` (default: admin)
- [ ] `DEVICE_ONLINE_SECONDS=120` (default: 120s)

---

## Render Configuration Checklist

### Web Service Settings
- [ ] **Build Command:** `pip install -r requirements.txt`
- [ ] **Start Command:** `gunicorn wsgi:app --bind 0.0.0.0:$PORT --workers 2 --threads 4 --timeout 120`
- [ ] **Environment:** Python 3
- [ ] **Region:** (choose closest to users)

### Disk/Database
- [ ] **Option A (SQLite):** Add persistent disk at `/data` (1 GB)
- [ ] **Option B (Postgres):** Link Postgres service or provide `DATABASE_URL`

### Health Check (Optional)
- [ ] **Health Check Path:** `/` (returns `{"ok": true, "service": "attendance"}`)
- [ ] **Expected Status:** 200

---

## Testing After Deploy

### Smoke Tests
```bash
# Health check
curl https://your-render-app.onrender.com/

# List events
curl https://your-render-app.onrender.com/events

# Admin login page
curl -L https://your-render-app.onrender.com/admin/login
```

### End-to-End (Manual)
1. Open admin dashboard at `https://your-render-app.onrender.com/admin`
2. Login with `ADMIN_PASSWORD`
3. Create an event
4. Create a session
5. Import sample roster (Excel)
6. Mark attendance manually or via Android app
7. Check live dashboard updates in real-time

---

## Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| `ModuleNotFoundError: sqlalchemy` | Missing dependency | Re-run build or add to `requirements.txt` |
| `OperationalError: no such table` | DB not initialized | Run `migrate_to_postgres.py` on Postgres or ensure SQLite path is writable |
| `CRITICAL DATABASE_URL not set` | ENV var missing | Set `FLASK_SECRET_KEY`, `ADMIN_PASSWORD` in Render dashboard |
| Android app can't connect | Wrong server URL | Update `ServerUrlDialog.kt` and rebuild |
| Slow performance | Too few workers | Increase `--workers` in start command |
| Disk space error | SQLite database too large | Upgrade persistent disk size or migrate to Postgres |

---

## Sign-Off Checklist

Before going live, confirm:

- [ ] Backend refactored to SQLAlchemy ORM ✅
- [ ] Migration scripts created (`migrate_to_postgres.py`) ✅
- [ ] Render config file (`render.yaml`) correct ✅
- [ ] Requirements updated with dependencies ✅
- [ ] Android app URL updated to Render service ✅
- [ ] Admin credentials set in environment variables ✅
- [ ] Database (SQLite disk or Postgres) configured ✅
- [ ] All endpoints tested and working ✅
- [ ] Logs monitored for errors on first deploy ✅

**Status:** Ready for Render deployment! 🚀
