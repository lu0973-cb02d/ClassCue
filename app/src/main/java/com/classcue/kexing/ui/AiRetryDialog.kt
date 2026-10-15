@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.engine.TextSelection
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.HistoryEvent

@Composable
internal fun AiRetryDialog(
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
