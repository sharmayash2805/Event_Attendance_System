package com.cu.attendance.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
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

enum class ExportFormat {
    CSV, EXCEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    onExport: (format: ExportFormat, presentOnly: Boolean) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.EXCEL) }
    var presentOnly by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Export Attendance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        onExport(selectedFormat, presentOnly)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text("EXPORT DATA")
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Export Format",
                fontWeight = FontWeight.SemiBold
            )

            ExportFormatCard(
                title = "Excel (.xlsx)",
                selected = selectedFormat == ExportFormat.EXCEL,
                onClick = { selectedFormat = ExportFormat.EXCEL }
            )

            ExportFormatCard(
                title = "CSV (.csv)",
                selected = selectedFormat == ExportFormat.CSV,
                onClick = { selectedFormat = ExportFormat.CSV }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presentOnly = !presentOnly },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = presentOnly,
                    onCheckedChange = { presentOnly = it }
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                Text("Export present students only")
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor =
        if (selected) Color(0xFFD32F2F) else Color.DarkGray

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        tonalElevation = if (selected) 2.dp else 0.dp,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
