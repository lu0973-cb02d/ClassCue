package com.classcue.kexing.data

import android.content.Context
import com.classcue.kexing.model.RecordingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingRepository(private val context: Context) {
    companion object {
        private const val WAV_BYTES_PER_SECOND = 16_000L * 2L
    }

    private val _recordings = MutableStateFlow<List<RecordingInfo>>(emptyList())
    val recordings: StateFlow<List<RecordingInfo>> = _recordings

    val directory: File by lazy {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        File(root, "recordings").also { it.mkdirs() }
    }

    private val files by lazy { RecordingFiles(directory) }

    init {
        sweepPartials()
    }

    fun newSessionFile(now: Long = System.currentTimeMillis()): File {
        val filename = "class_${now}.wav"
        return File(directory, filename)
    }

    @Synchronized
    fun cleanupExpired(now: Long = System.currentTimeMillis()): Int = sweepPartials(now).size

    /**
     * Repairs abandoned partial recordings and removes only partials that hold no audio.
     * [deadOwnerNames] marks recordings whose session is known to have died, so their
     * audio is recovered immediately instead of after the live-write grace period.
     */
    @Synchronized
    fun sweepPartials(
        now: Long = System.currentTimeMillis(),
        deadOwnerNames: Set<String> = emptySet()
    ): List<String> {
        val recovered = files.sweepPartials(now, deadOwnerNames)
        refresh(now)
        return recovered
    }

    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()) {
        _recordings.value = directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val startedAt = file.nameWithoutExtension
                    .removePrefix("class_")
                    .toLongOrNull() ?: file.lastModified()
                val audioBytes = (file.length() - 44L).coerceAtLeast(0L)
                RecordingInfo(
                    file = file,
                    startedAt = startedAt,
                    bytes = file.length(),
                    estimatedDurationMs = audioBytes * 1000L / WAV_BYTES_PER_SECOND
                )
            }.orEmpty()
    }

    fun totalBytes(): Long = _recordings.value.sumOf { it.bytes }

    @Synchronized
    fun delete(file: File): Boolean {
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (candidate.parentFile != root || !candidate.extension.equals("wav", ignoreCase = true)) return false
        val removed = candidate.delete()
        if (removed) refresh()
        return removed
    }

    fun deleteByName(fileName: String): Boolean {
        if (fileName != File(fileName).name) return false
        return delete(File(directory, fileName))
    }

    @Synchronized
    fun recoverInterruptedRecordings(now: Long = System.currentTimeMillis()): Int = sweepPartials(now).size
}
