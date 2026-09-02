package com.classcue.kexing.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreSqlTest {
    @Test
    fun lessonSummarySqlSeparatesArchiveAliasFromGroupBy() {
        val sql = buildLessonSummarySql(whereSelection = null, limit = null, offset = 0)

        assertTrue(sql.contains("archive_rows\nGROUP BY"))
        assertFalse(sql.contains("archive_rowsGROUP BY"))
    }

    @Test
    fun lessonSummarySqlSeparatesWhereClauseFromGroupBy() {
        val sql = buildLessonSummarySql(
            whereSelection = "course_name = ?",
            limit = 100,
            offset = 0
        )

        assertTrue(sql.contains("WHERE course_name = ?\nGROUP BY"))
        assertTrue(sql.endsWith("LIMIT 100 OFFSET 0"))
    }
}
