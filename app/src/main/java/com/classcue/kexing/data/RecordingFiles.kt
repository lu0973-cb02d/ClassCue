package com.classcue.kexing.data

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** Canonical 44-byte PCM WAV header shared by the recorder and crash recovery. */
object WavHeader {
    const val SIZE = 44L

    fun write(
        raf: RandomAccessFile,
        dataBytes: Long,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        raf.seek(0)
        raf.writeBytes("RIFF")
        writeIntLE(raf, (36L + dataBytes).coerceAtMost(0xffffffffL).toInt())
        raf.writeBytes("WAVEfmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1)
        writeShortLE(raf, channels)
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, blockAlign)
        writeShortLE(raf, bitsPerSample)
        raf.writeBytes("data")
        writeIntLE(raf, dataBytes.coerceAtMost(0xffffffffL).toInt())
    }

    /** Rewrites the header of a file whose writer died before closing it. */
    fun repair(file: File): Boolean = runCatching {
        RandomAccessFile(file, "rw").use { raf ->
            write(raf, (raf.length() - SIZE).coerceAtLeast(0L))
            raf.fd.sync()
        }
        true
    }.getOrDefault(false)

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value ushr 8 and 0xff)
        raf.write(value ushr 16 and 0xff)
        raf.write(value ushr 24 and 0xff)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value ushr 8 and 0xff)
    }
}

/** Format of a finished WAV, read from its header. */
data class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val dataBytes: Long) {
    val durationMs: Long
        get() = if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) 0L
        else dataBytes * 1_000L / (sampleRate.toLong() * channels * (bitsPerSample / 8))

    companion object {
        /** Reads a canonical 44-byte PCM header; data length comes from the file size so a stale header cannot lie. */
        fun read(file: File): WavInfo? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < WavHeader.SIZE) return@use null
                val header = ByteArray(WavHeader.SIZE.toInt())
                raf.readFully(header)
                fun int(at: Int) = (header[at].toInt() and 0xff) or ((header[at + 1].toInt() and 0xff) shl 8) or
                    ((header[at + 2].toInt() and 0xff) shl 16) or ((header[at + 3].toInt() and 0xff) shl 24)
                fun short(at: Int) = (header[at].toInt() and 0xff) or ((header[at + 1].toInt() and 0xff) shl 8)
                val tag = { at: Int -> String(header, at, 4, Charsets.US_ASCII) }
                if (tag(0) != "RIFF" || tag(8) != "WAVE" || tag(36) != "data" || short(20) != 1) return@use null
                WavInfo(
                    sampleRate = int(24),
                    channels = short(22),
                    bitsPerSample = short(34),
                    dataBytes = raf.length() - WavHeader.SIZE
                )
            }
        }.getOrNull()
    }
}

/** All files of one lesson recording: the lossless WAV and/or its compressed copy. */
data class RecordingGroup(val baseName: String, val wav: File?, val compressed: File?)

enum class PartialAction { KEEP, RECOVER, DELETE_EMPTY }

/**
 * Decides what to do with a `.partial` WAV left in the recordings folder.
 *
 * A partial that still holds audio is never deleted: it is repaired into a playable WAV.
 * Only files without any audio are removed. A partial is left alone while it may still be
 * written by a live capture thread, unless the caller knows the session that owned it died.
 */
object PartialRecordingPolicy {
    val LIVE_WRITE_GRACE_MS: Long = TimeUnit.MINUTES.toMillis(2)
    val EMPTY_FILE_GRACE_MS: Long = TimeUnit.HOURS.toMillis(2)

    fun decide(lengthBytes: Long, lastModified: Long, now: Long, ownerKnownDead: Boolean): PartialAction {
        val age = now - lastModified
        return if (lengthBytes > WavHeader.SIZE) {
            if (ownerKnownDead || age >= LIVE_WRITE_GRACE_MS) PartialAction.RECOVER else PartialAction.KEEP
        } else {
            if (ownerKnownDead || age >= EMPTY_FILE_GRACE_MS) PartialAction.DELETE_EMPTY else PartialAction.KEEP
        }
    }
}

