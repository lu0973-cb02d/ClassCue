package com.classcue.kexing.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Encodes a lesson WAV into AAC-LC in an .m4a container (about 14 MB per hour at 32 kbit/s for
 * 16 kHz mono speech, versus about 110 MB for WAV). The copy is written to a temporary file and
 * only renamed into place after its duration has been checked against the WAV.
 */
object RecordingCompressor {
    private const val BIT_RATE = 32_000
    private const val TIMEOUT_US = 10_000L

    class Cancelled : Exception()

    /** Returns the verified duration of the new file in milliseconds. */
    fun compress(wav: File, output: File, shouldContinue: () -> Boolean): Long {
        val info = requireNotNull(WavInfo.read(wav)) { "不是可识别的 PCM WAV：${wav.name}" }
        require(info.bitsPerSample == 16) { "只支持 16 位 WAV" }
        val temp = File(output.parentFile, output.name + ".tmp")
        temp.delete()
        try {
            encode(wav, temp, info, shouldContinue)
            val actualMs = durationMs(temp)
            check(CompressionCheck.durationMatches(info.durationMs, actualMs)) {
                "压缩结果时长 ${actualMs} ms 与原始录音 ${info.durationMs} ms 不符"
            }
            output.delete()
            check(temp.renameTo(output)) { "无法保存压缩录音" }
            output.setLastModified(wav.lastModified())
            return actualMs
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun encode(wav: File, target: File, info: WavInfo, shouldContinue: () -> Boolean) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, info.sampleRate, info.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            val bytesPerFrame = info.channels * 2
            var framesQueued = 0L
            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            BufferedInputStream(FileInputStream(wav), 64 * 1024).use { input ->
                input.skipFully(WavHeader.SIZE)
                val chunk = ByteArray(8 * 1024)
                while (!outputDone) {
                    if (!shouldContinue()) throw Cancelled()
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = requireNotNull(codec.getInputBuffer(index))
                            val wanted = minOf(chunk.size, buffer.remaining()) / bytesPerFrame * bytesPerFrame
                            // Drop a trailing half frame (only possible in a damaged file).
                            val read = input.readFully(chunk, wanted).let { it - it.mod(bytesPerFrame) }
                            val ptsUs = framesQueued * 1_000_000L / info.sampleRate
                            if (read <= 0) {
                                codec.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.clear()
                                buffer.put(chunk, 0, read)
                                codec.queueInputBuffer(index, 0, read, ptsUs, 0)
                                framesQueued += read / bytesPerFrame
                            }
                        }
                    }
                    when (val out = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "编码器格式重复变化" }
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (out >= 0) {
                            val buffer = requireNotNull(codec.getOutputBuffer(out))
                            val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                                buffer.position(bufferInfo.offset)
                                buffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(track, buffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(out, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun durationMs(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION)
                ?.div(1_000L) ?: 0L
        } finally {
            extractor.release()
        }
    }

    private fun BufferedInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    /** Reads up to [length] bytes, looping over short reads; returns -1 at end of stream. */
    private fun BufferedInputStream.readFully(target: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = read(target, total, length - total)
            if (read < 0) break
            total += read
        }
        return if (total == 0 && length > 0) -1 else total
    }
}
