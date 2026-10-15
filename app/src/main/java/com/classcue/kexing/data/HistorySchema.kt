package com.classcue.kexing.data

/**
 * SQL for the per-lesson summary table and full-text search, kept as plain strings so the same
 * statements can be executed against a real SQLite engine in JVM tests.
 *
 * Before version 4 the lesson list was rebuilt by aggregating every transcript and event at
 * each app start, which grows with the whole semester. The `lessons` table is now maintained
 * incrementally as rows are written and only recomputed for a lesson when it is deleted.
 */
internal object HistorySchema {
    const val DATABASE_VERSION = 4

    /** Groups rows without a recording (pre-0.2.4 data) by calendar day, like ArchiveAssembler. */
    const val LESSON_KEY_EXPR =
        "CASE WHEN recording_name IS NULL OR TRIM(recording_name) = '' " +
            "THEN '__legacy_day_' || CAST(created_at / $LEGACY_DAY_MS AS INTEGER) " +
            "ELSE recording_name END"

    const val CREATE_LESSONS = """CREATE TABLE IF NOT EXISTS lessons (
        course_name TEXT NOT NULL,
        lesson_key TEXT NOT NULL,
        recording_name TEXT,
        started_at INTEGER NOT NULL,
        ended_at INTEGER NOT NULL,
        transcript_count INTEGER NOT NULL DEFAULT 0,
        event_count INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (course_name, lesson_key)
    )"""

    const val CREATE_LESSONS_INDEX = "CREATE INDEX IF NOT EXISTS idx_lessons_started ON lessons(started_at)"

    private const val AGGREGATE_SOURCE = """SELECT course_name, $LESSON_KEY_EXPR AS lesson_key, recording_name,
                created_at, 1 AS t, 0 AS e FROM transcripts
            UNION ALL
            SELECT course_name, $LESSON_KEY_EXPR AS lesson_key, recording_name,
                created_at, 0 AS t, 1 AS e FROM events"""

    private const val AGGREGATE_COLUMNS = """course_name, lesson_key,
            MAX(NULLIF(TRIM(recording_name), '')), MIN(created_at), MAX(created_at), SUM(t), SUM(e)"""

    /** Rebuilds every lesson row from the base tables (migration and repair). */
    const val REBUILD_ALL_LESSONS = """INSERT OR REPLACE INTO lessons
        (course_name, lesson_key, recording_name, started_at, ended_at, transcript_count, event_count)
        SELECT $AGGREGATE_COLUMNS FROM ($AGGREGATE_SOURCE) GROUP BY course_name, lesson_key"""

    /** Recomputes one lesson; args: course_name, lesson_key. Deletes the row first. */
    const val DELETE_ONE_LESSON = "DELETE FROM lessons WHERE course_name = ? AND lesson_key = ?"
    const val REBUILD_ONE_LESSON = """INSERT OR REPLACE INTO lessons
        (course_name, lesson_key, recording_name, started_at, ended_at, transcript_count, event_count)
        SELECT $AGGREGATE_COLUMNS FROM ($AGGREGATE_SOURCE) WHERE course_name = ? AND lesson_key = ?
        GROUP BY course_name, lesson_key"""

    /**
     * Adds one transcript or event to its lesson.
     * Args: course_name, lesson_key, recording_name, created_at, created_at, transcript delta, event delta.
     */
    const val UPSERT_LESSON = """INSERT INTO lessons
        (course_name, lesson_key, recording_name, started_at, ended_at, transcript_count, event_count)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(course_name, lesson_key) DO UPDATE SET
            started_at = MIN(started_at, excluded.started_at),
            ended_at = MAX(ended_at, excluded.ended_at),
            transcript_count = transcript_count + excluded.transcript_count,
            event_count = event_count + excluded.event_count,
            recording_name = COALESCE(recording_name, excluded.recording_name)"""

    const val SELECT_LESSONS = """SELECT course_name, recording_name, started_at, ended_at,
        transcript_count, event_count FROM lessons ORDER BY started_at DESC"""

    /**
     * Full-text search over transcripts, alerts and AI answers, newest first.
     * Args: pattern, pattern, pattern, limit. Patterns come from [likePattern].
     */
    const val SEARCH = """SELECT * FROM (
            SELECT 'T' AS kind, id, created_at, text, NULL AS answer, course_name, recording_name
            FROM transcripts WHERE text LIKE ? ESCAPE '\'
            UNION ALL
            SELECT 'E' AS kind, id, created_at, transcript AS text, answer, course_name, recording_name
            FROM events WHERE transcript LIKE ? ESCAPE '\' OR answer LIKE ? ESCAPE '\'
        ) ORDER BY created_at DESC, id DESC LIMIT ?"""

    fun lessonKey(recordingName: String?, createdAt: Long): String =
        recordingName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "__legacy_day_${createdAt / LEGACY_DAY_MS}"

    /** Escapes LIKE wildcards so a search for "100%" matches the literal text. */
    fun likePattern(query: String): String {
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}
