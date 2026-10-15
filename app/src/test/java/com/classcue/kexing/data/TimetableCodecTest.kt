package com.classcue.kexing.data

import com.classcue.kexing.model.CourseSlot
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.WeekParity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableCodecTest {
    @Test
    fun roundTripsEveryField() {
        val table = Timetable(
            semesterStart = LocalDate.of(2026, 9, 7),
            slots = listOf(
                CourseSlot("a", "微观经济学", 1, 480, 580, 1, 16, WeekParity.ALL, "教1-101"),
                CourseSlot("b", "英语口译", 3, 600, 700, 3, 15, WeekParity.ODD, "")
            ),
            reminderEnabled = true,
            reminderLeadMinutes = 5
        )
        assertEquals(table, TimetableCodec.decode(TimetableCodec.encode(table)))
    }

    @Test
    fun missingOrDamagedDataGivesAnEmptyTimetable() {
        assertEquals(Timetable(), TimetableCodec.decode(null))
        assertEquals(Timetable(), TimetableCodec.decode("{not json"))
        assertEquals(Timetable(), TimetableCodec.decode(TimetableCodec.encode(Timetable())))
    }

    @Test
    fun invalidSlotsAreDroppedOnLoad() {
        val raw = """{"slots":[{"id":"x","course":"","weekday":1,"start":480,"end":580},
            {"id":"y","course":"统计学","weekday":9,"start":480,"end":580},
            {"id":"z","course":"统计学","weekday":2,"start":480,"end":580,"parity":"WEIRD"}]}"""
        val decoded = TimetableCodec.decode(raw)
        assertEquals(listOf("z"), decoded.slots.map { it.id })
        assertEquals(WeekParity.ALL, decoded.slots.single().parity)
    }
}
