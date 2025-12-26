package com.cu.attendance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class StatsResponse(
	val total: Int,
	val present: Int,
	val remaining: Int
)

sealed class MarkResult {
	data class Success(val student: StudentEntity) : MarkResult()
	data class AlreadyMarked(val student: StudentEntity) : MarkResult()
	object Invalid : MarkResult()
	data class Error(val message: String) : MarkResult()
}

object ApiService {
	private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

	private fun statsUrl(eventId: Long? = null, deviceId: String? = null): String {
		val params = mutableListOf<String>()
		if (eventId != null && eventId > 0L) params += "event_id=$eventId"
		val did = deviceId?.trim().orEmpty()
		if (did.isNotEmpty()) {
			val encoded = java.net.URLEncoder.encode(did, "UTF-8")
			params += "device_id=$encoded"
		}
		val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
		return ApiClient.url("/stats$query")
	}

	suspend fun ping(eventId: Long? = null, deviceId: String? = null): Boolean = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(statsUrl(eventId = eventId, deviceId = deviceId))
				.get()
				.build()
			ApiClient.client.newCall(request).execute().use { response ->
				response.isSuccessful
			}
		} catch (_: Exception) {
			false
		}
	}

	suspend fun fetchEvents(activeOnly: Boolean = true): List<EventDto> = withContext(Dispatchers.IO) {
		try {
			val query = if (activeOnly) "?active=1" else ""
			val request = Request.Builder()
				.url(ApiClient.url("/events$query"))
				.get()
				.build()
			ApiClient.client.newCall(request).execute().use { response ->
				val bodyText = response.body?.string().orEmpty()
				if (!response.isSuccessful) return@withContext emptyList<EventDto>()
				val array = JSONArray(bodyText)
				return@withContext buildList {
					for (i in 0 until array.length()) {
						val item = array.optJSONObject(i) ?: continue
						add(
							EventDto(
								eventId = item.optLong("event_id"),
								eventName = item.optString("event_name"),
								startTime = item.optString("start_time"),
								endTime = item.optString("end_time"),
								isActive = item.optBoolean("is_active", true)
							)
						)
					}
				}
			}
		} catch (_: Exception) {
			emptyList()
		}
	}

	suspend fun fetchOpenSession(eventId: Long): SessionDto? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(ApiClient.url("/api/event/$eventId/session"))
				.get()
				.build()
			ApiClient.client.newCall(request).execute().use { response ->
				val bodyText = response.body?.string().orEmpty()
				if (!response.isSuccessful) return@withContext null
				val json = JSONObject(bodyText)
				if (json.has("error")) return@withContext null
				return@withContext SessionDto(
					sessionId = json.optLong("session_id", 0L),
					sessionName = json.optString("session_name", ""),
					isOpen = json.optBoolean("is_open", true)
				)
			}
		} catch (_: Exception) {
			null
		}
	}

	suspend fun markAttendance(eventId: Long, uid: String, deviceId: String, deviceTimestamp: String): MarkResult =
		withContext(Dispatchers.IO) {
			try {
				val body = JSONObject()
					.put("uid", uid)
					.put("event_id", eventId)
					.put("device_id", deviceId)
					.put("device_timestamp", deviceTimestamp)
					.toString()
					.toRequestBody(jsonMediaType)

				val request = Request.Builder()
					.url(ApiClient.url("/mark"))
					.post(body)
					.build()

				ApiClient.client.newCall(request).execute().use { response ->
					val bodyText = response.body?.string().orEmpty()

					if (!response.isSuccessful) {
						if (response.code == 409) {
							val json = runCatching { JSONObject(bodyText) }.getOrNull()
							val student = json?.optJSONObject("student")
								.toStudentEntity(eventId, uid, defaultStatus = "Present")
							return@withContext MarkResult.AlreadyMarked(student)
						}

						val message = extractError(bodyText)
						return@withContext if (response.code == 404) {
							MarkResult.Invalid
						} else {
							MarkResult.Error(message)
						}
					}

					val json = JSONObject(bodyText)

					if (json.has("error")) {
						val message = json.optString("error")
						return@withContext if (message.contains("invalid", ignoreCase = true)) {
							MarkResult.Invalid
						} else {
							MarkResult.Error(message)
						}
					}

					if (json.optBoolean("success")) {
						val timestamp = json.optString("timestamp", "")
						val student = json.optJSONObject("student")
							.toStudentEntity(eventId, uid, defaultStatus = "Present")
						return@withContext MarkResult.Success(
							student.copy(
								status = "Present",
								timestamp = timestamp.ifEmpty { student.timestamp }
							)
						)
					}

					return@withContext MarkResult.Error("Unexpected server response")
				}
			} catch (e: Exception) {
				MarkResult.Error(e.message ?: "Network error")
			}
		}

	suspend fun addStudent(eventId: Long, uid: String, name: String, branch: String, year: String): MarkResult =
		withContext(Dispatchers.IO) {
			try {
				val payload = JSONObject()
					.put("event_id", eventId)
					.put("uid", uid)
					.put("name", name)
					.put("branch", branch)
					.put("year", year)
					.toString()
					.toRequestBody(jsonMediaType)

				val request = Request.Builder()
					.url(ApiClient.url("/add"))
					.post(payload)
					.build()

				ApiClient.client.newCall(request).execute().use { response ->
					val bodyText = response.body?.string().orEmpty()
					if (!response.isSuccessful) {
						return@withContext MarkResult.Error(extractError(bodyText))
					}

					val json = JSONObject(bodyText)
					if (json.optBoolean("success")) {
						val timestamp = json.optString("timestamp", "")
						val student = StudentEntity(
							eventId = eventId,
							uid = uid,
							name = name,
							branch = branch,
							year = year,
							status = "Present",
							timestamp = timestamp
						)
						return@withContext MarkResult.Success(student)
					}

					MarkResult.Error(json.optString("error", "Unable to add student"))
				}
			} catch (e: Exception) {
				MarkResult.Error(e.message ?: "Network error")
			}
		}

	suspend fun searchStudents(eventId: Long, query: String): List<StudentEntity> = withContext(Dispatchers.IO) {
		if (query.isBlank()) return@withContext emptyList()
		try {
			val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
			val request = Request.Builder()
				.url(ApiClient.url("/search?event_id=$eventId&q=$encodedQuery"))
				.get()
				.build()

			ApiClient.client.newCall(request).execute().use { response ->
				val bodyText = response.body?.string().orEmpty()
				if (!response.isSuccessful) return@withContext emptyList<StudentEntity>()
				val array = JSONArray(bodyText)
				return@withContext buildList {
					for (i in 0 until array.length()) {
						val item = array.optJSONObject(i)
						if (item != null) add(item.toStudentEntity(eventId, fallbackUid = item.optString("uid")))
					}
				}
			}
		} catch (_: Exception) {
			emptyList()
		}
	}

	suspend fun fetchStats(eventId: Long? = null, deviceId: String? = null): StatsResponse? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder()
				.url(statsUrl(eventId = eventId, deviceId = deviceId))
				.get()
				.build()

			ApiClient.client.newCall(request).execute().use { response ->
				val bodyText = response.body?.string().orEmpty()
				if (!response.isSuccessful) return@withContext null
				val json = JSONObject(bodyText)
				return@withContext StatsResponse(
					total = json.optInt("total", 0),
					present = json.optInt("present", 0),
					remaining = json.optInt("remaining", 0)
				)
			}
		} catch (_: Exception) {
			null
		}
	}

	private fun JSONObject?.toStudentEntity(eventId: Long, fallbackUid: String, defaultStatus: String = "Absent"): StudentEntity {
		if (this == null) {
			return StudentEntity(
				eventId = eventId,
				uid = fallbackUid,
				name = fallbackUid,
				branch = "",
				year = "",
				status = defaultStatus,
				timestamp = ""
			)
		}
		return StudentEntity(
			eventId = eventId,
			uid = optString("uid", fallbackUid),
			name = optString("name", fallbackUid),
			branch = optString("branch", ""),
			year = optString("year", ""),
			status = optString("status", defaultStatus),
			timestamp = optString("timestamp", "")
		)
	}

	private fun extractError(bodyText: String): String {
		return runCatching { JSONObject(bodyText).optString("error") }.getOrDefault("Request failed")
	}
}
