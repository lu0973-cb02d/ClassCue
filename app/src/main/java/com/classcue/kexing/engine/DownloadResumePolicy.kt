package com.classcue.kexing.engine

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
}
