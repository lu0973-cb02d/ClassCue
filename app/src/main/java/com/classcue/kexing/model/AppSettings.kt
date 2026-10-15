package com.classcue.kexing.model

import com.classcue.kexing.engine.ClassifierConfig
import com.classcue.kexing.engine.AudioInputStrategy
import com.classcue.kexing.engine.ModelDownloadSource

enum class DetectionMode { ALL, NAMED_ONLY, ROLL_CALL_ONLY }
enum class AiTier { FAST, STRONG }
enum class TranscriptionModelSize { SMALL, MEDIUM, LARGE }

/** Which alerts send the classroom text to AI automatically (manual re-asks are always allowed). */
enum class AutoAnswerScope { DIRECTED_AT_ME, ALL_QUESTIONS }

data class AiProfile(
    val id: String,
    val tier: AiTier,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean = true,
    val timeoutSeconds: Int = if (tier == AiTier.FAST) 10 else 35
) {
    companion object {
        fun defaultFast() = AiProfile(
            id = "default_fast",
            tier = AiTier.FAST,
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat",
            timeoutSeconds = 10
        )
    }
}

data class AppSettings(
    val courseName: String = "",
    val displayName: String = "",
    val studentId: String = "",
    val aliases: List<String> = emptyList(),
    val customKeywords: List<String> = DEFAULT_STRONG_KEYWORDS,
    val sensitivity: Int = 3,
    val detectionMode: DetectionMode = DetectionMode.ALL,
    val preferOnDevice: Boolean = true,
    val transcriptionModelSize: TranscriptionModelSize = TranscriptionModelSize.MEDIUM,
    /** Preferred model download source; the other one is tried automatically if it fails. */
    val modelDownloadSource: ModelDownloadSource = ModelDownloadSource.GITHUB,
    val audioInputStrategy: AudioInputStrategy = AudioInputStrategy.AUTO,
    val farFieldEnhancementEnabled: Boolean = true,
    /** Experimental: bias the medium/large streaming recognizer toward the user's name. */
    val nameHotwordsEnabled: Boolean = false,
    /** Use 2 recognition threads instead of 4 to reduce heat and battery drain. */
    val powerSavingRecognition: Boolean = false,
    /** Compress finished lesson WAVs to AAC in the background (about 1/8 of the size). */
    val compressRecordings: Boolean = true,
    /** Delete each WAV once its compressed copy has been verified. Off: keep both until deleted by hand. */
    val deleteWavAfterCompression: Boolean = false,
    val phoneVibrationEnabled: Boolean = true,
    /** Send cue/answer messages to the companion Lite Wearable app over Wear Engine. */
    val wearEngineEnabled: Boolean = true,
    val watchVibrationEnabled: Boolean = true,
    val wearEngineWatchPackage: String = DEFAULT_WATCH_PACKAGE,
    /** SHA-256 of the signed watch HAP; all-zero is an intentional pre-signing blocker. */
    val wearEngineWatchFingerprint: String = DEFAULT_WATCH_FINGERPRINT,
    val vibrationStrength: Int = 3,
    val autoAnswer: Boolean = true,
    val autoAnswerScope: AutoAnswerScope = AutoAnswerScope.DIRECTED_AT_ME,
    /** Upper bound on automatic AI requests per 10 minutes, to cap cost when alerts misfire. */
    val aiAutoLimitPer10Min: Int = DEFAULT_AI_AUTO_LIMIT,
    val aiEnabled: Boolean = false,
    val aiProfiles: List<AiProfile> = listOf(AiProfile.defaultFast()),
    val aiSystemPrompt: String = DEFAULT_PROMPT,
    val eventCooldownSeconds: Int = 15,
    val contextBeforeSeconds: Int = 60,
    val followupWindowSeconds: Int = 60
) {
    fun classifierConfig(): ClassifierConfig = ClassifierConfig(
        displayName,
        studentId,
        aliases,
        customKeywords,
        sensitivity,
        true,
        detectionMode != DetectionMode.ROLL_CALL_ONLY,
        detectionMode == DetectionMode.ALL
    )

    companion object {
        val DEFAULT_STRONG_KEYWORDS = listOf(
            "点名", "开始点名", "现在点名", "下面点名", "随机点名", "签到", "考勤",
            "答到", "报学号", "喊到名字", "念到名字", "请回答", "你来回答",
            "你来说说", "请你回答", "请这位同学", "哪位同学", "谁来回答",
            "谁愿意回答", "举手回答", "上黑板", "到黑板上", "请起立",
            "我点一个", "我找一位", "抽一位同学"
        )
        const val DEFAULT_PROMPT = """根据老师原话与课堂上下文回答。答案应准确、自然、适合直接口头表达；有多个问题时按“问题1/回答1”编号。信息不足时明确说明，不要编造。"""
        const val DEFAULT_AI_AUTO_LIMIT = 6
        const val DEFAULT_WATCH_PACKAGE = "com.classcue.kexing.watch"
        const val DEFAULT_WATCH_FINGERPRINT = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
