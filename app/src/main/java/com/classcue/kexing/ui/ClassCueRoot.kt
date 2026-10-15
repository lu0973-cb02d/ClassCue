@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.classcue.kexing.engine.ModelDownloadState
import com.classcue.kexing.engine.OfflineModelPack
import com.classcue.kexing.engine.InterruptedSession
import com.classcue.kexing.engine.SessionPhase
import com.classcue.kexing.engine.SessionState
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.TranscriptRecord
import java.io.File

internal enum class Destination(val title: String) { LISTEN("听课"), HISTORY("历史记录"), SETTINGS("设置") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCueRoot(
    session: SessionState,
    settings: AppSettings,
    events: List<HistoryEvent>,
    transcripts: List<TranscriptRecord>,
    lessonSummaries: List<LessonSummary>,
    recordings: List<RecordingInfo>,
    modelStates: Map<OfflineModelPack, ModelDownloadState>,
    interruptedSession: InterruptedSession?,
    onDismissInterrupted: () -> Unit,
    apiKeys: Map<String, String>,
    notificationPermissionGranted: Boolean,
    alertChannelReady: Boolean,
    audioPermissionGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTestAlert: () -> Unit,
    wearEngineStatus: String,
    onTestWearEngine: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApiKeyChange: (String, String) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onDeleteHistory: () -> Unit,
    onRetryAi: (Long, String, List<String>) -> Unit,
    onDownloadModel: (OfflineModelPack) -> Unit,
    onPauseModelDownload: () -> Unit,
    onDeleteModel: (OfflineModelPack) -> Unit,
    onPlayRecording: (File) -> Unit,
    onShareRecording: (File) -> Unit,
    onDeleteRecording: (File) -> Unit,
    onExportAllMarkdown: () -> Unit,
    onExportCourseMarkdown: (String) -> Unit,
    onExportLessonMarkdown: (LessonSummary) -> Unit,
    onDeleteLesson: (LessonSummary) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(Destination.LISTEN) }
    var retryEvent by remember { mutableStateOf<HistoryEvent?>(null) }
    retryEvent?.let { event ->
        AiRetryDialog(
            event = event,
            settings = settings,
            onDismiss = { retryEvent = null },
            onSend = { question, profileIds ->
                onRetryAi(event.id, question, profileIds)
                retryEvent = null
            }
        )
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(destination.title, fontWeight = FontWeight.SemiBold)
                        if (destination == Destination.LISTEN && session.phase == SessionPhase.LISTENING) {
                            Text("录音与识别运行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == Destination.LISTEN,
                    onClick = { destination = Destination.LISTEN },
                    icon = { Icon(Icons.Rounded.GraphicEq, null) },
                    label = { Text("听课") },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
                NavigationBarItem(
                    selected = destination == Destination.HISTORY,
                    onClick = { destination = Destination.HISTORY },
                    icon = { Icon(Icons.Rounded.History, null) },
                    label = { Text("记录") }
                )
                NavigationBarItem(
                    selected = destination == Destination.SETTINGS,
                    onClick = { destination = Destination.SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        when (destination) {
            Destination.LISTEN -> ListenScreen(
                modifier = Modifier.padding(padding),
                session = session,
                settings = settings,
                hasAudioPermission = audioPermissionGranted,
                hasNotificationPermission = notificationPermissionGranted,
                onStart = onStart,
                onStop = onStop,
                onTestAlert = onTestAlert,
                onOpenSettings = { destination = Destination.SETTINGS },
                onUpdateSettings = onUpdateSettings,
                latestEvent = events.firstOrNull(),
                interruptedSession = interruptedSession,
                onDismissInterrupted = onDismissInterrupted,
                onRetryEvent = { retryEvent = it }
            )
            Destination.HISTORY -> HistoryScreen(
                modifier = Modifier.padding(padding),
                events = events,
                transcripts = transcripts,
                lessonSummaries = lessonSummaries,
                recordings = recordings,
                currentCourseName = settings.courseName,
                onDeleteHistory = onDeleteHistory,
                onRetryEvent = { retryEvent = it },
                onPlayRecording = onPlayRecording,
                onShareRecording = onShareRecording,
                onDeleteRecording = onDeleteRecording,
                onExportAllMarkdown = onExportAllMarkdown,
                onExportCourseMarkdown = onExportCourseMarkdown,
                onExportLessonMarkdown = onExportLessonMarkdown,
                onDeleteLesson = onDeleteLesson
            )
            Destination.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                settings = settings,
                recordings = recordings,
                modelStates = modelStates,
                apiKeys = apiKeys,
                hasAudioPermission = audioPermissionGranted,
                hasNotificationPermission = notificationPermissionGranted,
                alertChannelReady = alertChannelReady,
                onUpdateSettings = onUpdateSettings,
                onApiKeyChange = onApiKeyChange,
                onDeleteAiProfile = onDeleteAiProfile,
                onDownloadModel = onDownloadModel,
                onPauseModelDownload = onPauseModelDownload,
                onDeleteModel = onDeleteModel,
                modelDeletionEnabled = session.phase == SessionPhase.STOPPED,
                onTestAlert = onTestAlert,
                wearEngineStatus = wearEngineStatus,
                onTestWearEngine = onTestWearEngine,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                onOpenBatterySettings = onOpenBatterySettings
            )
        }
    }
}
