package com.classcue.kexing.data

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Executes the production SQL strings against SQLite through JDBC. */
class HistorySchemaTest {
    private lateinit var db: Connection
    private val day = LEGACY_DAY_MS

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        // Version 3 schema, as created by 0.2.4p–0.2.7.
        exec(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, type TEXT NOT NULL,
                risk TEXT NOT NULL, transcript TEXT NOT NULL, confidence REAL NOT NULL, reason TEXT NOT NULL,
                answer TEXT, recording_name TEXT, recording_offset_ms INTEGER,
                course_name TEXT NOT NULL DEFAULT '')"""
        )
        exec(
            """CREATE TABLE transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, text TEXT NOT NULL,
                recording_name TEXT, course_name TEXT NOT NULL DEFAULT '')"""
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun migrationMatchesTheOldFullAggregation() {
        seed()
        exec(HistorySchema.CREATE_LESSONS)
        exec(HistorySchema.CREATE_LESSONS_INDEX)
        exec(HistorySchema.REBUILD_ALL_LESSONS)

        assertEquals(oldAggregation(), lessons())
        assertEquals(4, lessons().size)
    }

    @Test
    fun incrementalUpsertsEqualAFullRebuild() {
        exec(HistorySchema.CREATE_LESSONS)
        fun addTranscript(course: String, recording: String?, at: Long, text: String) {
            exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (?, ?, ?, ?)", at, text, recording, course)
            exec(HistorySchema.UPSERT_LESSON, course, HistorySchema.lessonKey(recording, at), recording, at, at, 1, 0)
        }
        fun addEvent(course: String, recording: String?, at: Long) {
            exec(
                "INSERT INTO events(created_at, type, risk, transcript, confidence, reason, recording_name, course_name) VALUES (?, 'ROLL_CALL', 'HIGH', 'x', 0.9, 'r', ?, ?)",
                at, recording, course
            )
            exec(HistorySchema.UPSERT_LESSON, course, HistorySchema.lessonKey(recording, at), recording, at, at, 0, 1)
        }
        addTranscript("高数", "class_1.wav", 1_000L, "第一句")
        addEvent("高数", "class_1.wav", 500L)
        addTranscript("高数", "class_1.wav", 9_000L, "第二句")
        addTranscript("英语", null, 3 * day + 10L, "旧数据")
        addTranscript("英语", null, 3 * day + 20L, "旧数据二")
        addTranscript("英语", null, 4 * day + 20L, "另一天")
        val incremental = lessons()

        exec("DELETE FROM lessons")
        exec(HistorySchema.REBUILD_ALL_LESSONS)
        assertEquals(lessons(), incremental)
        assertEquals(
            listOf(
                "高数|class_1.wav|500|9000|2|1",
                "英语|null|${4 * day + 20}|${4 * day + 20}|1|0",
                "英语|null|${3 * day + 10}|${3 * day + 20}|2|0"
            ),
            incremental
        )
    }

    @Test
    fun deletingALessonRemovesOnlyItsRow() {
        seed()
        exec(HistorySchema.CREATE_LESSONS)
        exec(HistorySchema.REBUILD_ALL_LESSONS)
        exec("DELETE FROM transcripts WHERE course_name = '高数' AND recording_name = 'class_1.wav'")
        exec("DELETE FROM events WHERE course_name = '高数' AND recording_name = 'class_1.wav'")
        val key = HistorySchema.lessonKey("class_1.wav", 0L)
        exec(HistorySchema.DELETE_ONE_LESSON, "高数", key)
        exec(HistorySchema.REBUILD_ONE_LESSON, "高数", key)

        assertEquals(3, lessons().size)
        assertEquals(oldAggregation(), lessons())
    }

    @Test
    fun searchTreatsWildcardsLiterallyAndCoversAnswers() {
        seed()
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (50, '利率提高了100%', 'class_9.wav', '经济')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (60, '利率提高了1000', 'class_9.wav', '经济')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (70, 'snake_case 命名', 'class_9.wav', '编程')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (80, 'snakeXcase 命名', 'class_9.wav', '编程')")

        assertEquals(listOf("T:利率提高了100%"), search("100%"))
        assertEquals(listOf("T:snake_case 命名"), search("snake_case"))
        // Matches inside an AI answer too.
        assertEquals(listOf("E:李明请回答机会成本"), search("放弃的最高价值"))
        assertEquals(emptyList<String>(), search("不存在的内容"))
    }

    @Test
    fun likePatternEscapesBackslashFirst() {
        assertEquals("%a\\\\b\\%c\\_d%", HistorySchema.likePattern(" a\\b%c_d "))
    }

    private fun seed() {
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (1000, '今天讲机会成本', 'class_1.wav', '高数')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (2000, '第二句', 'class_1.wav', '高数')")
        exec(
            "INSERT INTO events(created_at, type, risk, transcript, confidence, reason, answer, recording_name, course_name) " +
                "VALUES (1500, 'DIRECT_QUESTION', 'HIGH', '李明请回答机会成本', 0.9, 'r', '机会成本是放弃的最高价值', 'class_1.wav', '高数')"
        )
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (3000, '另一门课', 'class_2.wav', '英语')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (${2 * day + 5}, '旧版没有录音名', NULL, '英语')")
        exec("INSERT INTO events(created_at, type, risk, transcript, confidence, reason, recording_name, course_name) VALUES (${2 * day + 9}, 'ROLL_CALL', 'LOW', '点名', 0.7, 'r', '', '英语')")
        exec("INSERT INTO transcripts(created_at, text, recording_name, course_name) VALUES (${5 * day}, '再旧一天', NULL, '英语')")
    }

    private fun oldAggregation(): List<String> = query(buildLessonSummarySql(null, null, 0)) { rs ->
        listOf(rs.getString(1), rs.getString(2)?.takeIf { it.isNotBlank() }, rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getInt(6)).joinToString("|")
    }.sortedDescending()

    private fun lessons(): List<String> = query(HistorySchema.SELECT_LESSONS) { rs ->
        listOf(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getInt(6)).joinToString("|")
    }.sortedDescending()

    private fun search(text: String): List<String> {
        val p = HistorySchema.likePattern(text)
        return query(HistorySchema.SEARCH, p, p, p, 50) { rs -> "${rs.getString(1)}:${rs.getString(4)}" }
    }

    private fun exec(sql: String, vararg args: Any?) {
        db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.execute()
        }
    }

    private fun <T> query(sql: String, vararg args: Any?, row: (java.sql.ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }
}
