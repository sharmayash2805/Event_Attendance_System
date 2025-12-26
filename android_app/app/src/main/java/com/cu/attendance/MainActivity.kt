package com.cu.attendance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.cu.attendance.ui.export.ExportFormat
import com.cu.attendance.ui.export.ExportScreen
import com.cu.attendance.ui.ServerUrlDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

	private var showSearchScreen by mutableStateOf(false)
	private var showExportScreen by mutableStateOf(false)
	private var showServerDialog by mutableStateOf(false)
	private var serverUrl by mutableStateOf("")

	private lateinit var database: AppDatabase
	private lateinit var attendanceViewModel: AttendanceViewModel

	private val scannerLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		val message = result.data?.getStringExtra("result_message").orEmpty()
		if (result.resultCode == RESULT_OK) {
			Toast.makeText(
				this,
				if (message.isEmpty()) "Attendance marked successfully!" else message,
				Toast.LENGTH_SHORT
			).show()
		} else {
			Toast.makeText(
				this,
				if (message.isEmpty()) "Scan failed or cancelled" else message,
				Toast.LENGTH_SHORT
			).show()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
			ServerConfig.load(this)
			serverUrl = ServerConfig.getBaseUrl()
		database = AppDatabase.getDatabase(this)
		attendanceViewModel = ViewModelProvider(this)[AttendanceViewModel::class.java]
		attendanceViewModel.refreshEvents()

		setContent {
			val stats by attendanceViewModel.stats.collectAsState()
			val events by attendanceViewModel.events.collectAsState()
			val selectedEvent by attendanceViewModel.selectedEvent.collectAsState()
			val openSession by attendanceViewModel.openSession.collectAsState()
			val vmError by attendanceViewModel.error.collectAsState()
			var serverStatusText by remember { mutableStateOf("Tap to check") }
			var checkingServer by remember { mutableStateOf(false) }

			LaunchedEffect(Unit) {
				// Initial best-effort check (doesn't block UI).
				checkingServer = true
				val ok = try {
					ScannerHelper.checkServer(this@MainActivity, selectedEvent?.eventId)
				} catch (_: Exception) {
					false
				}
				serverStatusText = if (ok) "Online" else "Offline"
				checkingServer = false
			}

			LaunchedEffect(selectedEvent?.eventId) {
				// Background touch to associate device with the selected event for admin device-wise stats.
				val eid = selectedEvent?.eventId
				if (eid != null && eid > 0L) {
					runCatching { ScannerHelper.checkServer(this@MainActivity, eid) }
				}
			}

			MaterialTheme {
				Surface(color = MaterialTheme.colorScheme.background) {
					when {
						showExportScreen -> {
							ExportScreen(
								onBack = { showExportScreen = false },
								onExport = { format, presentOnly ->
								exportAttendance(format, presentOnly)
							}
						)
					}
					showSearchScreen -> {
						SearchScreen(onBack = { showSearchScreen = false })
					}
					else -> {
						HomeScreen(
							stats = stats,
							selectedEvent = selectedEvent,
							openSession = openSession,
							onScanClick = {
								val current = attendanceViewModel.selectedEvent.value
								if (current == null) {
									Toast.makeText(this@MainActivity, "No active event", Toast.LENGTH_SHORT).show()
									return@HomeScreen
								}
								val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
								intent.putExtra(BarcodeScannerActivity.EXTRA_EVENT_ID, current.eventId)
								intent.putExtra(BarcodeScannerActivity.EXTRA_EVENT_NAME, current.eventName)
								scannerLauncher.launch(intent)
							},
							onSearchClick = { showSearchScreen = true },
							onExportClick = { showExportScreen = true },
							onOpenServerSettings = {
								serverUrl = ServerConfig.getBaseUrl()
								showServerDialog = true
							},
							connectionHint = if (events.isEmpty()) {
								vmError ?: "No events loaded. Long-press the footer to set the Server URL to Render."
							} else {
								null
							},
							serverStatusText = if (checkingServer) "Checking..." else serverStatusText,
							onCheckServer = {
								if (checkingServer) return@HomeScreen
								checkingServer = true
								lifecycleScope.launch(Dispatchers.IO) {
									val ok = ScannerHelper.checkServer(this@MainActivity, selectedEvent?.eventId)
									withContext(Dispatchers.Main) {
										serverStatusText = if (ok) "Online" else "Offline"
										checkingServer = false
										Toast.makeText(
											this@MainActivity,
											if (ok) "Connected to server" else "Not connected to server",
											Toast.LENGTH_SHORT
										).show()
										if (ok) {
											attendanceViewModel.refreshEvents()
											attendanceViewModel.refreshStats()
											attendanceViewModel.refreshRosterFromServer()
										}
									}
								}
							}
						)
					}
					}

					if (showServerDialog) {
						ServerUrlDialog(
							currentUrl = serverUrl,
							onDismiss = { showServerDialog = false },
							onSave = { entered ->
								val normalized = ServerConfig.update(this@MainActivity, entered)
								serverUrl = normalized
								Toast.makeText(this@MainActivity, "Server set to ${ServerConfig.displayHost()}", Toast.LENGTH_SHORT).show()
								showServerDialog = false
								// Refresh data immediately using the new base URL.
								attendanceViewModel.refreshEvents()
								attendanceViewModel.refreshStats()
								attendanceViewModel.refreshRosterFromServer()
								// Kick a one-time sync in case there is pending offline work.
								SyncWork.enqueueOneTime(this@MainActivity)
							}
						)
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		SyncWork.enqueuePeriodic(this)
		SyncWork.enqueueOneTime(this)
		if (::attendanceViewModel.isInitialized) {
			attendanceViewModel.refreshStats()
		}
	}

	private fun exportAttendance(format: ExportFormat, presentOnly: Boolean) {
		// Simple CSV export to cache directory; shows file path in a toast.
		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val selected = attendanceViewModel.selectedEvent.value
				if (selected == null) {
					withContext(Dispatchers.Main) {
						Toast.makeText(this@MainActivity, "Select an event first", Toast.LENGTH_SHORT).show()
					}
					return@launch
				}

				val students = database.studentDao().getAllStudents(selected.eventId)
				val filtered = if (presentOnly) students.filter { it.status == "Present" } else students
				val csvBuilder = StringBuilder()
				csvBuilder.append("EventId,EventName,UID,Name,Branch,Year,Status,Timestamp\n")
				filtered.forEach { s ->
					csvBuilder.append(
						listOf(
							selected.eventId.toString(),
							"\"${selected.eventName.replace("\"", "\"\"")}\"",
							s.uid,
							s.name,
							s.branch,
							s.year,
							s.status,
							s.timestamp
						).joinToString(",")
					).append('\n')
				}

				val fileName = "attendance_${timeStamp()}.csv"
				val outFile = File(cacheDir, fileName)
				outFile.writeText(csvBuilder.toString())

				withContext(Dispatchers.Main) {
					shareFile(outFile, "text/csv")
					Toast.makeText(
						this@MainActivity,
						"Exported ${filtered.size} rows",
						Toast.LENGTH_LONG
					).show()
				}
			} catch (e: Exception) {
				withContext(Dispatchers.Main) {
					Toast.makeText(
						this@MainActivity,
						"Export failed: ${e.message}",
						Toast.LENGTH_LONG
					).show()
				}
			}
		}
	}

	private fun timeStamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

	private fun shareFile(file: File, mimeType: String) {
		val uri: Uri = FileProvider.getUriForFile(
			this,
			"com.cu.attendance.fileprovider",
			file
		)
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = mimeType
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		startActivity(Intent.createChooser(intent, "Share export"))
	}
}
