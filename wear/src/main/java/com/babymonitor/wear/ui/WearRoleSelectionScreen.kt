package com.babymonitor.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.babymonitor.core.model.DeviceRole

@Composable
fun WearRoleSelectionScreen(
    onSelectRole: (DeviceRole) -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text(
                text = "Baby Monitor",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                onClick = { onSelectRole(DeviceRole.BABY) },
                label = { Text("Reloj en Cuna", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("Micrófono vigilante") },
                colors = ChipDefaults.primaryChipColors()
            )
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                onClick = { onSelectRole(DeviceRole.PARENT) },
                label = { Text("Reloj en Muñeca", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("Receptor alertas") },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}
