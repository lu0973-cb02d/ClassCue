package com.classcue.kexing.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionTest {
    @Test
    fun parsesRecordingStartFromFileName() {
        assertEquals(1_760_500_000_000L, PlaybackPosition.recordingStartedAt("class_1760500000000.wav"))
        assertEquals(1_760_500_000_000L, PlaybackPosition.recordingStartedAt("class_1760500000000.m4a"))
        assertEquals(1_760_500_000_000L, PlaybackPosition.recordingStartedAt("class_1760500000000_recovered.wav"))
        assertNull(PlaybackPosition.recordingStartedAt("lecture.wav"))
        assertNull(PlaybackPosition.recordingStartedAt(null))
    }

    @Test
    fun stepsBackFromTheMomentTheLineWasFinalized() {
        // 10 characters: 1.8 s pause + 2.5 s speech + 1 s lead-in before the 60 s mark.
        assertEquals(54_700L, PlaybackPosition.estimateStartMs(1_060_000L, 1_000_000L, "一二三四五六七八九十"))
    }

    @Test
    fun usesTheLastLineOfAnAlertContext() {
        val context = "很长很长的上一句话很长很长的上一句话\n李明回答"
        assertEquals(56_200L, PlaybackPosition.estimateStartMs(1_060_000L, 1_000_000L, context))
    }

    @Test
    fun clampsToTheRecording() {
        assertEquals(0L, PlaybackPosition.estimateStartMs(1_001_000L, 1_000_000L, "开头"))
        assertEquals(29_999L, PlaybackPosition.estimateStartMs(2_000_000L, 1_000_000L, "x", durationMs = 30_000L))
    }
}
