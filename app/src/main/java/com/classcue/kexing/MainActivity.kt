package com.classcue.kexing

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.classcue.kexing.engine.ClassCueRuntime
import com.classcue.kexing.engine.ListenService
import com.classcue.kexing.engine.ModelDownloadService
import com.classcue.kexing.engine.OfflineModelPack
import com.classcue.kexing.model.TranscriptionModelSize
import com.classcue.kexing.ui.ClassCueRoot
import com.classcue.kexing.ui.ClassCueTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private enum class PendingAction { NONE, START, TEST }

    private var pendingAction = PendingAction.NONE
    private val permissionEpoch = mutableIntStateOf(0)
    private val apiKeysState = mutableStateOf<Map<String, String>>(emptyMap())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionEpoch.intValue++
        val action = pendingAction
        pendingAction = PendingAction.NONE
        when (action) {
            PendingAction.START -> if (hasAudioPermission() && hasNotificationPermission()) startListeningNow()
            PendingAction.TEST -> if (hasNotificationPermission()) testAlertNow()
            PendingAction.NONE -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        apiKeysState.value = ClassCueApplication.settingsStore.settings.value.aiProfiles.associate { profile ->
            profile.id to ClassCueApplication.secretStore.getApiKey(profile.id)
        }
        setContent {
            permissionEpoch.intValue
            val session by ClassCueRuntime.session.collectAsState()
            val appSettings by ClassCueApplication.settingsStore.settings.collectAsState()
            val events by ClassCueApplication.historyStore.events.collectAsState()
            val transcripts by ClassCueApplication.historyStore.transcripts.collectAsState()
            val recordings by ClassCueApplication.recordingRepository.recordings.collectAsState()
            val modelStates by ClassCueApplication.modelRepository.states.collectAsState()
            ClassCueTheme {
                ClassCueRoot(
                    session = session,
                    settings = appSettings,
                    events = events,
                    transcripts = transcripts,
                    recordings = recordings,
                    modelStates = modelStates,
                    apiKeys = apiKeysState.value,
                    notificationPermissionGranted = hasNotificationPermission(),
                    alertChannelReady = areAlertBannersEnabled(),
                    audioPermissionGranted = hasAudioPermission(),
                    onStart = { requestStart() },
                    onStop = { stopListening() },
                    onTestAlert = { requestTestAlert() },
                    onUpdateSettings = { transform -> ClassCueApplication.settingsStore.update(transform) },
                    onApiKeyChange = { profileId, value ->
                        apiKeysState.value = apiKeysState.value + (profileId to value)
                        ClassCueApplication.secretStore.setApiKey(profileId, value)
                    },
                    onDeleteAiProfile = { profileId ->
                        ClassCueApplication.secretStore.deleteApiKey(profileId)
                        apiKeysState.value = apiKeysState.value - profileId
                    },
                    onDeleteHistory = { ClassCueApplication.historyStore.deleteAll() },
                    onRetryAi = { eventId, question, profileIds ->
                        retryAi(eventId, question, profileIds)
                    },
                    onDownloadModel = { downloadModel(it) },
                    onPauseModelDownload = { pauseModelDownload() },
                    onDeleteModel = { deleteModel(it) },
                    onPlayRecording = { openRecording(it) },
                    onShareRecording = { shareRecording(it) },
                    onOpenNotificationSettings = { openNotificationSettings() },
                    onOpenLiveUpdateSettings = { openLiveUpdateSettings() },
                    onOpenBatterySettings = { openBatterySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch.intValue++
        ClassCueApplication.recordingRepository.cleanupExpired()
        ClassCueApplication.recordingRepository.refresh()
        ClassCueApplication.modelRepository.refresh()
    }

    private fun requestStart() {
        if (hasAudioPermission() && hasNotificationPermission()) {
            startListeningNow()
            return
        }
        pendingAction = PendingAction.START
        permissionLauncher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    private fun requestTestAlert() {
        if (hasNotificationPermission()) testAlertNow()
        else {
            pendingAction = PendingAction.TEST
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun startListeningNow() {
        startForegroundService(
            Intent(this, ListenService::class.java).setAction(ListenService.ACTION_START)
        )
    }

    private fun stopListening() {
        startService(Intent(this, ListenService::class.java).setAction(ListenService.ACTION_STOP))
    }

    private fun testAlertNow() {
        startService(Intent(this, ListenService::class.java).setAction(ListenService.ACTION_TEST_ALERT))
    }

    private fun retryAi(eventId: Long, question: String, profileIds: List<String>) {
        startService(
            Intent(this, ListenService::class.java).apply {
                action = ListenService.ACTION_RETRY_AI
                putExtra(ListenService.EXTRA_EVENT_ID, eventId)
                putExtra(ListenService.EXTRA_QUESTION, question)
                putStringArrayListExtra(ListenService.EXTRA_PROFILE_IDS, ArrayList(profileIds))
            }
        )
    }

    private fun downloadModel(pack: OfflineModelPack) {
        startForegroundService(
            Intent(this, ModelDownloadService::class.java).apply {
                action = ModelDownloadService.ACTION_DOWNLOAD
                putExtra(ModelDownloadService.EXTRA_PACK, pack.name)
            }
        )
    }

    private fun pauseModelDownload() {
        startService(
            Intent(this, ModelDownloadService::class.java).setAction(ModelDownloadService.ACTION_PAUSE)
        )
    }

    private fun deleteModel(pack: OfflineModelPack) {
        if (!ClassCueApplication.modelRepository.delete(pack)) return
        ClassCueApplication.settingsStore.update { current ->
            val replacement = when (pack) {
                OfflineModelPack.MEDIUM -> TranscriptionModelSize.SMALL
                OfflineModelPack.LARGE -> if (
                    ClassCueApplication.modelRepository.isReady(OfflineModelPack.MEDIUM)
                ) {
                    TranscriptionModelSize.MEDIUM
                } else {
                    TranscriptionModelSize.SMALL
                }
            }
            if (
                current.transcriptionModelSize == TranscriptionModelSize.LARGE ||
                current.transcriptionModelSize == TranscriptionModelSize.MEDIUM && pack == OfflineModelPack.MEDIUM
            ) current.copy(transcriptionModelSize = replacement) else current
        }
    }

    private fun hasAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun areAlertBannersEnabled(): Boolean {
        if (!hasNotificationPermission()) return false
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(
            ClassCueApplication.alertChannelId(
                ClassCueApplication.settingsStore.settings.value.phoneVibrationEnabled
            )
        )
        return manager.areNotificationsEnabled() &&
            channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    private fun openRecording(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "播放课堂录音")) }
    }

    private fun shareRecording(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "导出课堂录音"))
    }

    private fun openNotificationSettings() {
        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(
                Settings.EXTRA_CHANNEL_ID,
                ClassCueApplication.alertChannelId(
                    ClassCueApplication.settingsStore.settings.value.phoneVibrationEnabled
                )
            )
        }
        runCatching { startActivity(channelIntent) }
            .onFailure { openAppNotificationSettings() }
    }

    private fun openLiveUpdateSettings() {
        val promotionIntent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(promotionIntent) }
            .onFailure { openAppNotificationSettings() }
    }

    private fun openAppNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        })
    }

    private fun openBatterySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
