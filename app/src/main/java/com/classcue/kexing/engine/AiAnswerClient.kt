package com.classcue.kexing.engine

import com.classcue.kexing.model.AiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AiAnswerClient {
    private class SamplingLimitsRejected : Exception()

    /**
     * Asks one model. With [onPartial], the answer is streamed and [onPartial] receives the text
     * so far as it grows; servers that ignore `stream` still work through the normal JSON path.
     */
    suspend fun answer(
        profile: AiProfile,
        systemPrompt: String,
        apiKey: String,
        question: String,
        recentContext: String,
        onPartial: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "请先在设置中填写 API Key" }
            require(profile.baseUrl.trim().startsWith("https://")) { "AI 接口必须使用 HTTPS" }
            require(profile.model.isNotBlank()) { "模型名称不能为空" }
            val stream = onPartial != null
            try {
                post(profile, apiKey, ChatProtocol.requestBody(profile, systemPrompt, question, recentContext, stream), onPartial)
            } catch (_: SamplingLimitsRejected) {
                val body = ChatProtocol.requestBody(profile, systemPrompt, question, recentContext, stream, includeSamplingLimits = false)
                post(profile, apiKey, body, onPartial)
            }
        }
    }

    private fun post(profile: AiProfile, apiKey: String, body: String, onPartial: ((String) -> Unit)?): String {
        val connection = URL(endpoint(profile.baseUrl)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = minOf(profile.timeoutSeconds * 1_000, 12_000)
            connection.readTimeout = profile.timeoutSeconds * 1_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", if (onPartial != null) "text/event-stream, application/json" else "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (ChatProtocol.rejectsSamplingLimits(code, raw)) throw SamplingLimitsRejected()
                throw IllegalStateException("AI 接口返回 $code：${raw.take(180)}")
            }
            val isStream = connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)
            val answer = if (isStream) {
                val accumulator = ChatStreamAccumulator()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (!accumulator.done) {
                        val line = reader.readLine() ?: break
                        if (accumulator.accept(line)) onPartial?.invoke(accumulator.text)
                    }
                }
                accumulator.text.trim().also {
                    require(it.isNotEmpty()) {
                        if (accumulator.sawReasoning) "模型只返回了思考过程、没有给出答案；可调大超时或换用非推理模型"
                        else "AI 没有返回答案"
                    }
                }
            } else {
                val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ChatProtocol.parseCompletion(raw).also { require(it.isNotEmpty()) { "AI 没有返回答案" } }
            }
            return answer
        } finally {
            connection.disconnect()
        }
    }

    private fun endpoint(base: String): String {
        val clean = base.trim().trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean
        else if (clean.endsWith("/v1")) "$clean/chat/completions"
        else "$clean/v1/chat/completions"
    }
}