/** File-system operations on the recordings folder, kept free of Android types for testing. */
class RecordingFiles(private val directory: File) {
    companion object {
        const val WAV = "wav"
        const val COMPRESSED = "m4a"
        const val TEMP_SUFFIX = ".m4a.tmp"
    }

    fun partialFor(recordingName: String): File =
        File(directory, File(recordingName).nameWithoutExtension + ".partial")

    /**
     * Applies [PartialRecordingPolicy] to every partial file. [deadOwnerNames] lists recordings
     * whose session is known to have ended abnormally, so their partials are recovered at once.
     * Returns the names of the WAV files that were recovered.
     */
    fun sweepPartials(now: Long, deadOwnerNames: Set<String> = emptySet()): List<String> {
        val deadBases = deadOwnerNames.map { File(it).nameWithoutExtension }.toSet()
        val recovered = mutableListOf<String>()
        directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("partial", ignoreCase = true) }
            ?.forEach { partial ->
                val action = PartialRecordingPolicy.decide(
                    lengthBytes = partial.length(),
                    lastModified = partial.lastModified(),
                    now = now,
                    ownerKnownDead = partial.nameWithoutExtension in deadBases
                )
                when (action) {
                    PartialAction.KEEP -> Unit
                    PartialAction.DELETE_EMPTY -> partial.delete()
                    PartialAction.RECOVER -> recover(partial)?.let { recovered += it.name }
                }
            }
        return recovered
    }

    /** Lesson recordings keyed by base name (`class_<start>`), across WAV and AAC copies. */
    fun groups(): List<RecordingGroup> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in setOf(WAV, COMPRESSED) }
        .groupBy { it.nameWithoutExtension }
        .map { (base, files) ->
            RecordingGroup(
                baseName = base,
                wav = files.firstOrNull { it.extension.equals(WAV, ignoreCase = true) },
                compressed = files.firstOrNull { it.extension.equals(COMPRESSED, ignoreCase = true) }
            )
        }

    /** WAV files that do not have a compressed copy yet. */
    fun pendingCompression(): List<File> = groups().filter { it.compressed == null }.mapNotNull { it.wav }

    /** WAV originals whose compressed copy already exists, i.e. space that can be freed. */
    fun compressedOriginals(): List<File> = groups().filter { it.compressed != null }.mapNotNull { it.wav }

    fun compressedFileFor(wav: File): File = File(wav.parentFile, wav.nameWithoutExtension + "." + COMPRESSED)

    /** Removes leftovers of an interrupted compression run. */
    fun sweepTemporaryFiles(now: Long) {
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(TEMP_SUFFIX) && now - it.lastModified() >= PartialRecordingPolicy.EMPTY_FILE_GRACE_MS }
            ?.forEach { it.delete() }
    }

    private fun recover(partial: File): File? {
        if (!WavHeader.repair(partial)) return null
        val modifiedAt = partial.lastModified()
        var target = File(partial.parentFile, partial.nameWithoutExtension + ".wav")
        if (target.exists()) {
            // Never overwrite a finished recording; keep both under distinct names.
            target = File(partial.parentFile, partial.nameWithoutExtension + "_recovered.wav")
        }
        if (!partial.renameTo(target)) return null
        target.setLastModified(modifiedAt)
        return target
    }
}

object CompressionCheck {
    /** AAC frames are 1024 samples, so a faithful copy can differ by a fraction of a second. */
    fun durationMatches(expectedMs: Long, actualMs: Long): Boolean {
        if (expectedMs <= 0L || actualMs <= 0L) return false
        val tolerance = maxOf(1_000L, expectedMs / 100L)
        return kotlin.math.abs(expectedMs - actualMs) <= tolerance
    }
}
