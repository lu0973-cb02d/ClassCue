@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classcue.kexing.engine.CueClassifier
import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.engine.ModelDownloadPhase
import com.classcue.kexing.engine.ModelDownloadState
import com.classcue.kexing.engine.OfflineModelPack
import com.classcue.kexing.engine.SessionPhase
import com.classcue.kexing.engine.SessionState
import com.classcue.kexing.engine.TextSelection
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.AiProfile
import com.classcue.kexing.model.AiTier
import com.classcue.kexing.model.DetectionMode
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.TranscriptRecord
import com.classcue.kexing.model.TranscriptionModelSize
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private enum class Destination(val title: String) { LISTEN("听课"), HISTORY("历史记录"), SETTINGS("设置") }
private enum class HistoryView { EVENTS, TRANSCRIPT, RECORDINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCueRoot(
    session: SessionState,
    settings: AppSettings,
    events: List<HistoryEvent>,
    transcripts: List<TranscriptRecord>,
    recordings: List<RecordingInfo>,
    modelStates: Map<OfflineModelPack, ModelDownloadState>,
    apiKeys: Map<String, String>,
    notificationPermissionGranted: Boolean,
    alertChannelReady: Boolean,
    audioPermissionGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTestAlert: () -> Unit,
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
                onRetryEvent = { retryEvent = it }
            )
            Destination.HISTORY -> HistoryScreen(
                modifier = Modifier.padding(padding),
                events = events,
                transcripts = transcripts,
                recordings = recordings,
                onDeleteHistory = onDeleteHistory,
                onRetryEvent = { retryEvent = it },
                onPlayRecording = onPlayRecording,
                onShareRecording = onShareRecording
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
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                onOpenBatterySettings = onOpenBatterySettings
            )
        }
    }
}

