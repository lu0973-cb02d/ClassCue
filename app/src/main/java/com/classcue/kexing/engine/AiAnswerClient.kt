package com.classcue.kexing.engine

import com.classcue.kexing.model.AiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiAnswerClient {
    suspend fun answer(
        profile: AiProfile,
        systemPrompt: String,
        apiKey: String,
        question: String,
        recentContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "请先在设置中填写 API Key" }
            require(profile.baseUrl.trim().startsWith("https://")) { "AI 接口必须使用 HTTPS" }
            require(profile.model.isNotBlank()) { "模型名称不能为空" }
            val connection = URL(endpoint(profile.baseUrl)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = minOf(profile.timeoutSeconds * 1_000, 12_000)
                connection.readTimeout = profile.timeoutSeconds * 1_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")

                val body = JSONObject()
                    .put("model", profile.model.trim())
                    .put("temperature", 0.2)
                    .put("max_tokens", 320)
                    .put(
                        "messages", JSONArray()
                            .put(
                                JSONObject().put("role", "system").put(
                                    "content",
                                    ClassroomAiPrompt.system(profile.tier, systemPrompt)
                                )
                            )
                            .put(
                                JSONObject().put("role", "user").put(
                                    "content", ClassroomAiPrompt.user(question, recentContext)
                                )
                            )
                    )
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("AI 接口返回 $code：${raw.take(180)}")
                val content = JSONObject(raw)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                require(content.isNotEmpty()) { "AI 没有返回答案" }
                content
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun endpoint(base: String): String {
        val clean = base.trim().trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean
        else if (clean.endsWith("/v1")) "$clean/chat/completions"
        else "$clean/v1/chat/completions"
    }
}
