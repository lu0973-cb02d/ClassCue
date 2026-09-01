package com.classcue.kexing.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.model.ArchiveAssembler
import com.classcue.kexing.model.ArchiveFilter
import com.classcue.kexing.model.HistoryArchiveSnapshot
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.model.TranscriptRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoryStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "classcue_history.db"
        private const val DATABASE_VERSION = 3
        private const val EVENT_PREVIEW_LIMIT = "300"
        private const val TRANSCRIPT_PREVIEW_LIMIT = "2000"
        private const val LEGACY_DAY_MS = 86_400_000L
    }

    private val _events = MutableStateFlow<List<HistoryEvent>>(emptyList())
    val events: StateFlow<List<HistoryEvent>> = _events
    private val _transcripts = MutableStateFlow<List<TranscriptRecord>>(emptyList())
    val transcripts: StateFlow<List<TranscriptRecord>> = _transcripts
    private val _lessonSummaries = MutableStateFlow<List<LessonSummary>>(emptyList())
    val lessonSummaries: StateFlow<List<LessonSummary>> = _lessonSummaries

    init { reload() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                type TEXT NOT NULL,
                risk TEXT NOT NULL,
                transcript TEXT NOT NULL,
                confidence REAL NOT NULL,
                reason TEXT NOT NULL,
                answer TEXT,
                recording_name TEXT,
                recording_offset_ms INTEGER,
                course_name TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        createTranscriptTable(db)
        createArchiveIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTranscriptTable(db)
        if (oldVersion < 3) {
            addColumnIfMissing(db, "events", "course_name", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "transcripts", "course_name", "TEXT NOT NULL DEFAULT ''")
            createArchiveIndexes(db)
        }
    }

    private fun createTranscriptTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                text TEXT NOT NULL,
                recording_name TEXT,
                course_name TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, declaration: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $declaration")
    }

    private fun createArchiveIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_events_archive ON events(course_name, recording_name, created_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_transcripts_archive ON transcripts(course_name, recording_name, created_at)"
        )
    }

    @Synchronized
    fun insert(event: HistoryEvent): Long {
        val normalized = event.copy(
            recordingName = event.recordingName?.trim()?.takeIf { it.isNotEmpty() },
            courseName = event.courseName.trim()
        )
        val values = ContentValues().apply {
            put("created_at", normalized.createdAt)
            put("type", normalized.type.name)
            put("risk", normalized.risk.name)
            put("transcript", normalized.transcript)
            put("confidence", normalized.confidence)
            put("reason", normalized.reason)
            put("answer", normalized.answer)
            put("recording_name", normalized.recordingName)
            put("recording_offset_ms", normalized.recordingOffsetMs)
            put("course_name", normalized.courseName)
        }
        val id = writableDatabase.insertOrThrow("events", null, values)
        _events.value = (listOf(normalized.copy(id = id)) + _events.value).take(EVENT_PREVIEW_LIMIT.toInt())
        updateLessonSummary(normalized.courseName, normalized.recordingName, normalized.createdAt, eventDelta = 1)
        return id
    }

    @Synchronized
    fun updateAnswer(id: Long, answer: String) {
        writableDatabase.update(
            "events", ContentValues().apply { put("answer", answer) }, "id = ?", arrayOf(id.toString())
        )
        _events.value = _events.value.map { if (it.id == id) it.copy(answer = answer) else it }
    }

    @Synchronized
    fun getEvent(id: Long): HistoryEvent? = readableDatabase.query(
        "events", null, "id = ?", arrayOf(id.toString()), null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toHistoryEvent() else null }

    @Synchronized
    fun insertTranscript(record: TranscriptRecord): Long {
        val normalized = record.copy(
            recordingName = record.recordingName?.trim()?.takeIf { it.isNotEmpty() },
            courseName = record.courseName.trim()
        )
        val id = writableDatabase.insertOrThrow(
            "transcripts",
            null,
            ContentValues().apply {
                put("created_at", normalized.createdAt)
                put("text", normalized.text)
                put("recording_name", normalized.recordingName)
                put("course_name", normalized.courseName)
            }
        )
        _transcripts.value = (listOf(normalized.copy(id = id)) + _transcripts.value)
            .take(TRANSCRIPT_PREVIEW_LIMIT.toInt())
        updateLessonSummary(normalized.courseName, normalized.recordingName, normalized.createdAt, transcriptDelta = 1)
        return id
    }

    /** Loads all matching persisted text only when a lesson/course export or detail view requests it. */
    @Synchronized
    fun archiveSnapshot(filter: ArchiveFilter = ArchiveFilter()): HistoryArchiveSnapshot =
        ArchiveAssembler.assemble(queryTranscripts(filter), queryEvents(filter))

    @Synchronized
    fun archiveSnapshot(summary: LessonSummary): HistoryArchiveSnapshot = archiveSnapshot(summary.archiveFilter())

    @Synchronized
    fun queryLessonSummaries(
        filter: ArchiveFilter = ArchiveFilter(),
        limit: Int = 100,
        offset: Int = 0
    ): List<LessonSummary> = queryLessonSummariesInternal(
        filter = filter,
        limit = limit.coerceIn(1, 1000),
        offset = offset.coerceAtLeast(0)
    )

    @Synchronized
    fun deleteLesson(
        courseName: String,
        recordingName: String?,
        fromInclusive: Long? = null,
        toExclusive: Long? = null
    ): Int {
        val where = archiveWhere(
            ArchiveFilter(
                courseName = courseName,
                recordingName = recordingName ?: "",
                fromInclusive = fromInclusive,
                toExclusive = toExclusive
            )
        )
        var removed = 0
        writableDatabase.beginTransaction()
        try {
            removed += writableDatabase.delete("events", where.selection, where.args)
            removed += writableDatabase.delete("transcripts", where.selection, where.args)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        reload()
        return removed
    }

    @Synchronized
    fun deleteLesson(summary: LessonSummary): Int = deleteLesson(
        courseName = summary.courseName,
        recordingName = summary.recordingName,
        fromInclusive = summary.startedAt,
        toExclusive = summary.endedAt.plusOneOrNull()
    )

    @Synchronized
    fun deleteAll() {
        writableDatabase.delete("events", null, null)
        writableDatabase.delete("transcripts", null, null)
        reload()
    }

    @Synchronized
    fun reload() {
        val recentEvents = mutableListOf<HistoryEvent>()
        readableDatabase.query(
            "events", null, null, null, null, null, "created_at DESC", EVENT_PREVIEW_LIMIT
        ).use { cursor ->
            while (cursor.moveToNext()) recentEvents += cursor.toHistoryEvent()
        }
        _events.value = recentEvents
        reloadTranscripts()
        reloadLessonSummaries()
    }

    private fun reloadTranscripts() {
        val recentTranscripts = mutableListOf<TranscriptRecord>()
        readableDatabase.query(
            "transcripts", null, null, null, null, null, "created_at DESC", TRANSCRIPT_PREVIEW_LIMIT
        ).use { cursor ->
            while (cursor.moveToNext()) recentTranscripts += cursor.toTranscriptRecord()
        }
        _transcripts.value = recentTranscripts
    }

    private fun reloadLessonSummaries() {
        _lessonSummaries.value = queryLessonSummariesInternal(ArchiveFilter(), limit = null, offset = 0)
    }

    private fun updateLessonSummary(
        courseName: String,
        recordingName: String?,
        createdAt: Long,
        transcriptDelta: Int = 0,
        eventDelta: Int = 0
    ) {
        val existing = _lessonSummaries.value
        val index = existing.indexOfFirst { summary ->
            summary.courseName == courseName &&
                if (recordingName.isNullOrBlank()) {
                    summary.recordingName.isNullOrBlank() &&
                        Math.floorDiv(summary.startedAt, LEGACY_DAY_MS) == Math.floorDiv(createdAt, LEGACY_DAY_MS)
                } else {
                    summary.recordingName == recordingName
                }
        }
        val updated = if (index >= 0) {
            existing.toMutableList().apply {
                val current = get(index)
                set(
                    index,
                    current.copy(
                        startedAt = minOf(current.startedAt, createdAt),
                        endedAt = maxOf(current.endedAt, createdAt),
                        transcriptCount = current.transcriptCount + transcriptDelta,
                        eventCount = current.eventCount + eventDelta
                    )
                )
            }
        } else {
            existing + LessonSummary(
                courseName = courseName,
                recordingName = recordingName,
                startedAt = createdAt,
                endedAt = createdAt,
                transcriptCount = transcriptDelta,
                eventCount = eventDelta
            )
        }
        _lessonSummaries.value = updated.sortedByDescending { it.startedAt }
    }

    private fun queryTranscripts(filter: ArchiveFilter): List<TranscriptRecord> {
        val where = archiveWhere(filter)
        val result = mutableListOf<TranscriptRecord>()
        readableDatabase.query(
            "transcripts", null, where.selection, where.args, null, null, "created_at ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toTranscriptRecord()
        }
        return result
    }

    private fun queryEvents(filter: ArchiveFilter): List<HistoryEvent> {
        val where = archiveWhere(filter)
        val result = mutableListOf<HistoryEvent>()
        readableDatabase.query(
            "events", null, where.selection, where.args, null, null, "created_at ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toHistoryEvent()
        }
        return result
    }

    private fun queryLessonSummariesInternal(
        filter: ArchiveFilter,
        limit: Int?,
        offset: Int
    ): List<LessonSummary> {
        val where = archiveWhere(filter)
        val sql = buildString {
            append(
                """SELECT course_name, recording_name,
                    MIN(created_at) AS started_at, MAX(created_at) AS ended_at,
                    SUM(transcript_count) AS transcript_count, SUM(event_count) AS event_count
                    FROM (
                        SELECT course_name, recording_name, created_at, 1 AS transcript_count, 0 AS event_count FROM transcripts
                        UNION ALL
                        SELECT course_name, recording_name, created_at, 0 AS transcript_count, 1 AS event_count FROM events
                    ) archive_rows""".trimIndent()
            )
            if (where.selection != null) append(" WHERE ${where.selection}")
            append(
                """ GROUP BY course_name,
                    CASE WHEN recording_name IS NULL OR TRIM(recording_name) = ''
                    THEN '__legacy_day_' || CAST(created_at / $LEGACY_DAY_MS AS INTEGER)
                    ELSE recording_name END
                    ORDER BY started_at DESC""".trimIndent()
            )
            if (limit != null) append(" LIMIT $limit OFFSET $offset")
        }
        val result = mutableListOf<LessonSummary>()
        readableDatabase.rawQuery(sql, where.args).use { cursor ->
            val courseName = cursor.getColumnIndexOrThrow("course_name")
            val recordingName = cursor.getColumnIndexOrThrow("recording_name")
            val startedAt = cursor.getColumnIndexOrThrow("started_at")
            val endedAt = cursor.getColumnIndexOrThrow("ended_at")
            val transcriptCount = cursor.getColumnIndexOrThrow("transcript_count")
            val eventCount = cursor.getColumnIndexOrThrow("event_count")
            while (cursor.moveToNext()) {
                result += LessonSummary(
                    courseName = cursor.getString(courseName).orEmpty(),
                    recordingName = if (cursor.isNull(recordingName)) null else cursor.getString(recordingName),
                    startedAt = cursor.getLong(startedAt),
                    endedAt = cursor.getLong(endedAt),
                    transcriptCount = cursor.getInt(transcriptCount),
                    eventCount = cursor.getInt(eventCount)
                )
            }
        }
        return result
    }

    private data class ArchiveWhere(val selection: String?, val args: Array<String>)

    private fun LessonSummary.archiveFilter(): ArchiveFilter = ArchiveFilter(
        courseName = courseName,
        recordingName = recordingName ?: "",
        fromInclusive = startedAt,
        toExclusive = endedAt.plusOneOrNull()
    )

    private fun Long.plusOneOrNull(): Long? = if (this == Long.MAX_VALUE) null else this + 1L

    private fun archiveWhere(filter: ArchiveFilter): ArchiveWhere {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        filter.courseName?.let {
            clauses += "course_name = ?"
            args += it.trim()
        }
        filter.recordingName?.let {
            if (it.isBlank()) {
                clauses += "(recording_name IS NULL OR TRIM(recording_name) = '')"
            } else {
                clauses += "recording_name = ?"
                args += it
            }
        }
        filter.fromInclusive?.let {
            clauses += "created_at >= ?"
            args += it.toString()
        }
        filter.toExclusive?.let {
            clauses += "created_at < ?"
            args += it.toString()
        }
        return ArchiveWhere(clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "), args.toTypedArray())
    }

    private fun Cursor.toTranscriptRecord(): TranscriptRecord = TranscriptRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        text = getString(getColumnIndexOrThrow("text")),
        recordingName = nullableString("recording_name"),
        courseName = nullableString("course_name").orEmpty()
    )

    private fun Cursor.toHistoryEvent(): HistoryEvent = HistoryEvent(
        id = getLong(getColumnIndexOrThrow("id")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        type = runCatching { CueType.valueOf(getString(getColumnIndexOrThrow("type"))) }
            .getOrDefault(CueType.NONE),
        risk = runCatching { RiskLevel.valueOf(getString(getColumnIndexOrThrow("risk"))) }
            .getOrDefault(RiskLevel.LOW),
        transcript = getString(getColumnIndexOrThrow("transcript")),
        confidence = getDouble(getColumnIndexOrThrow("confidence")),
        reason = getString(getColumnIndexOrThrow("reason")),
        answer = nullableString("answer"),
        recordingName = nullableString("recording_name"),
        recordingOffsetMs = nullableLong("recording_offset_ms"),
        courseName = nullableString("course_name").orEmpty()
    )

    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }
}
