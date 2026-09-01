package com.classcue.kexing.data

import android.content.Context
import com.classcue.kexing.model.RecordingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class RecordingRepository(private val context: Context) {
    companion object {
        private const val WAV_BYTES_PER_SECOND = 16_000L * 2L
        private val STALE_PARTIAL_MS = TimeUnit.HOURS.toMillis(2)
    }

    private val _recordings = MutableStateFlow<List<RecordingInfo>>(emptyList())
    val recordings: StateFlow<List<RecordingInfo>> = _recordings

    val directory: File by lazy {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        File(root, "recordings").also { it.mkdirs() }
    }

    init {
        recoverInterruptedRecordings()
        cleanupExpired()
        refresh()
    }

    fun newSessionFile(now: Long = System.currentTimeMillis()): File {
        val filename = "class_${now}.wav"
        return File(directory, filename)
    }

    @Synchronized
    fun cleanupExpired(now: Long = System.currentTimeMillis()): Int {
        return cleanupStalePartials(now)
    }

    /** Completed recordings are permanent; only abandoned temporary files are cleaned automatically. */
    @Synchronized
    fun cleanupStalePartials(now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        directory.listFiles()?.forEach { file ->
            val isIncompleteStale = file.extension.equals("partial", ignoreCase = true) &&
                file.lastModified() < now - STALE_PARTIAL_MS
            if (isIncompleteStale) {
                if (file.delete()) removed++
            }
        }
        refresh(now)
        return removed
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
    fun recoverInterruptedRecordings(now: Long = System.currentTimeMillis()): Int {
        var recovered = 0
        directory.listFiles()?.filter {
            it.extension == "partial" && it.length() > 44L && it.lastModified() < now - TimeUnit.MINUTES.toMillis(2)
        }?.forEach { partial ->
            if (repairWavHeader(partial)) {
                val target = File(partial.parentFile, partial.nameWithoutExtension + ".wav")
                val modifiedAt = partial.lastModified()
                if (partial.renameTo(target)) {
                    target.setLastModified(modifiedAt)
                    recovered++
                }
            }
        }
        if (recovered > 0) refresh(now)
        return recovered
    }

    private fun repairWavHeader(file: File): Boolean = runCatching {
        val dataBytes = file.length() - 44L
        RandomAccessFile(file, "rw").use { raf ->
            fun writeIntLE(value: Int) {
                raf.write(value and 0xff)
                raf.write(value ushr 8 and 0xff)
                raf.write(value ushr 16 and 0xff)
                raf.write(value ushr 24 and 0xff)
            }
            fun writeShortLE(value: Int) {
                raf.write(value and 0xff)
                raf.write(value ushr 8 and 0xff)
            }
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeIntLE((36L + dataBytes).coerceAtMost(0xffffffffL).toInt())
            raf.writeBytes("WAVEfmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(1)
            writeIntLE(16_000)
            writeIntLE(32_000)
            writeShortLE(2)
            writeShortLE(16)
            raf.writeBytes("data")
            writeIntLE(dataBytes.coerceAtMost(0xffffffffL).toInt())
            raf.fd.sync()
        }
        true
    }.getOrDefault(false)
}
