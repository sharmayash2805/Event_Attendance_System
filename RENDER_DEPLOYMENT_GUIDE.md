# Render Deployment Guide

This guide walks through deploying the Attendance System backend to Render.com.

## Prerequisites

- GitHub repository synced (✅ already done)
- Render account (create at https://render.com)
- Postgres database URL (optional, for production scale)
- Admin credentials for backend

---

## Option A: Quick Deploy (SQLite on Persistent Disk)

### 1. Create Web Service on Render

1. Login to [Render Dashboard](https://dashboard.render.com)
2. Click **New +** → **Web Service**
3. Connect to your GitHub repo: `https://github.com/sharmayash2805/Event_Attendance_System.git`
4. Configure:
   - **Name:** `attendance-flask` (or your choice)
   - **Environment:** `Python 3`
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `gunicorn wsgi:app --bind 0.0.0.0:$PORT --workers 2 --threads 4 --timeout 120`
   - **Plan:** Free (or Starter)
   - **Region:** Choose closest to your users

### 2. Add Persistent Disk (for SQLite database)

1. In Service settings, scroll to **Disks**
2. Click **Add Disk**
   - **Mount Path:** `/data`
   - **Size:** 1 GB (adjust as needed)
3. Save

### 3. Set Environment Variables

In **Environment** tab, add:

```
FLASK_SECRET_KEY=<generate-random-string>
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<your-secure-password>
DB_PATH=/data/attendance.db
```

To generate `FLASK_SECRET_KEY`:
```bash
python -c "import secrets; print(secrets.token_hex(24))"
```

### 4. Deploy

Click **Deploy** and wait ~2-3 minutes for build and startup.

---

## Option B: Production Deploy (Postgres)

### 1. Provision Postgres Database

#### On Render:
1. Click **New +** → **PostgreSQL**
2. Configure:
   - **Name:** `attendance-db`
   - **Database:** `attendance_db`
   - **User:** `attendance_user`
   - **Region:** Same as web service
3. Copy the **Internal Database URL** (e.g., `postgresql://user:pass@localhost:5432/db`)

#### Or use external provider (AWS RDS, Heroku Postgres, etc.)

### 2. Create Postgres Schema

Run the migration script locally or on Render:

```bash
# Locally (if you have Postgres tools):
export DATABASE_URL="postgresql://user:pass@host:5432/attendance_db"
python migrate_to_postgres.py

# Or on Render:
# SSH into the service and run the command
```

### 3. Configure Web Service

Same as Option A, but **add Database URL**:

```
DATABASE_URL=postgresql://user:pass@render.internal:5432/attendance_db
FLASK_SECRET_KEY=<random-string>
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<secure-password>
```

**No need for DB_PATH or persistent disk** — Postgres handles all data.

### 4. Deploy

---

## After Deployment

### Verify Service is Running

```bash
curl https://your-render-app.onrender.com/
# Should return: {"ok": true, "service": "attendance"}
```

### Test Key Endpoints

**List Events:**
```bash
curl https://your-render-app.onrender.com/events
```

**Admin Dashboard:**
```
https://your-render-app.onrender.com/admin
```
Login with credentials you set in `ADMIN_PASSWORD`.

---

## Configure Android App

Update `ServerUrlDialog.kt` to use your Render URL:

```kotlin
const val DEFAULT_SERVER_URL = "https://your-render-app.onrender.com"
```

Rebuild and deploy Android app with the new URL.

---

## Database Persistence & Backups

### Option A (SQLite):
- Data persists on the mounted disk across redeploys
- **Backup:** Use Render dashboard to download disk snapshots
- **Restore:** Render can restore from snapshots

### Option B (Postgres):
- Render automatically backs up Postgres daily
- **Restore:** Contact Render support or use the dashboard

---

## Monitoring & Logs

1. Go to **Logs** tab in your service
2. Check for errors during startup or requests
3. Common issues:
   - `ModuleNotFoundError`: Missing dependency → rerun build
   - `OperationalError`: Database not accessible → check `DATABASE_URL`
   - `Permission denied`: Disk mount issue → contact Render support

---

## Scaling & Performance Tips

1. **Increase workers:** In `render.yaml`, change `--workers 4` (default 2)
2. **Use Postgres** if > 1000 concurrent users
3. **Enable paid plan** for better performance guarantees
4. **Monitor device count:** Max ~500 concurrent devices per service

---

## Troubleshooting

### "No active session" error from Android
- Admin must open an event + session first
- Verify on admin dashboard: `/admin`

### High response times
- Check "Device Online Seconds" setting (default 120s)
- Increase worker threads: `--threads 8`

### Database connection refused
- Verify `DATABASE_URL` is correct
- Check Postgres is running (if self-hosted)
- Verify IP whitelist (for external Postgres)

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | (none) | Postgres connection string; if not set, uses SQLite |
| `DB_PATH` | `attendance.db` | SQLite file path (ignored if `DATABASE_URL` set) |
| `FLASK_SECRET_KEY` | `os.urandom(24)` | Session encryption key; must be stable in production |
| `ADMIN_USERNAME` | `admin` | Admin panel username |
| `ADMIN_PASSWORD` | `admin` | Admin panel password (⚠️ change this!) |
| `DEVICE_ONLINE_SECONDS` | `120` | Device online status timeout (seconds) |

---

## Next Steps

1. Deploy to Render using one of the options above
2. Test admin dashboard at `/admin`
3. Create an event and session
4. Import student roster via Excel
5. Update Android app with Render URL
6. Test scanning from Android

**Questions?** Check logs in Render dashboard or contact support.
