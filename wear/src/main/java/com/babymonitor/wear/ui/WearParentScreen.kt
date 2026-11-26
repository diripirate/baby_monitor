package com.babymonitor.wear.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.babymonitor.core.model.MonitoringState

@Composable
fun WearParentScreen(
    state: MonitoringState,
    latestFrame: Bitmap?,
    isVideoFullscreen: Boolean,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleVideoFullscreen: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onExit: () -> Unit
) {
    if (isVideoFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onToggleVideoFullscreen),
            contentAlignment = Alignment.Center
        ) {
            if (latestFrame != null) {
                Image(
                    bitmap = latestFrame.asImageBitmap(),
                    contentDescription = "Vídeo en directo. Toca para volver a los controles",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Conectando vídeo...",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.LightGray
                )
            }
        }
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Preview (if active and frame available)
        if (state.isVideoStreaming) {
            item {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable(onClick = onToggleVideoFullscreen),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestFrame != null) {
                        Image(
                            bitmap = latestFrame.asImageBitmap(),
                            contentDescription = "Vídeo en directo. Toca para ampliar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Conectando vídeo...",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = Color.LightGray
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Bebé vigilado",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Live Audio Toggle
        item {
            Chip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                onClick = onToggleAudio,
                label = {
                    Text(if (state.isAudioStreaming) "Silenciar Audio" else "Escuchar en Directo")
                },
                colors = if (state.isAudioStreaming)
                    ChipDefaults.chipColors(backgroundColor = Color(0xFFD32F2F))
                else
                    ChipDefaults.primaryChipColors()
            )
        }

        // Live Video Toggle
        item {
            Chip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                onClick = onToggleVideo,
                label = {
                    Text(if (state.isVideoStreaming) "Ocultar Vídeo" else "Ver Vídeo en Directo")
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        // Remote Torch Toggle
        item {
            CompactChip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = onToggleTorch,
                label = {
                    Text(if (state.isTorchOn) "Linterna ON" else "Linterna OFF")
                },
                colors = if (state.isTorchOn)
                    ChipDefaults.chipColors(backgroundColor = Color(0xFFFFA000))
                else
                    ChipDefaults.chipColors()
            )
        }

        // Remote Switch Camera
        item {
            CompactChip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = onSwitchCamera,
                label = { Text("Cambiar Cámara") },
                colors = ChipDefaults.chipColors()
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Exit Button
        item {
            CompactChip(
                onClick = onExit,
                label = { Text("Salir", color = Color.White) },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFB00020))
            )
        }
    }
}
