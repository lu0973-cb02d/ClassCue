package com.classcue.kexing.engine

import android.content.Context
import com.classcue.kexing.ClassCueApplication
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Precision-oriented recognizer for flagship phones. MEDIUM uses a large
 * streaming Zipformer. LARGE adds a second non-streaming CTC pass at endpoints.
 * Model packages are fetched once from the official sherpa-onnx release and
 * remain in the app's private storage for fully offline use afterwards.
 */
class SherpaTranscriber(
    private val context: Context,
    private val secondPass: Boolean,
    /** Names and forms of address to favour; empty keeps plain greedy decoding. */
    private val nameHotwords: List<String> = emptyList(),
    private val numThreads: Int = 4,
    private val onStatus: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFallback: () -> Unit,
    private val onError: (String) -> Unit
) : TranscriberEngine {
    companion object {
        private val END = ByteArray(0)
        private const val SAMPLE_RATE = 16_000
        private const val QUEUE_CAPACITY = 600
        private const val DRAIN_TIMEOUT_MS = 15_000L
        /** Silence fed before inputFinished() so the last frames reach the decoder. */
        private const val TAIL_PADDING_SAMPLES = (SAMPLE_RATE * 66) / 100
        private const val HOTWORDS_SCORE = 2.0f
    }

    private val running = AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private var worker: Thread? = null
    @Volatile private var fallback: TranscriberEngine? = null
    @Volatile private var droppedChunks = 0
    @Volatile private var lastBackpressureNoticeAt = 0L

    override fun start() {
        check(running.compareAndSet(false, true)) { "Sherpa transcriber is already running" }
        worker = Thread({ recognitionLoop() }, "classcue-sherpa").apply { start() }
    }

    override fun offer(pcm: ByteArray) {
        fallback?.let {
            it.offer(pcm)
            return
        }
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
        fallback?.stop()
        fallback = null
        if (!running.compareAndSet(true, false)) return
        runCatching { queue.put(END) }
        worker?.join(DRAIN_TIMEOUT_MS)
        if (worker?.isAlive == true) worker?.interrupt()
        worker = null
        queue.clear()
    }

    private fun recognitionLoop() {
        var online: OnlineRecognizer? = null
        var offline: OfflineRecognizer? = null
        try {
            val models = ClassCueApplication.modelRepository
            val streamingDir = models.installedDirectory(OfflineModelPack.MEDIUM)
                ?: error("中档模型尚未下载，请先在设置中下载；本节课已自动使用小档")
            val accurateDir = if (secondPass) {
                models.installedDirectory(OfflineModelPack.LARGE)
                    ?: error("大档精修模型尚未下载，请先在设置中下载；本节课已自动使用小档")
            } else null
            if (!running.get()) return

            onStatus(if (secondPass) "正在加载大档两遍识别模型…" else "正在加载中档流式模型…")
            val hotwords = if (nameHotwords.isEmpty()) emptyList() else {
                NameHotwords.build(nameHotwords, NameHotwords.readVocabulary(File(streamingDir, "tokens.txt")))
            }
            online = createOnlineRecognizer(streamingDir, useHotwords = hotwords.isNotEmpty())
            offline = accurateDir?.let(::createOfflineRecognizer)
            onReady()
            if (nameHotwords.isNotEmpty()) {
                onStatus(
                    if (hotwords.isEmpty()) "姓名热词未启用：称呼里有模型不认识的字"
                    else "已启用 ${hotwords.size} 个姓名热词"
                )
            }
            processAudio(online, offline, hotwords)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            val reason = error.message ?: error.javaClass.simpleName
            if (running.get()) {
                onStatus("中/大档暂不可用，已自动改用内置小档：$reason")
                onFallback()
                val small = OfflineTranscriber(
                    context = context,
                    onStatus = onStatus,
                    onReady = onReady,
                    onPartial = onPartial,
                    onFinal = onFinal,
                    onError = onError
                )
                fallback = small
                small.start()
                while (true) {
                    val pending = queue.poll() ?: break
                    if (pending !== END) small.offer(pending)
                }
            } else {
                onError(reason)
            }
        } finally {
            runCatching { offline?.release() }
            runCatching { online?.release() }
            running.set(false)
        }
    }

    private fun processAudio(online: OnlineRecognizer, offline: OfflineRecognizer?, hotwords: List<String>) {
        val stream = if (hotwords.isEmpty()) online.createStream() else online.createStream(hotwords.joinToString("/"))
        val utterance = ArrayList<FloatArray>()
        var previousPartial = ""
        try {
            while (true) {
                val pcm = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                if (pcm === END) {
                    flushLastUtterance(online, stream, offline, utterance)
                    break
                }
                val samples = pcm16ToFloat(pcm)
                if (samples.isEmpty()) continue
                if (offline != null) utterance += samples
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (online.isReady(stream)) online.decode(stream)

                val text = normalize(online.getResult(stream).text)
                if (text.isNotBlank() && text != previousPartial) {
                    previousPartial = text
                    onPartial(text)
                }
                if (online.isEndpoint(stream)) {
                    val finalText = if (text.isNotBlank() && offline != null) {
                        runSecondPass(offline, utterance).ifBlank { text }
                    } else text
                    if (finalText.isNotBlank()) onFinal(finalText)
                    previousPartial = ""
                    online.reset(stream)
                    if (offline == null || text.isBlank()) utterance.clear()
                }
            }
        } finally {
            stream.release()
        }
    }

    /**
     * Emits the sentence that was still in progress when the lesson ended. Without this, words
     * spoken after the last detected pause were only ever shown as a partial result and lost.
     */
    private fun flushLastUtterance(
        online: OnlineRecognizer,
        stream: OnlineStream,
        offline: OfflineRecognizer?,
        utterance: MutableList<FloatArray>
    ) {
        stream.acceptWaveform(FloatArray(TAIL_PADDING_SAMPLES), SAMPLE_RATE)
        stream.inputFinished()
        while (online.isReady(stream)) online.decode(stream)
        val text = normalize(online.getResult(stream).text)
        if (text.isBlank()) return
        val finalText = if (offline != null) {
            runSecondPass(offline, utterance, keepTrailingAudio = false).ifBlank { text }
        } else text
        onFinal(finalText)
    }

    private fun runSecondPass(
        recognizer: OfflineRecognizer,
        chunks: MutableList<FloatArray>,
        keepTrailingAudio: Boolean = true
    ): String {
        val total = chunks.sumOf { it.size }
        if (total < SAMPLE_RATE / 4) {
            chunks.clear()
            return ""
        }
        val all = FloatArray(total)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(all, destinationOffset = offset)
            offset += chunk.size
        }
        val keep = if (keepTrailingAudio) minOf(SAMPLE_RATE / 2, all.size / 3) else 0
        val speechEnd = (all.size - keep).coerceAtLeast(1)
        chunks.clear()
        if (keep > 0) chunks += all.copyOfRange(speechEnd, all.size)

        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(all.copyOfRange(0, speechEnd), SAMPLE_RATE)
            recognizer.decode(stream)
            normalize(recognizer.getResult(stream).text)
        } finally {
            stream.release()
        }
    }

    private fun createOnlineRecognizer(directory: File, useHotwords: Boolean): OnlineRecognizer {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(directory, "encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "decoder.onnx").absolutePath,
                    joiner = File(directory, "joiner.int8.onnx").absolutePath
                ),
                tokens = File(directory, "tokens.txt").absolutePath,
                numThreads = numThreads
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.0f, 0.0f),
                // A teacher's mid-sentence pause should not flush the stream too early.
                // Final alerts are now gated on stable endpoint text, so the small latency
                // increase is preferable to losing the next phrase after a pause.
                rule2 = EndpointRule(true, 1.8f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 18.0f)
            ),
            enableEndpoint = true
        )
        if (useHotwords) {
            // Hotwords only take effect with beam search. Measured on the model's test clips,
            // four active paths cost about 5-10 % more decoding time than greedy search.
            config.decodingMethod = "modified_beam_search"
            config.maxActivePaths = 4
            config.hotwordsScore = HOTWORDS_SCORE
            config.modelConfig.modelingUnit = "cjkchar"
        }
        return OnlineRecognizer(config = config)
    }

    private fun createOfflineRecognizer(directory: File): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                zipformerCtc = OfflineZipformerCtcModelConfig(
                    model = File(directory, "model.int8.onnx").absolutePath
                ),
                tokens = File(directory, "tokens.txt").absolutePath,
                numThreads = numThreads,
                modelingUnit = "cjkchar"
            )
        )
        return OfflineRecognizer(config = config)
    }

    private fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val result = FloatArray(pcm.size / 2)
        var source = 0
        var destination = 0
        while (source + 1 < pcm.size) {
            val value = ((pcm[source + 1].toInt() shl 8) or (pcm[source].toInt() and 0xff)).toShort()
            result[destination++] = value / 32768.0f
            source += 2
        }
        return if (destination == result.size) result else result.copyOf(destination)
    }

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), "")
}
