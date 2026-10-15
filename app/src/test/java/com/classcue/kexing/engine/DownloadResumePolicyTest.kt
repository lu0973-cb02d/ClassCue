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

    @Test
    fun partialFilesAreResumedCompletedOrDiscardedAgainstThePinnedSize() {
        assertEquals(PartialDownloadAction.RESTART, DownloadResumePolicy.partialAction(0L, 1_000L))
        assertEquals(PartialDownloadAction.RESUME, DownloadResumePolicy.partialAction(400L, 1_000L))
        assertEquals(PartialDownloadAction.COMPLETE, DownloadResumePolicy.partialAction(1_000L, 1_000L))
        assertEquals(PartialDownloadAction.RESTART, DownloadResumePolicy.partialAction(1_001L, 1_000L))
    }

    @Test
    fun advertisedTotalPrefersContentRangeAndIgnoresPartialContentLength() {
        assertEquals(1_000L, DownloadResumePolicy.advertisedTotal(206, 600L, 1_000L))
        assertEquals(1_000L, DownloadResumePolicy.advertisedTotal(200, 1_000L, -1L))
        assertEquals(-1L, DownloadResumePolicy.advertisedTotal(206, 600L, -1L))
        assertEquals(-1L, DownloadResumePolicy.advertisedTotal(200, -1L, -1L))
    }

    @Test
    fun serverOfferingADifferentSizeConflictsWithThePin() {
        assertTrue(DownloadResumePolicy.conflictsWithPinnedSize(1_200L, 1_000L))
        assertFalse(DownloadResumePolicy.conflictsWithPinnedSize(1_000L, 1_000L))
        assertFalse("unknown size defers to the post-download digest", DownloadResumePolicy.conflictsWithPinnedSize(-1L, 1_000L))
    }
}
