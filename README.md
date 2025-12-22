# CU Attendance System

Native Android (Kotlin/Compose) app + Flask (Python) backend using SQLite and Excel imports. Optimized for scanning student IDs with a real Android device (camera, network, storage). No Google Sheets dependency.

## What You Need
- Android device with a working camera and network (recommended: physical device, not an emulator, for reliable scanning).
- PC with Python 3.9+ (Windows) to run the Flask backend.
- Android Studio (latest) with Android SDK and JDK 17+.
- Excel file in the required format (see "Excel Import Format").

## Project Layout
- android_app/  → Android client (Kotlin, Compose, Room, CameraX, ML Kit)
- app.py        → Flask backend entrypoint (SQLite + Excel import/export)
- flask_server.py / SEARCH_API_EXAMPLE.py → reference helpers
- EXCEL_IMPORT_FORMAT.md → required columns for imports
- uploads/      → exported files (can be cleaned if not needed)

## Quick Start (Backend)
```powershell
cd d:\attendance_project
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt  # or: pip install Flask pandas openpyxl
python app.py
# Server runs on http://0.0.0.0:5000 (allow through firewall)
```

## Quick Start (Android)
1) Open Android Studio → **File > Open** → select `android_app`.
2) Wait for Gradle sync.
3) Set the backend URL in the app (Server settings dialog in the app UI). Use:
   - Emulator: `http://10.0.2.2:5000`
   - Physical device on same Wi‑Fi: `http://<your_pc_ipv4>:5000` (e.g., `http://192.168.1.50:5000`)
   - You can edit/update this IP anytime after opening the app via the Server settings dialog.
   
4) Connect a physical Android device (preferred) with USB debugging enabled. The emulator works, but camera decoding is far less reliable.
5) Run the app from Android Studio.

## Using the App
- Select Event: Use the event dropdown on the home screen (active events are fetched from the server).
- Import: Tap "IMPORT LIST" and choose an Excel file (see format below). Data is stored locally (Room/SQLite) and scoped by the selected event.
- Scan: Tap "ENTER SCANNER" and scan student barcodes/QR codes. The app marks locally first, then syncs to the server (server is authoritative per (event_id, uid)).
- Search/Manual: Use "SEARCH DB" to lookup and mark present.
- Export: Tap "EXPORT DATA" to generate CSV locally for the selected event.
- Reset: "RESET DATA" clears only the currently selected event.

## Excel Import Format
See EXCEL_IMPORT_FORMAT.md for the exact columns. Typical headers:
```
UID | Name | Branch | Year
```
All rows after the header are imported as absent; scanning or marking sets them to present with a timestamp.

## Recommended Device Setup
- Use a physical Android device with a good camera for ML Kit scanning.
- Keep the device and the backend machine on the same network (or expose your backend securely over HTTPS if remote).
- Allow camera permission when prompted; without it scanning will fail.

## Troubleshooting
- Backend unreachable: confirm `python app.py` is running; check firewall on port 5000; use correct IP (not localhost for physical devices).
- Scanner slow or failing: use a physical device; ensure good lighting and focus; clean the camera lens.
- Import issues: ensure headers match the format; file must be `.xlsx`.
- Build issues: delete `android_app/.gradle`, `android_app/build`, and `android_app/app/build`, then resync.

## Housekeeping (safe to delete)
- Android build outputs: `android_app/.gradle/`, `android_app/build/`, `android_app/app/build/`.
- Python caches: any `__pycache__/` folders, `.pytest_cache/`, `.ipynb_checkpoints/`.
- `uploads/` if you do not need exported files.

---
Built for reliable, offline-friendly attendance with simple deployment and no external SaaS dependencies.
