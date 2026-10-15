package com.classcue.kexing.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun partialStillBeingWrittenIsKept() {
        assertEquals(PartialAction.KEEP, PartialRecordingPolicy.decide(10_000L, now - 10_000L, now, false))
        assertEquals(PartialAction.KEEP, PartialRecordingPolicy.decide(44L, now - minute, now, false))
    }

    @Test
    fun abandonedPartialWithAudioIsRecoveredNotDeleted() {
        // Before 0.2.8 a partial older than two hours was deleted even when it held audio.
        assertEquals(PartialAction.RECOVER, PartialRecordingPolicy.decide(10_000L, now - 3 * hour, now, false))
        assertEquals(PartialAction.RECOVER, PartialRecordingPolicy.decide(10_000L, now - 3 * minute, now, false))
    }

    @Test
    fun knownDeadSessionIsRecoveredImmediately() {
        assertEquals(PartialAction.RECOVER, PartialRecordingPolicy.decide(10_000L, now - 1_000L, now, true))
    }

    @Test
    fun onlyEmptyPartialsAreDeleted() {
        assertEquals(PartialAction.DELETE_EMPTY, PartialRecordingPolicy.decide(44L, now - 3 * hour, now, false))
        assertEquals(PartialAction.DELETE_EMPTY, PartialRecordingPolicy.decide(0L, now, now, true))
    }

    @Test
    fun sweepRepairsHeaderAndKeepsAudioBytes() {
        val dir = temp.newFolder("recordings")
        val audio = ByteArray(32_000) { (it % 251).toByte() }
        val partial = File(dir, "class_123.partial").apply {
            writeBytes(ByteArray(44) + audio)
            setLastModified(now - 1_000L)
        }

        val recovered = RecordingFiles(dir).sweepPartials(now, deadOwnerNames = setOf("class_123.wav"))

        assertEquals(listOf("class_123.wav"), recovered)
        assertFalse(partial.exists())
        val wav = File(dir, "class_123.wav")
        val bytes = wav.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(36 + audio.size, header.getInt(4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(audio.size, header.getInt(40))
        assertTrue(audio.contentEquals(bytes.copyOfRange(44, bytes.size)))
    }

    @Test
    fun sweepLeavesLivePartialAndNeverOverwritesFinishedWav() {
        val dir = temp.newFolder("recordings")
        val live = File(dir, "class_live.partial").apply {
            writeBytes(ByteArray(1_000))
            setLastModified(now - 5_000L)
        }
        val finished = File(dir, "class_old.wav").apply { writeText("finished") }
        File(dir, "class_old.partial").apply {
            writeBytes(ByteArray(1_000))
            setLastModified(now - 3 * hour)
        }

        val recovered = RecordingFiles(dir).sweepPartials(now)

        assertTrue(live.exists())
        assertEquals("finished", finished.readText())
        assertEquals(listOf("class_old_recovered.wav"), recovered)
    }

    @Test
    fun headerWriterMatchesRecorderLayout() {
        val file = temp.newFile("x.wav")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(44 + 64)
            WavHeader.write(raf, dataBytes = 64)
        }
        val header = ByteBuffer.wrap(file.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1.toShort(), header.getShort(20))
        assertEquals(1.toShort(), header.getShort(22))
        assertEquals(2.toShort(), header.getShort(32))
        assertEquals(16.toShort(), header.getShort(34))
        assertEquals(64, header.getInt(40))
    }
}