@Composable
private fun ListenScreen(
    modifier: Modifier,
    session: SessionState,
    settings: AppSettings,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTestAlert: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    latestEvent: HistoryEvent?,
    onRetryEvent: (HistoryEvent) -> Unit
) {
    val isActive = session.phase in setOf(SessionPhase.STARTING, SessionPhase.LISTENING, SessionPhase.ERROR) && session.startedAt != null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isActive) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasAudioPermission || !hasNotificationPermission) {
            item {
                InfoCard(
                    icon = Icons.Rounded.Warning,
                    title = "需要授权后才能可靠提醒",
                    body = "麦克风用于录音与转写；通知权限用于点名和提问横幅。点击开始时会一次申请。",
                    warning = true
                )
            }
        }
        if (settings.displayName.isBlank() && settings.studentId.isBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("先填写你的姓名或学号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("否则只能识别面向全班的一般提问，无法可靠判断老师是否在叫你。")
                        TextButton(onClick = onOpenSettings) { Text("去设置") }
                    }
                }
            }
        }
        item {
            StatusCard(session, now)
        }
        item {
            Button(
                onClick = if (isActive) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(if (isActive) Icons.Rounded.StopCircle else Icons.Rounded.Mic, null)
                Spacer(Modifier.width(10.dp))
                Text(if (isActive) "结束本节课并保存" else "开始听课", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            SectionCard(title = "识别灵敏度", subtitle = sensitivityLabel(settings.sensitivity)) {
                Slider(
                    value = settings.sensitivity.toFloat(),
                    onValueChange = { value -> onUpdateSettings { it.copy(sensitivity = value.roundToInt().coerceIn(1, 5)) } },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("少误报", style = MaterialTheme.typography.labelMedium)
                    Text("少漏报", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            SectionCard("实时转写", session.recognizerMode) {
                if (session.transcript.isEmpty() && session.partialText.isBlank()) {
                    val hint = when {
                        !isActive -> "开始后，这里显示最近的课堂文字。"
                        session.phase == SessionPhase.STARTING -> "正在准备离线模型，原始录音不会中断。"
                        session.inputLevel < 8 -> "当前环境较安静，请靠近手机说一句话测试。"
                        else -> "已收到声音，正在等待完整语句…"
                    }
                    Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        Column {
                            session.transcript.takeLast(5).forEach { line ->
                                Text(line.text, modifier = Modifier.padding(vertical = 5.dp))
                            }
                        }
                    }
                    AnimatedVisibility(session.partialText.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                session.partialText,
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Text(
                        "长按文字可选择并复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        session.lastCue?.let { cue ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = riskContainer(cue.risk))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.AddAlert, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(cueTitle(cue.type), fontWeight = FontWeight.SemiBold)
                            Text("${cue.reason} · 置信度 ${(cue.confidence * 100).roundToInt()}%")
                        }
                    }
                }
            }
        }
        latestEvent?.let { event ->
            item {
                EventCard(
                    event = event,
                    canRetry = settings.aiEnabled,
                    onRetry = { onRetryEvent(event) }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTestAlert, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text("测试提醒")
                }
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("详细设置")
                }
            }
        }
        item {
            InfoCard(
                icon = Icons.Rounded.Security,
                title = "录音保存规则",
                body = "原始 WAV 录音仅保存在本机应用目录，停止后可播放或导出；超过 3 天自动永久清理。"
            )
        }
    }
}

@Composable
private fun StatusCard(session: SessionState, now: Long) {
    val active = session.startedAt != null && session.phase != SessionPhase.STOPPED
    val container = when (session.phase) {
        SessionPhase.LISTENING -> MaterialTheme.colorScheme.primaryContainer
        SessionPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (active) Icons.Rounded.GraphicEq else Icons.Rounded.PauseCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (session.phase == SessionPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (session.phase) {
                    SessionPhase.STOPPED -> "课醒已就绪"
                    SessionPhase.STARTING -> "正在启动"
                    SessionPhase.LISTENING -> "正在听课"
                    SessionPhase.ERROR -> "部分功能需要恢复"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(session.statusText, style = MaterialTheme.typography.bodyMedium)
            if (active) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(formatDuration(now - requireNotNull(session.startedAt))) }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                    AssistChip(onClick = {}, label = { Text(formatBytes(session.recordingBytes)) }, leadingIcon = { Icon(Icons.Rounded.Save, null) })
                    AssistChip(onClick = {}, label = { Text("声音 ${session.inputLevel}%") }, leadingIcon = { Icon(Icons.Rounded.Mic, null) })
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    events: List<HistoryEvent>,
    transcripts: List<TranscriptRecord>,
    recordings: List<RecordingInfo>,
    onDeleteHistory: () -> Unit,
    onRetryEvent: (HistoryEvent) -> Unit,
    onPlayRecording: (File) -> Unit,
    onShareRecording: (File) -> Unit
) {
    var view by rememberSaveable { mutableStateOf(HistoryView.EVENTS) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("清空文字记录？") },
            text = { Text("此操作会清空课堂转写、点名/提问记录和 AI 回答，不会删除仍在 3 天保留期内的原始录音。") },
            confirmButton = { TextButton(onClick = { onDeleteHistory(); confirmDelete = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == HistoryView.EVENTS, onClick = { view = HistoryView.EVENTS }, label = { Text("提醒 ${events.size}") })
                FilterChip(selected = view == HistoryView.TRANSCRIPT, onClick = { view = HistoryView.TRANSCRIPT }, label = { Text("课堂转写 ${transcripts.size}") })
                FilterChip(selected = view == HistoryView.RECORDINGS, onClick = { view = HistoryView.RECORDINGS }, label = { Text("录音 ${recordings.size}") })
            }
        }
        if (view == HistoryView.EVENTS) {
            if (events.isEmpty()) item { EmptyCard("还没有提醒记录", "检测到点名或提问后会保存在这里。") }
            items(events, key = { it.id }) { event ->
                EventCard(event = event, canRetry = true, onRetry = { onRetryEvent(event) })
            }
            if (events.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("清空文字记录") }
                }
            }
        } else if (view == HistoryView.TRANSCRIPT) {
            item {
                InfoCard(Icons.Rounded.GraphicEq, "老师原话", "这里逐句显示系统确认后的转写，不经过 AI 改写；点名通知会引用同样的文字窗口。")
            }
            if (transcripts.isEmpty()) item { EmptyCard("还没有课堂转写", "开始听课后，所有确认文字都会按时间保存在这里。") }
            items(transcripts, key = { it.id }) { transcript -> TranscriptCard(transcript) }
            if (transcripts.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("清空文字记录") }
                }
            }
        } else {
            item {
                InfoCard(Icons.Rounded.Info, "自动滚动保留 3 天", "到期录音会自动永久清理。需要长期保留时，请在到期前点“导出”。")
            }
            if (recordings.isEmpty()) item { EmptyCard("还没有课堂录音", "结束一次听课后，完整 WAV 文件会显示在这里。") }
            items(recordings, key = { it.file.absolutePath }) { recording ->
                RecordingCard(recording, onPlayRecording, onShareRecording)
            }
        }
    }
}

