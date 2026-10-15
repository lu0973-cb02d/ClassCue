@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classcue.kexing.engine.CueClassifier
import com.classcue.kexing.engine.AudioInputStrategy
import com.classcue.kexing.engine.ModelDownloadPhase
import com.classcue.kexing.engine.ModelDownloadSource
import com.classcue.kexing.engine.ModelDownloadState
import com.classcue.kexing.engine.OfflineModelPack
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.AiProfile
import com.classcue.kexing.model.AiTier
import com.classcue.kexing.model.AutoAnswerScope
import com.classcue.kexing.model.DetectionMode
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.TranscriptionModelSize
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun ModelPackCard(
    title: String,
    subtitle: String,
    state: ModelDownloadState,
    deletionEnabled: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    val active = state.phase in setOf(
        ModelDownloadPhase.QUEUED,
        ModelDownloadPhase.DOWNLOADING,
        ModelDownloadPhase.INSTALLING
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    modelPhaseLabel(state.phase),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.phase == ModelDownloadPhase.READY) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (state.phase == ModelDownloadPhase.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(state.progress * 100).toInt()}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)} · ${formatDownloadSpeed(state.bytesPerSecond)}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state.phase in setOf(ModelDownloadPhase.QUEUED, ModelDownloadPhase.INSTALLING)) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            } else if (state.message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == ModelDownloadPhase.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                active -> OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PauseCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("暂停后台下载")
                }
                state.phase == ModelDownloadPhase.READY -> OutlinedButton(
                    onClick = onDelete,
                    enabled = deletionEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (deletionEnabled) "删除模型" else "听课中不能删除")
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.downloadedBytes > 0L) "继续下载" else "后台下载")
                    }
                    if (state.downloadedBytes > 0L) {
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = deletionEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清除")
                        }
                    }
                }
            }
        }
    }
}

internal fun modelPhaseLabel(phase: ModelDownloadPhase): String = when (phase) {
    ModelDownloadPhase.NOT_DOWNLOADED -> "未下载"
    ModelDownloadPhase.QUEUED -> "排队中"
    ModelDownloadPhase.DOWNLOADING -> "下载中"
    ModelDownloadPhase.INSTALLING -> "安装中"
    ModelDownloadPhase.READY -> "已安装"
    ModelDownloadPhase.PAUSED -> "已暂停"
    ModelDownloadPhase.FAILED -> "失败"
}

internal fun formatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "计算速度中"
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / 1024.0 / 1024.0)
    else -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
}

