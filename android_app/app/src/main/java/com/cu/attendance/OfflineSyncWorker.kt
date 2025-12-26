package com.cu.attendance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject

class OfflineSyncWorker(
	appContext: Context,
	params: WorkerParameters
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result {
		ServerConfig.load(applicationContext)

		val db = AppDatabase.getDatabase(applicationContext)
		val queue = db.offlineQueueDao().getAllPending()
		if (queue.isEmpty()) return Result.success()

		val deviceId = DeviceIdProvider.getOrCreate(applicationContext)
		var shouldRetry = false

		for (item in queue) {
			when (item.action) {
				"MARK_PRESENT" -> {
					val payload = item.payload.orEmpty()
					val deviceTimestamp = runCatching {
						if (payload.isBlank()) "" else JSONObject(payload).optString("device_timestamp", "")
					}.getOrDefault("")

					when (val result = ApiService.markAttendance(item.eventId, item.uid, deviceId, deviceTimestamp)) {
						is MarkResult.Success -> {
							// Server confirmed: ensure local row is Present (not Queued).
							db.studentDao().insert(result.student.copy(eventId = item.eventId, uid = item.uid, status = "Present"))
							db.offlineQueueDao().deleteById(item.id)
						}

						is MarkResult.AlreadyMarked -> {
							db.studentDao().insert(result.student.copy(eventId = item.eventId, uid = item.uid, status = "Present"))
							db.offlineQueueDao().deleteById(item.id)
						}

						MarkResult.Invalid -> {
							// Server rejected it: revert local optimistic mark.
							db.studentDao().updateStatus(item.eventId, item.uid, "Absent", "")
							db.offlineQueueDao().deleteById(item.id)
						}

						is MarkResult.Error -> {
							shouldRetry = true
						}
					}
				}

				"ADD_STUDENT", "INSERT_STUDENT" -> {
					val payload = item.payload.orEmpty()
					val json = runCatching { JSONObject(payload) }.getOrNull()
					val name = json?.optString("name", "").orEmpty()
					val branch = json?.optString("branch", "").orEmpty()
					val year = json?.optString("year", "").orEmpty()

					when (val result = ApiService.addStudent(item.eventId, item.uid, name, branch, year)) {
						is MarkResult.Success,
						is MarkResult.AlreadyMarked,
						MarkResult.Invalid -> {
							db.offlineQueueDao().deleteById(item.id)
						}

						is MarkResult.Error -> {
							shouldRetry = true
						}
					}
				}

				else -> {
					// Unknown action: drop it so it doesn't block the queue.
					db.offlineQueueDao().deleteById(item.id)
				}
			}
		}

		return if (shouldRetry) Result.retry() else Result.success()
	}
}
