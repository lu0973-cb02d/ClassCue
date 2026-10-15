package com.classcue.kexing

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.classcue.kexing.data.MarkdownExporter
import com.classcue.kexing.engine.ClassCueRuntime
import com.classcue.kexing.engine.ListenService
import com.classcue.kexing.engine.ModelDownloadService
import com.classcue.kexing.engine.OfflineModelPack
import com.classcue.kexing.engine.SessionRecovery
import com.classcue.kexing.engine.WearEngineBridge
import com.classcue.kexing.model.TranscriptionModelSize
import com.classcue.kexing.model.ArchiveFilter
import com.classcue.kexing.model.HistoryArchiveSnapshot
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.ui.ClassCueRoot
import com.classcue.kexing.ui.ClassCueTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private enum class PendingAction { NONE, START, TEST }

    private var pendingAction = PendingAction.NONE
    private val permissionEpoch = mutableIntStateOf(0)
    private val apiKeysState = mutableStateOf<Map<String, String>>(emptyMap())
    private val wearEngineStatusState = mutableStateOf("尚未测试 Wear Engine 直连")

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
            val lessonSummaries by ClassCueApplication.historyStore.lessonSummaries.collectAsState()
            val recordings by ClassCueApplication.recordingRepository.recordings.collectAsState()
            val modelStates by ClassCueApplication.modelRepository.states.collectAsState()
            val interruptedSession by SessionRecovery.interrupted.collectAsState()
            ClassCueTheme {
                ClassCueRoot(
                    session = session,
                    settings = appSettings,
                    events = events,
                    transcripts = transcripts,
                    lessonSummaries = lessonSummaries,
                    recordings = recordings,
                    modelStates = modelStates,
                    interruptedSession = interruptedSession,
                    onDismissInterrupted = { SessionRecovery.dismiss(this) },
                    apiKeys = apiKeysState.value,
                    notificationPermissionGranted = hasNotificationPermission(),
                    alertChannelReady = areAlertBannersEnabled(),
                    audioPermissionGranted = hasAudioPermission(),
                    onStart = { requestStart() },
                    onStop = { stopListening() },
                    onTestAlert = { requestTestAlert() },
                    wearEngineStatus = wearEngineStatusState.value,
                    onTestWearEngine = { testWearEngine() },
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
                    onDeleteRecording = { deleteRecording(it) },
                    onExportAllMarkdown = { exportMarkdown(ArchiveFilter()) },
                    onExportCourseMarkdown = { courseName ->
                        exportMarkdown(ArchiveFilter(courseName = courseName.trim()))
                    },
                    onExportLessonMarkdown = { lesson -> exportLessonMarkdown(lesson) },
                    onDeleteLesson = { lesson -> deleteLesson(lesson) },
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

    private fun testWearEngine() {
        WearEngineBridge.authorizeAndSendTest(
            activity = this,
            settings = ClassCueApplication.settingsStore.settings.value
        ) { status -> runOnUiThread { wearEngineStatusState.value = status } }
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

    private fun deleteRecording(file: File) {
        val deleted = ClassCueApplication.recordingRepository.delete(file)
        Toast.makeText(this, if (deleted) "录音已删除" else "录音删除失败", Toast.LENGTH_SHORT).show()
    }

    private fun exportLessonMarkdown(lesson: LessonSummary) {
        exportMarkdown { ClassCueApplication.historyStore.archiveSnapshot(lesson) }
    }

    private fun exportMarkdown(filter: ArchiveFilter) {
        exportMarkdown { ClassCueApplication.historyStore.archiveSnapshot(filter) }
    }

    private fun exportMarkdown(snapshotProvider: () -> HistoryArchiveSnapshot) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = snapshotProvider()
                    check(!snapshot.isEmpty) { "当前范围内没有可导出的课堂文字" }
                    val directory = File(cacheDir, "exports").apply { mkdirs() }
                    val file = File(directory, MarkdownExporter.suggestedFileName(snapshot))
                    file.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                        MarkdownExporter.write(snapshot, writer)
                    }
                    file
                }
            }.onSuccess { file ->
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "导出课堂 Markdown"))
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Markdown 导出失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun deleteLesson(lesson: LessonSummary) {
        lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) {
                ClassCueApplication.historyStore.deleteLesson(lesson)
            }
            Toast.makeText(
                this@MainActivity,
                if (removed > 0) "本节课文字记录已删除" else "没有找到可删除的文字记录",
                Toast.LENGTH_SHORT
            ).show()
        }
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
