package com.classcue.kexing.engine

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs Vosk inside the app. It consumes the exact PCM bytes written to the WAV
 * file, avoiding OEM SpeechRecognizer and MagicOS audio-pipe incompatibilities.
 */
class OfflineTranscriber(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit
) : TranscriberEngine {
    companion object {
        private const val ASSET_MODEL = "vosk-model-small-cn-0.22"
        private const val INSTALLED_MODEL = "vosk-small-cn-0.22"
        private val END = ByteArray(0)
        private const val QUEUE_CAPACITY = 600
        private const val DRAIN_TIMEOUT_MS = 15_000L
    }

    private val running = AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private var worker: Thread? = null
    @Volatile private var droppedChunks = 0
    @Volatile private var lastBackpressureNoticeAt = 0L

    override fun start() {
        check(running.compareAndSet(false, true)) { "Offline transcriber is already running" }
        worker = Thread({ recognitionLoop() }, "classcue-vosk").apply { start() }
    }

    override fun offer(pcm: ByteArray) {
        if (!running.get() || pcm.isEmpty()) return
        val accepted = runCatching { queue.offer(pcm, 50, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!accepted) {
            droppedChunks++
            val now = System.currentTimeMillis()
            if (now - lastBackpressureNoticeAt >= 5_000L) {
                lastBackpressureNoticeAt = now
                onStatus("离线识别落后，已跳过 $droppedChunks 个音频片段")
            }
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { queue.put(END) }
        worker?.join(DRAIN_TIMEOUT_MS)
        if (worker?.isAlive == true) worker?.interrupt()
        worker = null
        queue.clear()
    }

    private fun recognitionLoop() {
        try {
            val modelDir = prepareModel()
            onStatus("正在载入离线中文模型…")
            Model(modelDir.absolutePath).use { model ->
                Recognizer(model, PcmRecordingEngine.SAMPLE_RATE.toFloat()).use { recognizer ->
                    onReady()
                    var previousPartial = ""
                    while (running.get() || queue.isNotEmpty()) {
                        val pcm = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                        if (pcm === END) break
                        if (recognizer.acceptWaveForm(pcm, pcm.size)) {
                            val text = resultText(recognizer.result, "text")
                            if (text.isNotBlank()) onFinal(text)
                            previousPartial = ""
                        } else {
                            val partial = resultText(recognizer.partialResult, "partial")
                            if (partial.isNotBlank() && partial != previousPartial) {
                                previousPartial = partial
                                onPartial(partial)
                            }
                        }
                    }
                    val finalText = resultText(recognizer.finalResult, "text")
                    if (finalText.isNotBlank()) onFinal(finalText)
                }
            }
        } catch (error: Throwable) {
            onError(error.message ?: error.javaClass.simpleName)
        } finally {
            running.set(false)
        }
    }

    private fun prepareModel(): File {
        val root = File(context.filesDir, "offline_models")
        val target = File(root, INSTALLED_MODEL)
        if (isValidModel(target)) return target

        onStatus("首次使用：正在准备离线中文模型…")
        root.mkdirs()
        val staging = File(root, "$INSTALLED_MODEL.preparing")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        copyAssetTree(ASSET_MODEL, staging)
        check(isValidModel(staging)) { "离线中文模型文件不完整" }

        if (target.exists()) target.deleteRecursively()
        if (!staging.renameTo(target)) {
            staging.copyRecursively(target, overwrite = true)
            staging.deleteRecursively()
        }
        check(isValidModel(target)) { "离线中文模型安装失败" }
        return target
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    private fun isValidModel(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/mfcc.conf").isFile &&
            File(directory, "graph/HCLr.fst").isFile

    private fun resultText(json: String, key: String): String = runCatching {
        JSONObject(json).optString(key).trim().replace(Regex("\\s+"), "")
    }.getOrDefault("")
}
