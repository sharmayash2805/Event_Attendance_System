package com.cu.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    students: List<StudentImportRow>,
    onBack: () -> Unit,
    onConfirm: (List<StudentImportRow>) -> Unit
) {
    val bg = Color(0xFF0E0E0E)
    val card = Color(0xFF1C1C1E)
    val red = Color(0xFFE53935)

    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }

    val validCount = students.count { it.isValid }
    val invalidCount = students.count { !it.isValid }

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
                text = "IMPORT PREVIEW",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = card
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${students.size}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Total", color = Color.Gray, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$validCount",
                        color = Color(0xFF4CAF50),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Valid", color = Color.Gray, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$invalidCount",
                        color = Color(0xFFE53935),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Invalid", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isImporting) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = importProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = red
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Importing... ${(importProgress * 100).toInt()}%",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(students) { student ->
                ImportRowCard(student)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onBack,
                enabled = !isImporting,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("CANCEL")
            }

            Button(
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = red),
                onClick = {
                    isImporting = true
                    onConfirm(students.filter { it.isValid })
                },
                enabled = !isImporting && validCount > 0
            ) {
                Text("CONFIRM & IMPORT")
            }
        }
    }
}

@Composable
fun ImportRowCard(student: StudentImportRow) {
    val bgColor = if (student.isValid)
        Color(0xFF1C1C1E)
    else
        Color(0xFF3A1C1C)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (student.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (student.isValid) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    student.uid,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(student.name, color = Color.White, fontSize = 13.sp)
                Text(
                    "${student.branch} • ${student.year}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                if (!student.isValid) {
                    Text(
                        "Invalid Row - Missing Data",
                        color = Color.Red,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
