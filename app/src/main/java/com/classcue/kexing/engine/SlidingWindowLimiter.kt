package com.classcue.kexing.engine

import java.util.ArrayDeque

/** Allows at most [maxEvents] acquisitions within any [windowMs] interval. */
class SlidingWindowLimiter(private val maxEvents: Int, private val windowMs: Long) {
    private val acquiredAt = ArrayDeque<Long>()

    init {
        require(maxEvents > 0) { "maxEvents must be positive" }
        require(windowMs > 0L) { "windowMs must be positive" }
    }

    @Synchronized
    fun tryAcquire(now: Long): Boolean {
        evict(now)
        if (acquiredAt.size >= maxEvents) return false
        acquiredAt.addLast(now)
        return true
    }

    /** Milliseconds until the next acquisition would succeed; 0 when one is available now. */
    @Synchronized
    fun waitMs(now: Long): Long {
        evict(now)
        if (acquiredAt.size < maxEvents) return 0L
        return (acquiredAt.first + windowMs - now).coerceAtLeast(0L)
    }

    private fun evict(now: Long) {
        while (acquiredAt.isNotEmpty() && now - acquiredAt.first >= windowMs) acquiredAt.removeFirst()
    }
}
