package com.classcue.kexing.model

/**
 * Estimates where a transcript line starts inside the lesson recording.
 *
 * A line is stored when the recognizer finalizes it, i.e. after the speaker finished and the
 * end-of-sentence pause was detected. Seeking to the storage time would land after the sentence,
 * so the estimate steps back by the pause, by a speaking-rate allowance for the line's length and
 * by a short lead-in. Starting a little early is preferable to cutting off the first words.
 */
object PlaybackPosition {
    private const val ENDPOINT_DELAY_MS = 1_800L
    private const val MS_PER_CHARACTER = 250L
    private const val LEAD_IN_MS = 1_000L

    /** Recording start encoded in names like `class_1760500000000.wav`. */
    fun recordingStartedAt(recordingName: String?): Long? = recordingName
        ?.substringBeforeLast('.')
        ?.removePrefix("class_")
        ?.substringBefore('_')
        ?.toLongOrNull()

    fun estimateStartMs(createdAt: Long, recordingStartedAt: Long, text: String, durationMs: Long? = null): Long {
        val characters = text.lineSequence().lastOrNull { it.isNotBlank() }?.trim()?.length ?: 0
        val spoken = ENDPOINT_DELAY_MS + characters * MS_PER_CHARACTER + LEAD_IN_MS
        val start = (createdAt - recordingStartedAt - spoken).coerceAtLeast(0L)
        return if (durationMs != null && durationMs > 0L) start.coerceAtMost(durationMs - 1L) else start
    }
}
