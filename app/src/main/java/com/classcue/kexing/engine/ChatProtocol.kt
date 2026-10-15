package com.classcue.kexing.engine

import com.classcue.kexing.model.AiProfile
import com.classcue.kexing.model.AiTier
import org.json.JSONArray
import org.json.JSONObject

/** Request building and response parsing for OpenAI-compatible Chat Completions. */
object ChatProtocol {
    /** Upper bounds, not targets: the prompt asks for short answers; reasoning models need headroom. */
    fun maxTokens(tier: AiTier): Int = if (tier == AiTier.FAST) 1_024 else 2_048

    fun requestBody(
        profile: AiProfile,
        systemPrompt: String,
        question: String,
        recentContext: String,
        stream: Boolean,
        includeSamplingLimits: Boolean = true
    ): String = JSONObject()
        .put("model", profile.model.trim())
        .apply {
            if (includeSamplingLimits) {
                put("temperature", 0.2)
                put("max_tokens", maxTokens(profile.tier))
            }
            if (stream) put("stream", true)
        }
        .put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", ClassroomAiPrompt.system(profile.tier, systemPrompt)))
                .put(JSONObject().put("role", "user").put("content", ClassroomAiPrompt.user(question, recentContext)))
        )
        .toString()

    /**
     * Some providers and models (for example OpenAI reasoning models) reject `max_tokens` or a
     * non-default `temperature`. Such a 400 response is retried once without both.
     */
    fun rejectsSamplingLimits(code: Int, body: String): Boolean {
        if (code != 400) return false
        val text = body.lowercase()
        return ("max_tokens" in text || "temperature" in text) &&
            ("unsupported" in text || "not supported" in text || "max_completion_tokens" in text || "does not support" in text)
    }

    /** Extracts the answer from a non-streaming response. */
    fun parseCompletion(body: String): String {
        val message = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        return if (message.isNull("content")) "" else message.optString("content").trim()
    }
}

/** Accumulates a server-sent-events Chat Completions stream line by line. */
class ChatStreamAccumulator {
    private val builder = StringBuilder()
    var done = false
        private set
    /** The model produced reasoning output (e.g. `reasoning_content`) besides or instead of an answer. */
    var sawReasoning = false
        private set
    val text: String get() = builder.toString()

    /** Returns true when the line appended answer text. */
    fun accept(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return false
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") {
            done = true
            return false
        }
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return false
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return false
        if (!delta.isNull("reasoning_content") && delta.optString("reasoning_content").isNotEmpty()) sawReasoning = true
        if (!delta.isNull("reasoning") && delta.optString("reasoning").isNotEmpty()) sawReasoning = true
        val content = if (delta.isNull("content")) "" else delta.optString("content")
        if (content.isEmpty()) return false
        builder.append(content)
        return true
    }
}
