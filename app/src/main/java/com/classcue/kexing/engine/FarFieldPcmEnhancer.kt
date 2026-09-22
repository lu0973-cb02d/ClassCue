package com.classcue.kexing.engine

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Conservative PCM16 gain control for quiet, distant speech.
 *
 * Gain rises slowly, falls quickly when a loud sound arrives, and a soft limiter prevents hard
 * clipping. A deliberately conservative frame-level energy gate removes only sustained
 * near-silence from the recognizer input. A short hangover keeps pauses inside one sentence,
 * while the lossless WAV continues to receive the original microphone bytes.
 */
class FarFieldPcmEnhancer(
    private val enabled: Boolean = true,
    private val targetRmsDbfs: Double = -24.0,
    private val maximumGainDb: Double = 12.0,
    private val gainRisePerBufferDb: Double = 1.5,
    private val gainFallPerBufferDb: Double = 4.0,
    private val limiterThreshold: Double = 0.86,
    private val noiseGateEnabled: Boolean = true,
    private val noiseGateThresholdDbfs: Double = -56.0,
    private val speechHangoverFrames: Int = 12,
    private val frameSamples: Int = 320
) {
    private var currentGainDb = 0.0
    private var remainingHangoverFrames = 0

    fun reset() {
        currentGainDb = 0.0
        remainingHangoverFrames = 0
    }

    fun process(input: ByteArray, count: Int = input.size): Result {
        require(count in 0..input.size) { "count must be within the input buffer" }
        val evenCount = count - (count % 2)
        if (evenCount == 0) {
            return Result(
                pcm = input.copyOf(count),
                diagnostics = diagnostics(
                    rawRmsDbfs = SILENCE_DBFS,
                    enhancedRmsDbfs = SILENCE_DBFS,
                    appliedGainDb = 0.0,
                    limiterReductionDb = 0.0,
                    limitedRatio = 0.0,
                    speechLikely = false
                )
            )
        }

        var rawSumSquares = 0.0
        var index = 0
        while (index < evenCount) {
            val sample = readPcm16(input, index)
            rawSumSquares += sample.toDouble() * sample.toDouble()
            index += 2
        }
        val sampleCount = evenCount / 2
        val rawRms = sqrt(rawSumSquares / sampleCount)
        val rawRmsDbfs = amplitudeToDbfs(rawRms)

        if (!enabled) {
            currentGainDb = 0.0
            return Result(
                pcm = input.copyOf(count),
                diagnostics = diagnostics(
                    rawRmsDbfs = rawRmsDbfs,
                    enhancedRmsDbfs = rawRmsDbfs,
                    appliedGainDb = 0.0,
                    limiterReductionDb = 0.0,
                    limitedRatio = 0.0,
                    speechLikely = rawRmsDbfs > SILENCE_DBFS
                )
            )
        }

        val frameSpeech = BooleanArray((sampleCount + frameSamples - 1) / frameSamples)
        var frameIndex = 0
        var hasSpeech = false
        while (frameIndex < frameSpeech.size) {
            val startSample = frameIndex * frameSamples
            val endSample = minOf(sampleCount, startSample + frameSamples)
            var frameSumSquares = 0.0
            var sampleOffset = startSample * 2
            while (sampleOffset < endSample * 2) {
                val sample = readPcm16(input, sampleOffset)
                frameSumSquares += sample.toDouble() * sample.toDouble()
                sampleOffset += 2
            }
            val frameRms = sqrt(frameSumSquares / (endSample - startSample))
            val frameDbfs = amplitudeToDbfs(frameRms)
            val speech = !noiseGateEnabled || frameDbfs >= noiseGateThresholdDbfs
            frameSpeech[frameIndex] = speech
            hasSpeech = hasSpeech || speech
            frameIndex++
        }

        val desiredGainDb = if (hasSpeech) {
            (targetRmsDbfs - rawRmsDbfs).coerceIn(0.0, maximumGainDb)
        } else {
            0.0
        }
        currentGainDb = when {
            desiredGainDb > currentGainDb -> minOf(desiredGainDb, currentGainDb + gainRisePerBufferDb)
            desiredGainDb < currentGainDb -> maxOf(desiredGainDb, currentGainDb - gainFallPerBufferDb)
            else -> currentGainDb
        }

        val gain = 10.0.pow(currentGainDb / 20.0)
        val output = input.copyOf(count)
        var enhancedSumSquares = 0.0
        var scaledPeak = 0.0
        var outputPeak = 0.0
        var limitedSamples = 0
        index = 0
        frameIndex = 0
        var sampleInFrame = 0
        var currentFrameKept = true
        while (index < evenCount) {
            if (sampleInFrame == 0) {
                val speech = frameSpeech[frameIndex]
                val keepFrame = speech || remainingHangoverFrames > 0
                if (noiseGateEnabled) {
                    if (speech) {
                        remainingHangoverFrames = speechHangoverFrames.coerceAtLeast(0)
                    } else if (remainingHangoverFrames > 0) {
                        remainingHangoverFrames--
                    }
                }
                currentFrameKept = keepFrame || !noiseGateEnabled
                frameIndex++
            }
            val normalized = readPcm16(input, index) / PCM_FULL_SCALE
            val scaled = normalized * gain
            val magnitude = abs(scaled)
            scaledPeak = maxOf(scaledPeak, magnitude)
            if (magnitude > limiterThreshold) limitedSamples++
            val limited = if (currentFrameKept) softLimit(scaled) else 0.0
            outputPeak = maxOf(outputPeak, abs(limited))
            val outputSample = (limited * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            writePcm16(output, index, outputSample)
            enhancedSumSquares += outputSample.toDouble() * outputSample.toDouble()
            index += 2
            sampleInFrame++
            if (sampleInFrame >= frameSamples || index >= evenCount) sampleInFrame = 0
        }

        val enhancedRms = sqrt(enhancedSumSquares / sampleCount)
        val limiterReductionDb = if (scaledPeak > 0.0 && outputPeak > 0.0) {
            (20.0 * log10(scaledPeak / outputPeak)).coerceAtLeast(0.0)
        } else {
            0.0
        }
        return Result(
            pcm = output,
            diagnostics = diagnostics(
                rawRmsDbfs = rawRmsDbfs,
                enhancedRmsDbfs = amplitudeToDbfs(enhancedRms),
                appliedGainDb = currentGainDb,
                limiterReductionDb = limiterReductionDb,
                limitedRatio = limitedSamples.toDouble() / sampleCount,
                speechLikely = hasSpeech
            )
        )
    }

    private fun softLimit(value: Double): Double {
        val sign = if (value < 0.0) -1.0 else 1.0
        val magnitude = abs(value)
        if (magnitude <= limiterThreshold) return value
        val kneeWidth = 1.0 - limiterThreshold
        return sign * (limiterThreshold + kneeWidth * tanh((magnitude - limiterThreshold) / kneeWidth))
    }

    private fun diagnostics(
        rawRmsDbfs: Double,
        enhancedRmsDbfs: Double,
        appliedGainDb: Double,
        limiterReductionDb: Double,
        limitedRatio: Double,
        speechLikely: Boolean
    ) = AudioInputDiagnostics(
        rawLevelPercent = dbfsToPercent(rawRmsDbfs),
        rawRmsDbfs = rawRmsDbfs,
        enhancedLevelPercent = dbfsToPercent(enhancedRmsDbfs),
        enhancedRmsDbfs = enhancedRmsDbfs,
        appliedGainDb = appliedGainDb,
        limiterReductionDb = limiterReductionDb,
        clippedSampleRatio = limitedRatio.coerceIn(0.0, 1.0),
        speechLikely = speechLikely
    )

    data class Result(
        val pcm: ByteArray,
        val diagnostics: AudioInputDiagnostics
    )

    companion object {
        private const val PCM_FULL_SCALE = 32768.0
        private const val SILENCE_DBFS = -120.0

        private fun readPcm16(buffer: ByteArray, index: Int): Int =
            ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xff)).toShort().toInt()

        private fun writePcm16(buffer: ByteArray, index: Int, sample: Int) {
            buffer[index] = (sample and 0xff).toByte()
            buffer[index + 1] = (sample shr 8 and 0xff).toByte()
        }

        private fun amplitudeToDbfs(amplitude: Double): Double =
            if (amplitude <= 0.0) SILENCE_DBFS else 20.0 * log10(amplitude / PCM_FULL_SCALE)

        private fun dbfsToPercent(dbfs: Double): Int =
            (((dbfs + 60.0) / 60.0) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
