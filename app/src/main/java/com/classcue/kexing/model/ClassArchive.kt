package com.classcue.kexing.model

private const val DAY_MS = 86_400_000L

data class ArchiveFilter(
    val courseName: String? = null,
    val recordingName: String? = null,
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null
)

data class LessonSummary(
    val courseName: String,
    val recordingName: String?,
    val startedAt: Long,
    val endedAt: Long,
    val transcriptCount: Int,
    val eventCount: Int
)

/** One search result: a transcript line or an alert (whose text or AI answer matched). */
data class SearchHit(
    val kind: Kind,
    val id: Long,
    val createdAt: Long,
    val text: String,
    val answer: String?,
    val courseName: String,
    val recordingName: String?
) {
    enum class Kind { TRANSCRIPT, EVENT }
}

data class LessonArchive(
    val courseName: String,
    val recordingName: String?,
    val startedAt: Long,
    val endedAt: Long,
    val transcripts: List<TranscriptRecord>,
    val events: List<HistoryEvent>
)

data class HistoryArchiveSnapshot(
    val lessons: List<LessonArchive>
) {
    val isEmpty: Boolean get() = lessons.isEmpty()
}

/** Groups persisted rows without keeping the complete archive in a long-lived StateFlow. */
object ArchiveAssembler {
    fun assemble(
        transcripts: List<TranscriptRecord>,
        events: List<HistoryEvent>
    ): HistoryArchiveSnapshot {
        data class LessonKey(val courseName: String, val recordingKey: String)

        fun key(courseName: String, recordingName: String?, createdAt: Long): LessonKey {
            val normalizedCourse = courseName.trim()
            val recordingKey = recordingName?.takeIf { it.isNotBlank() }
                ?: "__legacy_day_${Math.floorDiv(createdAt, DAY_MS)}"
            return LessonKey(normalizedCourse, recordingKey)
        }

        val transcriptGroups = transcripts.groupBy { key(it.courseName, it.recordingName, it.createdAt) }
        val eventGroups = events.groupBy { key(it.courseName, it.recordingName, it.createdAt) }

        return (transcriptGroups.keys + eventGroups.keys)
            .distinct()
            .map { lessonKey ->
                val lessonTranscripts = transcriptGroups[lessonKey].orEmpty().sortedBy { it.createdAt }
                val lessonEvents = eventGroups[lessonKey].orEmpty().sortedBy { it.createdAt }
                val timestamps = lessonTranscripts.map { it.createdAt } + lessonEvents.map { it.createdAt }
                val recordingName = lessonTranscripts.firstNotNullOfOrNull { it.recordingName?.takeIf(String::isNotBlank) }
                    ?: lessonEvents.firstNotNullOfOrNull { it.recordingName?.takeIf(String::isNotBlank) }
                LessonArchive(
                    courseName = lessonKey.courseName,
                    recordingName = recordingName,
                    startedAt = timestamps.minOrNull() ?: 0L,
                    endedAt = timestamps.maxOrNull() ?: 0L,
                    transcripts = lessonTranscripts,
                    events = lessonEvents
                )
            }
            .sortedBy { it.startedAt }
            .let(::HistoryArchiveSnapshot)
    }
}
