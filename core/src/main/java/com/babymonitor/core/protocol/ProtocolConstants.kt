package com.babymonitor.core.protocol

object ProtocolConstants {
    // Message API Paths
    const val PATH_ALERT_SOUND = "/alert/sound"
    const val PATH_ALERT_MOTION = "/alert/motion"
    const val PATH_CMD_START_AUDIO = "/cmd/start_audio"
    const val PATH_CMD_STOP_AUDIO = "/cmd/stop_audio"
    const val PATH_CMD_START_VIDEO = "/cmd/start_video"
    const val PATH_CMD_STOP_VIDEO = "/cmd/stop_video"
    const val PATH_CMD_TOGGLE_TORCH = "/cmd/toggle_torch"
    const val PATH_CMD_SWITCH_CAMERA = "/cmd/switch_camera"
    const val PATH_CMD_EXIT = "/cmd/exit"
    const val PATH_STATUS_TELEMETRY = "/status/telemetry"
    const val PATH_CONFIG_SYNC = "/config/sync"

    // Channel API Paths (Streaming)
    const val CHANNEL_AUDIO = "/channel/audio_stream"
    const val CHANNEL_VIDEO = "/channel/video_stream"

    // Audio Configurations
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_BUFFER_SIZE_SAMPLES = 640 // 40ms buffer at 16kHz
    const val DEFAULT_SOUND_THRESHOLD_DB = 65

    // Video Configurations
    const val VIDEO_WIDTH = 320
    const val VIDEO_HEIGHT = 320
    const val VIDEO_JPEG_QUALITY = 55
    const val DEFAULT_MOTION_THRESHOLD_PERCENT = 15

    // Notification IDs
    const val NOTIFICATION_ID_FOREGROUND_SERVICE = 1001
    const val NOTIFICATION_ID_ALARM = 1002
    const val CHANNEL_ID_SERVICE = "baby_monitor_service_channel"
    const val CHANNEL_ID_ALARM = "baby_monitor_alarm_channel"
}
