package com.cu.attendance

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface ScannedCallback {
    fun onResult(status: String, student: StudentEntity?)
}

object ScannerHelper {

    @JvmStatic
    fun handleScannedUid(context: Context, eventId: Long, uid: String, callback: ScannedCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val normalizedUid = uid.trim()
            // Always mark locally first (temporary cache).
            val localResult = processOffline(db, eventId, normalizedUid)
            val localTimestamp = localResult.second?.timestamp?.ifBlank { getCurrentTimestamp() } ?: getCurrentTimestamp()
            val deviceId = DeviceIdProvider.getOrCreate(context)

            // Then try syncing to Flask (final authority).
            when (val result = ApiService.markAttendance(eventId, normalizedUid, deviceId, localTimestamp)) {
                is MarkResult.Success -> {
                    val local = upsertStudent(db, eventId, result.student.copy(uid = normalizedUid))
                    withContext(Dispatchers.Main) { callback.onResult("SUCCESS", local) }
                }

                is MarkResult.AlreadyMarked -> {
                    val local = upsertStudent(db, eventId, result.student.copy(uid = normalizedUid))
                    withContext(Dispatchers.Main) { callback.onResult("ALREADY", local) }
                }

                MarkResult.Invalid -> {
                    // If local had a match, keep local; else invalid.
                    if (localResult.first != "INVALID") {
                        withContext(Dispatchers.Main) { callback.onResult(localResult.first, localResult.second) }
                    } else {
                        withContext(Dispatchers.Main) { callback.onResult("INVALID", null) }
                    }
                }

                is MarkResult.Error -> {
                    val msg = result.message
                    val isClosed = msg.contains("closed", ignoreCase = true)
                    if (isClosed && localResult.first == "SUCCESS") {
                        // Server says the event is closed; undo optimistic local mark.
                        db.studentDao().updateStatus(eventId, normalizedUid, "Absent", "")
                        val reverted = db.studentDao().getStudentByUid(eventId, normalizedUid)
                        withContext(Dispatchers.Main) { callback.onResult("INVALID", reverted) }
                    } else {

                        // Network or other server error: if we optimistically marked Present locally,
                        // queue a sync retry and report QUEUED to the UI.
                        if (localResult.first == "SUCCESS" && localResult.second != null) {
                            val exists = db.offlineQueueDao().findExistingId(
                                action = "MARK_PRESENT",
                                eventId = eventId,
                                uid = normalizedUid
                            )
                            if (exists == null) {
                                val payload = JSONObject()
                                    .put("device_timestamp", localTimestamp)
                                    .toString()
                                db.offlineQueueDao().enqueue(
                                    OfflineQueueEntity(
                                        action = "MARK_PRESENT",
                                        uid = normalizedUid,
                                        eventId = eventId,
                                        payload = payload
                                    )
                                )
                            }

                            // IMPORTANT: keep local state honest. If it's not yet confirmed by server,
                            // mark it as QUEUED (not Present) so counts/exports don't imply sync.
                            db.studentDao().updateStatus(eventId, normalizedUid, "Queued", localTimestamp)
                            val queuedStudent = db.studentDao().getStudentByUid(eventId, normalizedUid)
                            SyncWork.enqueueOneTime(context)
                            withContext(Dispatchers.Main) { callback.onResult("QUEUED", queuedStudent ?: localResult.second) }
                        } else {
                            // Otherwise fall back to local result.
                            withContext(Dispatchers.Main) { callback.onResult(localResult.first, localResult.second) }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun handleScannedUidOffline(context: Context, eventId: Long, uid: String, callback: ScannedCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val result = processOffline(db, eventId, uid)
            withContext(Dispatchers.Main) { callback.onResult(result.first, result.second) }
        }
    }

    suspend fun checkServer(context: Context, eventId: Long? = null): Boolean {
        val deviceId = DeviceIdProvider.getOrCreate(context)
        return ApiService.ping(eventId = eventId, deviceId = deviceId)
    }

    // Backwards compatible (no device tracking).
    suspend fun checkServer(): Boolean = ApiService.ping()

    private suspend fun processOffline(db: AppDatabase, eventId: Long, uid: String): Pair<String, StudentEntity?> {
        return try {
            val student = db.studentDao().getStudentByUid(eventId, uid)
            when {
                student == null -> "INVALID" to null
                student.status == "Present" -> "ALREADY" to student
                else -> {
                    val timestamp = getCurrentTimestamp()
                    db.studentDao().updateStatus(eventId, uid, "Present", timestamp)
                    val updated = student.copy(status = "Present", timestamp = timestamp)
                    "SUCCESS" to updated
                }
            }
        } catch (_: Exception) {
            "ERROR" to null
        }
    }

    private suspend fun upsertStudent(db: AppDatabase, eventId: Long, remote: StudentEntity): StudentEntity {
        val existing = db.studentDao().getStudentByUid(eventId, remote.uid)
        val merged = remote.copy(
            eventId = eventId,
            name = remote.name.ifBlank { existing?.name ?: remote.uid },
            branch = remote.branch.ifBlank { existing?.branch ?: "" },
            year = remote.year.ifBlank { existing?.year ?: "" },
            status = "Present",
            timestamp = remote.timestamp.ifEmpty { existing?.timestamp ?: getCurrentTimestamp() }
        )
        db.studentDao().insert(merged)
        return merged
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
