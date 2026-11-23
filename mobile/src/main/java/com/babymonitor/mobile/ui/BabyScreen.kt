package com.babymonitor.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babymonitor.core.model.MonitoringState
import com.babymonitor.core.model.MonitoringType

@Composable
fun BabyScreen(
    state: MonitoringState,
    onThresholdChange: (soundDb: Int, motionPercent: Int, type: MonitoringType) -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onExit: () -> Unit
) {
    var isBlackScreenMode by remember { mutableStateOf(false) }

    if (isBlackScreenMode) {
        // OLED Black Screen mode: minimal battery usage and zero light in the baby's bedroom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isBlackScreenMode = false },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Modo Ahorro Pantalla (Vigilancia activa)\nToca para ver controles",
                color = Color.DarkGray,
                fontSize = 14.sp
            )
        }
        return
    }

    var soundDb by remember { mutableFloatStateOf(state.soundThresholdDb.toFloat()) }
    var motionPercent by remember { mutableFloatStateOf(state.motionThresholdPercent.toFloat()) }
    var selectedType by remember { mutableStateOf(state.monitoringType) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Habitación del Bebé",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Badge(containerColor = if (state.isRunning) Color(0xFF4CAF50) else Color.Gray) {
                Text(if (state.isRunning) "ACTIVO" else "DETENIDO", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound level meter
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Nivel de Ruido en directo: ${state.currentDb} dB", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (state.currentDb / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = if (state.currentDb >= soundDb) Color.Red else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Umbral alarma: ${soundDb.toInt()} dB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Monitoring Mode Selection
        Text(text = "Tipo de Detección", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = selectedType == MonitoringType.BOTH,
                onClick = {
                    selectedType = MonitoringType.BOTH
                    onThresholdChange(soundDb.toInt(), motionPercent.toInt(), selectedType)
                },
                label = { Text("Sonido + Vídeo") }
            )
            FilterChip(
                selected = selectedType == MonitoringType.SOUND_ONLY,
                onClick = {
                    selectedType = MonitoringType.SOUND_ONLY
                    onThresholdChange(soundDb.toInt(), motionPercent.toInt(), selectedType)
                },
                label = { Text("Solo Sonido") }
            )
            FilterChip(
                selected = selectedType == MonitoringType.MOTION_ONLY,
                onClick = {
                    selectedType = MonitoringType.MOTION_ONLY
                    onThresholdChange(soundDb.toInt(), motionPercent.toInt(), selectedType)
                },
                label = { Text("Solo Movimiento") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound Threshold Slider
        if (selectedType != MonitoringType.MOTION_ONLY) {
            Text(text = "Sensibilidad de Sonido: ${soundDb.toInt()} dB", modifier = Modifier.align(Alignment.Start))
            Slider(
                value = soundDb,
                onValueChange = {
                    soundDb = it
                    onThresholdChange(soundDb.toInt(), motionPercent.toInt(), selectedType)
                },
                valueRange = 40f..90f,
                steps = 10
            )
        }

        // Motion Threshold Slider
        if (selectedType != MonitoringType.SOUND_ONLY) {
            Text(text = "Sensibilidad de Movimiento: ${motionPercent.toInt()}%", modifier = Modifier.align(Alignment.Start))
            Slider(
                value = motionPercent,
                onValueChange = {
                    motionPercent = it
                    onThresholdChange(soundDb.toInt(), motionPercent.toInt(), selectedType)
                },
                valueRange = 5f..50f,
                steps = 9
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Controls: Torch & Camera Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalButton(onClick = onToggleTorch) {
                Icon(
                    imageVector = if (state.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isTorchOn) "Linterna ON" else "Linterna OFF")
            }

            FilledTonalButton(onClick = onSwitchCamera) {
                Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isBackCamera) "Cámara Trasera" else "Cámara Frontal")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Black Screen Mode Button
        OutlinedButton(
            onClick = { isBlackScreenMode = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.NightlightRound, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Activar Pantalla Negra (Modo Noche OLED)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exit / Stop Button
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar y Detener Monitor", fontSize = 16.sp)
        }
    }
}
