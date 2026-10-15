package com.classcue.kexing.data

import android.content.Context
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.AiProfile
import com.classcue.kexing.model.AiTier
import com.classcue.kexing.model.AutoAnswerScope
import com.classcue.kexing.model.DetectionMode
import com.classcue.kexing.model.TranscriptionModelSize
import com.classcue.kexing.engine.AudioInputStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("classcue_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val value = transform(_settings.value)
        write(value)
        _settings.value = value
    }

    private fun read(): AppSettings = AppSettings(
        courseName = prefs.getString("course_name", "").orEmpty(),
        displayName = prefs.getString("display_name", "").orEmpty(),
        studentId = prefs.getString("student_id", "").orEmpty(),
        aliases = parseList(prefs.getString("aliases", null)),
        customKeywords = readKeywords(),
        sensitivity = prefs.getInt("sensitivity", 3).coerceIn(1, 5),
        detectionMode = runCatching {
            DetectionMode.valueOf(prefs.getString("detection_mode", DetectionMode.ALL.name).orEmpty())
        }.getOrDefault(DetectionMode.ALL),
        preferOnDevice = prefs.getBoolean("prefer_on_device", true),
        transcriptionModelSize = runCatching {
            TranscriptionModelSize.valueOf(
                prefs.getString("transcription_model_size", TranscriptionModelSize.MEDIUM.name).orEmpty()
            )
        }.getOrDefault(TranscriptionModelSize.MEDIUM),
        audioInputStrategy = runCatching {
            AudioInputStrategy.valueOf(
                prefs.getString("audio_input_strategy", AudioInputStrategy.AUTO.name).orEmpty()
            )
        }.getOrDefault(AudioInputStrategy.AUTO),
        farFieldEnhancementEnabled = prefs.getBoolean("far_field_enhancement_enabled", true),
        phoneVibrationEnabled = readPhoneVibrationEnabled(),
        wearEngineEnabled = prefs.getBoolean("wear_engine_enabled", true),
        watchVibrationEnabled = prefs.getBoolean("watch_vibration_enabled", true),
        wearEngineWatchPackage = prefs.getString(
            "wear_engine_watch_package",
            AppSettings.DEFAULT_WATCH_PACKAGE
        ).orEmpty().ifBlank { AppSettings.DEFAULT_WATCH_PACKAGE },
        wearEngineWatchFingerprint = prefs.getString(
            "wear_engine_watch_fingerprint",
            AppSettings.DEFAULT_WATCH_FINGERPRINT
        ).orEmpty().ifBlank { AppSettings.DEFAULT_WATCH_FINGERPRINT },
        vibrationStrength = prefs.getInt("vibration_strength", 3).coerceIn(1, 5),
        autoAnswer = prefs.getBoolean("auto_answer", true),
        autoAnswerScope = runCatching {
            AutoAnswerScope.valueOf(
                prefs.getString("auto_answer_scope", AutoAnswerScope.DIRECTED_AT_ME.name).orEmpty()
            )
        }.getOrDefault(AutoAnswerScope.DIRECTED_AT_ME),
        aiAutoLimitPer10Min = prefs.getInt("ai_auto_limit", AppSettings.DEFAULT_AI_AUTO_LIMIT).coerceIn(1, 30),
        aiEnabled = prefs.getBoolean("ai_enabled", false),
        aiProfiles = parseProfiles(prefs.getString("ai_profiles", null)).ifEmpty {
            listOf(
                AiProfile.defaultFast().copy(
                    baseUrl = prefs.getString("ai_base_url", "https://api.deepseek.com").orEmpty(),
                    model = prefs.getString("ai_model", "deepseek-chat").orEmpty()
                )
            )
        },
        aiSystemPrompt = prefs.getString("ai_prompt", AppSettings.DEFAULT_PROMPT).orEmpty(),
        eventCooldownSeconds = prefs.getInt("cooldown", 15).coerceIn(5, 120),
        contextBeforeSeconds = prefs.getInt("context_before", 60).coerceIn(30, 120),
        followupWindowSeconds = prefs.getInt("followup_window", 60).coerceIn(30, 120)
    )

    private fun write(value: AppSettings) {
        prefs.edit()
            .putString("course_name", value.courseName)
            .putString("display_name", value.displayName)
            .putString("student_id", value.studentId)
            .putString("aliases", JSONArray(value.aliases).toString())
            .putString("keywords", JSONArray(value.customKeywords).toString())
            .putInt("keyword_defaults_version", KEYWORD_DEFAULTS_VERSION)
            .putInt("sensitivity", value.sensitivity)
            .putString("detection_mode", value.detectionMode.name)
            .putBoolean("prefer_on_device", value.preferOnDevice)
            .putString("transcription_model_size", value.transcriptionModelSize.name)
            .putString("audio_input_strategy", value.audioInputStrategy.name)
            .putBoolean("far_field_enhancement_enabled", value.farFieldEnhancementEnabled)
            .putBoolean("phone_vibration_enabled", value.phoneVibrationEnabled)
            .putBoolean("wear_engine_enabled", value.wearEngineEnabled)
            .putBoolean("watch_vibration_enabled", value.watchVibrationEnabled)
            .putString("wear_engine_watch_package", value.wearEngineWatchPackage)
            .putString("wear_engine_watch_fingerprint", value.wearEngineWatchFingerprint)
            .putInt("vibration_strength", value.vibrationStrength)
            .putBoolean("auto_answer", value.autoAnswer)
            .putString("auto_answer_scope", value.autoAnswerScope.name)
            .putInt("ai_auto_limit", value.aiAutoLimitPer10Min)
            .putBoolean("ai_enabled", value.aiEnabled)
            .putString("ai_profiles", profilesJson(value.aiProfiles))
            .putString("ai_prompt", value.aiSystemPrompt)
            .putInt("cooldown", value.eventCooldownSeconds)
            .putInt("context_before", value.contextBeforeSeconds)
            .putInt("followup_window", value.followupWindowSeconds)
            .apply()
    }

    private fun readKeywords(): List<String> {
        val stored = parseList(prefs.getString("keywords", null))
        val defaultsVersion = prefs.getInt("keyword_defaults_version", 0)
        return if (defaultsVersion < KEYWORD_DEFAULTS_VERSION) {
            (stored + AppSettings.DEFAULT_STRONG_KEYWORDS).distinct()
        } else {
            stored.ifEmpty { AppSettings.DEFAULT_STRONG_KEYWORDS }
        }
    }

    private fun readPhoneVibrationEnabled(): Boolean {
        if (prefs.contains("phone_vibration_enabled")) {
            return prefs.getBoolean("phone_vibration_enabled", true)
        }
        return when (prefs.getString("vibration_target", null)) {
            "PHONE", "BOTH" -> true
            null -> prefs.getBoolean("vibrate", true)
            else -> false
        }
    }

    private fun parseList(value: String?): List<String> = runCatching {
        if (value.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(value)
        List(array.length()) { index -> array.optString(index).trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun parseProfiles(value: String?): List<AiProfile> = runCatching {
        if (value.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(value)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val tier = runCatching { AiTier.valueOf(item.optString("tier", AiTier.FAST.name)) }
                .getOrDefault(AiTier.FAST)
            AiProfile(
                id = item.getString("id"),
                tier = tier,
                baseUrl = item.optString("base_url"),
                model = item.optString("model"),
                enabled = item.optBoolean("enabled", true),
                timeoutSeconds = item.optInt("timeout", if (tier == AiTier.FAST) 10 else 35).coerceIn(5, 60)
            )
        }.filter { it.id.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun profilesJson(profiles: List<AiProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                org.json.JSONObject()
                    .put("id", profile.id)
                    .put("tier", profile.tier.name)
                    .put("base_url", profile.baseUrl)
                    .put("model", profile.model)
                    .put("enabled", profile.enabled)
                    .put("timeout", profile.timeoutSeconds)
            )
        }
        return array.toString()
    }

    private companion object {
        const val KEYWORD_DEFAULTS_VERSION = 2
    }
}
