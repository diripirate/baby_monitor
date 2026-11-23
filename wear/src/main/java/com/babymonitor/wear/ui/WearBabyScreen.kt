package com.babymonitor.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.babymonitor.core.model.MonitoringState

@Composable
fun WearBabyScreen(
    state: MonitoringState,
    onExit: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text(
                text = "Vigilando Cuna",
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
        item {
            Text(
                text = "${state.currentDb} dB",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.currentDb >= state.soundThresholdDb) Color.Red else Color.Green
            )
        }
        item {
            Text(
                text = if (state.isAudioStreaming) "Transmitiendo audio..." else "En silencio",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = if (state.isAudioStreaming) Color.Yellow else Color.Gray
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            CompactChip(
                onClick = onExit,
                label = { Text("Salir", color = Color.White) },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFB00020))
            )
        }
    }
}
