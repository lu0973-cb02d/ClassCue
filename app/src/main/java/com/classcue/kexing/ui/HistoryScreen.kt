@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.TranscriptRecord
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.classcue.kexing.model.LessonArchive
import com.classcue.kexing.model.SearchHit
import kotlinx.coroutines.delay

internal enum class HistoryView { LESSONS, EVENTS, TRANSCRIPT, RECORDINGS }

@Composable
internal fun HistoryScreen(
    modifier: Modifier,
    events: List<HistoryEvent>,
    transcripts: List<TranscriptRecord>,
    lessonSummaries: List<LessonSummary>,
    recordings: List<RecordingInfo>,
    currentCourseName: String,
    onDeleteHistory: () -> Unit,
    onRetryEvent: (HistoryEvent) -> Unit,
    onPlayRecording: (File) -> Unit,
    onShareRecording: (File) -> Unit,
    onDeleteRecording: (File) -> Unit,
    onExportAllMarkdown: () -> Unit,
    onExportCourseMarkdown: (String) -> Unit,
    onExportLessonMarkdown: (LessonSummary) -> Unit,
    onDeleteLesson: (LessonSummary) -> Unit,
    aiEnabled: Boolean,
    loadLesson: suspend (LessonSummary) -> LessonArchive?,
    searchHistory: suspend (String) -> List<SearchHit>,
    initialQuery: String = ""
) {
    var view by rememberSaveable { mutableStateOf(HistoryView.LESSONS) }
    var openedLesson by remember { mutableStateOf<OpenedLesson?>(null) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchResults by remember { mutableStateOf<List<SearchHit>?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            searchResults = null
        } else {
            delay(300)
            searchResults = searchHistory(query.trim())
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var lessonToDelete by remember { mutableStateOf<LessonSummary?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingInfo?>(null) }
    val archivedCourses = remember(lessonSummaries) {
        lessonSummaries.map { it.courseName.trim() }.distinct().sortedWith(
            compareBy<String> { it.isBlank() }.thenBy { it }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("清空文字记录？") },
            text = { Text("此操作会清空课堂转写、点名/提问记录和 AI 回答，不会删除原始录音。") },
            confirmButton = { TextButton(onClick = { onDeleteHistory(); confirmDelete = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    lessonToDelete?.let { lesson ->
        AlertDialog(
            onDismissRequest = { lessonToDelete = null },
            title = { Text("删除本节课文字？") },
            text = { Text("将删除本节课的转写、提醒和 AI 回答；对应 WAV 录音不会同时删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLesson(lesson)
                    if (openedLesson?.summary == lesson) openedLesson = null
                    lessonToDelete = null
                }) { Text("删除文字") }
            },
            dismissButton = { TextButton(onClick = { lessonToDelete = null }) { Text("取消") } }
        )
    }
    recordingToDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("永久删除这段录音？") },
            text = { Text("将删除 ${formatDate(recording.startedAt)} 的 WAV 文件，文字记录仍会保留。此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { onDeleteRecording(recording.file); recordingToDelete = null }) { Text("删除录音") }
            },
            dismissButton = { TextButton(onClick = { recordingToDelete = null }) { Text("取消") } }
        )
    }
    openedLesson?.let { opened ->
        Column(modifier.fillMaxSize()) {
            LessonDetailScreen(
                summary = opened.summary,
                focusAt = opened.focusAt,
                recording = recordings.firstOrNull { it.matchesLesson(opened.summary.recordingName) },
                loadLesson = loadLesson,
                aiEnabled = aiEnabled,
                onBack = { openedLesson = null },
                onExport = { onExportLessonMarkdown(opened.summary) },
                onDelete = { lessonToDelete = opened.summary },
                onRetryEvent = onRetryEvent
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("搜索课堂文字、提醒和 AI 回答") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "清除") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val results = searchResults
        if (query.isNotBlank()) {
            if (results == null) {
                item { Text("正在搜索…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (results.isEmpty()) {
                item { EmptyCard("没有找到“${query.trim()}”", "搜索范围包括所有课次的转写、提醒原话和 AI 回答。") }
            } else {
                item {
                    Text(
                        if (results.size >= SEARCH_LIMIT) "显示最近 $SEARCH_LIMIT 条结果" else "共 ${results.size} 条结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(results, key = { "${it.kind}${it.id}" }) { hit ->
                    SearchHitCard(hit, query.trim()) {
                        lessonSummaries.firstOrNull { it.contains(hit) }?.let { summary ->
                            openedLesson = OpenedLesson(summary, hit.createdAt)
                        }
                    }
                }
            }
            return@LazyColumn
        }
        item {
            SectionCard("Markdown 导出", "按课次、课程或全部学期归档") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onExportCourseMarkdown(currentCourseName) },
                        enabled = currentCourseName.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("当前课程")
                    }
                    OutlinedButton(onClick = onExportAllMarkdown, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null)
                        Spacer(Modifier.width(6.dp))
                        Text("全部课程")
                    }
                }
                if (currentCourseName.isBlank()) {
                    Text("先在听课页填写课程名称，才能按整门课程导出。", style = MaterialTheme.typography.bodySmall)
                }
                if (archivedCourses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("已归档课程", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        archivedCourses.forEach { courseName ->
                            AssistChip(
                                onClick = { onExportCourseMarkdown(courseName) },
                                label = { Text(courseName.ifEmpty { "未命名课程" }) },
                                leadingIcon = { Icon(Icons.Rounded.Save, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == HistoryView.LESSONS, onClick = { view = HistoryView.LESSONS }, label = { Text("课次 ${lessonSummaries.size}") })
                FilterChip(selected = view == HistoryView.EVENTS, onClick = { view = HistoryView.EVENTS }, label = { Text("提醒 ${events.size}") })
                FilterChip(selected = view == HistoryView.TRANSCRIPT, onClick = { view = HistoryView.TRANSCRIPT }, label = { Text("近期转写 ${transcripts.size}") })
                FilterChip(selected = view == HistoryView.RECORDINGS, onClick = { view = HistoryView.RECORDINGS }, label = { Text("录音 ${recordings.size}") })
            }
        }
        if (view == HistoryView.LESSONS) {
            item {
                InfoCard(Icons.Rounded.History, "按课次长期归档", "所有文字都会持续保存在本机；这里按课程和每次录音整理，可单独导出 Markdown 或手动删除。")
            }
            if (lessonSummaries.isEmpty()) item { EmptyCard("还没有课堂归档", "填写课程名称并开始听课后，会自动生成一节课的归档。") }
            items(lessonSummaries, key = { "${it.courseName}|${it.recordingName}|${it.startedAt}" }) { lesson ->
                LessonSummaryCard(
                    lesson = lesson,
                    onOpen = { openedLesson = OpenedLesson(lesson, null) },
                    onExport = { onExportLessonMarkdown(lesson) },
                    onDelete = { lessonToDelete = lesson }
                )
            }
        } else if (view == HistoryView.EVENTS) {
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
                InfoCard(Icons.Rounded.GraphicEq, "近期老师原话", "这里显示最近 2,000 条确认转写；更早的文字仍在“课次”归档和 Markdown 导出中，不会自动删除。")
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
                InfoCard(Icons.Rounded.Info, "录音长期保留", "录音不会自动到期；请留意本机占用，并手动导出或删除不再需要的文件。")
            }
            if (recordings.isEmpty()) item { EmptyCard("还没有课堂录音", "结束一次听课后，完整 WAV 文件会显示在这里。") }
            items(recordings, key = { it.file.absolutePath }) { recording ->
                RecordingCard(
                    recording = recording,
                    onPlay = onPlayRecording,
                    onShare = onShareRecording,
                    onDelete = { recordingToDelete = recording }
                )
            }
        }
    }
}

@Composable
internal fun TranscriptCard(record: TranscriptRecord) {
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
internal fun LessonSummaryCard(
    lesson: LessonSummary,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val courseLabel = lesson.courseName.trim().ifEmpty { "未命名课程" }
    val durationMs = (lesson.endedAt - lesson.startedAt).coerceAtLeast(0L)
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable(onClick = onOpen)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(courseLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatDate(lesson.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${lesson.transcriptCount} 条转写") },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp)) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "时长约 ${formatDuration(durationMs)} · ${lesson.eventCount} 条课堂提醒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("查看与回放")
                }
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("导出")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("删文字")
                }
            }
        }
    }
}

@Composable
internal fun EventCard(event: HistoryEvent, canRetry: Boolean, onRetry: () -> Unit) {
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
internal fun RecordingCard(
    recording: RecordingInfo,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDate(recording.startedAt), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatDuration(recording.estimatedDurationMs)} · ${formatBytes(recording.bytes)} · 手动管理",
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
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text("删除录音")
            }
        }
    }
}

internal const val SEARCH_LIMIT = 200

private data class OpenedLesson(val summary: LessonSummary, val focusAt: Long?)

/** Whether a recording file belongs to the lesson recorded as [recordingName] (any audio format). */
internal fun RecordingInfo.matchesLesson(recordingName: String?): Boolean {
    val base = recordingName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: return false
    return file.nameWithoutExtension == base
}

private fun LessonSummary.contains(hit: SearchHit): Boolean {
    if (courseName != hit.courseName) return false
    return if (hit.recordingName.isNullOrBlank()) {
        recordingName.isNullOrBlank() && startedAt / LEGACY_DAY_MS_UI == hit.createdAt / LEGACY_DAY_MS_UI
    } else {
        recordingName == hit.recordingName
    }
}

private const val LEGACY_DAY_MS_UI = 86_400_000L

@Composable
private fun SearchHitCard(hit: SearchHit, query: String, onOpen: () -> Unit) {
    val isAlert = hit.kind == SearchHit.Kind.EVENT
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isAlert) "提醒" else "转写",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isAlert) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    hit.courseName.ifBlank { "未命名课程" },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatDate(hit.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(highlight(snippet(hit.text, query), query), style = MaterialTheme.typography.bodyMedium)
            val answer = hit.answer
            if (isAlert && !answer.isNullOrBlank() && answer.contains(query, ignoreCase = true)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    highlight("AI：" + snippet(answer, query), query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Keeps the part of a long text around the first match so the hit is visible in the card. */
internal fun snippet(text: String, query: String, radius: Int = 40): String {
    val flat = text.replace('\n', ' ')
    val index = flat.indexOf(query, ignoreCase = true)
    if (index < 0 || flat.length <= radius * 2 + query.length) return flat
    val start = (index - radius).coerceAtLeast(0)
    val end = (index + query.length + radius).coerceAtMost(flat.length)
    return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
}

@Composable
private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isBlank()) return@buildAnnotatedString
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    var from = 0
    while (true) {
        val index = text.indexOf(query, from, ignoreCase = true)
        if (index < 0) break
        addStyle(style, index, index + query.length)
        from = index + query.length
    }
}
