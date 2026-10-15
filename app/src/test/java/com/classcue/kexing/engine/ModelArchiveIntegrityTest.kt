package com.classcue.kexing.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelArchiveIntegrityTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun matchingSizeAndDigestIsVerified() {
        val file = archiveWith("abc")

        val result = ModelArchiveIntegrity.verify(file, ArchiveChecksum(3L, ABC_SHA256))

        assertEquals(ArchiveVerification.Verified, result)
    }

    @Test
    fun streamingDigestSpansMultipleBuffers() {
        // 600 KiB of 'a' crosses the 256 KiB read buffer twice; digest computed independently.
        val file = temp.newFile("large.tar.bz2").apply { writeBytes(ByteArray(600 * 1024) { 0x61 }) }

        val digest = ModelArchiveIntegrity.sha256Hex(file)

        assertEquals("79b1f2657e3325ff16db5d3c65a47b780dd5a145b530e0915401400cff351dd3", digest)
    }

    @Test
    fun truncatedArchiveFailsTheSizeGateBeforeHashing() {
        val file = archiveWith("ab")
        var hashed = false

        val result = ModelArchiveIntegrity.verify(file, ArchiveChecksum(3L, ABC_SHA256)) {
            hashed = true
            true
        }

        assertEquals(ArchiveVerification.SizeMismatch(expectedBytes = 3L, actualBytes = 2L), result)
        assertTrue("size mismatch must not spend time hashing", !hashed)
    }

    @Test
    fun sameLengthWithDifferentContentFailsTheDigest() {
        val file = archiveWith("abd")

        val result = ModelArchiveIntegrity.verify(file, ArchiveChecksum(3L, ABC_SHA256))

        assertTrue(result is ArchiveVerification.DigestMismatch)
        assertEquals(ABC_SHA256, (result as ArchiveVerification.DigestMismatch).expectedSha256)
        assertEquals(64, result.actualSha256.length)
    }

    @Test
    fun missingArchiveIsReportedAsZeroBytes() {
        val missing = File(temp.root, "absent.tar.bz2")

        val result = ModelArchiveIntegrity.verify(missing, ArchiveChecksum(3L, ABC_SHA256))

        assertEquals(ArchiveVerification.SizeMismatch(expectedBytes = 3L, actualBytes = 0L), result)
    }

    @Test
    fun pausingDuringHashingInterruptsWithoutDeletingTheArchive() {
        val file = archiveWith("abc")

        assertThrows(InterruptedException::class.java) {
            ModelArchiveIntegrity.verify(file, ArchiveChecksum(3L, ABC_SHA256)) { false }
        }
        assertTrue(file.isFile)
    }

    @Test
    fun checksumRejectsMalformedPins() {
        assertThrows(IllegalArgumentException::class.java) { ArchiveChecksum(0L, ABC_SHA256) }
        assertThrows(IllegalArgumentException::class.java) { ArchiveChecksum(3L, ABC_SHA256.uppercase()) }
        assertThrows(IllegalArgumentException::class.java) { ArchiveChecksum(3L, ABC_SHA256.dropLast(1)) }
    }

    private fun archiveWith(content: String): File =
        temp.newFile("model.tar.bz2").apply { writeText(content) }

    private companion object {
        // FIPS 180-2 test vector for the message "abc".
        const val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
