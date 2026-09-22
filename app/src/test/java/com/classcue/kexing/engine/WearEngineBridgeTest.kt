package com.classcue.kexing.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class WearEngineBridgeTest {
    @Test
    fun payloadKeepsWatchProtocolFieldsAndBoundsLargeText() {
        val payload = WearEngineBridge.payload(
            eventId = 42L,
            kind = WearEngineBridge.KIND_ANSWER,
            title = "标题".repeat(100),
            question = "问题",
            tier = "强力",
            model = "model",
            answer = "答案".repeat(2_000),
            vibrate = false
        )

        assertTrue(payload.startsWith("{\"schema\":1,"))
        assertTrue(payload.contains("\"eventId\":\"42\""))
        assertTrue(payload.contains("\"kind\":\"answer\""))
        assertTrue(payload.contains("\"vibrate\":false"))
        assertTrue(payload.contains("\"title\":\"${"标题".repeat(40)}\""))
        assertTrue(payload.contains("\"answer\":\"${"答案".repeat(1_750)}\""))
        assertTrue(payload.contains("\"sentAt\":") && payload.endsWith("}"))
    }
}
