package com.classcue.kexing.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class WeekParity { ALL, ODD, EVEN }

/** One weekly class meeting, e.g. 微观经济学 every Monday 08:00–09:40, weeks 1–16. */
data class CourseSlot(
    val id: String,
    val courseName: String,
    /** 1 = Monday … 7 = Sunday (ISO-8601, as java.time.DayOfWeek). */
    val weekday: Int,
    /** Minutes after midnight. */
    val startMinute: Int,
    val endMinute: Int,
    val firstWeek: Int = 1,
    val lastWeek: Int = 20,
    val parity: WeekParity = WeekParity.ALL,
    val location: String = ""
) {
    val isValid: Boolean
        get() = courseName.isNotBlank() && weekday in 1..7 && startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in (startMinute + 1)..MINUTES_PER_DAY && firstWeek in 1..lastWeek && lastWeek <= MAX_WEEK

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_WEEK = 30
    }
}

data class Timetable(
    /** Any day of teaching week 1; weeks are counted from that week's Monday. Null disables week rules. */
    val semesterStart: LocalDate? = null,
    val slots: List<CourseSlot> = emptyList(),
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Int = 10
) {
    val courseNames: List<String>
        get() = slots.map { it.courseName.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
}

/** A slot placed on a concrete date. */
data class ScheduledClass(val slot: CourseSlot, val date: LocalDate) {
    val start: LocalDateTime get() = date.atStartOfDay().plusMinutes(slot.startMinute.toLong())
    val end: LocalDateTime get() = date.atStartOfDay().plusMinutes(slot.endMinute.toLong())
}

object TimetableResolver {
    /** How early before the bell a lesson already counts as "this class". */
    const val EARLY_MINUTES = 20L

    /** Teaching week of [date], 1-based; 0 or negative before the semester starts. */
    fun weekNumber(semesterStart: LocalDate, date: LocalDate): Int {
        val firstMonday = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = ChronoUnit.DAYS.between(firstMonday, date)
        return Math.floorDiv(days, 7L).toInt() + 1
    }

    fun occursOn(slot: CourseSlot, timetable: Timetable, date: LocalDate): Boolean {
        if (date.dayOfWeek.value != slot.weekday) return false
        val start = timetable.semesterStart ?: return true
        val week = weekNumber(start, date)
        if (week < slot.firstWeek || week > slot.lastWeek) return false
        return when (slot.parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
    }

    fun classesOn(timetable: Timetable, date: LocalDate): List<ScheduledClass> = timetable.slots
        .filter { it.isValid && occursOn(it, timetable, date) }
        .map { ScheduledClass(it, date) }
        .sortedBy { it.slot.startMinute }

    /**
     * The class the user is most likely sitting in at [now]: one that has started (or starts
     * within [EARLY_MINUTES]) and has not ended. With overlaps, the one that started last wins.
     */
    fun current(timetable: Timetable, now: LocalDateTime): ScheduledClass? = classesOn(timetable, now.toLocalDate())
        .filter { !now.isBefore(it.start.minusMinutes(EARLY_MINUTES)) && now.isBefore(it.end) }
        .maxByOrNull { it.start }

    /** Next class starting after [now] within [withinDays] days, used for reminders. */
    fun next(timetable: Timetable, now: LocalDateTime, withinDays: Long = 14): ScheduledClass? {
        var date = now.toLocalDate()
        val last = date.plusDays(withinDays)
        while (!date.isAfter(last)) {
            classesOn(timetable, date).firstOrNull { it.start.isAfter(now) }?.let { return it }
            date = date.plusDays(1)
        }
        return null
    }

    /**
     * Suggested course for a lesson that is about to start: the current class, otherwise the next
     * one today if it begins within [aheadMinutes].
     */
    fun suggestion(timetable: Timetable, now: LocalDateTime, aheadMinutes: Long = 60): ScheduledClass? =
        current(timetable, now) ?: classesOn(timetable, now.toLocalDate()).firstOrNull {
            it.start.isAfter(now) && !it.start.isAfter(now.plusMinutes(aheadMinutes))
        }
}
