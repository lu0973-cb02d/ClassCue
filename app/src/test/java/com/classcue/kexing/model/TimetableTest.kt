package com.classcue.kexing.model

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableTest {
    // 2026-09-07 is a Monday.
    private val semesterStart = LocalDate.of(2026, 9, 7)
    private val micro = CourseSlot("a", "微观经济学", weekday = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40)
    private val english = CourseSlot("b", "英语口译", weekday = 1, startMinute = 10 * 60, endMinute = 11 * 60 + 40, firstWeek = 3, lastWeek = 16, parity = WeekParity.ODD)
    private val stats = CourseSlot("c", "统计学", weekday = 3, startMinute = 14 * 60, endMinute = 15 * 60 + 40, parity = WeekParity.EVEN)
    private val table = Timetable(semesterStart, listOf(micro, english, stats))

    @Test
    fun weekNumbersCountFromTheMondayOfTheStartWeek() {
        assertEquals(1, TimetableResolver.weekNumber(semesterStart, LocalDate.of(2026, 9, 7)))
        assertEquals(1, TimetableResolver.weekNumber(semesterStart, LocalDate.of(2026, 9, 13)))
        assertEquals(2, TimetableResolver.weekNumber(semesterStart, LocalDate.of(2026, 9, 14)))
        // A start date given mid-week still anchors to that week's Monday.
        assertEquals(2, TimetableResolver.weekNumber(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14)))
        assertEquals(0, TimetableResolver.weekNumber(semesterStart, LocalDate.of(2026, 9, 6)))
    }

    @Test
    fun weekRangeAndParityAreApplied() {
        val week3Monday = LocalDate.of(2026, 9, 21)
        val week4Monday = LocalDate.of(2026, 9, 28)
        assertTrue(TimetableResolver.occursOn(english, table, week3Monday))
        assertFalse(TimetableResolver.occursOn(english, table, week4Monday))
        assertFalse(TimetableResolver.occursOn(english, table, LocalDate.of(2026, 9, 7)))
        assertFalse(TimetableResolver.occursOn(english, table, LocalDate.of(2026, 12, 28))) // week 17
        assertTrue(TimetableResolver.occursOn(stats, table, LocalDate.of(2026, 9, 16))) // week 2, Wednesday
        assertFalse(TimetableResolver.occursOn(stats, table, LocalDate.of(2026, 9, 9)))
    }

    @Test
    fun withoutSemesterStartEveryWeekMatches() {
        val noWeeks = table.copy(semesterStart = null)
        assertTrue(TimetableResolver.occursOn(english, noWeeks, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun currentClassIncludesTheMinutesBeforeTheBell() {
        val monday = LocalDate.of(2026, 9, 21)
        assertNull(TimetableResolver.current(table, monday.atTime(7, 39)))
        assertEquals("微观经济学", TimetableResolver.current(table, monday.atTime(7, 45))?.slot?.courseName)
        assertEquals("微观经济学", TimetableResolver.current(table, monday.atTime(9, 39))?.slot?.courseName)
        // 09:45: micro has ended and English starts in 15 minutes.
        assertEquals("英语口译", TimetableResolver.current(table, monday.atTime(9, 45))?.slot?.courseName)
        assertNull(TimetableResolver.current(table, monday.atTime(12, 0)))
    }

    @Test
    fun suggestionLooksAheadWithinTheHour() {
        val wednesday = LocalDate.of(2026, 9, 16)
        assertEquals("统计学", TimetableResolver.suggestion(table, wednesday.atTime(13, 5))?.slot?.courseName)
        assertNull(TimetableResolver.suggestion(table, wednesday.atTime(12, 30)))
    }

    @Test
    fun nextClassSkipsWeeksWhereTheSlotDoesNotRun() {
        // Friday of week 2: next is Monday week 3 micro at 08:00.
        val next = TimetableResolver.next(table, LocalDateTime.of(2026, 9, 18, 12, 0))
        assertEquals(LocalDateTime.of(2026, 9, 21, 8, 0), next?.start)
        // After micro on Monday week 4, English (odd weeks) is skipped; Wednesday stats is next.
        val afterMicro = TimetableResolver.next(table, LocalDateTime.of(2026, 9, 28, 9, 0))
        assertEquals("统计学", afterMicro?.slot?.courseName)
    }

    @Test
    fun invalidSlotsAreIgnored() {
        val broken = micro.copy(id = "x", startMinute = 600, endMinute = 600)
        assertFalse(broken.isValid)
        assertEquals(listOf("微观经济学"), TimetableResolver.classesOn(Timetable(null, listOf(micro, broken)), LocalDate.of(2026, 9, 7)).map { it.slot.courseName })
        assertEquals(listOf("微观经济学", "统计学", "英语口译"), table.courseNames)
    }
}
