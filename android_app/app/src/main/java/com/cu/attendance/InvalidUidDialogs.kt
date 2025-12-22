package com.cu.attendance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class AlreadyMarkedResult(
    val uid: String,
    val name: String,
    val time: String
)

@Composable
fun shakeAnimation(): androidx.compose.runtime.State<Float> {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 500
                -12f at 50
                12f at 100
                -8f at 150
                8f at 200
                -4f at 250
                4f at 300
                0f at 350
            }
        )
    }
    return offsetX.asState()
}

@Composable
fun pulseScale(): androidx.compose.runtime.State<Float> {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }
    return scale.asState()
}

@Composable
fun InvalidUidDialog(
    uid: String,
    onAddStudent: () -> Unit,
    onDismiss: () -> Unit
) {
    val shakeOffset = shakeAnimation()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onAddStudent,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("Add Student")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        title = {
            Text("❌ Invalid UID", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("This UID is not registered.")
                Spacer(Modifier.height(6.dp))
                Text("UID: $uid", color = Color.Gray)
            }
        },
        modifier = Modifier.offset(x = shakeOffset.value.dp),
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentDialog(
    uid: String,
    onSubmit: (name: String, branch: String, year: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && branch.isNotBlank() && year.isNotBlank(),
                onClick = { onSubmit(name, branch, year) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("Save & Mark")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        title = {
            Text("Add New Student", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                OutlinedTextField(
                    value = uid,
                    onValueChange = {},
                    label = { Text("UID") },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.Gray,
                        disabledBorderColor = Color.Gray,
                        disabledLabelColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFE53935),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch (e.g., CS, ECE)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFE53935),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year (e.g., 3rd Year)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFE53935),
                        unfocusedLabelColor = Color.Gray
                    )
                )
            }
        },
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun AlreadyMarkedDialog(
    result: AlreadyMarkedResult,
    onDismiss: () -> Unit
) {
    val pulse = pulseScale()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300)
                )
            ) {
                Text("OK", color = Color.Black)
            }
        },
        title = {
            Text(
                text = "⚠️ Already Marked",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.scale(pulse.value)
            ) {
                Text("This student is already marked present.")
                Spacer(Modifier.height(8.dp))

                Text("Name: ${result.name}", fontWeight = FontWeight.Medium)
                Text("UID: ${result.uid}")
                Text("Marked at: ${result.time}", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2A2416),
        titleContentColor = Color(0xFFFFD54F),
        textContentColor = Color.White
    )
}
