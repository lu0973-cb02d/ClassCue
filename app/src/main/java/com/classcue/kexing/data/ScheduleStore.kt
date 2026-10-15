package com.classcue.kexing.data

import android.content.Context
import com.classcue.kexing.model.CourseSlot
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.WeekParity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Persists the user's timetable as JSON in private SharedPreferences. */
class ScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences("classcue_timetable", Context.MODE_PRIVATE)
    private val _timetable = MutableStateFlow(TimetableCodec.decode(prefs.getString(KEY, null)))
    val timetable: StateFlow<Timetable> = _timetable

    @Synchronized
    fun update(transform: (Timetable) -> Timetable) {
        val value = transform(_timetable.value)
        prefs.edit().putString(KEY, TimetableCodec.encode(value)).apply()
        _timetable.value = value
    }

    private companion object {
        const val KEY = "timetable_v1"
    }
}

internal object TimetableCodec {
    fun encode(timetable: Timetable): String = JSONObject()
        .put("version", 1)
        .put("semester_start", timetable.semesterStart?.toString())
        .put("reminder_enabled", timetable.reminderEnabled)
        .put("reminder_lead_minutes", timetable.reminderLeadMinutes)
        .put("slots", JSONArray().apply {
            timetable.slots.forEach { slot ->
                put(
                    JSONObject()
                        .put("id", slot.id)
                        .put("course", slot.courseName)
                        .put("weekday", slot.weekday)
                        .put("start", slot.startMinute)
                        .put("end", slot.endMinute)
                        .put("first_week", slot.firstWeek)
                        .put("last_week", slot.lastWeek)
                        .put("parity", slot.parity.name)
                        .put("location", slot.location)
                )
            }
        })
        .toString()

    /** Never throws: a damaged value yields an empty timetable instead of a crash at startup. */
    fun decode(raw: String?): Timetable = runCatching {
        if (raw.isNullOrBlank()) return@runCatching Timetable()
        val json = JSONObject(raw)
        val slots = json.optJSONArray("slots") ?: JSONArray()
        Timetable(
            semesterStart = json.optString("semester_start", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            reminderEnabled = json.optBoolean("reminder_enabled", false),
            reminderLeadMinutes = json.optInt("reminder_lead_minutes", 10).coerceIn(0, 60),
            slots = List(slots.length()) { index ->
                val item = slots.getJSONObject(index)
                CourseSlot(
                    id = item.getString("id"),
                    courseName = item.optString("course").trim(),
                    weekday = item.optInt("weekday", 1),
                    startMinute = item.optInt("start", 0),
                    endMinute = item.optInt("end", 0),
                    firstWeek = item.optInt("first_week", 1),
                    lastWeek = item.optInt("last_week", 20),
                    parity = runCatching { WeekParity.valueOf(item.optString("parity", "ALL")) }
                        .getOrDefault(WeekParity.ALL),
                    location = item.optString("location").trim()
                )
            }.filter { it.isValid }
        )
    }.getOrDefault(Timetable())
}
