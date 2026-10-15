@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonArchive
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.model.PlaybackPosition
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.TranscriptRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface TimelineItem {
    val createdAt: Long
    val key: String

    data class Line(val record: TranscriptRecord) : TimelineItem {
        override val createdAt get() = record.createdAt
        override val key get() = "t${record.id}"
    }

    data class Alert(val event: HistoryEvent) : TimelineItem {
        override val createdAt get() = event.createdAt
        override val key get() = "e${event.id}"
    }
}

/** One lesson as a timeline of transcript lines and alerts, with the recording beside it. */
@Composable
internal fun LessonDetailScreen(
    summary: LessonSummary,
    focusAt: Long?,
    recording: RecordingInfo?,
    loadLesson: suspend (LessonSummary) -> LessonArchive?,
    aiEnabled: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onRetryEvent: (HistoryEvent) -> Unit
) {
    BackHandler(onBack = onBack)
    val load by produceState(LessonLoad(null, done = false), summary) {
        value = LessonLoad(loadLesson(summary), done = true)
    }
    val lesson = load.lesson
    val loaded = load.done
    val items = remember(lesson) {
        val archive = lesson ?: return@remember emptyList()
        (archive.transcripts.map { TimelineItem.Line(it) } + archive.events.map { TimelineItem.Alert(it) })
            .sortedWith(compareBy<TimelineItem> { it.createdAt }.thenBy { it is TimelineItem.Alert })
    }
    val player = rememberLessonPlayer(recording?.file)
    val recordingStart = recording?.startedAt ?: PlaybackPosition.recordingStartedAt(summary.recordingName)
    val listState = rememberLazyListState()
    LaunchedEffect(items, focusAt) {
        if (focusAt != null && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.createdAt >= focusAt }.takeIf { it >= 0 } ?: items.lastIndex
            listState.scrollToItem(index + HEADER_ITEMS)
        }
    }

    fun play(item: TimelineItem) {
        val start = recordingStart ?: return
        val text = when (item) {
            is TimelineItem.Line -> item.record.text
            is TimelineItem.Alert -> item.event.transcript
        }
        player?.seekTo(PlaybackPosition.estimateStartMs(item.createdAt, start, text, player.durationMs))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    summary.courseName.ifBlank { "未命名课程" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${formatDate(summary.startedAt)} · ${summary.transcriptCount} 条转写 · ${summary.eventCount} 条提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (player != null) {
            PlayerBar(player)
        } else {
            Text(
                "这节课没有找到对应的录音文件，只能查看文字。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("导出 Markdown")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("删文字")
                    }
                }
            }
            item {
                if (!loaded) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (items.isEmpty()) {
                    EmptyCard("这节课没有文字记录", "可能已被删除，或录音时没有识别到完整语句。")
                } else if (player != null && recordingStart != null) {
                    Text(
                        "点任意一句，从它附近开始播放（位置按识别时间估算，会提前几秒）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(items, key = { it.key }) { item ->
                val offset = recordingStart?.let { item.createdAt - it }
                when (item) {
                    is TimelineItem.Line -> TimelineLine(
                        record = item.record,
                        offsetMs = offset,
                        highlighted = focusAt != null && item.createdAt == focusAt,
                        onClick = { play(item) }
                    )
                    is TimelineItem.Alert -> Column(Modifier.clickable { play(item) }) {
                        EventCard(event = item.event, canRetry = aiEnabled, onRetry = { onRetryEvent(item.event) })
                    }
                }
            }
        }
    }
}

private const val HEADER_ITEMS = 2

private data class LessonLoad(val lesson: LessonArchive?, val done: Boolean)

@Composable
private fun PlayerBar(player: LessonPlayer) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        // Not secondaryContainer: that is the Slider's inactive track colour and would hide it.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.skip(-10_000L) }) { Icon(Icons.Rounded.Replay10, "后退 10 秒") }
                FilledTonalIconButton(onClick = { player.toggle() }) {
                    Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.isPlaying) "暂停" else "播放")
                }
                IconButton(onClick = { player.skip(10_000L) }) { Icon(Icons.Rounded.Forward10, "前进 10 秒") }
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatDuration(player.positionMs)} / ${formatDuration(player.durationMs)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (player.durationMs > 0L) {
                Slider(
                    value = player.positionMs.toFloat(),
                    onValueChange = { player.seekTo(it.toLong(), play = player.isPlaying) },
                    valueRange = 0f..player.durationMs.toFloat()
                )
            }
            player.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimelineLine(record: TranscriptRecord, offsetMs: Long?, highlighted: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(64.dp)) {
                Text(clockTime(record.createdAt), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (offsetMs != null && offsetMs >= 0L) {
                    Text(formatDuration(offsetMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SelectionContainer(Modifier.weight(1f)) {
                Text(record.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun clockTime(time: Long): String = SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(time))
