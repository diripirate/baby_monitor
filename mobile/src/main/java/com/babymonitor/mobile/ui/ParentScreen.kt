package com.babymonitor.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babymonitor.core.model.MonitoringState

@Composable
fun ParentScreen(
    state: MonitoringState,
    onToggleListen: () -> Unit,
    onExit: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (state.isAudioStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "listen_btn_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Modo Receptor (Padre)",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "El reloj está vigilando la habitación del bebé",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sound indicator card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nivel de ruido detectado",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.currentDb} dB",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.currentDb > state.soundThresholdDb) Color.Red else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (state.currentDb / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }
        }

        // Live Audio Button (big circular button)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onToggleListen,
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (state.isAudioStreaming) Icons.Default.Mic else Icons.Default.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.isAudioStreaming) "Detener\nEscucha" else "Escuchar\nDirecto",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (state.isAudioStreaming)
                    "Transmitiendo audio en tiempo real desde el reloj"
                else
                    "Pulsa para escuchar en tiempo real sin esperar a la alarma",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Exit / Disconnect button
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar y Desconectar", fontSize = 16.sp)
        }
    }
}
