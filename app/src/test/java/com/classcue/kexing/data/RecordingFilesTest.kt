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

class RecordingGroupsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun groupsFormatsByLessonAndFindsWorkToDo() {
        val dir = temp.newFolder("recordings")
        File(dir, "class_1.wav").writeBytes(ByteArray(100))
        File(dir, "class_2.wav").writeBytes(ByteArray(100))
        File(dir, "class_2.m4a").writeBytes(ByteArray(10))
        File(dir, "class_3.m4a").writeBytes(ByteArray(10))
        File(dir, "class_4.partial").writeBytes(ByteArray(100))
        File(dir, "notes.txt").writeText("x")
        val files = RecordingFiles(dir)

        assertEquals(setOf("class_1", "class_2", "class_3"), files.groups().map { it.baseName }.toSet())
        assertEquals(listOf("class_1.wav"), files.pendingCompression().map { it.name })
        assertEquals(listOf("class_2.wav"), files.compressedOriginals().map { it.name })
        assertEquals("class_1.m4a", files.compressedFileFor(File(dir, "class_1.wav")).name)
    }

    @Test
    fun wavInfoReadsTheHeaderWrittenByTheRecorder() {
        val file = temp.newFile("class_9.wav")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(44 + 64_000)
            WavHeader.write(raf, dataBytes = 64_000)
        }
        val info = WavInfo.read(file)!!
        assertEquals(16_000, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(2_000L, info.durationMs)
    }

    @Test
    fun wavInfoRejectsNonPcmOrTruncatedFiles() {
        assertEquals(null, WavInfo.read(temp.newFile("empty.wav")))
        assertEquals(null, WavInfo.read(temp.newFile("junk.wav").apply { writeBytes(ByteArray(100) { 7 }) }))
    }

    @Test
    fun staleCompressionLeftoversAreRemoved() {
        val dir = temp.newFolder("recordings")
        val now = 1_800_000_000_000L
        val stale = File(dir, "class_1.m4a.tmp").apply { writeBytes(ByteArray(5)); setLastModified(now - 3 * 3_600_000L) }
        val fresh = File(dir, "class_2.m4a.tmp").apply { writeBytes(ByteArray(5)); setLastModified(now - 60_000L) }
        RecordingFiles(dir).sweepTemporaryFiles(now)
        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }
}

class CompressionCheckTest {
    @Test
    fun acceptsSmallCodecPaddingAndRejectsTruncation() {
        assertTrue(CompressionCheck.durationMatches(5_400_000L, 5_400_064L))
        assertTrue(CompressionCheck.durationMatches(2_000L, 2_900L))
        assertFalse(CompressionCheck.durationMatches(5_400_000L, 5_000_000L))
        assertFalse(CompressionCheck.durationMatches(2_000L, 0L))
    }
}
