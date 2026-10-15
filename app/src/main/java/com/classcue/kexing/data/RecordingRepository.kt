package com.classcue.kexing.data

import android.content.Context
import com.classcue.kexing.model.RecordingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingRepository(private val context: Context) {
    private val _recordings = MutableStateFlow<List<RecordingInfo>>(emptyList())
    val recordings: StateFlow<List<RecordingInfo>> = _recordings

    val directory: File by lazy {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        File(root, "recordings").also { it.mkdirs() }
    }

    private val files by lazy { RecordingFiles(directory) }
    /** Durations of compressed copies, recorded when they are verified (an .m4a header is costly to read on the main thread). */
    private val meta by lazy { context.getSharedPreferences("recording_meta", Context.MODE_PRIVATE) }

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
        files.sweepTemporaryFiles(now)
        refresh(now)
        return recovered
    }

    @Synchronized
    fun refresh(@Suppress("UNUSED_PARAMETER") now: Long = System.currentTimeMillis()) {
        _recordings.value = files.groups()
            .mapNotNull { group ->
                val wav = group.wav
                val compressed = group.compressed
                val preferred = wav ?: compressed ?: return@mapNotNull null
                val startedAt = group.baseName.removePrefix("class_").substringBefore('_').toLongOrNull()
                    ?: preferred.lastModified()
                val duration = when {
                    wav != null -> ((wav.length() - WavHeader.SIZE).coerceAtLeast(0L)) * 1_000L / WAV_BYTES_PER_SECOND
                    compressed != null -> meta.getLong(compressed.name, 0L)
                    else -> 0L
                }
                RecordingInfo(
                    file = preferred,
                    startedAt = startedAt,
                    bytes = (wav?.length() ?: 0L) + (compressed?.length() ?: 0L),
                    estimatedDurationMs = duration,
                    wavFile = wav,
                    compressedFile = compressed
                )
            }
            .sortedByDescending { it.startedAt }
    }

    fun totalBytes(): Long = _recordings.value.sumOf { it.bytes }

    /** Deletes every stored format of the lesson recording that [file] belongs to. */
    @Synchronized
    fun delete(file: File): Boolean {
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (candidate.parentFile != root) return false
        if (candidate.extension.lowercase() !in setOf(RecordingFiles.WAV, RecordingFiles.COMPRESSED)) return false
        val group = files.groups().firstOrNull { it.baseName == candidate.nameWithoutExtension } ?: return false
        var removed = true
        group.wav?.let { removed = it.delete() && removed }
        group.compressed?.let {
            removed = it.delete() && removed
            meta.edit().remove(it.name).apply()
        }
        refresh()
        return removed
    }

    fun deleteByName(fileName: String): Boolean {
        if (fileName != File(fileName).name) return false
        return delete(File(directory, fileName))
    }

    @Synchronized
    fun recoverInterruptedRecordings(now: Long = System.currentTimeMillis()): Int = sweepPartials(now).size

    /** WAV files still waiting for a compressed copy. */
    @Synchronized
    fun pendingCompression(): List<File> = files.pendingCompression()

    /** Bytes that deleting the already-compressed WAV originals would free. */
    fun reclaimableBytes(): Long = files.compressedOriginals().sumOf { it.length() }

    /**
     * Compresses one WAV, keeping the original unless [deleteOriginal] is set. The original is
     * only removed after the compressed copy has passed its duration check.
     */
    fun compress(wav: File, deleteOriginal: Boolean, shouldContinue: () -> Boolean): Boolean {
        val output = files.compressedFileFor(wav)
        val durationMs = RecordingCompressor.compress(wav, output, shouldContinue)
        meta.edit().putLong(output.name, durationMs).apply()
        if (deleteOriginal) wav.delete()
        synchronized(this) { refresh() }
        return true
    }

    /** Deletes WAV originals that have a verified compressed copy. Returns bytes freed. */
    @Synchronized
    fun deleteCompressedOriginals(): Long {
        var freed = 0L
        files.compressedOriginals().forEach { wav ->
            val size = wav.length()
            if (wav.delete()) freed += size
        }
        refresh()
        return freed
    }

    private companion object {
        const val WAV_BYTES_PER_SECOND = 16_000L * 2L
    }
}
