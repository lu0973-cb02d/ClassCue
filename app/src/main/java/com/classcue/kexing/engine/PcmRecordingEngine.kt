package com.classcue.kexing.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.classcue.kexing.data.RecordingRepository
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures one PCM stream. The same bytes are written to a WAV recording and
 * offered to the in-process offline recognizer, so components never compete
 * for the microphone. Recording remains lossless even if recognition is slow.
 */
class PcmRecordingEngine(
    private val context: Context,
    private val repository: RecordingRepository,
    private val onBytesWritten: (Long) -> Unit,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onAudioLevel: (Int) -> Unit,
    private val onInputRoute: (String) -> Unit,
    private val onFinalized: (File) -> Unit,
    private val onError: (String) -> Unit,
    private val inputStrategy: AudioInputStrategy = AudioInputStrategy.AUTO,
    private val farFieldEnhancementEnabled: Boolean = true,
    private val onAudioDiagnostics: (AudioInputDiagnostics) -> Unit = {}
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var partialFile: File? = null
    private var finalFile: File? = null
    private var wavSink: WavSink? = null
    private var enhancer = FarFieldPcmEnhancer(enabled = farFieldEnhancementEnabled)

    val recordingFileName: String?
        get() = finalFile?.name

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        check(running.compareAndSet(false, true)) { "Recording is already running" }
        repository.cleanupExpired()
        val target = repository.newSessionFile()
        val partial = File(target.parentFile, target.nameWithoutExtension + ".partial")
        finalFile = target
        partialFile = partial
        wavSink = WavSink(partial, SAMPLE_RATE, CHANNEL_COUNT, 16)
        enhancer = FarFieldPcmEnhancer(enabled = farFieldEnhancementEnabled)

        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // Keep capture chunks near 200 ms on common devices. The previous ~400 ms chunks
        // made a short pause look like an endpoint and delayed the next speech fragment.
        val bufferSize = maxOf(min * 2, SAMPLE_RATE / 10 * 2)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setPrivacySensitive(true)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val inputDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
        val preferredDevice = when (inputStrategy) {
            AudioInputStrategy.AUTO -> null
            AudioInputStrategy.BUILT_IN -> inputDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
            }
            AudioInputStrategy.EXTERNAL -> inputDevices.firstOrNull(::isExternalMicrophone)
        }
        val preferred = preferredDevice != null && record.setPreferredDevice(preferredDevice)
        onInputRoute(routeLabel(inputStrategy, preferredDevice, preferred))
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            running.set(false)
            record.release()
            runCatching { wavSink?.close() }
            throw IllegalStateException("麦克风初始化失败，请检查是否被其他应用占用")
        }
        audioRecord = record

        captureThread = Thread({ captureLoop(record, bufferSize) }, "classcue-recorder").apply { start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { audioRecord?.stop() }
        captureThread?.join(2_000)
        runCatching { wavSink?.close() }
    }

    private fun captureLoop(record: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            record.startRecording()
            record.routedDevice?.let { routed ->
                onInputRoute("${inputStrategyLabel(inputStrategy)} · ${deviceLabel(routed)} · 不采集媒体音")
            }
            while (running.get()) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    wavSink?.write(buffer, count)
                    onBytesWritten(wavSink?.dataBytes ?: 0L)
                    val enhanced = enhancer.process(buffer, count)
                    onAudioLevel(enhanced.diagnostics.rawLevelPercent)
                    onAudioDiagnostics(enhanced.diagnostics)
                    onAudioChunk(enhanced.pcm)
                } else if (count < 0) {
                    throw IllegalStateException("录音读取失败：$count")
                }
            }
        } catch (error: Throwable) {
            if (running.getAndSet(false)) onError(error.message ?: "录音意外中断")
        } finally {
            runCatching { record.stop() }
            record.release()
            audioRecord = null
            finalizeRecording()
        }
    }

    private fun routeLabel(
        strategy: AudioInputStrategy,
        preferredDevice: AudioDeviceInfo?,
        preferred: Boolean
    ): String = when {
        strategy == AudioInputStrategy.AUTO -> "系统自动选择输入 · 不采集媒体音"
        preferred && preferredDevice != null ->
            "${inputStrategyLabel(strategy)} · ${deviceLabel(preferredDevice)} · 不采集媒体音"
        strategy == AudioInputStrategy.EXTERNAL -> "未检测到可用外置麦克风 · 已回退系统自动 · 不采集媒体音"
        else -> "未能固定手机内置麦克风 · 已回退系统自动 · 不采集媒体音"
    }

    private fun inputStrategyLabel(strategy: AudioInputStrategy): String = when (strategy) {
        AudioInputStrategy.AUTO -> "系统自动"
        AudioInputStrategy.BUILT_IN -> "手机内置麦克风"
        AudioInputStrategy.EXTERNAL -> "外置麦克风"
    }

    private fun deviceLabel(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.trim().orEmpty()
        return product.ifBlank {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "手机内置麦克风"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳麦"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 麦克风"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙麦克风"
                else -> "物理麦克风"
            }
        }
    }

    private fun isExternalMicrophone(device: AudioDeviceInfo): Boolean = when (device.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
        else -> false
    }

    private fun finalizeRecording() {
        val sink = wavSink
        wavSink = null
        runCatching { sink?.close() }
        val partial = partialFile
        val target = finalFile
        if (partial != null && target != null && partial.exists() && partial.length() > 44L) {
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            repository.refresh()
            onFinalized(target)
        } else {
            partial?.delete()
        }
    }

    private class WavSink(
        file: File,
        private val sampleRate: Int,
        private val channels: Int,
        private val bitsPerSample: Int
    ) : AutoCloseable {
        private val raf = RandomAccessFile(file, "rw")
        var dataBytes: Long = 0L
            private set

        init {
            raf.setLength(0)
            repeat(44) { raf.write(0) }
        }

        @Synchronized
        fun write(buffer: ByteArray, count: Int) {
            raf.write(buffer, 0, count)
            dataBytes += count
        }

        @Synchronized
        override fun close() {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeIntLE((36L + dataBytes).coerceAtMost(0xffffffffL).toInt())
            raf.writeBytes("WAVEfmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(channels)
            writeIntLE(sampleRate)
            writeIntLE(byteRate)
            writeShortLE(blockAlign)
            writeShortLE(bitsPerSample)
            raf.writeBytes("data")
            writeIntLE(dataBytes.coerceAtMost(0xffffffffL).toInt())
            raf.fd.sync()
            raf.close()
        }

        private fun writeIntLE(value: Int) {
            raf.write(value and 0xff)
            raf.write(value ushr 8 and 0xff)
            raf.write(value ushr 16 and 0xff)
            raf.write(value ushr 24 and 0xff)
        }

        private fun writeShortLE(value: Int) {
            raf.write(value and 0xff)
            raf.write(value ushr 8 and 0xff)
        }
    }
}
