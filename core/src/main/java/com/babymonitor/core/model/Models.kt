package com.babymonitor.core.model

/**
 * Roles for devices in the baby monitor system.
 */
enum class DeviceRole {
    BABY,       // Device is in the baby's room (transmitting audio/video, detecting alerts)
    PARENT,     // Device is with the parent (receiving alerts, listening/watching stream)
    UNSELECTED
}

/**
 * Monitoring mode when phone is in the baby room.
 */
enum class MonitoringType {
    SOUND_ONLY,
    MOTION_ONLY,
    BOTH
}

/**
 * Types of alarms triggered by the baby station.
 */
enum class AlertType {
    SOUND,
    MOTION
}

/**
 * Telemetry and state shared between devices.
 */
data class MonitoringState(
    val isRunning: Boolean = false,
    val isAudioStreaming: Boolean = false,
    val isVideoStreaming: Boolean = false,
    val currentDb: Int = 0,
    val soundThresholdDb: Int = 65,
    val motionThresholdPercent: Int = 15,
    val monitoringType: MonitoringType = MonitoringType.BOTH,
    val isTorchOn: Boolean = false,
    val isBackCamera: Boolean = true
)
