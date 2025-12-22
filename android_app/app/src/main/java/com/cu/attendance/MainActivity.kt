package com.cu.attendance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
	private var showImportScreen by mutableStateOf(false)
	private var importedStudents by mutableStateOf<List<StudentImportRow>>(emptyList())
	private var showExportScreen by mutableStateOf(false)
	private var showServerDialog by mutableStateOf(false)
	private var serverUrl by mutableStateOf("")
	private var testingServer by mutableStateOf(false)
	private var showResetDialog by mutableStateOf(false)
	private var resetMessage by mutableStateOf("")

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

			val filePicker = rememberLauncherForActivityResult(
				ActivityResultContracts.OpenDocument()
			) { uri: Uri? ->
				uri?.let {
					lifecycleScope.launch(Dispatchers.IO) {
						try {
							// Persist temporary read permission to avoid SecurityException on some providers.
							val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
							try {
								contentResolver.takePersistableUriPermission(it, flags)
							} catch (_: SecurityException) {
								// Not all providers allow persistable permission; ignore.
							}

							val students = readExcelFile(this@MainActivity, it)
							withContext(Dispatchers.Main) {
								if (students.isEmpty()) {
									Toast.makeText(
										this@MainActivity,
										"No rows found in Excel file",
										Toast.LENGTH_LONG
									).show()
									return@withContext
								}
								importedStudents = students
								showImportScreen = true
							}
						} catch (e: Exception) {
							withContext(Dispatchers.Main) {
								Toast.makeText(
									this@MainActivity,
									"Failed to import: ${e.message ?: "Unknown error"}",
									Toast.LENGTH_LONG
								).show()
							}
						}
					}
				}
			}

			MaterialTheme {
				Surface(color = MaterialTheme.colorScheme.background) {
					when {
						showImportScreen -> {
							ImportPreviewScreen(
								students = importedStudents,
								onBack = {
									showImportScreen = false
									importedStudents = emptyList()
								},
								onConfirm = { validStudents ->
									importToDatabase(validStudents)
								}
							)
						}
						showExportScreen -> {
							ExportScreen(
								onBack = { showExportScreen = false },
								onExport = { format, presentOnly ->
									exportAttendance(format, presentOnly)
									showExportScreen = false
								}
							)
						}
						showSearchScreen -> {
							SearchScreen(
								onBack = { showSearchScreen = false }
							)
						}
						else -> {
							HomeScreen(
								stats = stats,
								events = events,
								selectedEvent = selectedEvent,
								onEventSelected = { option ->
									attendanceViewModel.setSelectedEvent(option.eventId, option.eventName)
									attendanceViewModel.refreshStats()
									Toast.makeText(this@MainActivity, "Selected ${option.eventName}", Toast.LENGTH_SHORT).show()
								},
								onScanClick = {
									val current = attendanceViewModel.selectedEvent.value
									if (current == null) {
										Toast.makeText(this@MainActivity, "Select an event first", Toast.LENGTH_SHORT).show()
										return@HomeScreen
									}
									val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
									intent.putExtra(BarcodeScannerActivity.EXTRA_EVENT_ID, current.eventId)
									intent.putExtra(BarcodeScannerActivity.EXTRA_EVENT_NAME, current.eventName)
									scannerLauncher.launch(intent)
								},
								onSearchClick = {
									showSearchScreen = true
								},
								onImportClick = {
									filePicker.launch(arrayOf(
										"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
									))
								},
								onExportClick = {
									showExportScreen = true
								},
								onResetClick = {
									val name = selectedEvent?.eventName ?: "this event"
									resetMessage = "Clear all students and attendance for $name? This cannot be undone."
									showResetDialog = true
								},
								serverLabel = ServerConfig.displayHost(),
								onServerSettingsClick = { showServerDialog = true },
								onTestServerClick = {
									if (testingServer) return@HomeScreen
									testingServer = true
									lifecycleScope.launch(Dispatchers.IO) {
										val ok = ScannerHelper.checkServer()
										withContext(Dispatchers.Main) {
											testingServer = false
											val msg = if (ok) "Server reachable" else "Server unreachable"
											Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
										}
									}
								}
							)
						}
					}

					if (showResetDialog) {
						AlertDialog(
							onDismissRequest = {
								showResetDialog = false
							},
							title = { Text("Reset data?") },
							text = { Text(resetMessage) },
							confirmButton = {
								TextButton(onClick = {
									performReset()
								}) {
									Text("Clear data")
								}
							},
							dismissButton = {
								TextButton(onClick = {
									showResetDialog = false
								}) {
									Text("Cancel")
								}
							}
						)
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
							}
						)
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		if (::attendanceViewModel.isInitialized) {
			attendanceViewModel.refreshStats()
		}
	}

	private fun importToDatabase(students: List<StudentImportRow>) {
		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val selected = attendanceViewModel.selectedEvent.value
				if (selected == null) {
					withContext(Dispatchers.Main) {
						Toast.makeText(this@MainActivity, "Select an event first", Toast.LENGTH_SHORT).show()
					}
					return@launch
				}

				val entities = students.map {
					StudentEntity(
						eventId = selected.eventId,
						uid = it.uid.trim(),
						name = it.name,
						branch = it.branch,
						year = it.year,
						status = "Absent",
						timestamp = ""
					)
				}

				database.studentDao().insertAll(entities)

				withContext(Dispatchers.Main) {
					Toast.makeText(
						this@MainActivity,
						"✅ ${entities.size} students imported successfully!",
						Toast.LENGTH_LONG
					).show()
					showImportScreen = false
					importedStudents = emptyList()
					attendanceViewModel.refreshStats()
				}
			} catch (e: Exception) {
				withContext(Dispatchers.Main) {
					Toast.makeText(
						this@MainActivity,
						"Import failed: ${e.message}",
						Toast.LENGTH_LONG
					).show()
				}
			}
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

	private fun performReset() {
		showResetDialog = false
		attendanceViewModel.resetAllData(
			onComplete = {
				val name = attendanceViewModel.selectedEvent.value?.eventName ?: "event"
				Toast.makeText(this, "Data cleared for $name. Import a new list to start fresh.", Toast.LENGTH_LONG).show()
			},
			onError = { msg ->
				Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
			}
		)
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
