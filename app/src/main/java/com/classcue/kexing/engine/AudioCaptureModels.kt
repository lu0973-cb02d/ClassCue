package com.classcue.kexing.engine

/** Controls how ClassCue asks Android to route the physical microphone input. */
enum class AudioInputStrategy {
    /** Let Android follow the currently active wired, USB, Bluetooth, or built-in route. */
    AUTO,

    /** Prefer the phone's own microphone and fall back to Android routing if unavailable. */
    BUILT_IN,

    /** Prefer an attached wired, USB, or Bluetooth microphone and fall back if absent. */
    EXTERNAL
}

/** Per-buffer measurements intended for a lightweight input-quality display. */
data class AudioInputDiagnostics(
    val rawLevelPercent: Int,
    val rawRmsDbfs: Double,
    val enhancedLevelPercent: Int,
    val enhancedRmsDbfs: Double,
    val appliedGainDb: Double,
    val limiterReductionDb: Double,
    /** Fraction of samples that entered the soft limiter knee, from 0.0 to 1.0. */
    val clippedSampleRatio: Double,
    /** Whether this buffer contains enough sustained energy to be treated as speech. */
    val speechLikely: Boolean
)
