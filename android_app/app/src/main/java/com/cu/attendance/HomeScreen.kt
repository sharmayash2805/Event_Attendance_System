package com.cu.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    stats: AttendanceStats,
    events: List<EventDto>,
    selectedEvent: SelectedEvent?,
    onEventSelected: (EventDto) -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onResetClick: () -> Unit,
    serverLabel: String,
    onServerSettingsClick: () -> Unit,
    onTestServerClick: () -> Unit
) {
    val bg = Color(0xFF0E0E0E)
    val card = Color(0xFF1C1C1E)
    val red = Color(0xFFE53935)
    val textSecondary = Color(0xFFB0B0B0)

    val present = stats.present
    val total = stats.total
    val remaining = stats.remaining
    val percentage = if (total == 0) 0 else (present * 100 / total)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AETHER",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = card,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                onClick = onServerSettingsClick
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Server", color = textSecondary, fontSize = 12.sp)
                    Text(text = serverLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                onClick = onTestServerClick
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Test", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            var eventMenuExpanded by remember { mutableStateOf(false) }

            Surface(
                color = Color.Transparent,
                onClick = { eventMenuExpanded = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedEvent?.eventName ?: "Select Event",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Change event",
                        tint = textSecondary
                    )
                }
            }

            DropdownMenu(
                expanded = eventMenuExpanded,
                onDismissRequest = { eventMenuExpanded = false }
            ) {
                if (events.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No active events") },
                        onClick = { eventMenuExpanded = false }
                    )
                } else {
                    events.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.eventName) },
                            onClick = {
                                eventMenuExpanded = false
                                onEventSelected(option)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percentage%",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$present / $total Attendees",
                color = textSecondary,
                fontSize = 14.sp
            )
            Text(
                text = "Remaining: $remaining",
                color = textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onScanClick,
            enabled = selectedEvent != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = red)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ENTER SCANNER",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledCardButton(
                    text = "SEARCH DB",
                    icon = Icons.Default.Search,
                    iconTint = Color.White,
                    background = card,
                    onClick = onSearchClick,
                    modifier = Modifier.weight(1f)
                )
                OutlineCardButton(
                    text = "IMPORT LIST",
                    icon = Icons.Default.Download,
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledCardButton(
                    text = "EXPORT DATA",
                    icon = Icons.Default.Upload,
                    iconTint = red,
                    background = card,
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f)
                )
                OutlineCardButton(
                    text = "RESET DATA",
                    icon = Icons.Default.Delete,
                    onClick = onResetClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Made by Angadveer Deol",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FilledCardButton(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(56.dp),
        color = background,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
fun OutlineCardButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.Gray),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}