@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    recordings: List<RecordingInfo>,
    modelStates: Map<OfflineModelPack, ModelDownloadState>,
    apiKeys: Map<String, String>,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    alertChannelReady: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApiKeyChange: (String, String) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onDownloadModel: (OfflineModelPack) -> Unit,
    onPauseModelDownload: () -> Unit,
    onDeleteModel: (OfflineModelPack) -> Unit,
    modelDeletionEnabled: Boolean,
    onTestAlert: () -> Unit,
    wearEngineStatus: String,
    onTestWearEngine: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var name by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var studentId by remember(settings.studentId) { mutableStateOf(settings.studentId) }
    var aliases by remember(settings.aliases) { mutableStateOf(settings.aliases.joinToString("，")) }
    var keywords by remember(settings.customKeywords) { mutableStateOf(settings.customKeywords.joinToString("，")) }
    var revealedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val totalBytes = recordings.sumOf { it.bytes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("身份识别", "只保存在本机") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value; onUpdateSettings { it.copy(displayName = value.trim()) } },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { value -> studentId = value; onUpdateSettings { it.copy(studentId = value.trim()) } },
                    label = { Text("学号（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { value ->
                        aliases = value
                        onUpdateSettings { it.copy(aliases = splitTerms(value)) }
                    },
                    label = { Text("我可能被叫到的称呼（可填多个）") },
                    supportingText = { Text("在同一个输入框中用逗号分隔，例如：小明，李同学，班长，课代表") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SectionCard("检测范围", "控制哪些课堂话语会提醒") {
                Text(
                    "已内置 ${CueClassifier.builtInCueCount()} 条中国课堂点名、提问、作答和任务表达；下方词语用于补充你所在课程的特殊说法。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DetectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.detectionMode == mode,
                        onClick = { onUpdateSettings { it.copy(detectionMode = mode) } },
                        label = { Text(detectionModeLabel(mode)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("灵敏度：${settings.sensitivity} · ${sensitivityLabel(settings.sensitivity)}", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.sensitivity.toFloat(),
                    onValueChange = { value -> onUpdateSettings { it.copy(sensitivity = value.roundToInt().coerceIn(1, 5)) } },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Text("点名前回溯：${settings.contextBeforeSeconds} 秒", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.contextBeforeSeconds.toFloat(),
                    onValueChange = { value ->
                        val snapped = (value / 15f).roundToInt() * 15
                        onUpdateSettings { it.copy(contextBeforeSeconds = snapped.coerceIn(30, 120)) }
                    },
                    valueRange = 30f..120f,
                    steps = 5
                )
                Text("点名后继续捕获：${settings.followupWindowSeconds} 秒", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.followupWindowSeconds.toFloat(),
                    onValueChange = { value ->
                        val snapped = (value / 15f).roundToInt() * 15
                        onUpdateSettings { it.copy(followupWindowSeconds = snapped.coerceIn(30, 120)) }
                    },
                    valueRange = 30f..120f,
                    steps = 5
                )
                Text(
                    "先问后点会回带前文；先点后问会继续捕获后文。宁可多带内容，不强行判断题目归属。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { value -> keywords = value; onUpdateSettings { it.copy(customKeywords = splitTerms(value)) } },
                    label = { Text("自定义强提醒词") },
                    supportingText = { Text("识别到任一词就弹通知，用逗号分隔；课程高频普通术语不建议填写") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SectionCard("语音转写", "荣耀兼容模式 · 离线中文") {
                val mediumState = modelStates[OfflineModelPack.MEDIUM]
                    ?: ModelDownloadState(OfflineModelPack.MEDIUM, ModelDownloadPhase.NOT_DOWNLOADED)
                val largeState = modelStates[OfflineModelPack.LARGE]
                    ?: ModelDownloadState(OfflineModelPack.LARGE, ModelDownloadPhase.NOT_DOWNLOADED)
                val mediumReady = mediumState.phase == ModelDownloadPhase.READY
                val largeReady = mediumReady && largeState.phase == ModelDownloadPhase.READY
                Text("识别档位", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TranscriptionModelSize.entries.forEach { size ->
                        val label = when (size) {
                            TranscriptionModelSize.SMALL -> "小 · 快速"
                            TranscriptionModelSize.MEDIUM -> "中 · 默认"
                            TranscriptionModelSize.LARGE -> "大 · 两遍"
                        }
                        FilterChip(
                            selected = settings.transcriptionModelSize == size,
                            onClick = { onUpdateSettings { it.copy(transcriptionModelSize = size) } },
                            enabled = when (size) {
                                TranscriptionModelSize.SMALL -> true
                                TranscriptionModelSize.MEDIUM -> mediumReady
                                TranscriptionModelSize.LARGE -> largeReady
                            },
                            label = { Text(label) }
                        )
                    }
                }
                val modelDescription = when (settings.transcriptionModelSize) {
                    TranscriptionModelSize.SMALL -> "约 42 MB，内置 Vosk；启动最快、耗电最低，准确性一般。"
                    TranscriptionModelSize.MEDIUM -> "下载约 127 MB（安装约 160 MB），大型流式 Zipformer；边说边出字，适合较新的 arm64 Android 设备日常听课。"
                    TranscriptionModelSize.LARGE -> "中档流式 + 额外下载约 287 MB 高精度 CTC；先快速提醒，停顿后精修最终转写。"
                }
                Text(modelDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("麦克风输入", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioInputStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = settings.audioInputStrategy == strategy,
                            onClick = { onUpdateSettings { it.copy(audioInputStrategy = strategy) } },
                            label = { Text(audioInputStrategyLabel(strategy)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "省电识别",
                    subtitle = "中/大档改用 2 个线程，发热和耗电更低；较旧的手机可能出现识别落后提示",
                    checked = settings.powerSavingRecognition,
                    onChecked = { enabled -> onUpdateSettings { it.copy(powerSavingRecognition = enabled) } }
                )
                SettingSwitchRow(
                    title = "姓名热词（实验）",
                    subtitle = "中/大档识别时更倾向写出你的姓名和称呼；称呼里有模型不认识的字时自动跳过。读音相近的名字无论是否开启都能识别",
                    checked = settings.nameHotwordsEnabled,
                    onChecked = { enabled -> onUpdateSettings { it.copy(nameHotwordsEnabled = enabled) } }
                )
                SettingSwitchRow(
                    title = "教室远场增强",
                    subtitle = "仅增强送入离线识别器的声音；原始 WAV 保持不变，便于复核",
                    checked = settings.farFieldEnhancementEnabled,
                    onChecked = { enabled ->
                        onUpdateSettings { it.copy(farFieldEnhancementEnabled = enabled) }
                    }
                )
                Text(
                    "自动模式遵循系统当前输入；外置模式会优先使用有线、USB 或蓝牙麦克风，找不到时安全回退。25 米等远距离场景仍建议把手机前移或使用更靠近老师的麦克风。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    "小档随 APK 安装。中/大档改为手动下载：离开课醒或锁屏后仍会在后台继续，并支持暂停和断点续传。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("下载源", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.modelDownloadSource == ModelDownloadSource.GITHUB,
                        onClick = { onUpdateSettings { it.copy(modelDownloadSource = ModelDownloadSource.GITHUB) } },
                        label = { Text("GitHub 官方") }
                    )
                    FilterChip(
                        selected = settings.modelDownloadSource == ModelDownloadSource.MIRROR,
                        onClick = { onUpdateSettings { it.copy(modelDownloadSource = ModelDownloadSource.MIRROR) } },
                        label = { Text("国内镜像 hf-mirror.com") }
                    )
                }
                Text(
                    "两个来源的文件都会按课醒内置的 SHA-256 逐一校验，对不上就不会安装；首选来源失败时自动改用另一个。GitHub 在国内较慢时可选镜像。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ModelPackCard(
                    title = "中档流式模型",
                    subtitle = "约 127 MB 下载 · 约 160 MB 安装",
                    state = mediumState,
                    deletionEnabled = modelDeletionEnabled,
                    onDownload = { onDownloadModel(OfflineModelPack.MEDIUM) },
                    onPause = onPauseModelDownload,
                    onDelete = { onDeleteModel(OfflineModelPack.MEDIUM) }
                )
                Spacer(Modifier.height(10.dp))
                ModelPackCard(
                    title = "大档精修模型",
                    subtitle = "额外约 287 MB；缺少中档时会先自动排队下载中档",
                    state = largeState,
                    deletionEnabled = modelDeletionEnabled,
                    onDownload = { onDownloadModel(OfflineModelPack.LARGE) },
                    onPause = onPauseModelDownload,
                    onDelete = { onDeleteModel(OfflineModelPack.LARGE) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "未下载完成时不能选择对应档位；若模型文件异常，本节课会自动使用小档。切换档位在下一节课生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text("录音先以 16 kHz 单声道原始 WAV 保存，课后可压缩为 AAC；远场增强只作用于离线识别输入，不会把媒体播放内容作为音频流采集。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("通知与震动", "仅手机提醒") {
                if (!alertChannelReady) {
                    InfoCard(
                        Icons.Rounded.Warning,
                        "横幅通知可能被系统关闭",
                        "请打开课堂提醒通道，并在 MagicOS 中允许横幅、锁屏和状态栏显示。",
                        warning = true
                    )
                    Spacer(Modifier.height(10.dp))
                }
                SettingSwitchRow(
                    title = "手机震动",
                    subtitle = "点名或提问时由手机震动提醒",
                    checked = settings.phoneVibrationEnabled,
                    onChecked = { value ->
                        onUpdateSettings { it.copy(phoneVibrationEnabled = value) }
                    }
                )
                AnimatedVisibility(settings.phoneVibrationEnabled) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("震动力度：${settings.vibrationStrength} / 5", fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.vibrationStrength.toFloat(),
                            onValueChange = { value -> onUpdateSettings { it.copy(vibrationStrength = value.roundToInt().coerceIn(1, 5)) } },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onTestAlert, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text("测试手机提醒")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("检查课堂提醒横幅设置")
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("华为手表直连", fontWeight = FontWeight.Medium)
                SettingSwitchRow(
                    title = "发送到手表",
                    subtitle = "识别到点名/提问和 AI 回答后，通过 Wear Engine 推送到已连接的华为手表",
                    checked = settings.wearEngineEnabled,
                    onChecked = { value -> onUpdateSettings { it.copy(wearEngineEnabled = value) } }
                )
                SettingSwitchRow(
                    title = "手表震动",
                    subtitle = "手表端收到课堂提醒时按当前力度震动",
                    checked = settings.watchVibrationEnabled,
                    onChecked = { value -> onUpdateSettings { it.copy(watchVibrationEnabled = value) } }
                )
                OutlinedTextField(
                    value = settings.wearEngineWatchPackage,
                    onValueChange = { value -> onUpdateSettings { it.copy(wearEngineWatchPackage = value.trim()) } },
                    label = { Text("手表应用包名") },
                    supportingText = { Text("默认：${AppSettings.DEFAULT_WATCH_PACKAGE}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.wearEngineWatchFingerprint,
                    onValueChange = { value -> onUpdateSettings { it.copy(wearEngineWatchFingerprint = value.trim()) } },
                    label = { Text("手表 HAP 签名 SHA-256") },
                    supportingText = { Text("必须使用正式签名 HAP 的指纹；未签名前不会发送") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onTestWearEngine, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Security, null)
                    Spacer(Modifier.width(8.dp))
                    Text("测试手表直连")
                }
                Text(
                    wearEngineStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard("AI 回答", "兼容 OpenAI 风格的 Chat Completions 接口") {
                SettingSwitchRow(
                    title = "启用 AI 回答",
                    subtitle = "仅在检测到问题后发送文字上下文，不上传原始录音",
                    checked = settings.aiEnabled,
                    onChecked = { value -> onUpdateSettings { it.copy(aiEnabled = value) } }
                )
                AnimatedVisibility(settings.aiEnabled) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "自动生成回答",
                            subtitle = "检测到问题后自动首发；之后仍可圈选原话重发",
                            checked = settings.autoAnswer,
                            onChecked = { value -> onUpdateSettings { it.copy(autoAnswer = value) } }
                        )
                        AnimatedVisibility(settings.autoAnswer) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text("自动回答范围", fontWeight = FontWeight.Medium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = settings.autoAnswerScope == AutoAnswerScope.DIRECTED_AT_ME,
                                        onClick = { onUpdateSettings { it.copy(autoAnswerScope = AutoAnswerScope.DIRECTED_AT_ME) } },
                                        label = { Text("只在问到我时（推荐）") }
                                    )
                                    FilterChip(
                                        selected = settings.autoAnswerScope == AutoAnswerScope.ALL_QUESTIONS,
                                        onClick = { onUpdateSettings { it.copy(autoAnswerScope = AutoAnswerScope.ALL_QUESTIONS) } },
                                        label = { Text("所有课堂提问") }
                                    )
                                }
                                Text("自动回答上限：10 分钟内 ${settings.aiAutoLimitPer10Min} 次", fontWeight = FontWeight.Medium)
                                Slider(
                                    value = settings.aiAutoLimitPer10Min.toFloat(),
                                    onValueChange = { value ->
                                        onUpdateSettings { it.copy(aiAutoLimitPer10Min = value.roundToInt().coerceIn(1, 30)) }
                                    },
                                    valueRange = 1f..30f,
                                    steps = 28
                                )
                                Text(
                                    "面向全班的问题和误报不会再自动花费 AI 额度；达到上限后提醒照常弹出，可在提醒里点「AI 重答」手动发送。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请求会自动附带课堂场景规则和语音识别局限：极速模型先给可直接开口的短答，强力模型再补依据与步骤。当前提醒与历史记录仍可圈选题目后重发。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        settings.aiProfiles.forEach { profile ->
                            AiProfileEditor(
                                profile = profile,
                                apiKey = apiKeys[profile.id].orEmpty(),
                                revealKey = revealedProfileId == profile.id,
                                onToggleReveal = {
                                    revealedProfileId = if (revealedProfileId == profile.id) null else profile.id
                                },
                                onUpdate = { updated ->
                                    onUpdateSettings { current ->
                                        current.copy(
                                            aiProfiles = current.aiProfiles.map {
                                                if (it.id == updated.id) updated else it
                                            }
                                        )
                                    }
                                },
                                onApiKeyChange = { value -> onApiKeyChange(profile.id, value) },
                                onDelete = {
                                    onDeleteAiProfile(profile.id)
                                    onUpdateSettings { current ->
                                        current.copy(aiProfiles = current.aiProfiles.filterNot { it.id == profile.id })
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdateSettings { current ->
                                    val tier = if (current.aiProfiles.none { it.tier == AiTier.STRONG }) {
                                        AiTier.STRONG
                                    } else AiTier.FAST
                                    current.copy(
                                        aiProfiles = current.aiProfiles + AiProfile(
                                            id = UUID.randomUUID().toString(),
                                            tier = tier,
                                            baseUrl = "",
                                            model = "",
                                            timeoutSeconds = if (tier == AiTier.FAST) 10 else 35
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("＋ 添加模型") }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = settings.aiSystemPrompt,
                            onValueChange = { value -> onUpdateSettings { it.copy(aiSystemPrompt = value) } },
                            label = { Text("补充回答偏好") },
                            supportingText = { Text("内置的课堂场景、转写纠错、防编造和极速/强力分工规则始终保留") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            SectionCard("后台驻留", "上课时保持录音与识别") {
                InfoRow(Icons.Rounded.BatterySaver, "应用使用前台麦克风服务和 CPU 唤醒锁；通知栏会一直显示“课醒正在听课”。")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenLiveUpdateSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("尝试启用灵动胶囊 / 实时状态")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "课醒会按 Android 实时通知规范请求显示顶部状态；MagicOS 是否呈现为灵动胶囊仍由系统决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开电池优化设置")
                }
                Spacer(Modifier.height(6.dp))
                Text("荣耀 MagicOS 建议把课醒设为“允许后台活动/手动管理”，否则锁屏较久后仍可能被系统结束。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("权限与存储", "当前录音 ${recordings.size} 个，共 ${formatBytes(totalBytes)}") {
                PermissionRow("麦克风", hasAudioPermission)
                PermissionRow("通知", hasNotificationPermission)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("打开系统通知设置") }
                Spacer(Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "课后压缩录音",
                    subtitle = "下课后在后台把 WAV 压缩为 AAC（约 14 MB/小时，WAV 约 110 MB/小时）；电量低或正在听课时不压缩",
                    checked = settings.compressRecordings,
                    onChecked = { value -> onUpdateSettings { it.copy(compressRecordings = value) } }
                )
                SettingSwitchRow(
                    title = "压缩后删除 WAV 原件",
                    subtitle = "压缩副本核对时长无误后才删除；关闭时两份都保留，可在记录页手动释放",
                    checked = settings.deleteWavAfterCompression,
                    onChecked = { value -> onUpdateSettings { it.copy(deleteWavAfterCompression = value) } }
                )
                Spacer(Modifier.height(8.dp))
                Text("原始录音和课堂文字不会自动到期；请定期检查占用，并在历史记录中手动导出或删除。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            InfoCard(
                Icons.Rounded.Security,
                "使用边界",
                "本应用用于课堂参与辅助和避免漏听点名，不应在考试、测验或明确禁止电子设备的场景中使用。录音前请遵守学校和课堂规则。"
            )
        }
    }
}

@Composable
internal fun AiProfileEditor(
    profile: AiProfile,
    apiKey: String,
    revealKey: Boolean,
    onToggleReveal: () -> Unit,
    onUpdate: (AiProfile) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.model.ifBlank { "未填写模型名" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (profile.tier == AiTier.FAST) "极速模型" else "强力模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = { onUpdate(profile.copy(enabled = it)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = profile.tier == AiTier.FAST,
                    onClick = {
                        onUpdate(profile.copy(tier = AiTier.FAST, timeoutSeconds = 10))
                    },
                    label = { Text("极速") }
                )
                FilterChip(
                    selected = profile.tier == AiTier.STRONG,
                    onClick = {
                        onUpdate(profile.copy(tier = AiTier.STRONG, timeoutSeconds = 35))
                    },
                    label = { Text("强力") }
                )
            }
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { onUpdate(profile.copy(baseUrl = it)) },
                label = { Text("BaseURL") },
                supportingText = { Text("必须使用 HTTPS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = profile.model,
                onValueChange = { onUpdate(profile.copy(model = it)) },
                label = { Text("模型名（同时作为显示名称）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                supportingText = { Text("系统密钥库加密，仅保存在本机") },
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = onToggleReveal) { Text(if (revealKey) "隐藏" else "显示") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("超时：${profile.timeoutSeconds} 秒", fontWeight = FontWeight.Medium)
            Slider(
                value = profile.timeoutSeconds.toFloat(),
                onValueChange = { value ->
                    val timeout = (value / 5f).roundToInt() * 5
                    onUpdate(profile.copy(timeoutSeconds = timeout.coerceIn(5, 60)))
                },
                valueRange = 5f..60f,
                steps = 10
            )
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("删除此模型", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
