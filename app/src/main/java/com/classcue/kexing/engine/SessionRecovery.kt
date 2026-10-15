package com.classcue.kexing.engine

import android.content.Context
import com.classcue.kexing.data.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A lesson whose process ended without the user pressing "stop". */
data class InterruptedSession(
    val courseName: String,
    val startedAt: Long,
    val recordingName: String?,
    /** Name of the WAV rebuilt from the abandoned partial file, if any audio survived. */
    val recoveredRecordingName: String?,
    val recoveredDurationMs: Long
)

/**
 * Tracks whether a lesson is in progress so that a lesson cut off by the system (process
 * killed, phone rebooted) can be detected on the next start, its audio recovered right away,
 * and the user told about it. The listening service is no longer restarted automatically,
 * because Android 12+ does not let an app open the microphone from the background.
 */
object SessionRecovery {
    private const val PREFS = "service_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_RECORDING = "active_recording"
    private const val KEY_COURSE = "active_course"
    private const val KEY_STARTED_AT = "active_started_at"
    private const val KEY_INTERRUPTED = "interrupted"
    private const val KEY_INTERRUPTED_RECORDING = "interrupted_recording"
    private const val KEY_INTERRUPTED_RECOVERED = "interrupted_recovered"
    private const val KEY_INTERRUPTED_COURSE = "interrupted_course"
    private const val KEY_INTERRUPTED_STARTED_AT = "interrupted_started_at"
    private const val KEY_INTERRUPTED_DURATION = "interrupted_duration_ms"

    private val _interrupted = MutableStateFlow<InterruptedSession?>(null)
    val interrupted: StateFlow<InterruptedSession?> = _interrupted

    fun markActive(context: Context, recordingName: String?, courseName: String, startedAt: Long) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_RECORDING, recordingName)
            .putString(KEY_COURSE, courseName)
            .putLong(KEY_STARTED_AT, startedAt)
            .apply()
    }

    fun markStopped(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_RECORDING)
            .remove(KEY_COURSE)
            .remove(KEY_STARTED_AT)
            .apply()
    }

    /**
     * Must run once per process start, before the user can start a new lesson. A lesson
     * still marked active at this point belonged to a process that is gone.
     */
    fun detect(context: Context, recordings: RecordingRepository) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_ACTIVE, false)) {
            val recordingName = prefs.getString(KEY_RECORDING, null)
            val recovered = recordings.sweepPartials(
                deadOwnerNames = setOfNotNull(recordingName)
            )
            val base = recordingName?.substringBeforeLast('.')
            val wav = recordings.recordings.value.firstOrNull { info ->
                base != null && info.file.nameWithoutExtension.startsWith(base)
            }
            prefs.edit()
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_INTERRUPTED, true)
                .putString(KEY_INTERRUPTED_RECORDING, recordingName)
                .putString(KEY_INTERRUPTED_RECOVERED, wav?.file?.name ?: recovered.firstOrNull())
                .putString(KEY_INTERRUPTED_COURSE, prefs.getString(KEY_COURSE, "").orEmpty())
                .putLong(KEY_INTERRUPTED_STARTED_AT, prefs.getLong(KEY_STARTED_AT, 0L))
                .putLong(KEY_INTERRUPTED_DURATION, wav?.estimatedDurationMs ?: 0L)
                .apply()
        }
        _interrupted.value = if (prefs.getBoolean(KEY_INTERRUPTED, false)) {
            InterruptedSession(
                courseName = prefs.getString(KEY_INTERRUPTED_COURSE, "").orEmpty(),
                startedAt = prefs.getLong(KEY_INTERRUPTED_STARTED_AT, 0L),
                recordingName = prefs.getString(KEY_INTERRUPTED_RECORDING, null),
                recoveredRecordingName = prefs.getString(KEY_INTERRUPTED_RECOVERED, null),
                recoveredDurationMs = prefs.getLong(KEY_INTERRUPTED_DURATION, 0L)
            )
        } else null
    }

    fun dismiss(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_INTERRUPTED, false)
            .remove(KEY_INTERRUPTED_RECORDING)
            .remove(KEY_INTERRUPTED_RECOVERED)
            .remove(KEY_INTERRUPTED_COURSE)
            .remove(KEY_INTERRUPTED_STARTED_AT)
            .remove(KEY_INTERRUPTED_DURATION)
            .apply()
        _interrupted.value = null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
