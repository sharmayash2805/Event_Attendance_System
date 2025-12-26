package com.cu.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class AttendanceStats(
    val total: Int = 0,
    val present: Int = 0,
    val remaining: Int = 0
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    private val _events = MutableStateFlow<List<EventDto>>(emptyList())
    val events: StateFlow<List<EventDto>> = _events.asStateFlow()

    private val _selectedEvent = MutableStateFlow<SelectedEvent?>(EventPrefs.loadSelectedEvent(application))
    val selectedEvent: StateFlow<SelectedEvent?> = _selectedEvent.asStateFlow()

    private val _stats = MutableStateFlow(AttendanceStats())
    val stats: StateFlow<AttendanceStats> = _stats.asStateFlow()

	private val _openSession = MutableStateFlow<SessionDto?>(null)
	val openSession: StateFlow<SessionDto?> = _openSession.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StudentEntity>>(emptyList())
    val searchResults: StateFlow<List<StudentEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isMarking = MutableStateFlow(false)
    val isMarking: StateFlow<Boolean> = _isMarking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
		ServerConfig.load(getApplication())
        refreshEvents()
        refreshStats()
    }

    fun setSelectedEvent(eventId: Long, eventName: String) {
        val next = SelectedEvent(eventId = eventId, eventName = eventName)
        if (_selectedEvent.value == next) return
        _selectedEvent.value = next
        EventPrefs.saveSelectedEvent(getApplication(), next)
        refreshStats()
        refreshRosterFromServer()
        refreshOpenSession()
        clearSearchResults()
        clearError()
    }

    fun refreshRosterFromServer() {
        viewModelScope.launch {
            val eventId = _selectedEvent.value?.eventId
            if (eventId == null || eventId <= 0L) return@launch

            try {
                val roster = ApiService.fetchRoster(eventId)
                if (roster.isEmpty()) return@launch

                withContext(Dispatchers.IO) {
                    val existing = database.studentDao().getAllStudents(eventId)
                    val existingByUid = existing.associateBy { it.uid }

                    val merged = roster.mapNotNull { remote ->
                        val uid = remote.uid.trim()
                        if (uid.isBlank()) return@mapNotNull null

                        val local = existingByUid[uid]
                        val keepStatus = local?.status == "Present" || local?.status == "Queued"

                        remote.copy(
                            status = if (keepStatus) local!!.status else "Absent",
                            timestamp = if (keepStatus) local!!.timestamp else ""
                        )
                    }

                    if (merged.isNotEmpty()) {
                        database.studentDao().insertAll(merged)
                    }
                }

                // Refresh local-based UI (search/stats) after syncing roster.
                refreshStats()
            } catch (e: Exception) {
                // Keep it silent; roster sync is best-effort.
                _error.value = "Failed to sync roster: ${e.message}"
            }
        }
    }

    private fun refreshOpenSession() {
        viewModelScope.launch {
            val eventId = _selectedEvent.value?.eventId
            if (eventId == null || eventId <= 0L) {
                _openSession.value = null
                return@launch
            }
            val session = ApiService.fetchOpenSession(eventId)
            _openSession.value = session
        }
    }

    fun refreshEvents() {
        viewModelScope.launch {
            val active = ApiService.fetchEvents(activeOnly = true)
            if (active.isNotEmpty()) {
                _events.value = active
				_error.value = null
				// Auto-select the first active event (no dropdown on Home screen).
				val first = active.firstOrNull()
				if (first != null) {
					setSelectedEvent(first.eventId, first.eventName)
				}
                return@launch
            }

            // Fallback: if no active events exist (or they were all closed), still show events
            // so the operator can pick the correct one (scanner will still be blocked server-side
            // if the event is closed).
            val all = ApiService.fetchEvents(activeOnly = false)
            if (all.isNotEmpty()) {
                _events.value = all
				_error.value = null
				// No active event; keep whatever was last selected (scanner may be blocked).
				return@launch
            }

			// Still empty: likely not connected / wrong URL / server sleeping.
			_error.value = "Can't load events. Check Server URL and internet, then tap Server → Check."
        }
    }

    private fun requireEventIdOrError(): Long? {
        val id = _selectedEvent.value?.eventId
        if (id == null || id <= 0L) {
            _error.value = "Select an event first"
            return null
        }
        return id
    }
    fun refreshStats() {
        viewModelScope.launch {
            try {
                val eventId = _selectedEvent.value?.eventId
                if (eventId == null || eventId <= 0L) {
                    _stats.value = AttendanceStats(total = 0, present = 0, remaining = 0)
                    return@launch
                }

                val deviceId = DeviceIdProvider.getOrCreate(getApplication())
                val server = ApiService.fetchStats(eventId = eventId, deviceId = deviceId)
                if (server != null) {
                    _stats.value = AttendanceStats(
                        total = server.total,
                        present = server.present,
                        remaining = server.remaining
                    )
                    return@launch
                }

                // Offline fallback: use local Room DB.
                val local = withContext(Dispatchers.IO) {
                    val total = database.studentDao().getCount(eventId)
                    val present = database.studentDao().getPresentCount(eventId)
                    AttendanceStats(total = total, present = present, remaining = (total - present).coerceAtLeast(0))
                }
                _stats.value = local
            } catch (e: Exception) {
                _error.value = "Failed to load stats: ${e.message}"
            }
        }
    }

    fun searchStudents(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
		val eventId = requireEventIdOrError() ?: return
        _isSearching.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
					database.studentDao().searchStudents(eventId, query)
                }
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun resetAllData(
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
		val eventId = requireEventIdOrError() ?: run {
			onError("Select an event first")
			return
		}
        _isMarking.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
					database.studentDao().deleteAll(eventId)
					database.offlineQueueDao().clear(eventId)
                }
                _searchResults.value = emptyList()
                _stats.value = AttendanceStats()
                _error.value = null
                onComplete()
            } catch (e: Exception) {
                val msg = "Reset failed: ${e.message}"
                _error.value = msg
                onError(msg)
            } finally {
                _isMarking.value = false
            }
        }
    }

    fun markStudentPresent(
        uid: String,
        onSuccess: (StudentEntity) -> Unit,
        onAlreadyMarked: (StudentEntity) -> Unit,
        onInvalid: () -> Unit
    ) {
		val eventId = requireEventIdOrError() ?: return
        _isMarking.value = true
        _error.value = null
        val normalizedUid = uid.trim()

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
					val student = database.studentDao().getStudentByUid(eventId, normalizedUid)

                    if (student == null) {
                        return@withContext "INVALID" to null
                    } else if (student.status == "Present") {
                        return@withContext "ALREADY" to student
                    } else {
                        val timestamp = getCurrentTimestamp()
						database.studentDao().updateStatus(eventId, normalizedUid, "Present", timestamp)
                        val updatedStudent = student.copy(status = "Present", timestamp = timestamp)
                        return@withContext "SUCCESS" to updatedStudent
                    }
                }

                when (result.first) {
                    "INVALID" -> onInvalid()
                    "ALREADY" -> onAlreadyMarked(result.second!!)
                    "SUCCESS" -> {
                        onSuccess(result.second!!)
                        refreshStats()
                    }
                }

            } catch (e: Exception) {
                _error.value = "Failed to mark attendance: ${e.message}"
            } finally {
                _isMarking.value = false
            }
        }
    }

    fun addStudentAndMarkPresent(
        uid: String,
        name: String,
        branch: String,
        year: String,
        onSuccess: (StudentEntity) -> Unit,
        onError: (String) -> Unit = {}
    ) {
		val eventId = requireEventIdOrError() ?: run {
			onError("Select an event first")
			return
		}
        _isMarking.value = true
        _error.value = null
        val normalizedUid = uid.trim()

        viewModelScope.launch {
            try {
                val newStudent = withContext(Dispatchers.IO) {
                    val timestamp = getCurrentTimestamp()
                    val student = StudentEntity(
						eventId = eventId,
						uid = normalizedUid,
                        name = name,
                        branch = branch,
                        year = year,
                        status = "Present",
						timestamp = timestamp
                    )
                    database.studentDao().insert(student)
                    student
                }

                onSuccess(newStudent)
                refreshStats()

                // Best-effort server sync; queue for background retry on failure.
                val result = ApiService.addStudent(eventId, normalizedUid, name, branch, year)
                when (result) {
                    is MarkResult.Success,
                    is MarkResult.AlreadyMarked -> {
                        // If server returns a timestamp, keep local in sync.
                        val remote = (result as? MarkResult.Success)?.student
                        if (remote != null && remote.timestamp.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                database.studentDao().insert(remote.copy(eventId = eventId, uid = normalizedUid))
                            }
                        }
                    }
                    MarkResult.Invalid -> {
                        // Don't queue invalid requests.
                    }
                    is MarkResult.Error -> {
                        withContext(Dispatchers.IO) {
                            val action = "ADD_STUDENT"
                            val exists = database.offlineQueueDao().findExistingId(action, eventId, normalizedUid)
                            if (exists == null) {
                                val payload = JSONObject()
                                    .put("name", name)
                                    .put("branch", branch)
                                    .put("year", year)
                                    .toString()
                                database.offlineQueueDao().enqueue(
                                    OfflineQueueEntity(
                                        action = action,
                                        uid = normalizedUid,
                                        eventId = eventId,
                                        payload = payload
                                    )
                                )
                            }
                        }
                        SyncWork.enqueueOneTime(getApplication())
                    }
                }
            } catch (e: Exception) {
                val msg = "Failed to add student: ${e.message}"
                _error.value = msg
                onError(msg)
            } finally {
                _isMarking.value = false
            }
        }
    }

    suspend fun getAllStudentsForExport(): List<StudentEntity> {
		val eventId = _selectedEvent.value?.eventId
		if (eventId == null || eventId <= 0L) return emptyList()
		return withContext(Dispatchers.IO) {
			try {
				database.studentDao().getAllStudents(eventId)
			} catch (e: Exception) {
				_error.value = "Export failed: ${e.message}"
				emptyList()
			}
		}
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date())
    }

    fun clearError() {
        _error.value = null
    }
}
