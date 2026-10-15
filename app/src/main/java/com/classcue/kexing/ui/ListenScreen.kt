@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.engine.InterruptedSession
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.TimetableResolver
import java.time.LocalDateTime
import com.classcue.kexing.engine.SessionPhase
import com.classcue.kexing.engine.SessionState
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.HistoryEvent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun ListenScreen(
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
    onRetryEvent: (HistoryEvent) -> Unit,
    interruptedSession: InterruptedSession?,
    onDismissInterrupted: () -> Unit,
    timetable: Timetable = Timetable(),
    confirmRequest: String? = null,
    onConsumeConfirmRequest: () -> Unit = {}
) {
    val isStopping = session.phase == SessionPhase.STOPPING
    val isActive = session.phase in setOf(
        SessionPhase.STARTING,
        SessionPhase.LISTENING,
        SessionPhase.ERROR,
        SessionPhase.STOPPING
    ) && session.startedAt != null
    var courseName by remember(settings.courseName) { mutableStateOf(settings.courseName) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalDateTime.now()
            delay(30_000)
        }
    }
    val suggestion = remember(timetable, clock) { TimetableResolver.suggestion(timetable, clock) }
    var confirming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmRequest, isActive) {
        if (confirmRequest != null) {
            if (!isActive) confirming = confirmRequest
            onConsumeConfirmRequest()
        }
    }
    // With a timetable, every lesson is confirmed against it before recording starts.
    val requestStart: () -> Unit = { if (timetable.slots.isNotEmpty()) confirming = "" else onStart() }
    confirming?.let { preselect ->
        CourseConfirmDialog(
            suggestion = TimetableResolver.suggestion(timetable, LocalDateTime.now()),
            typedCourse = settings.courseName,
            knownCourses = timetable.courseNames,
            preselect = preselect.ifBlank { null },
            onDismiss = { confirming = null },
            onConfirm = { chosen ->
                onUpdateSettings { it.copy(courseName = chosen.trim()) }
                confirming = null
                onStart()
            }
        )
    }
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
        if (interruptedSession != null && !isActive) {
            item { InterruptedSessionCard(interruptedSession, onDismissInterrupted) }
        }
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
            SectionCard("本节课程", if (settings.courseName.isBlank()) "未命名课程" else settings.courseName) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { value ->
                        courseName = value.take(60)
                        onUpdateSettings { it.copy(courseName = courseName) }
                    },
                    enabled = !isActive,
                    label = { Text("课程名称") },
                    supportingText = {
                        Text(if (isActive) "本节课已按开始时的课程名称归档" else "用于按课程整理历史并导出整学期 Markdown")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isActive && suggestion != null) {
                    val scheduled = suggestion.slot
                    if (scheduled.courseName != settings.courseName.trim()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "课程表：现在是《${scheduled.courseName}》${timeRange(scheduled)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onUpdateSettings { it.copy(courseName = scheduled.courseName) } }) {
                                Text("改用")
                            }
                        }
                    } else {
                        Text(
                            "与课程表一致 · ${timeRange(scheduled)}${scheduled.location.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            StatusCard(session, now)
        }
        item {
            Button(
                onClick = if (isActive) onStop else requestStart,
                enabled = !isStopping,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(if (isActive) Icons.Rounded.StopCircle else Icons.Rounded.Mic, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        isStopping -> "正在收尾，请稍候…"
                        isActive -> "结束本节课并保存"
                        else -> "开始听课"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
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
                body = "原始 WAV 录音和课堂文字仅保存在本机，默认长期保留；请在记录页查看占用并手动删除不再需要的内容。"
            )
        }
    }
}

@Composable
internal fun StatusCard(session: SessionState, now: Long) {
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
                    SessionPhase.STOPPING -> "正在收尾"
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
                    AssistChip(onClick = {}, label = { Text("原声 ${session.inputLevel}%") }, leadingIcon = { Icon(Icons.Rounded.Mic, null) })
                    if (session.appliedInputGainDb > 0.1) {
                        AssistChip(
                            onClick = {},
                            label = { Text("识别 ${session.enhancedInputLevel}% · +${session.appliedInputGainDb.roundToInt()} dB") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InterruptedSessionCard(session: InterruptedSession, onDismiss: () -> Unit) {
    val course = session.courseName.ifBlank { "未命名课程" }
    val started = if (session.startedAt > 0L) formatDate(session.startedAt) else "上次"
    val body = buildString {
        append("$started 开始的《$course》没有正常结束，可能是系统清理了后台或手机重启。")
        if (session.recoveredRecordingName != null) {
            append("已恢复约 ${formatDuration(session.recoveredDurationMs)} 的原始录音，")
        } else {
            append("没有找到可恢复的录音，")
        }
        append("中断前确认的课堂文字都已保存在记录页。课醒不会在后台自动重新开始录音。")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null)
                Spacer(Modifier.width(10.dp))
                Text("上节课意外中断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("知道了") }
        }
    }
}
