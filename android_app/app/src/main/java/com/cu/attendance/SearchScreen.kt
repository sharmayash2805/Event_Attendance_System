package com.cu.attendance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Student(
    val uid: String,
    val name: String,
    val year: String,
    val department: String,
    val isMarked: Boolean = false
)

data class AttendanceResult(
    val uid: String,
    val name: String,
    val time: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit
) {
    val viewModel: AttendanceViewModel = viewModel()
    val bg = Color(0xFF0E0E0E)
    val card = Color(0xFF1C1C1E)

    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AttendanceResult?>(null) }
    var showInvalidDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var showAlreadyMarkedDialog by remember { mutableStateOf(false) }
    var currentUid by remember { mutableStateOf("") }
    var alreadyMarkedResult by remember { mutableStateOf<AlreadyMarkedResult?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isMarking by viewModel.isMarking.collectAsState()
    val error by viewModel.error.collectAsState()

    val students = searchResults.map { entity ->
        Student(
            uid = entity.uid,
            name = entity.name,
            year = entity.year,
            department = entity.branch,
            isMarked = entity.status == "Present"
        )
    }

    fun markStudentPresent(student: Student) {
        viewModel.markStudentPresent(
            uid = student.uid,
            onSuccess = { updatedStudent ->
                result = AttendanceResult(
                    uid = updatedStudent.uid,
                    name = updatedStudent.name,
                    time = updatedStudent.timestamp
                )
                showDialog = true
            },
            onAlreadyMarked = { existingStudent ->
                alreadyMarkedResult = AlreadyMarkedResult(
                    uid = existingStudent.uid,
                    name = existingStudent.name,
                    time = existingStudent.timestamp
                )
                showAlreadyMarkedDialog = true
            },
            onInvalid = {
                currentUid = student.uid
                showInvalidDialog = true
            }
        )
    }

    fun addStudentAndMarkAttendance(uid: String, name: String, branch: String, year: String) {
        viewModel.addStudentAndMarkPresent(
            uid = uid,
            name = name,
            branch = branch,
            year = year,
            onSuccess = { newStudent ->
                result = AttendanceResult(
                    uid = newStudent.uid,
                    name = newStudent.name,
                    time = newStudent.timestamp
                )
                showDialog = true
            },
            onError = {
            }
        )
    }

    fun searchStudents(query: String) {
        if (query.length < 2) {
            viewModel.clearSearchResults()
            return
        }

        viewModel.searchStudents(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "SEARCH DATABASE",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                searchStudents(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter name or UID", color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE53935),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color(0xFFE53935)
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    searchStudents(searchQuery)
                }
            )
        )

        Spacer(Modifier.height(16.dp))

        error?.let { errorMsg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFD32F2F),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = errorMsg,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (isSearching || isMarking) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFE53935))
            }
            Spacer(Modifier.height(16.dp))
        }

        if (students.isNotEmpty()) {
            Text(
                text = "${students.size} result${if (students.size != 1) "s" else ""} found",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(students) { student ->
                StudentCard(
                    student = student,
                    onMarkPresent = { markStudentPresent(it) }
                )
            }
        }

        if (students.isEmpty() && searchQuery.isNotEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No students found",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }
    }

    if (showDialog && result != null) {
        AttendanceSuccessDialog(
            result = result!!,
            onDismiss = { showDialog = false }
        )
    }

    if (showInvalidDialog) {
        InvalidUidDialog(
            uid = currentUid,
            onAddStudent = {
                showInvalidDialog = false
                showAddStudentDialog = true
            },
            onDismiss = {
                showInvalidDialog = false
            }
        )
    }

    if (showAddStudentDialog) {
        AddStudentDialog(
            uid = currentUid,
            onSubmit = { name, branch, year ->
                addStudentAndMarkAttendance(currentUid, name, branch, year)
                showAddStudentDialog = false
            },
            onDismiss = {
                showAddStudentDialog = false
            }
        )
    }

    if (showAlreadyMarkedDialog && alreadyMarkedResult != null) {
        AlreadyMarkedDialog(
            result = alreadyMarkedResult!!,
            onDismiss = {
                showAlreadyMarkedDialog = false
                alreadyMarkedResult = null
            }
        )
    }
}

@Composable
fun StudentCard(
    student: Student,
    onMarkPresent: (Student) -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (student.isMarked)
            Color(0xFF2A2A2A)
        else
            Color(0xFF1C1C1E),
        animationSpec = tween(400),
        label = "cardColor"
    )

    val alpha by animateFloatAsState(
        targetValue = if (student.isMarked) 0.6f else 1f,
        animationSpec = tween(400),
        label = "cardAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .alpha(alpha),
        shape = RoundedCornerShape(20.dp),
        color = cardColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.Gray, CircleShape)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    student.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${student.year}, ${student.department}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    student.uid,
                    color = Color(0xFF888888),
                    fontSize = 11.sp
                )
            }

            MarkPresentButton(
                enabled = !student.isMarked,
                onClick = { onMarkPresent(student) }
            )
        }
    }
}

@Composable
fun MarkPresentButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) Color(0xFFE53935) else Color.DarkGray,
        animationSpec = tween(300),
        label = "buttonColor"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = Color.DarkGray
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = if (enabled) "Mark Present" else "Already Marked",
            fontSize = 12.sp
        )
    }
}

@Composable
fun AttendanceSuccessDialog(
    result: AttendanceResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935)
                )
            ) {
                Text("OK")
            }
        },
        title = {
            Text(
                text = "✅ Attendance Marked",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Name: ${result.name}")
                Spacer(Modifier.height(4.dp))
                Text("UID: ${result.uid}")
                Spacer(Modifier.height(4.dp))
                Text("Status: Present")
                Spacer(Modifier.height(4.dp))
                Text("Time: ${result.time}")
            }
        },
        containerColor = Color(0xFF1C1C1E),
        textContentColor = Color.White,
        titleContentColor = Color.White
    )
}

fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    return sdf.format(Date())
}

fun getSampleStudents(): List<Student> {
    return listOf(
        Student("22BCS024", "Alex Johnson", "3rd Year", "CS"),
        Student("22BCS015", "Sarah Williams", "3rd Year", "CS"),
        Student("22BCS041", "Raj Patel", "3rd Year", "CS"),
        Student("22BCE032", "Emma Davis", "2nd Year", "ECE"),
        Student("22BME018", "Michael Brown", "4th Year", "ME"),
        Student("22BCS007", "Priya Sharma", "3rd Year", "CS"),
        Student("22BCS089", "David Miller", "2nd Year", "CS"),
        Student("22BCE055", "Lisa Anderson", "3rd Year", "ECE")
    )
}
