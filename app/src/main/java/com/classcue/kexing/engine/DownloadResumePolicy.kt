package com.classcue.kexing.engine

enum class PartialDownloadAction {
    /** Nothing usable on disk, or more bytes than the pinned archive: start from zero. */
    RESTART,
    /** A shorter partial file: continue with an HTTP Range request. */
    RESUME,
    /** Exactly the pinned length is already on disk: skip the network and verify it. */
    COMPLETE
}

object DownloadResumePolicy {
    fun isAlreadyComplete(existingBytes: Long, responseCode: Int, rangeTotal: Long): Boolean =
        responseCode == 416 && rangeTotal > 0L && existingBytes == rangeTotal

    fun shouldAppend(existingBytes: Long, responseCode: Int): Boolean =
        responseCode == 206 && existingBytes > 0L

    fun resolvedTotal(
        existingBytes: Long,
        append: Boolean,
        contentLength: Long,
        rangeTotal: Long,
        expectedBytes: Long
    ): Long = when {
        rangeTotal > 0L -> rangeTotal
        contentLength > 0L -> (if (append) existingBytes else 0L) + contentLength
        else -> expectedBytes
    }

    fun partialAction(existingBytes: Long, pinnedBytes: Long): PartialDownloadAction = when {
        existingBytes <= 0L || existingBytes > pinnedBytes -> PartialDownloadAction.RESTART
        existingBytes == pinnedBytes -> PartialDownloadAction.COMPLETE
        else -> PartialDownloadAction.RESUME
    }

    /**
     * Full size the server claims for the resource, or -1 when it does not say.
     * A 206 or 416 reports it through Content-Range; a plain 200 through Content-Length.
     */
    fun advertisedTotal(responseCode: Int, contentLength: Long, rangeTotal: Long): Long = when {
        rangeTotal > 0L -> rangeTotal
        responseCode == 200 && contentLength > 0L -> contentLength
        else -> -1L
    }

    /** True when the server is clearly offering a different file than the pinned archive. */
    fun conflictsWithPinnedSize(advertisedTotal: Long, pinnedBytes: Long): Boolean =
        advertisedTotal > 0L && advertisedTotal != pinnedBytes
}
