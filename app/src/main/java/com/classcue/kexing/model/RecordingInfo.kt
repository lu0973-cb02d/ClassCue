package com.classcue.kexing.model

import java.io.File

/** One lesson recording, which may be stored as a WAV original, a compressed copy, or both. */
data class RecordingInfo(
    /** File to play: the lossless WAV when it still exists, otherwise the compressed copy. */
    val file: File,
    val startedAt: Long,
    /** Total bytes across all stored formats. */
    val bytes: Long,
    val estimatedDurationMs: Long,
    val wavFile: File? = null,
    val compressedFile: File? = null
) {
    /** File to hand to other apps: the compressed copy is far smaller and widely supported. */
    val shareFile: File get() = compressedFile ?: file
}
