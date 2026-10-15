@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.classcue.kexing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.classcue.kexing.model.CourseSlot
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.TimetableResolver
import com.classcue.kexing.model.WeekParity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToInt

internal val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
internal fun ScheduleScreen(
    modifier: Modifier,
    timetable: Timetable,
    now: LocalDateTime,
    onUpdate: ((Timetable) -> Timetable) -> Unit
) {
    var editing by remember { mutableStateOf<CourseSlot?>(null) }
    var pickingSemesterStart by remember { mutableStateOf(false) }
    editing?.let { slot ->
        CourseSlotDialog(
            initial = slot,
            isNew = timetable.slots.none { it.id == slot.id },
            knownCourses = timetable.courseNames,
            onDismiss = { editing = null },
            onSave = { saved ->
                onUpdate { current ->
                    val others = current.slots.filterNot { it.id == saved.id }
                    current.copy(slots = (others + saved).sortedWith(compareBy({ it.weekday }, { it.startMinute })))
                }
                editing = null
            },
            onDelete = {
                onUpdate { current -> current.copy(slots = current.slots.filterNot { it.id == slot.id }) }
                editing = null
            }
        )
    }
    if (pickingSemesterStart) {
        SemesterStartDialog(
            initial = timetable.semesterStart ?: now.toLocalDate(),
            onDismiss = { pickingSemesterStart = false },
            onPick = { date ->
                onUpdate { it.copy(semesterStart = date) }
                pickingSemesterStart = false
            },
            onClear = {
                onUpdate { it.copy(semesterStart = null) }
                pickingSemesterStart = false
            }
        )
    }
    val today = now.toLocalDate()
    val week = timetable.semesterStart?.let { TimetableResolver.weekNumber(it, today) }
    val todayClasses = TimetableResolver.classesOn(timetable, today)
    val current = TimetableResolver.current(timetable, now)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(
                "学期与提醒",
                when {
                    week == null -> "未设置开学日期：每周都按课表匹配，不区分周次"
                    week < 1 -> "还没开学：第 1 周从 ${formatLocalDate(timetable.semesterStart!!)} 所在的周一开始"
                    else -> "今天是第 $week 周（${if (week % 2 == 1) "单周" else "双周"}）"
                }
            ) {
                OutlinedButton(onClick = { pickingSemesterStart = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        timetable.semesterStart?.let { "第 1 周：${formatLocalDate(it)}（点击修改）" }
                            ?: "设置开学日期（第 1 周）"
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "上课前提醒",
                    subtitle = "上课前约 ${timetable.reminderLeadMinutes} 分钟推送通知，点开确认课程再开始听课；不会自动录音",
                    checked = timetable.reminderEnabled,
                    onChecked = { enabled -> onUpdate { it.copy(reminderEnabled = enabled) } }
                )
                if (timetable.reminderEnabled) {
                    Slider(
                        value = timetable.reminderLeadMinutes.toFloat(),
                        onValueChange = { value ->
                            val snapped = ((value / 5f).roundToInt() * 5).coerceIn(5, 30)
                            onUpdate { it.copy(reminderLeadMinutes = snapped) }
                        },
                        valueRange = 5f..30f,
                        steps = 4
                    )
                    Text(
                        "系统可能把提醒前后挪动几分钟以省电。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SectionCard("今天", "${WEEKDAY_LABELS[today.dayOfWeek.value - 1]} · ${todayClasses.size} 节课") {
                if (todayClasses.isEmpty()) {
                    Text("今天课表里没有课。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    todayClasses.forEach { scheduled ->
                        val label = when {
                            scheduled == current -> "现在"
                            scheduled.end.isBefore(now) -> "已结束"
                            else -> null
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(timeRange(scheduled.slot), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(104.dp))
                            Text(scheduled.slot.courseName, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            label?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (it == "现在") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        if (timetable.slots.isEmpty()) {
            item {
                EmptyCard(
                    "还没有课程",
                    "添加每周的上课时间后，开始听课时会按课表自动带出课程，确认后再录音。"
                )
            }
        }
        (1..7).forEach { weekday ->
            val slots = timetable.slots.filter { it.weekday == weekday }.sortedBy { it.startMinute }
            if (slots.isNotEmpty()) {
                item(key = "header$weekday") {
                    Text(
                        WEEKDAY_LABELS[weekday - 1],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
                items(slots, key = { it.id }) { slot ->
                    CourseSlotCard(slot = slot, onClick = { editing = slot })
                }
            }
        }
        item {
            Button(
                onClick = {
                    editing = CourseSlot(
                        id = UUID.randomUUID().toString(),
                        courseName = "",
                        weekday = today.dayOfWeek.value,
                        startMinute = 8 * 60,
                        endMinute = 9 * 60 + 40
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("添加课程时间")
            }
        }
    }
}

@Composable
private fun CourseSlotCard(slot: CourseSlot, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(96.dp)) {
                Text(minuteLabel(slot.startMinute), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(minuteLabel(slot.endMinute), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(slot.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    weeksLabel(slot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (slot.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, Modifier.height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(slot.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSlotDialog(
    initial: CourseSlot,
    isNew: Boolean,
    knownCourses: List<String>,
    onDismiss: () -> Unit,
    onSave: (CourseSlot) -> Unit,
    onDelete: () -> Unit
) {
    var slot by remember(initial.id) { mutableStateOf(initial) }
    var firstWeekText by remember(initial.id) { mutableStateOf(initial.firstWeek.toString()) }
    var lastWeekText by remember(initial.id) { mutableStateOf(initial.lastWeek.toString()) }
    var pickingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    pickingTime?.let { start ->
        TimeDialog(
            title = if (start) "上课时间" else "下课时间",
            minute = if (start) slot.startMinute else slot.endMinute,
            onDismiss = { pickingTime = null },
            onPick = { minute ->
                slot = if (start) {
                    val length = (slot.endMinute - slot.startMinute).coerceAtLeast(45)
                    slot.copy(startMinute = minute, endMinute = (minute + length).coerceAtMost(CourseSlot.MINUTES_PER_DAY))
                } else {
                    slot.copy(endMinute = minute)
                }
                pickingTime = null
            }
        )
    }
    val candidate = slot.copy(
        courseName = slot.courseName.trim(),
        location = slot.location.trim(),
        firstWeek = firstWeekText.toIntOrNull() ?: 0,
        lastWeek = lastWeekText.toIntOrNull() ?: 0
    )
    val problem = when {
        candidate.courseName.isEmpty() -> "请填写课程名称"
        candidate.endMinute <= candidate.startMinute -> "下课时间要晚于上课时间"
        candidate.firstWeek < 1 || candidate.lastWeek > CourseSlot.MAX_WEEK || candidate.firstWeek > candidate.lastWeek ->
            "周次范围需在 1–${CourseSlot.MAX_WEEK} 之间，且开始周不晚于结束周"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加课程时间" else "编辑课程时间") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = slot.courseName,
                    onValueChange = { slot = slot.copy(courseName = it.take(60)) },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val suggestions = knownCourses.filter { it != slot.courseName.trim() }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.take(8).forEach { name ->
                            AssistChip(onClick = { slot = slot.copy(courseName = name) }, label = { Text(name) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("星期", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEKDAY_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = slot.weekday == index + 1,
                            onClick = { slot = slot.copy(weekday = index + 1) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.weight(1f)) {
                        Text("上课 ${minuteLabel(slot.startMinute)}")
                    }
                    OutlinedButton(onClick = { pickingTime = false }, modifier = Modifier.weight(1f)) {
                        Text("下课 ${minuteLabel(slot.endMinute)}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = firstWeekText,
                        onValueChange = { firstWeekText = it.filter(Char::isDigit).take(2) },
                        label = { Text("从第几周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastWeekText,
                        onValueChange = { lastWeekText = it.filter(Char::isDigit).take(2) },
                        label = { Text("到第几周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekParity.entries.forEach { parity ->
                        FilterChip(
                            selected = slot.parity == parity,
                            onClick = { slot = slot.copy(parity = parity) },
                            label = { Text(parityLabel(parity)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = slot.location,
                    onValueChange = { slot = slot.copy(location = it.take(40)) },
                    label = { Text("地点（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                problem?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!isNew) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onDelete) { Text("删除这个时间", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(enabled = problem == null, onClick = { onSave(candidate) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TimeDialog(title: String, minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (minute / 60).coerceIn(0, 23),
        initialMinute = minute % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SemesterStartDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    onClear: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("不区分周次") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    ) {
        DatePicker(
            state = state,
            title = { Text("选择第 1 周的任意一天", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
        )
    }
}

internal fun minuteLabel(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

internal fun timeRange(slot: CourseSlot): String = "${minuteLabel(slot.startMinute)}–${minuteLabel(slot.endMinute)}"

private fun parityLabel(parity: WeekParity): String = when (parity) {
    WeekParity.ALL -> "每周"
    WeekParity.ODD -> "单周"
    WeekParity.EVEN -> "双周"
}

private fun weeksLabel(slot: CourseSlot): String {
    val parity = when (slot.parity) {
        WeekParity.ALL -> ""
        WeekParity.ODD -> " · 单周"
        WeekParity.EVEN -> " · 双周"
    }
    return "第 ${slot.firstWeek}–${slot.lastWeek} 周$parity"
}

private fun formatLocalDate(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${WEEKDAY_LABELS[date.dayOfWeek.value - 1]}"
