package com.cu.attendance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(currentUrl)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Address") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Enter your server base URL (LAN or Render).")
                Text("Examples:")
                Text("- http://192.168.1.39:5000")
                Text("- https://event-attendance-system-fslz.onrender.com")
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    placeholder = { Text("https://event-attendance-system-fslz.onrender.com") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value.text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
