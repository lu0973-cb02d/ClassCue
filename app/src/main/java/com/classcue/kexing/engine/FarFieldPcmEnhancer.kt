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
 * This processor deliberately has no noise gate: weak speech is never discarded. Gain rises
 * slowly, falls quickly when a loud sound arrives, and a soft limiter prevents hard clipping.
 * It is stateful so consecutive capture buffers do not jump abruptly in volume.
 */
class FarFieldPcmEnhancer(
    private val enabled: Boolean = true,
    private val targetRmsDbfs: Double = -24.0,
    private val maximumGainDb: Double = 12.0,
    private val gainRisePerBufferDb: Double = 1.5,
    private val gainFallPerBufferDb: Double = 4.0,
    private val limiterThreshold: Double = 0.86
) {
    private var currentGainDb = 0.0

    fun reset() {
        currentGainDb = 0.0
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
                    limitedRatio = 0.0
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
                    limitedRatio = 0.0
                )
            )
        }

        val desiredGainDb = (targetRmsDbfs - rawRmsDbfs).coerceIn(0.0, maximumGainDb)
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
        while (index < evenCount) {
            val normalized = readPcm16(input, index) / PCM_FULL_SCALE
            val scaled = normalized * gain
            val magnitude = abs(scaled)
            scaledPeak = maxOf(scaledPeak, magnitude)
            if (magnitude > limiterThreshold) limitedSamples++
            val limited = softLimit(scaled)
            outputPeak = maxOf(outputPeak, abs(limited))
            val outputSample = (limited * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            writePcm16(output, index, outputSample)
            enhancedSumSquares += outputSample.toDouble() * outputSample.toDouble()
            index += 2
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
                limitedRatio = limitedSamples.toDouble() / sampleCount
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
        limitedRatio: Double
    ) = AudioInputDiagnostics(
        rawLevelPercent = dbfsToPercent(rawRmsDbfs),
        rawRmsDbfs = rawRmsDbfs,
        enhancedLevelPercent = dbfsToPercent(enhancedRmsDbfs),
        enhancedRmsDbfs = enhancedRmsDbfs,
        appliedGainDb = appliedGainDb,
        limiterReductionDb = limiterReductionDb,
        clippedSampleRatio = limitedRatio.coerceIn(0.0, 1.0)
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
