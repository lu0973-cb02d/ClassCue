package com.classcue.kexing.engine

import java.io.File
import java.security.MessageDigest

/**
 * Exact identity of an upstream model archive, pinned when a ClassCue release is prepared.
 * A download is only installed when both the byte length and the SHA-256 digest match.
 */
data class ArchiveChecksum(val sizeBytes: Long, val sha256: String) {
    init {
        require(sizeBytes > 0L) { "Archive size must be positive" }
        require(SHA256_HEX.matches(sha256)) { "SHA-256 must be 64 lowercase hexadecimal characters" }
    }

    private companion object {
        val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

sealed interface ArchiveVerification {
    data object Verified : ArchiveVerification
    data class SizeMismatch(val expectedBytes: Long, val actualBytes: Long) : ArchiveVerification
    data class DigestMismatch(val expectedSha256: String, val actualSha256: String) : ArchiveVerification
}

object ModelArchiveIntegrity {
    private const val BUFFER_BYTES = 256 * 1024

    /**
     * Checks the cheap size gate first, then streams the file through SHA-256.
     * Throws [InterruptedException] when [shouldContinue] turns false so a paused
     * download keeps the completed archive and verifies it again on resume.
     */
    fun verify(
        file: File,
        expected: ArchiveChecksum,
        shouldContinue: () -> Boolean = { true }
    ): ArchiveVerification {
        val actualBytes = if (file.isFile) file.length() else 0L
        if (actualBytes != expected.sizeBytes) {
            return ArchiveVerification.SizeMismatch(expected.sizeBytes, actualBytes)
        }
        val actualSha256 = sha256Hex(file, shouldContinue)
        return if (MessageDigest.isEqual(actualSha256.toByteArray(), expected.sha256.toByteArray())) {
            ArchiveVerification.Verified
        } else {
            ArchiveVerification.DigestMismatch(expected.sha256, actualSha256)
        }
    }

    fun sha256Hex(file: File, shouldContinue: () -> Boolean = { true }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                if (!shouldContinue() || Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
