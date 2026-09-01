package com.classcue.kexing.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.classcue.kexing.ClassCueApplication
import com.classcue.kexing.MainActivity
import com.classcue.kexing.R
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.AiTier
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.TranscriptRecord
import com.classcue.kexing.model.TranscriptionModelSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ListenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val recentUtterances = ArrayDeque<TranscriptLine>()
    private var transcriber: TranscriberEngine? = null
    private var recordingEngine: PcmRecordingEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionStartedAt = 0L
    private var sessionCourseName = ""
    @Volatile private var lastLevelUpdateAt = 0L
    @Volatile private var lastDiagnosticsUpdateAt = 0L
    private var lastAlertAt = 0L
    private var lastAlertKey = ""
    private var lastProvisionalAt = 0L
    private var lastProvisionalKey = ""
    private var identityArmedUntil = 0L
    @Volatile private var latestCueEventId = 0L
    private val aiAnswers = ConcurrentHashMap<Long, ConcurrentHashMap<String, ModelAnswer>>()
    private val aiGenerations = ConcurrentHashMap<Long, AtomicInteger>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                START_NOT_STICKY
            }
            ACTION_TEST_ALERT -> {
                showTestAlert()
                START_NOT_STICKY
            }
            ACTION_RETRY_AI -> {
                retryAi(intent)
                START_NOT_STICKY
            }
            else -> {
                startSession()
                START_STICKY
            }
        }
    }

    private fun startSession() {
        if (!running.compareAndSet(false, true)) return
        recentUtterances.clear()
        identityArmedUntil = 0L
        lastAlertAt = 0L
        lastAlertKey = ""
        lastProvisionalAt = 0L
        lastProvisionalKey = ""
        lastLevelUpdateAt = 0L
        lastDiagnosticsUpdateAt = 0L
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            running.set(false)
            ClassCueRuntime.update { it.copy(phase = SessionPhase.ERROR, statusText = "没有麦克风权限") }
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_LISTENING, listeningNotification("正在启动录音和识别…"))
        sessionStartedAt = System.currentTimeMillis()
        sessionCourseName = ClassCueApplication.settingsStore.settings.value.courseName.trim()
        getSharedPreferences("service_state", MODE_PRIVATE).edit().putBoolean("active", true).apply()
        ClassCueRuntime.update {
            SessionState(
                phase = SessionPhase.STARTING,
                startedAt = sessionStartedAt,
                statusText = "正在启动录音和识别…"
            )
        }
        acquireWakeLock()

        val modelSize = ClassCueApplication.settingsStore.settings.value.transcriptionModelSize
        val recognizerLabel = AtomicReference(when (modelSize) {
            TranscriptionModelSize.SMALL -> "小档 · 离线快速"
            TranscriptionModelSize.MEDIUM -> "中档 · Zipformer 流式"
            TranscriptionModelSize.LARGE -> "大档 · Zipformer 两遍识别"
        })
        val inputRouteLabel = AtomicReference("手机麦克风")
        val onStatus: (String) -> Unit = { message ->
            if (running.get()) {
                ClassCueRuntime.update { it.copy(statusText = "$message · 原始录音已开启") }
                updateListeningNotification("录音中 · $message")
            }
        }
        val onReady: () -> Unit = {
            if (running.get()) {
                ClassCueRuntime.update {
                    it.copy(
                        phase = SessionPhase.LISTENING,
                        statusText = "正在听课 · 离线转写已就绪",
                        recognizerMode = "${recognizerLabel.get()} · ${inputRouteLabel.get()}"
                    )
                }
                updateListeningNotification("正在听课、录音并离线转写")
            }
        }
        val onError: (String) -> Unit = { message ->
            if (running.get()) {
                ClassCueRuntime.update {
                    it.copy(
                        phase = SessionPhase.ERROR,
                        statusText = "离线转写异常：$message；原始录音仍在继续"
                    )
                }
                updateListeningNotification("录音中 · 离线转写需要恢复")
            }
        }
        transcriber = when (modelSize) {
            TranscriptionModelSize.SMALL -> OfflineTranscriber(
                context = applicationContext,
                onStatus = onStatus,
                onReady = onReady,
                onPartial = { text -> if (running.get()) processTranscript(text, isFinal = false) },
                onFinal = { text -> if (running.get()) processTranscript(text, isFinal = true) },
                onError = onError
            )
            TranscriptionModelSize.MEDIUM, TranscriptionModelSize.LARGE -> SherpaTranscriber(
                context = applicationContext,
                secondPass = modelSize == TranscriptionModelSize.LARGE,
                onStatus = onStatus,
                onReady = onReady,
                onPartial = { text -> if (running.get()) processTranscript(text, isFinal = false) },
                onFinal = { text -> if (running.get()) processTranscript(text, isFinal = true) },
                onFallback = {
                    recognizerLabel.set("小档 · 自动兼容")
                    ClassCueRuntime.update { it.copy(recognizerMode = "小档 · 自动兼容 · 荣耀") }
                },
                onError = onError
            )
        }.also { it.start() }

        recordingEngine = PcmRecordingEngine(
            context = applicationContext,
            repository = ClassCueApplication.recordingRepository,
            inputStrategy = ClassCueApplication.settingsStore.settings.value.audioInputStrategy,
            farFieldEnhancementEnabled = ClassCueApplication.settingsStore.settings.value.farFieldEnhancementEnabled,
            onBytesWritten = { bytes ->
                if (bytes % 64_000L < 8_192L) {
                    ClassCueRuntime.update { it.copy(recordingBytes = bytes) }
                }
            },
            onAudioChunk = { pcm -> transcriber?.offer(pcm) },
            onAudioLevel = { level ->
                val now = System.currentTimeMillis()
                if (now - lastLevelUpdateAt >= 250L) {
                    lastLevelUpdateAt = now
                    ClassCueRuntime.update { it.copy(inputLevel = level) }
                }
            },
            onAudioDiagnostics = { diagnostics ->
                val now = System.currentTimeMillis()
                if (now - lastDiagnosticsUpdateAt >= 500L) {
                    lastDiagnosticsUpdateAt = now
                    ClassCueRuntime.update {
                        it.copy(
                            enhancedInputLevel = diagnostics.enhancedLevelPercent,
                            inputRmsDbfs = diagnostics.rawRmsDbfs,
                            appliedInputGainDb = diagnostics.appliedGainDb
                        )
                    }
                }
            },
            onInputRoute = { route ->
                inputRouteLabel.set(route)
                ClassCueRuntime.update { it.copy(recognizerMode = "${recognizerLabel.get()} · $route") }
            },
            onFinalized = { file ->
                ClassCueApplication.recordingRepository.refresh()
                ClassCueRuntime.update { it.copy(recordingName = file.name) }
            },
            onError = { message ->
                ClassCueRuntime.update { it.copy(phase = SessionPhase.ERROR, statusText = message) }
                updateListeningNotification("录音异常：$message")
            }
        )

        try {
            recordingEngine?.start()
            ClassCueRuntime.update {
                it.copy(
                    recordingName = recordingEngine?.recordingFileName,
                    statusText = if (it.phase == SessionPhase.STARTING) {
                        "原始录音已开启 · 正在准备离线转写"
                    } else it.statusText,
                    recognizerMode = "${recognizerLabel.get()} · ${inputRouteLabel.get()}"
                )
            }
        } catch (error: Throwable) {
            running.set(false)
            ClassCueRuntime.update {
                it.copy(phase = SessionPhase.ERROR, statusText = error.message ?: "启动失败")
            }
            stopSession()
        }
    }

    private fun stopSession() {
        val wasRunning = running.getAndSet(false)
        getSharedPreferences("service_state", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        recordingEngine?.stop()
        recordingEngine = null
        transcriber?.stop()
        transcriber = null
        releaseWakeLock()
        ClassCueApplication.recordingRepository.cleanupExpired()
        if (wasRunning) {
            ClassCueRuntime.update {
                it.copy(
                    phase = SessionPhase.STOPPED,
                    statusText = "本节课已结束，录音已保存",
                    partialText = "",
                    inputLevel = 0
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun processTranscript(rawText: String, isFinal: Boolean) {
        val text = rawText.trim()
        if (text.isBlank()) return
        if (isFinal) {
            val timestamp = System.currentTimeMillis()
            recentUtterances.addLast(TranscriptLine(text, timestamp, true))
            ClassCueApplication.historyStore.insertTranscript(
                TranscriptRecord(
                    createdAt = timestamp,
                    text = text,
                    recordingName = recordingEngine?.recordingFileName,
                    courseName = sessionCourseName
                )
            )
            val cutoff = timestamp - ClassCueApplication.settingsStore.settings.value.contextBeforeSeconds * 1_000L
            while (recentUtterances.isNotEmpty() && recentUtterances.first.timestamp < cutoff) {
                recentUtterances.removeFirst()
            }
            ClassCueRuntime.update { current ->
                current.copy(
                    partialText = "",
                    transcript = (current.transcript + TranscriptLine(text, timestamp, true)).takeLast(12)
                )
            }
        } else {
            ClassCueRuntime.update { it.copy(partialText = text) }
        }
        detectCue(text, isFinal)
    }

    private fun detectCue(text: String, isFinal: Boolean) {
        val settings = ClassCueApplication.settingsStore.settings.value
        val now = System.currentTimeMillis()
        val identityMentioned = mentionsIdentity(text, settings)
        if (identityMentioned) {
            identityArmedUntil = now + settings.followupWindowSeconds * 1_000L
        }
        if (!isFinal) {
            val customKeywordMentioned = CueClassifier.containsCustomKeyword(text, settings.customKeywords)
            if (identityMentioned || customKeywordMentioned) {
                val key = text.lowercase(Locale.ROOT).replace(Regex("[\\s，。！？、,.!?]"), "")
                val provisional = if (identityMentioned) {
                    CueResult(CueType.ROLL_CALL, RiskLevel.HIGH, 0.88, "临时转写中检测到你的称呼")
                } else {
                    CueResult(CueType.GENERAL_QUESTION, RiskLevel.MEDIUM, 0.78, "临时转写中命中自定义强提醒词")
                }
                ClassCueRuntime.update { it.copy(lastCue = provisional) }
                val sameGrowingUtterance = lastProvisionalKey.isNotEmpty() &&
                    (key.contains(lastProvisionalKey) || lastProvisionalKey.contains(key))
                if (now - lastProvisionalAt > 8_000L || !sameGrowingUtterance) {
                    lastProvisionalAt = now
                    lastProvisionalKey = key
                    notifyProvisional(text, customKeywordMentioned && !identityMentioned)
                    if (settings.phoneVibrationEnabled) vibrate(RiskLevel.HIGH, settings.vibrationStrength)
                }
            }
            return
        }
        if (now < identityArmedUntil && looksLikeQuestion(text)) {
            identityArmedUntil = now + settings.followupWindowSeconds * 1_000L
        }
        val classified = CueClassifier.classify(text, settings.classifierConfig())
        val rawResult = if (!classified.shouldAlert() && now < identityArmedUntil && looksLikeQuestion(text)) {
            CueResult(
                CueType.DIRECT_QUESTION,
                RiskLevel.MEDIUM,
                0.74,
                "你的称呼刚刚出现，附带后续课堂问题"
            )
        } else classified
        val cutoff = now - settings.contextBeforeSeconds * 1_000L
        val contextLines = (recentUtterances.filter { it.timestamp >= cutoff }.map { it.text } + text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { lines, line ->
                if (lines.lastOrNull() != line) lines += line
                lines
            }
        val contextWindow = contextLines
            .joinToString("\n")
        val result = if (rawResult.type == CueType.ROLL_CALL && looksLikeQuestion(contextWindow)) {
            CueResult(
                CueType.DIRECT_QUESTION,
                if (rawResult.risk == RiskLevel.LOW) RiskLevel.MEDIUM else rawResult.risk,
                maxOf(rawResult.confidence, 0.76),
                "检测到你的称呼，附带最近的课堂问题"
            )
        } else rawResult
        ClassCueRuntime.update { it.copy(lastCue = result.takeIf { cue -> cue.shouldAlert() }) }
        if (!result.shouldAlert()) return
        val key = text.lowercase(Locale.ROOT).replace(Regex("[\\s，。！？、,.!?]"), "")
        val cooldown = settings.eventCooldownSeconds * 1_000L
        if (now - lastAlertAt < cooldown && (key.contains(lastAlertKey) || lastAlertKey.contains(key))) return
        lastAlertAt = now
        lastAlertKey = key

        val recording = recordingEngine?.recordingFileName
        val eventId = ClassCueApplication.historyStore.insert(
            HistoryEvent(
                createdAt = now,
                type = result.type,
                risk = result.risk,
                transcript = contextWindow,
                confidence = result.confidence,
                reason = result.reason,
                recordingName = recording,
                recordingOffsetMs = (now - sessionStartedAt).coerceAtLeast(0L),
                courseName = sessionCourseName
            )
        )
        latestCueEventId = eventId
        notifyCue(eventId, result, contextWindow, answer = null, settings)
        val followsProvisional = now - lastProvisionalAt < 7_000L &&
            (key.contains(lastProvisionalKey) || lastProvisionalKey.contains(key))
        if (settings.phoneVibrationEnabled && !followsProvisional) {
            vibrate(result.risk, settings.vibrationStrength)
        }

        if (settings.aiEnabled && settings.autoAnswer &&
            result.type in setOf(CueType.DIRECT_QUESTION, CueType.GENERAL_QUESTION)
        ) {
            val context = recentUtterances.map { it.text }.takeLast(20).joinToString("\n")
            requestAiAnswers(eventId, result, contextWindow, context, settings)
        }
    }

    private fun retryAi(intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val event = ClassCueApplication.historyStore.getEvent(eventId)
        if (event == null) {
            if (!running.get()) stopSelf()
            return
        }
        val question = intent.getStringExtra(EXTRA_QUESTION)?.trim().orEmpty()
            .ifBlank { event.transcript }
        val selectedProfileIds = intent.getStringArrayListExtra(EXTRA_PROFILE_IDS)?.toSet()
        val settings = ClassCueApplication.settingsStore.settings.value
        latestCueEventId = eventId
        val result = CueResult(event.type, event.risk, event.confidence, event.reason)
        requestAiAnswers(
            eventId = eventId,
            result = result,
            question = question,
            recentContext = if (running.get()) {
                recentUtterances.map { it.text }.takeLast(20).joinToString("\n")
            } else {
                event.transcript
            },
            settings = settings,
            selectedProfileIds = selectedProfileIds,
            manualQuestion = question
        )
    }

    private fun requestAiAnswers(
        eventId: Long,
        result: CueResult,
        question: String,
        recentContext: String,
        settings: AppSettings,
        selectedProfileIds: Set<String>? = null,
        manualQuestion: String? = null
    ) {
        val generation = aiGenerations.computeIfAbsent(eventId) { AtomicInteger(0) }.incrementAndGet()
        aiAnswers.remove(eventId)
        val configured = settings.aiProfiles
            .filter { profile ->
                profile.enabled && profile.baseUrl.isNotBlank() && profile.model.isNotBlank() &&
                    (selectedProfileIds.isNullOrEmpty() || profile.id in selectedProfileIds)
            }
            .mapNotNull { profile ->
                ClassCueApplication.secretStore.getApiKey(profile.id)
                    .takeIf { it.isNotBlank() }
                    ?.let { keyValue -> profile to keyValue }
            }
        if (configured.isEmpty()) {
            val message = "AI 未配置可用的模型与 Key"
            ClassCueApplication.historyStore.updateAnswer(eventId, message)
            notifyCue(eventId, result, question, message, settings, onlyAlertOnce = true)
            finishManualRequestIfIdle(manualQuestion)
            return
        }

        if (manualQuestion != null) {
            val pending = "【本次发送】\n${manualQuestion.trim()}\n\n正在调用 ${configured.joinToString("、") { it.first.model }}…"
            ClassCueApplication.historyStore.updateAnswer(eventId, pending)
            notifyCue(eventId, result, question, pending, settings, onlyAlertOnce = true)
        }
        val completed = AtomicInteger(0)
        val succeeded = AtomicInteger(0)
        configured.forEach { (profile, keyValue) ->
            scope.launch {
                try {
                    AiAnswerClient().answer(
                        profile = profile,
                        systemPrompt = settings.aiSystemPrompt,
                        apiKey = keyValue,
                        question = question,
                        recentContext = recentContext
                    ).onSuccess { answer ->
                        if (aiGenerations[eventId]?.get() != generation) return@onSuccess
                        succeeded.incrementAndGet()
                        val tier = if (profile.tier == AiTier.FAST) "极速" else "强力"
                        val eventAnswers = aiAnswers.computeIfAbsent(eventId) { ConcurrentHashMap() }
                        synchronized(eventAnswers) {
                            val modelAnswer = ModelAnswer(profile.tier, profile.model, tier, answer)
                            eventAnswers[profile.id] = modelAnswer
                            val responses = eventAnswers.values
                                .sortedWith(
                                    compareByDescending<ModelAnswer> { it.tier == AiTier.STRONG }
                                        .thenBy { it.model }
                                )
                                .joinToString("\n\n") { item ->
                                    "【${item.tierLabel} · ${item.model}】\n${item.answer.trim()}"
                                }
                            val combined = if (manualQuestion == null) {
                                responses
                            } else {
                                "【本次发送】\n${manualQuestion.trim()}\n\n$responses"
                            }
                            ClassCueApplication.historyStore.updateAnswer(eventId, combined)
                            if (latestCueEventId == eventId) {
                                notifyCue(eventId, result, question, combined, settings, onlyAlertOnce = true)
                            }
                        }
                    }
                } finally {
                    if (completed.incrementAndGet() == configured.size &&
                        aiGenerations[eventId]?.get() == generation
                    ) {
                        aiAnswers.remove(eventId)
                        aiGenerations.remove(eventId)
                        if (succeeded.get() == 0) {
                            val failure = "所有 AI 模型均未能返回答案，请检查接口或网络"
                            ClassCueApplication.historyStore.updateAnswer(eventId, failure)
                            if (latestCueEventId == eventId) {
                                notifyCue(eventId, result, question, failure, settings, onlyAlertOnce = true)
                            }
                        }
                        finishManualRequestIfIdle(manualQuestion)
                    }
                }
            }
        }
    }

    private fun finishManualRequestIfIdle(manualQuestion: String?) {
        if (manualQuestion != null && !running.get()) stopSelf()
    }

    private fun looksLikeQuestion(text: String): Boolean {
        return CueClassifier.looksLikeQuestion(text)
    }

    private fun mentionsIdentity(text: String, settings: AppSettings): Boolean {
        val tokens = buildList {
            if (settings.displayName.isNotBlank()) add(settings.displayName)
            if (settings.studentId.isNotBlank()) add(settings.studentId)
            addAll(settings.aliases)
        }
        return tokens.any(text::contains)
    }

    private fun notifyCue(
        eventId: Long,
        result: CueResult,
        transcript: String,
        answer: String?,
        settings: AppSettings,
        onlyAlertOnce: Boolean = false
    ) {
        val content = if (answer.isNullOrBlank()) transcript else "$transcript\n\n建议回答：$answer"
        val builder = NotificationCompat.Builder(
            this,
            ClassCueApplication.alertChannelId(settings.phoneVibrationEnabled)
        )
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(cueTitle(result))
            .setContentText(if (answer.isNullOrBlank()) transcript else "AI 回答已生成，点此查看")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(eventId.toInt()))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTicker(cueTitle(result))
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce || !answer.isNullOrBlank())
            .setTimeoutAfter(90_000)
        if (settings.aiEnabled && settings.aiProfiles.any { it.enabled } &&
            ClassCueApplication.historyStore.getEvent(eventId) != null
        ) {
            builder.addAction(0, "AI 重答", retryAiIntent(eventId))
        }
        val notification = builder.build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ACTIVE_CUE, notification)
        if (running.get()) {
            updateListeningNotification(
                when {
                    answer.isNullOrBlank() -> "检测到课堂提醒 · 点开确认老师原话"
                    answer.contains("正在调用") -> "AI 正在回答 · 极速模型会先显示"
                    answer.contains("未配置") || answer.contains("未能返回") -> "AI 回答失败 · 点开检查设置"
                    else -> "AI 回答已生成 · 点开查看"
                }
            )
        }
    }

    private fun notifyProvisional(transcript: String, strongKeyword: Boolean) {
        val settings = ClassCueApplication.settingsStore.settings.value
        val title = if (strongKeyword) "检测到强提醒词" else "老师可能叫到你"
        val waitingText = if (strongKeyword) "正在等待老师说完这句话…" else "正在等待老师说完问题…"
        val notification = NotificationCompat.Builder(
            this,
            ClassCueApplication.alertChannelId(settings.phoneVibrationEnabled)
        )
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(waitingText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$transcript\n\n${waitingText}确认转写后再生成 AI 回答。"))
            .setContentIntent(openAppIntent(NOTIFICATION_ACTIVE_CUE))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTicker(title)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(90_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ACTIVE_CUE, notification)
    }

    private fun cueTitle(result: CueResult): String = when (result.type) {
        CueType.ROLL_CALL -> "可能正在点名"
        CueType.DIRECT_QUESTION -> "老师可能在问你"
        CueType.GENERAL_QUESTION -> "老师提出了问题"
        CueType.NONE -> "课堂提醒"
    }

    private fun listeningNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            700,
            Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val startedAt = sessionStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        return NotificationCompat.Builder(this, ClassCueApplication.CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("课醒正在听课")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$status\n录音与课堂文字长期保存在本机，可在历史记录中手动管理"))
            .setContentIntent(openAppIntent(600))
            .addAction(0, "结束并保存", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShortCriticalText(capsuleText(status))
            .setRequestPromotedOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun capsuleText(status: String): String = when {
        status.contains("AI") -> "AI 已答"
        status.contains("检测到") -> "有提醒"
        else -> "听课中"
    }

    private fun updateListeningNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_LISTENING, listeningNotification(status))
    }

    private fun openAppIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun retryAiIntent(eventId: Long): PendingIntent = PendingIntent.getService(
        this,
        (eventId xor (eventId ushr 32)).toInt(),
        Intent(this, ListenService::class.java).apply {
            action = ACTION_RETRY_AI
            putExtra(EXTRA_EVENT_ID, eventId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun vibrate(risk: RiskLevel, strength: Int) {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val level = strength.coerceIn(1, 5)
        val durationScale = 0.72 + level * 0.12
        val base = when (risk) {
            RiskLevel.HIGH -> longArrayOf(0, 360, 150, 520, 150, 360)
            RiskLevel.MEDIUM -> longArrayOf(0, 290, 170, 420)
            RiskLevel.LOW -> longArrayOf(0, 240, 150, 240)
        }
        val timings = LongArray(base.size) { index ->
            if (index == 0) 0L else (base[index] * durationScale).toLong()
        }
        if (vibrator.hasAmplitudeControl()) {
            val amplitude = (35 + level * 44).coerceAtMost(255)
            val amplitudes = IntArray(timings.size) { index -> if (index % 2 == 1) amplitude else 0 }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    private fun showTestAlert() {
        val settings = ClassCueApplication.settingsStore.settings.value
        val result = CueResult(CueType.DIRECT_QUESTION, RiskLevel.HIGH, 0.96, "提醒测试")
        val eventId = System.currentTimeMillis()
        val question = "提醒测试：老师可能正在叫你回答问题"
        notifyCue(eventId, result, question, null, settings)
        if (settings.phoneVibrationEnabled) vibrate(RiskLevel.HIGH, settings.vibrationStrength)
        if (!running.get()) stopSelf()
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClassCue::Listening")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        if (running.get()) stopSession()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.classcue.kexing.START"
        const val ACTION_STOP = "com.classcue.kexing.STOP"
        const val ACTION_TEST_ALERT = "com.classcue.kexing.TEST_ALERT"
        const val ACTION_RETRY_AI = "com.classcue.kexing.RETRY_AI"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_PROFILE_IDS = "profile_ids"
        private const val NOTIFICATION_LISTENING = 2001
        private const val NOTIFICATION_ACTIVE_CUE = 2100
    }

    private data class ModelAnswer(
        val tier: AiTier,
        val model: String,
        val tierLabel: String,
        val answer: String
    )
}
