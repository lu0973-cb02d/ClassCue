package com.classcue.kexing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.model.ScheduledClass

private data class CourseOption(val name: String, val note: String?)

/**
 * Confirms which course a lesson belongs to before recording starts. The timetable's current
 * class is offered first; the user can pick another course, type a new name, or leave the
 * lesson unfiled.
 */
@Composable
internal fun CourseConfirmDialog(
    suggestion: ScheduledClass?,
    typedCourse: String,
    knownCourses: List<String>,
    preselect: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val options = remember(suggestion, typedCourse, knownCourses, preselect) {
        buildList {
            suggestion?.let {
                val where = it.slot.location.takeIf(String::isNotBlank)?.let { place -> " · $place" }.orEmpty()
                add(CourseOption(it.slot.courseName, "课程表：${timeRange(it.slot)}$where"))
            }
            preselect?.takeIf { it.isNotBlank() }?.let { add(CourseOption(it, "来自上课提醒")) }
            typedCourse.trim().takeIf { it.isNotEmpty() }?.let { add(CourseOption(it, "当前填写的课程")) }
            knownCourses.forEach { add(CourseOption(it, null)) }
        }.distinctBy { it.name }
    }
    var selected by remember(options) {
        mutableStateOf(preselect?.takeIf { it.isNotBlank() } ?: options.firstOrNull()?.name ?: "")
    }
    var custom by remember { mutableStateOf("") }
    val chosen = custom.trim().ifEmpty { selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认本节课程") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (suggestion != null) "按课程表，现在是《${suggestion.slot.courseName}》。转写、提醒和录音会归到所选课程下。"
                    else "课程表里现在没有课。选择这节课归到哪门课程：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    OptionRow(
                        label = option.name,
                        note = option.note,
                        selected = custom.isBlank() && selected == option.name,
                        onSelect = { selected = option.name; custom = "" }
                    )
                }
                OptionRow(
                    label = "不归类",
                    note = "记为未命名课程，之后无法按课程导出",
                    selected = custom.isBlank() && selected.isEmpty(),
                    onSelect = { selected = ""; custom = "" }
                )
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(60) },
                    label = { Text("或输入其他课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(chosen) }) { Text("确认并开始听课") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OptionRow(label: String, note: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
