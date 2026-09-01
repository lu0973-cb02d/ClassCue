package com.classcue.kexing.data

import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.model.HistoryArchiveSnapshot
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonArchive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object MarkdownExporter {
    fun export(
        snapshot: HistoryArchiveSnapshot,
        generatedAt: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = buildString { write(snapshot, this, generatedAt, timeZone) }

    fun write(
        snapshot: HistoryArchiveSnapshot,
        out: Appendable,
        generatedAt: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ) {
        val dateTime = formatter("yyyy-MM-dd HH:mm:ss", timeZone)
        val time = formatter("HH:mm:ss", timeZone)
        val lessons = snapshot.lessons.sortedBy { it.startedAt }
        val courses = lessons.groupBy { it.courseName.trim() }

        out.appendLine("# 课醒课堂归档")
        out.appendLine()
        out.appendLine("- 导出时间：${dateTime.format(Date(generatedAt))}")
        out.appendLine("- 课程数：${courses.size}")
        out.appendLine("- 课次数：${lessons.size}")

        if (lessons.isEmpty()) {
            out.appendLine()
            out.appendLine("_当前范围内没有课堂记录。_")
            return
        }

        courses.forEach { (courseName, courseLessons) ->
            out.appendLine()
            out.appendLine("## 课程：${escapeInline(displayCourseName(courseName))}")
            courseLessons.forEach { lesson -> writeLesson(out, lesson, dateTime, time) }
        }
    }

    fun suggestedFileName(
        snapshot: HistoryArchiveSnapshot,
        generatedAt: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val courses = snapshot.lessons.map { displayCourseName(it.courseName) }.distinct()
        val prefix = when {
            courses.size == 1 -> courses.single()
            courses.isEmpty() -> "课堂归档"
            else -> "全部课程"
        }
        val date = formatter("yyyyMMdd-HHmm", timeZone).format(Date(generatedAt))
        val safePrefix = prefix.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "课堂归档" }
        return "${safePrefix}_课醒课堂归档_$date.md"
    }

    private fun writeLesson(
        out: Appendable,
        lesson: LessonArchive,
        dateTime: SimpleDateFormat,
        time: SimpleDateFormat
    ) {
        out.appendLine()
        out.appendLine("### 课次：${dateTime.format(Date(lesson.startedAt))}")
        out.appendLine()
        out.appendLine("- 开始时间：${dateTime.format(Date(lesson.startedAt))}")
        out.appendLine("- 结束时间：${dateTime.format(Date(lesson.endedAt))}")
        lesson.recordingName?.takeIf { it.isNotBlank() }?.let {
            out.appendLine("- 录音标识：`${escapeCode(it)}`")
        }

        out.appendLine()
        out.appendLine("#### 老师原话（自动转写）")
        out.appendLine()
        if (lesson.transcripts.isEmpty()) {
            out.appendLine("_本课次没有保存转写文字。_")
        } else {
            lesson.transcripts.sortedBy { it.createdAt }.forEach { record ->
                out.appendLine("- **${time.format(Date(record.createdAt))}**　${escapeInline(record.text)}")
            }
        }

        out.appendLine()
        out.appendLine("#### 课堂提醒、问题与 AI 回答")
        if (lesson.events.isEmpty()) {
            out.appendLine()
            out.appendLine("_本课次没有保存提醒或问题。_")
        } else {
            lesson.events.sortedBy { it.createdAt }.forEach { event -> writeEvent(out, event, time) }
        }
    }

    private fun writeEvent(out: Appendable, event: HistoryEvent, time: SimpleDateFormat) {
        out.appendLine()
        out.append("##### ${time.format(Date(event.createdAt))} · ${cueLabel(event.type)} · ${riskLabel(event.risk)}")
        event.recordingOffsetMs?.let { out.append(" · 录音位置 ${formatOffset(it)}") }
        out.appendLine()
        out.appendLine()
        out.appendLine("- 老师原话：${escapeInline(event.transcript)}")
        event.reason.takeIf { it.isNotBlank() }?.let {
            out.appendLine("- 识别说明：${escapeInline(it)}")
        }
        event.answer?.takeIf { it.isNotBlank() }?.let { answer ->
            out.appendLine("- AI 回答：")
            answer.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
                out.appendLine(if (line.isEmpty()) ">" else "> ${escapeLine(line)}")
            }
        }
    }

    private fun formatter(pattern: String, timeZone: TimeZone): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.CHINA).apply { this.timeZone = timeZone }

    private fun displayCourseName(courseName: String): String = courseName.trim().ifEmpty { "未命名课程" }

    private fun cueLabel(type: CueType): String = when (type) {
        CueType.ROLL_CALL -> "点名提醒"
        CueType.DIRECT_QUESTION -> "老师直接提问"
        CueType.GENERAL_QUESTION -> "课堂问题"
        CueType.NONE -> "课堂提醒"
    }

    private fun riskLabel(risk: RiskLevel): String = when (risk) {
        RiskLevel.LOW -> "低优先级"
        RiskLevel.MEDIUM -> "中优先级"
        RiskLevel.HIGH -> "高优先级"
    }

    private fun formatOffset(offsetMs: Long): String {
        val totalSeconds = offsetMs.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun escapeInline(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("<br>") { escapeLine(it) }

    private fun escapeLine(text: String): String {
        val special = "\\`*_{}[]<>#|~"
        return buildString(text.length) {
            text.forEach { char ->
                if (char in special) append('\\')
                append(char)
            }
        }
    }

    private fun escapeCode(text: String): String = text.replace('`', '\'')
}
