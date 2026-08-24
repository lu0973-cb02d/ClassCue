package com.classcue.kexing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumePolicyTest {
    @Test
    fun partialContentAppendsToExistingFile() {
        assertTrue(DownloadResumePolicy.shouldAppend(64L, 206))
        assertEquals(256L, DownloadResumePolicy.resolvedTotal(64L, true, 192L, -1L, 999L))
    }

    @Test
    fun fullResponseRestartsInsteadOfDuplicatingExistingBytes() {
        assertFalse(DownloadResumePolicy.shouldAppend(64L, 200))
        assertEquals(192L, DownloadResumePolicy.resolvedTotal(64L, false, 192L, -1L, 999L))
    }

    @Test
    fun rangeTotalOverridesRedirectContentLength() {
        assertEquals(1_000L, DownloadResumePolicy.resolvedTotal(400L, true, 600L, 1_000L, 999L))
    }

    @Test
    fun exactRangeEndIsTreatedAsCompletedDownload() {
        assertTrue(DownloadResumePolicy.isAlreadyComplete(1_000L, 416, 1_000L))
        assertFalse(DownloadResumePolicy.isAlreadyComplete(999L, 416, 1_000L))
    }
}
