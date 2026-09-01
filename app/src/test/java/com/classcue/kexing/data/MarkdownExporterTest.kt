package com.classcue.kexing.data

import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.model.ArchiveAssembler
import com.classcue.kexing.model.HistoryArchiveSnapshot
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.TranscriptRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class MarkdownExporterTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun assemblerGroupsByCourseAndRecordingAndSortsChronologically() {
        val snapshot = ArchiveAssembler.assemble(
            transcripts = listOf(
                transcript(3_000, "第二句", "高等数学", "class_1000.wav"),
                transcript(1_000, "第一句", "高等数学", "class_1000.wav"),
                transcript(5_000, "另一门课", "大学英语", "class_5000.wav")
            ),
            events = listOf(
                event(2_000, "问题", "高等数学", "class_1000.wav")
            )
        )

        assertEquals(2, snapshot.lessons.size)
        val math = snapshot.lessons.first()
        assertEquals("高等数学", math.courseName)
        assertEquals("class_1000.wav", math.recordingName)
        assertEquals(listOf("第一句", "第二句"), math.transcripts.map { it.text })
        assertEquals(1, math.events.size)
        assertEquals(1_000, math.startedAt)
        assertEquals(3_000, math.endedAt)
    }

    @Test
    fun legacyRowsWithoutRecordingNameAreGroupedByDayNotForever() {
        val day = 86_400_000L
        val snapshot = ArchiveAssembler.assemble(
            transcripts = listOf(
                transcript(1_000, "第一天", "", null),
                transcript(day + 1_000, "第二天", "", null)
            ),
            events = emptyList()
        )

        assertEquals(2, snapshot.lessons.size)
        assertTrue(snapshot.lessons.all { it.recordingName == null })
    }

    @Test
    fun markdownIncludesTranscriptQuestionsAnswersAndEscapesContent() {
        val snapshot = ArchiveAssembler.assemble(
            transcripts = listOf(
                transcript(0, "函数 *重点*\n范围 [1,2]", "高数 #1", "class_0.wav")
            ),
            events = listOf(
                event(
                    createdAt = 1_000,
                    transcript = "为什么 x < 2？",
                    courseName = "高数 #1",
                    recordingName = "class_0.wav",
                    answer = "先看 `定义`\n再推导"
                )
            )
        )

        val markdown = MarkdownExporter.export(snapshot, generatedAt = 2_000, timeZone = utc)

        assertTrue(markdown.contains("## 课程：高数 \\#1"))
        assertTrue(markdown.contains("函数 \\*重点\\*<br>范围 \\[1,2\\]"))
        assertTrue(markdown.contains("课堂提醒、问题与 AI 回答"))
        assertTrue(markdown.contains("- 老师原话：为什么 x \\< 2？"))
        assertTrue(markdown.contains("> 先看 \\`定义\\`"))
        assertTrue(markdown.contains("> 再推导"))
        assertFalse(markdown.contains("null"))
    }

    @Test
    fun emptySnapshotProducesReadableDocument() {
        val markdown = MarkdownExporter.export(
            HistoryArchiveSnapshot(emptyList()),
            generatedAt = 0,
            timeZone = utc
        )

        assertTrue(markdown.startsWith("# 课醒课堂归档"))
        assertTrue(markdown.contains("课次数：0"))
        assertTrue(markdown.contains("没有课堂记录"))
    }

    @Test
    fun suggestedFileNameRemovesWindowsInvalidCharacters() {
        val snapshot = ArchiveAssembler.assemble(
            transcripts = listOf(transcript(0, "内容", "计/算:机*课", "class_0.wav")),
            events = emptyList()
        )

        val fileName = MarkdownExporter.suggestedFileName(snapshot, generatedAt = 0, timeZone = utc)

        assertEquals("计_算_机_课_课醒课堂归档_19700101-0000.md", fileName)
    }

    private fun transcript(
        createdAt: Long,
        text: String,
        courseName: String,
        recordingName: String?
    ) = TranscriptRecord(
        createdAt = createdAt,
        text = text,
        recordingName = recordingName,
        courseName = courseName
    )

    private fun event(
        createdAt: Long,
        transcript: String,
        courseName: String,
        recordingName: String?,
        answer: String? = "示例回答"
    ) = HistoryEvent(
        createdAt = createdAt,
        type = CueType.GENERAL_QUESTION,
        risk = RiskLevel.MEDIUM,
        transcript = transcript,
        confidence = 0.85,
        reason = "检测到问题",
        answer = answer,
        recordingName = recordingName,
        recordingOffsetMs = 1_000,
        courseName = courseName
    )
}
