package com.classcue.kexing

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.classcue.kexing.data.HistoryStore
import com.classcue.kexing.data.RecordingCleanupJob
import com.classcue.kexing.data.RecordingRepository
import com.classcue.kexing.data.ScheduleStore
import com.classcue.kexing.engine.ClassReminders
import com.classcue.kexing.data.SecretStore
import com.classcue.kexing.data.SettingsStore
import com.classcue.kexing.engine.ModelRepository
import com.classcue.kexing.engine.SessionRecovery

class ClassCueApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        secretStore = SecretStore(this)
        historyStore = HistoryStore(this)
        recordingRepository = RecordingRepository(this)
        SessionRecovery.detect(this, recordingRepository)
        modelRepository = ModelRepository(this)
        scheduleStore = ScheduleStore(this)
        createNotificationChannels()
        RecordingCleanupJob.schedule(this)
        ClassReminders.reschedule(this, scheduleStore.timetable.value)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val listening = NotificationChannel(
            CHANNEL_LISTENING,
            getString(R.string.listening_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示正在听课、录音和识别的常驻状态"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "课堂提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "点名或提问时显示高优先级横幅"
            setSound(null, null)
            enableVibration(false)
            enableLights(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        val channels = mutableListOf(listening, alert)
        if (isHonorDevice()) {
            channels += NotificationChannel(
                CHANNEL_ALERT_HONOR,
                "课堂提醒（荣耀横幅兼容）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于 MagicOS 后台点名与提问横幅；震动力度仍由课醒控制"
                setSound(null, null)
                // MagicOS may demote a completely silent channel and suppress its banner.
                // A 1 ms system pulse keeps the channel alerting while the app owns the
                // actual, user-configurable vibration waveform.
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 1L)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        }
        channels += NotificationChannel(
            CHANNEL_CLASS_REMINDER,
            "上课提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "按课程表在上课前提示确认课程；不会自动开始录音"
            setShowBadge(false)
        }
        channels += NotificationChannel(
            CHANNEL_MODEL_DOWNLOAD,
            "离线模型下载",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示中档和大档离线转写模型的后台下载进度"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannels(channels)
    }

    companion object {
        const val CHANNEL_LISTENING = "classcue_listening_v1"
        const val CHANNEL_ALERT = "classcue_alert_v3"
        const val CHANNEL_ALERT_HONOR = "classcue_alert_v3_honor"
        const val CHANNEL_MODEL_DOWNLOAD = "classcue_model_download_v1"
        const val CHANNEL_CLASS_REMINDER = "classcue_class_reminder_v1"

        fun alertChannelId(phoneVibrationEnabled: Boolean): String =
            if (isHonorDevice() && phoneVibrationEnabled) CHANNEL_ALERT_HONOR else CHANNEL_ALERT

        private fun isHonorDevice(): Boolean =
            Build.MANUFACTURER.contains("HONOR", ignoreCase = true) ||
                Build.BRAND.contains("HONOR", ignoreCase = true)

        lateinit var settingsStore: SettingsStore
            private set
        lateinit var secretStore: SecretStore
            private set
        lateinit var historyStore: HistoryStore
            private set
        lateinit var recordingRepository: RecordingRepository
            private set
        lateinit var modelRepository: ModelRepository
            private set
        lateinit var scheduleStore: ScheduleStore
            private set
    }
}
