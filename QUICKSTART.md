# Quick Start Guide

## 🚀 Setup in 5 Minutes

### 1️⃣ Google Sheets (2 min)
```
1. Create sheet named: CU_Attendance
2. Add headers: UID | Name | Status | Time
3. Add student data starting Row 2
4. Get credentials.json from Google Cloud
5. Share sheet with service account email
```

### 2️⃣ Flask Backend (1 min)
```powershell
cd c:\attendance_project
pip install Flask flask-cors gspread google-auth
python app.py
```

Check: http://127.0.0.1:5000/test_connection

### 3️⃣ Android App (2 min)
```
1. Open Android Studio
2. Import: android_app folder
3. Update IP in ApiClient.java:
   - Emulator: http://10.0.2.2:5000/
   - Device: http://YOUR_PC_IP:5000/
4. Run app
```

### 4️⃣ Test
```
1. Click "Scan ID Card"
2. Point at barcode
3. Check response
4. Verify in Google Sheet
```

## ✅ Done!

---

## 🔧 Common Commands

### Start Flask Server
```powershell
cd c:\attendance_project
python app.py
```

### Get PC IP Address (for real device)
```powershell
ipconfig
# Look for IPv4 Address under WiFi adapter
```

### Check Python Packages
```powershell
pip list
```

### Android Studio: View Logs
```
View > Tool Windows > Logcat
Filter: "Barcode"
```

---

## 📋 File Structure

```
attendance_project/
├── app.py                      # Flask backend
├── credentials.json            # Google Sheets credentials
├── index.html                  # Web interface (optional)
├── README.md                   # Full documentation
├── SECURITY.md                 # Security guide
└── android_app/
    ├── build.gradle
    └── app/
        ├── build.gradle
        ├── src/main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/cu/attendance/
        │   │   ├── MainActivity.java
        │   │   ├── BarcodeScannerActivity.java
        │   │   ├── ApiClient.java
        │   │   ├── AttendanceApiService.java
        │   │   ├── AttendanceRequest.java
        │   │   └── AttendanceResponse.java
        │   └── res/
        │       ├── layout/
        │       │   ├── activity_main.xml
        │       │   └── activity_barcode_scanner.xml
        │       └── values/
        │           └── strings.xml
        └── ...
```

---

## 🎯 Key Files to Modify

### 1. Update Server URL
**File:** `android_app/app/src/main/java/com/cu/attendance/ApiClient.java`
```java
private static final String BASE_URL = "http://YOUR_IP:5000/";
```

### 2. Update Sheet Name
**File:** `app.py`
```python
sheet = client.open("CU_Attendance").sheet1
```

---

## 🐛 Quick Fixes

### Problem: Network Error
```
Solution: Update BASE_URL in ApiClient.java
```

### Problem: Sheet Not Found
```
Solution: Check sheet name is exactly "CU_Attendance"
```

### Problem: Camera Not Working
```
Solution: Grant camera permission in Settings > Apps > CU Attendance
```

---

## 📞 Need Help?

1. Check [README.md](README.md) for detailed guide
2. Check [SECURITY.md](SECURITY.md) for security features
3. Check Flask console logs
4. Check Android Logcat

---

**Ready to build something awesome! 🎉**
