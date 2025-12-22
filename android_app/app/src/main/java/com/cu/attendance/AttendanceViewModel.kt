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

    private val _searchResults = MutableStateFlow<List<StudentEntity>>(emptyList())
    val searchResults: StateFlow<List<StudentEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isMarking = MutableStateFlow(false)
    val isMarking: StateFlow<Boolean> = _isMarking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshEvents()
        refreshStats()
    }

    fun setSelectedEvent(eventId: Long, eventName: String) {
        val next = SelectedEvent(eventId = eventId, eventName = eventName)
        if (_selectedEvent.value == next) return
        _selectedEvent.value = next
        EventPrefs.saveSelectedEvent(getApplication(), next)
        refreshStats()
        clearSearchResults()
        clearError()
    }

    fun refreshEvents() {
        viewModelScope.launch {
            val fetched = ApiService.fetchEvents(activeOnly = true)
            if (fetched.isNotEmpty()) {
                _events.value = fetched
            }
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
            withContext(Dispatchers.IO) {
                try {
                    val eventId = _selectedEvent.value?.eventId
                    if (eventId == null || eventId <= 0L) {
						_stats.value = AttendanceStats(total = 0, present = 0, remaining = 0)
						return@withContext
					}

                    val total = database.studentDao().getCount(eventId)
                    val present = database.studentDao().getPresentCount(eventId)
                    val remaining = total - present

                    _stats.value = AttendanceStats(
                        total = total,
                        present = present,
                        remaining = remaining
                    )
                } catch (e: Exception) {
                    _error.value = "Failed to load stats: ${e.message}"
                }
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
