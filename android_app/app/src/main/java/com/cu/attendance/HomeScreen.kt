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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    selectedEvent: SelectedEvent?,
	openSession: SessionDto?,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onExportClick: () -> Unit,
	onOpenServerSettings: () -> Unit,
    connectionHint: String?,
    serverStatusText: String,
    onCheckServer: () -> Unit
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
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // 1) Current scanning target (read-only)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                Text(
                    text = "Event",
                    color = textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = selectedEvent?.eventName ?: "No active event",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Session",
                    color = textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = openSession?.sessionName?.takeIf { it.isNotBlank() } ?: "No open session",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Online/Offline button (checks actual server reachability)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            onClick = onCheckServer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Server", color = textSecondary, fontSize = 12.sp)
                Text(text = serverStatusText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

		Spacer(modifier = Modifier.height(16.dp))

		// 2) Event stats card
		EventStatsCard(
			cardColor = card,
			secondaryText = textSecondary,
			total = total,
			present = present,
			remaining = remaining,
			percentage = percentage
		)

		Spacer(modifier = Modifier.height(22.dp))

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

		Spacer(modifier = Modifier.height(18.dp))

		// 4) Manual search
		Surface(
			modifier = Modifier
				.fillMaxWidth()
				.height(56.dp),
			color = card,
			shape = RoundedCornerShape(18.dp),
			onClick = onSearchClick
		) {
			Row(
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
				Spacer(modifier = Modifier.width(8.dp))
				Text("SEARCH DATABASE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
			}
		}

		Spacer(modifier = Modifier.height(16.dp))

        // 5) Export attendance
        FilledCardButton(
            text = "EXPORT ATTENDANCE",
            icon = Icons.Default.Upload,
            iconTint = red,
            background = card,
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth()
        )

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
				,
				modifier = Modifier.pointerInput(Unit) {
					detectTapGestures(
						onLongPress = { onOpenServerSettings() }
					)
				}
            )
        }
    }
}

@Composable
private fun EventStatsCard(
    cardColor: Color,
    secondaryText: Color,
    total: Int,
    present: Int,
    remaining: Int,
    percentage: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Event Stats",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatsItem(label = "Total Registered", value = total.toString(), secondaryText = secondaryText)
                StatsItem(label = "Present", value = present.toString(), secondaryText = secondaryText)
                StatsItem(label = "Remaining", value = remaining.toString(), secondaryText = secondaryText)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$percentage%",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsItem(label: String, value: String, secondaryText: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, color = secondaryText, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