@Composable
private fun TranscriptCard(record: TranscriptRecord) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("已确认", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(formatDate(record.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Text(record.text, style = MaterialTheme.typography.bodyLarge) }
            if (!record.recordingName.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("已关联本节原始录音", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EventCard(event: HistoryEvent, canRetry: Boolean, onRetry: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cueTitle(event.type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatDate(event.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Text(event.transcript) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskLabel(event.risk)
                Text("置信度 ${(event.confidence * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            if (!event.answer.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("建议回答", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                SelectionContainer { Text(event.answer) }
            }
            event.recordingOffsetMs?.let {
                Spacer(Modifier.height(10.dp))
                Text("录音位置 ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canRetry) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("圈选题目并重发")
                }
            }
        }
    }
}

@Composable
private fun AiRetryDialog(
    event: HistoryEvent,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSend: (String, List<String>) -> Unit
) {
    val tokens = remember(event.id, event.transcript) { TextSelection.tokenize(event.transcript) }
    val availableProfiles = settings.aiProfiles.filter {
        it.enabled && it.baseUrl.isNotBlank() && it.model.isNotBlank()
    }
    var selectedIndices by remember(event.id) { mutableStateOf(emptySet<Int>()) }
    val tokenBounds = remember(event.id, tokens) { mutableMapOf<Int, Rect>() }
    var selectedProfiles by remember(event.id, availableProfiles.map { it.id }) {
        mutableStateOf(availableProfiles.map { it.id }.toSet())
    }
    val question = TextSelection.selectedText(event.transcript, tokens, selectedIndices)
    fun hitToken(position: Offset): Int? = tokenBounds.entries
        .firstOrNull { (_, bounds) -> bounds.contains(position) }
        ?.key
    fun hitTokensBetween(start: Offset, end: Offset): Set<Int> {
        val distance = (end - start).getDistance()
        val steps = (distance / 8f).toInt().coerceAtLeast(1)
        return (0..steps).mapNotNull { step ->
            val fraction = step.toFloat() / steps
            hitToken(start + (end - start) * fraction)
        }.toSet()
    }
    fun setTokenSelected(index: Int, selected: Boolean) {
        selectedIndices = if (selected) selectedIndices + index else selectedIndices - index
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("圈选题目并重发") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "点一下可单独选择或取消；按住并滑过多个字可批量选择。可以分多次选取不连续内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(tokens) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val startPosition = down.position
                                var dragging = false
                                var addMode = true
                                var previousPosition = startPosition
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!dragging) {
                                            hitToken(startPosition)?.let { index ->
                                                setTokenSelected(index, index !in selectedIndices)
                                            }
                                        }
                                        break
                                    }
                                    if (!dragging &&
                                        (change.position - startPosition).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        dragging = true
                                        hitToken(startPosition)?.let { index ->
                                            addMode = index !in selectedIndices
                                            setTokenSelected(index, addMode)
                                        }
                                    }
                                    if (dragging) {
                                        hitTokensBetween(previousPosition, change.position).forEach { index ->
                                            setTokenSelected(index, addMode)
                                        }
                                        previousPosition = change.position
                                        change.consume()
                                    }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tokens.forEachIndexed { index, token ->
                        val selected = index in selectedIndices
                        Surface(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                tokenBounds[index] = coordinates.boundsInParent()
                            },
                            shape = RoundedCornerShape(7.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Text(
                                token.text,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { selectedIndices = tokens.indices.toSet() }
                    ) { Text("全选") }
                    TextButton(onClick = { selectedIndices = emptySet() }) { Text("清空") }
                }
                if (question.isNotBlank()) {
                    Text("已选 ${selectedIndices.size} 个字块，将发送：", fontWeight = FontWeight.SemiBold)
                    Text(question, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("发送到模型", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (availableProfiles.isEmpty()) {
                    Text("请先在设置中填写并启用模型、BaseURL 和 Key。", color = MaterialTheme.colorScheme.error)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableProfiles.forEach { profile ->
                            FilterChip(
                                selected = profile.id in selectedProfiles,
                                onClick = {
                                    selectedProfiles = if (profile.id in selectedProfiles) {
                                        selectedProfiles - profile.id
                                    } else {
                                        selectedProfiles + profile.id
                                    }
                                },
                                label = { Text(profile.model) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = question.isNotBlank() && selectedProfiles.isNotEmpty(),
                onClick = { onSend(question, selectedProfiles.toList()) }
            ) { Text("重发给 AI") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RecordingCard(
    recording: RecordingInfo,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDate(recording.startedAt), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatDuration(recording.estimatedDurationMs)} · ${formatBytes(recording.bytes)} · ${expiryText(recording.expiresAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onPlay(recording.file) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("播放")
                }
                OutlinedButton(onClick = { onShare(recording.file) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("导出")
                }
            }
        }
    }
}

@Composable
private fun ModelPackCard(
    title: String,
    subtitle: String,
    state: ModelDownloadState,
    deletionEnabled: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    val active = state.phase in setOf(
        ModelDownloadPhase.QUEUED,
        ModelDownloadPhase.DOWNLOADING,
        ModelDownloadPhase.INSTALLING
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    modelPhaseLabel(state.phase),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.phase == ModelDownloadPhase.READY) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (state.phase == ModelDownloadPhase.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(state.progress * 100).toInt()}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)} · ${formatDownloadSpeed(state.bytesPerSecond)}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state.phase in setOf(ModelDownloadPhase.QUEUED, ModelDownloadPhase.INSTALLING)) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            } else if (state.message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == ModelDownloadPhase.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                active -> OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PauseCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("暂停后台下载")
                }
                state.phase == ModelDownloadPhase.READY -> OutlinedButton(
                    onClick = onDelete,
                    enabled = deletionEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (deletionEnabled) "删除模型" else "听课中不能删除")
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.downloadedBytes > 0L) "继续下载" else "后台下载")
                    }
                    if (state.downloadedBytes > 0L) {
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = deletionEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清除")
                        }
                    }
                }
            }
        }
    }
}

