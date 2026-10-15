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
