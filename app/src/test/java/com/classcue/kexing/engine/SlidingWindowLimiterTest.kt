package com.classcue.kexing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingWindowLimiterTest {
    @Test
    fun blocksOnceTheWindowIsFullAndReopensAsOldEventsExpire() {
        val limiter = SlidingWindowLimiter(maxEvents = 2, windowMs = 600_000L)
        assertTrue(limiter.tryAcquire(0L))
        assertTrue(limiter.tryAcquire(60_000L))
        assertFalse(limiter.tryAcquire(120_000L))
        assertEquals(480_000L, limiter.waitMs(120_000L))
        assertTrue(limiter.tryAcquire(600_000L))
        assertFalse(limiter.tryAcquire(600_001L))
        assertTrue(limiter.tryAcquire(660_000L))
    }

    @Test
    fun rejectedAttemptsDoNotConsumeCapacity() {
        val limiter = SlidingWindowLimiter(maxEvents = 1, windowMs = 1_000L)
        assertTrue(limiter.tryAcquire(0L))
        repeat(5) { assertFalse(limiter.tryAcquire(500L)) }
        assertTrue(limiter.tryAcquire(1_000L))
    }
}