private fun modelPhaseLabel(phase: ModelDownloadPhase): String = when (phase) {
    ModelDownloadPhase.NOT_DOWNLOADED -> "未下载"
    ModelDownloadPhase.QUEUED -> "排队中"
    ModelDownloadPhase.DOWNLOADING -> "下载中"
    ModelDownloadPhase.INSTALLING -> "安装中"
    ModelDownloadPhase.READY -> "已安装"
    ModelDownloadPhase.PAUSED -> "已暂停"
    ModelDownloadPhase.FAILED -> "失败"
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "计算速度中"
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / 1024.0 / 1024.0)
    else -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    recordings: List<RecordingInfo>,
    modelStates: Map<OfflineModelPack, ModelDownloadState>,
    apiKeys: Map<String, String>,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    alertChannelReady: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApiKeyChange: (String, String) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onDownloadModel: (OfflineModelPack) -> Unit,
    onPauseModelDownload: () -> Unit,
    onDeleteModel: (OfflineModelPack) -> Unit,
    modelDeletionEnabled: Boolean,
    onTestAlert: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var name by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var studentId by remember(settings.studentId) { mutableStateOf(settings.studentId) }
    var aliases by remember(settings.aliases) { mutableStateOf(settings.aliases.joinToString("，")) }
    var keywords by remember(settings.customKeywords) { mutableStateOf(settings.customKeywords.joinToString("，")) }
    var revealedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val totalBytes = recordings.sumOf { it.bytes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("身份识别", "只保存在本机") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value; onUpdateSettings { it.copy(displayName = value.trim()) } },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { value -> studentId = value; onUpdateSettings { it.copy(studentId = value.trim()) } },
                    label = { Text("学号（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { value ->
                        aliases = value
                        onUpdateSettings { it.copy(aliases = splitTerms(value)) }
                    },
                    label = { Text("我可能被叫到的称呼（可填多个）") },
                    supportingText = { Text("在同一个输入框中用逗号分隔，例如：小明，李同学，班长，课代表") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SectionCard("检测范围", "控制哪些课堂话语会提醒") {
                Text(
                    "已内置 ${CueClassifier.builtInCueCount()} 条中国课堂点名、提问、作答和任务表达；下方词语用于补充你所在课程的特殊说法。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DetectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.detectionMode == mode,
                        onClick = { onUpdateSettings { it.copy(detectionMode = mode) } },
                        label = { Text(detectionModeLabel(mode)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("灵敏度：${settings.sensitivity} · ${sensitivityLabel(settings.sensitivity)}", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.sensitivity.toFloat(),
                    onValueChange = { value -> onUpdateSettings { it.copy(sensitivity = value.roundToInt().coerceIn(1, 5)) } },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Text("点名前回溯：${settings.contextBeforeSeconds} 秒", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.contextBeforeSeconds.toFloat(),
                    onValueChange = { value ->
                        val snapped = (value / 15f).roundToInt() * 15
                        onUpdateSettings { it.copy(contextBeforeSeconds = snapped.coerceIn(30, 120)) }
                    },
                    valueRange = 30f..120f,
                    steps = 5
                )
                Text("点名后继续捕获：${settings.followupWindowSeconds} 秒", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.followupWindowSeconds.toFloat(),
                    onValueChange = { value ->
                        val snapped = (value / 15f).roundToInt() * 15
                        onUpdateSettings { it.copy(followupWindowSeconds = snapped.coerceIn(30, 120)) }
                    },
                    valueRange = 30f..120f,
                    steps = 5
                )
                Text(
                    "先问后点会回带前文；先点后问会继续捕获后文。宁可多带内容，不强行判断题目归属。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { value -> keywords = value; onUpdateSettings { it.copy(customKeywords = splitTerms(value)) } },
                    label = { Text("自定义强提醒词") },
                    supportingText = { Text("识别到任一词就弹通知，用逗号分隔；课程高频普通术语不建议填写") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SectionCard("语音转写", "荣耀兼容模式 · 离线中文") {
                val mediumState = modelStates[OfflineModelPack.MEDIUM]
                    ?: ModelDownloadState(OfflineModelPack.MEDIUM, ModelDownloadPhase.NOT_DOWNLOADED)
                val largeState = modelStates[OfflineModelPack.LARGE]
                    ?: ModelDownloadState(OfflineModelPack.LARGE, ModelDownloadPhase.NOT_DOWNLOADED)
                val mediumReady = mediumState.phase == ModelDownloadPhase.READY
                val largeReady = mediumReady && largeState.phase == ModelDownloadPhase.READY
                Text("识别档位", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TranscriptionModelSize.entries.forEach { size ->
                        val label = when (size) {
                            TranscriptionModelSize.SMALL -> "小 · 快速"
                            TranscriptionModelSize.MEDIUM -> "中 · 默认"
                            TranscriptionModelSize.LARGE -> "大 · 两遍"
                        }
                        FilterChip(
                            selected = settings.transcriptionModelSize == size,
                            onClick = { onUpdateSettings { it.copy(transcriptionModelSize = size) } },
                            enabled = when (size) {
                                TranscriptionModelSize.SMALL -> true
                                TranscriptionModelSize.MEDIUM -> mediumReady
                                TranscriptionModelSize.LARGE -> largeReady
                            },
                            label = { Text(label) }
                        )
                    }
                }
                val modelDescription = when (settings.transcriptionModelSize) {
                    TranscriptionModelSize.SMALL -> "约 42 MB，内置 Vosk；启动最快、耗电最低，准确性一般。"
                    TranscriptionModelSize.MEDIUM -> "下载约 127 MB（安装约 160 MB），大型流式 Zipformer；边说边出字，适合较新的 arm64 Android 设备日常听课。"
                    TranscriptionModelSize.LARGE -> "中档流式 + 额外下载约 287 MB 高精度 CTC；先快速提醒，停顿后精修最终转写。"
                }
                Text(modelDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    "小档随 APK 安装。中/大档改为手动下载：离开课醒或锁屏后仍会在后台继续，并支持暂停和断点续传。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                ModelPackCard(
                    title = "中档流式模型",
                    subtitle = "约 127 MB 下载 · 约 160 MB 安装",
                    state = mediumState,
                    deletionEnabled = modelDeletionEnabled,
                    onDownload = { onDownloadModel(OfflineModelPack.MEDIUM) },
                    onPause = onPauseModelDownload,
                    onDelete = { onDeleteModel(OfflineModelPack.MEDIUM) }
                )
                Spacer(Modifier.height(10.dp))
                ModelPackCard(
                    title = "大档精修模型",
                    subtitle = "额外约 287 MB；缺少中档时会先自动排队下载中档",
                    state = largeState,
                    deletionEnabled = modelDeletionEnabled,
                    onDownload = { onDownloadModel(OfflineModelPack.LARGE) },
                    onPause = onPauseModelDownload,
                    onDelete = { onDeleteModel(OfflineModelPack.LARGE) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "未下载完成时不能选择对应档位；若模型文件异常，本节课会自动使用小档。切换档位在下一节课生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text("录音始终以 16 kHz 单声道 WAV 保存，优先锁定手机内置麦克风并使用语音识别优化源；不会启用媒体播放捕获，戴耳机播放的音乐不会直接进入转写。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("通知与震动", "仅手机提醒") {
                if (!alertChannelReady) {
                    InfoCard(
                        Icons.Rounded.Warning,
                        "横幅通知可能被系统关闭",
                        "请打开课堂提醒通道，并在 MagicOS 中允许横幅、锁屏和状态栏显示。",
                        warning = true
                    )
                    Spacer(Modifier.height(10.dp))
                }
                SettingSwitchRow(
                    title = "手机震动",
                    subtitle = "点名或提问时由手机震动提醒",
                    checked = settings.phoneVibrationEnabled,
                    onChecked = { value ->
                        onUpdateSettings { it.copy(phoneVibrationEnabled = value) }
                    }
                )
                AnimatedVisibility(settings.phoneVibrationEnabled) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("震动力度：${settings.vibrationStrength} / 5", fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.vibrationStrength.toFloat(),
                            onValueChange = { value -> onUpdateSettings { it.copy(vibrationStrength = value.roundToInt().coerceIn(1, 5)) } },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onTestAlert, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text("测试手机提醒")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("检查课堂提醒横幅设置")
                }
            }
        }
        item {
            SectionCard("AI 回答", "兼容 OpenAI 风格的 Chat Completions 接口") {
                SettingSwitchRow(
                    title = "启用 AI 回答",
                    subtitle = "仅在检测到问题后发送文字上下文，不上传原始录音",
                    checked = settings.aiEnabled,
                    onChecked = { value -> onUpdateSettings { it.copy(aiEnabled = value) } }
                )
                AnimatedVisibility(settings.aiEnabled) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "自动生成回答",
                            subtitle = "检测到问题后自动首发；之后仍可圈选原话重发",
                            checked = settings.autoAnswer,
                            onChecked = { value -> onUpdateSettings { it.copy(autoAnswer = value) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请求会自动附带课堂场景规则和语音识别局限：极速模型先给可直接开口的短答，强力模型再补依据与步骤。当前提醒与历史记录仍可圈选题目后重发。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        settings.aiProfiles.forEach { profile ->
                            AiProfileEditor(
                                profile = profile,
                                apiKey = apiKeys[profile.id].orEmpty(),
                                revealKey = revealedProfileId == profile.id,
                                onToggleReveal = {
                                    revealedProfileId = if (revealedProfileId == profile.id) null else profile.id
                                },
                                onUpdate = { updated ->
                                    onUpdateSettings { current ->
                                        current.copy(
                                            aiProfiles = current.aiProfiles.map {
                                                if (it.id == updated.id) updated else it
                                            }
                                        )
                                    }
                                },
                                onApiKeyChange = { value -> onApiKeyChange(profile.id, value) },
                                onDelete = {
                                    onDeleteAiProfile(profile.id)
                                    onUpdateSettings { current ->
                                        current.copy(aiProfiles = current.aiProfiles.filterNot { it.id == profile.id })
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdateSettings { current ->
                                    val tier = if (current.aiProfiles.none { it.tier == AiTier.STRONG }) {
                                        AiTier.STRONG
                                    } else AiTier.FAST
                                    current.copy(
                                        aiProfiles = current.aiProfiles + AiProfile(
                                            id = UUID.randomUUID().toString(),
                                            tier = tier,
                                            baseUrl = "",
                                            model = "",
                                            timeoutSeconds = if (tier == AiTier.FAST) 10 else 35
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("＋ 添加模型") }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = settings.aiSystemPrompt,
                            onValueChange = { value -> onUpdateSettings { it.copy(aiSystemPrompt = value) } },
                            label = { Text("补充回答偏好") },
                            supportingText = { Text("内置的课堂场景、转写纠错、防编造和极速/强力分工规则始终保留") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            SectionCard("后台驻留", "上课时保持录音与识别") {
                InfoRow(Icons.Rounded.BatterySaver, "应用使用前台麦克风服务和 CPU 唤醒锁；通知栏会一直显示“课醒正在听课”。")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenLiveUpdateSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("尝试启用灵动胶囊 / 实时状态")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "课醒会按 Android 实时通知规范请求显示顶部状态；MagicOS 是否呈现为灵动胶囊仍由系统决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开电池优化设置")
                }
                Spacer(Modifier.height(6.dp))
                Text("荣耀 MagicOS 建议把课醒设为“允许后台活动/手动管理”，否则锁屏较久后仍可能被系统结束。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("权限与存储", "当前录音 ${recordings.size} 个，共 ${formatBytes(totalBytes)}") {
                PermissionRow("麦克风", hasAudioPermission)
                PermissionRow("通知", hasNotificationPermission)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("打开系统通知设置") }
                Spacer(Modifier.height(8.dp))
                Text("原始录音保存满 3 天后自动永久删除；导出的副本不受此规则影响。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            InfoCard(
                Icons.Rounded.Security,
                "使用边界",
                "本应用用于课堂参与辅助和避免漏听点名，不应在考试、测验或明确禁止电子设备的场景中使用。录音前请遵守学校和课堂规则。"
            )
        }
    }
}

@Composable
private fun AiProfileEditor(
    profile: AiProfile,
    apiKey: String,
    revealKey: Boolean,
    onToggleReveal: () -> Unit,
    onUpdate: (AiProfile) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.model.ifBlank { "未填写模型名" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (profile.tier == AiTier.FAST) "极速模型" else "强力模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = { onUpdate(profile.copy(enabled = it)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = profile.tier == AiTier.FAST,
                    onClick = {
                        onUpdate(profile.copy(tier = AiTier.FAST, timeoutSeconds = 10))
                    },
                    label = { Text("极速") }
                )
                FilterChip(
                    selected = profile.tier == AiTier.STRONG,
                    onClick = {
                        onUpdate(profile.copy(tier = AiTier.STRONG, timeoutSeconds = 35))
                    },
                    label = { Text("强力") }
                )
            }
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { onUpdate(profile.copy(baseUrl = it)) },
                label = { Text("BaseURL") },
                supportingText = { Text("必须使用 HTTPS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = profile.model,
                onValueChange = { onUpdate(profile.copy(model = it)) },
                label = { Text("模型名（同时作为显示名称）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                supportingText = { Text("系统密钥库加密，仅保存在本机") },
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = onToggleReveal) { Text(if (revealKey) "隐藏" else "显示") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("超时：${profile.timeoutSeconds} 秒", fontWeight = FontWeight.Medium)
            Slider(
                value = profile.timeoutSeconds.toFloat(),
                onValueChange = { value ->
                    val timeout = (value / 5f).roundToInt() * 5
                    onUpdate(profile.copy(timeoutSeconds = timeout.coerceIn(5, 60)))
                },
                valueRange = 5f..60f,
                steps = 10
            )
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("删除此模型", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(if (granted) "已允许" else "未允许", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, warning: Boolean = false) {
    val color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.History, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RiskLabel(risk: RiskLevel) {
    val text = when (risk) { RiskLevel.HIGH -> "高优先级"; RiskLevel.MEDIUM -> "中优先级"; RiskLevel.LOW -> "一般" }
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun riskContainer(risk: RiskLevel): Color = when (risk) {
    RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
    RiskLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
}

private fun cueTitle(type: CueType): String = when (type) {
    CueType.ROLL_CALL -> "可能正在点名"
    CueType.DIRECT_QUESTION -> "老师可能在问你"
    CueType.GENERAL_QUESTION -> "老师提出了问题"
    CueType.NONE -> "课堂记录"
}

private fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.ALL -> "点名、问我、面向全班的问题"
    DetectionMode.NAMED_ONLY -> "只提醒点名和明确问我"
    DetectionMode.ROLL_CALL_ONLY -> "只提醒点名"
}

private fun sensitivityLabel(value: Int): String = when (value) {
    1 -> "最稳健，误报最少"
    2 -> "偏稳健"
    3 -> "平衡（推荐）"
    4 -> "偏灵敏"
    else -> "最灵敏，可能增加误报"
}

private fun splitTerms(value: String): List<String> = value
    .split(',', '，', ';', '；', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
    .take(100)

private fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDate(time: Long): String = SimpleDateFormat("MM月dd日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(time))

private fun expiryText(expiresAt: Long): String {
    val remaining = expiresAt - System.currentTimeMillis()
    if (remaining <= 0) return "即将清理"
    val hours = remaining / 3_600_000L
    return if (hours >= 24) "${hours / 24}天后清理" else "${hours.coerceAtLeast(1)}小时后清理"
}
