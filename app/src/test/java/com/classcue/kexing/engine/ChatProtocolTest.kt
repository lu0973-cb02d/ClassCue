package com.classcue.kexing.engine

import com.classcue.kexing.model.AiProfile
import com.classcue.kexing.model.AiTier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProtocolTest {
    private val profile = AiProfile("p", AiTier.FAST, "https://example.com", "deepseek-chat")

    @Test
    fun streamingRequestCarriesLimitsAndStreamFlag() {
        val body = JSONObject(ChatProtocol.requestBody(profile, "", "什么是机会成本", "上下文", stream = true))
        assertTrue(body.getBoolean("stream"))
        assertEquals(1_024, body.getInt("max_tokens"))
        assertEquals(2, body.getJSONArray("messages").length())
        val bare = JSONObject(ChatProtocol.requestBody(profile, "", "q", "c", stream = false, includeSamplingLimits = false))
        assertFalse(bare.has("max_tokens") || bare.has("temperature") || bare.has("stream"))
    }

    @Test
    fun recognisesProvidersThatRejectMaxTokens() {
        val openAi = """{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."}}"""
        assertTrue(ChatProtocol.rejectsSamplingLimits(400, openAi))
        assertTrue(ChatProtocol.rejectsSamplingLimits(400, """{"error":"temperature does not support 0.2 with this model"}"""))
        assertFalse(ChatProtocol.rejectsSamplingLimits(401, openAi))
        assertFalse(ChatProtocol.rejectsSamplingLimits(400, """{"error":"invalid api key"}"""))
    }

    @Test
    fun accumulatesStreamedDeltasAndStopsAtDone() {
        val stream = ChatStreamAccumulator()
        val lines = listOf(
            ": keep-alive",
            """data: {"choices":[{"delta":{"role":"assistant","content":null}}]}""",
            """data: {"choices":[{"delta":{"content":"机会成本"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":"是放弃的最高价值。"}}]}""",
            "data: [DONE]",
            """data: {"choices":[{"delta":{"content":"不应出现"}}]}"""
        )
        val updates = mutableListOf<String>()
        for (line in lines) {
            if (stream.done) break
            if (stream.accept(line)) updates += stream.text
        }
        assertEquals(listOf("机会成本", "机会成本是放弃的最高价值。"), updates)
        assertTrue(stream.done)
    }

    @Test
    fun reasoningOnlyStreamIsDetected() {
        val stream = ChatStreamAccumulator()
        stream.accept("""data: {"choices":[{"delta":{"reasoning_content":"先想一想……","content":null}}]}""")
        assertTrue(stream.sawReasoning)
        assertEquals("", stream.text)
    }

    @Test
    fun parsesNonStreamingAnswersIncludingNullContent() {
        assertEquals("答案", ChatProtocol.parseCompletion("""{"choices":[{"message":{"content":" 答案 "}}]}"""))
        assertEquals("", ChatProtocol.parseCompletion("""{"choices":[{"message":{"content":null}}]}"""))
    }
}
