package com.classcue.kexing.engine

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarFieldPcmEnhancerTest {
    @Test
    fun disabledEnhancementIsBitExact() {
        val input = sineWave(amplitude = 1_200, samples = 800)

        val result = FarFieldPcmEnhancer(enabled = false).process(input)

        assertArrayEquals(input, result.pcm)
        assertEquals(0.0, result.diagnostics.appliedGainDb, 0.0001)
        assertEquals(result.diagnostics.rawRmsDbfs, result.diagnostics.enhancedRmsDbfs, 0.0001)
    }

    @Test
    fun quietSpeechIsRaisedWithoutMutatingInput() {
        val input = sineWave(amplitude = 700, samples = 1_600)
        val untouched = input.copyOf()
        val enhancer = FarFieldPcmEnhancer(enabled = true)

        var result = enhancer.process(input)
        repeat(10) { result = enhancer.process(input) }

        assertArrayEquals(untouched, input)
        assertTrue(result.diagnostics.appliedGainDb > 6.0)
        assertTrue(result.diagnostics.appliedGainDb <= 12.0)
        assertTrue(result.diagnostics.enhancedRmsDbfs > result.diagnostics.rawRmsDbfs + 5.0)
    }

    @Test
    fun suddenLoudSignalReducesGainAndSoftLimitsInsteadOfWrapping() {
        val enhancer = FarFieldPcmEnhancer(enabled = true)
        val quiet = sineWave(amplitude = 500, samples = 1_600)
        repeat(10) { enhancer.process(quiet) }
        val gainBefore = enhancer.process(quiet).diagnostics.appliedGainDb

        val loud = constantPcm(sample = 30_000, samples = 800)
        val result = enhancer.process(loud)

        assertTrue(result.diagnostics.appliedGainDb < gainBefore)
        assertTrue(result.diagnostics.clippedSampleRatio > 0.9)
        assertTrue(result.diagnostics.limiterReductionDb > 0.0)
        assertTrue(readSamples(result.pcm).all { it in Short.MIN_VALUE..Short.MAX_VALUE })
        assertTrue(readSamples(result.pcm).all { it > 0 })
    }

    @Test
    fun sustainedSilenceIsGatedAndOddTrailingByteIsPreserved() {
        val input = ByteArray(17)
        input[input.lastIndex] = 0x5a

        val result = FarFieldPcmEnhancer(enabled = true).process(input)

        assertEquals(input.size, result.pcm.size)
        assertEquals(0x5a.toByte(), result.pcm.last())
        assertEquals(0, result.diagnostics.rawLevelPercent)
        assertTrue(!result.diagnostics.speechLikely)
        assertTrue(readSamples(result.pcm).all { it == 0 })
        assertEquals(0.0, result.diagnostics.clippedSampleRatio, 0.0)
    }

    @Test
    fun shortPauseKeepsHangoverWhileLongQuietTailIsSuppressed() {
        val samples = IntArray(320 * 16)
        for (index in 0 until 320) samples[index] = 700
        for (index in 320 until samples.size) samples[index] = 20
        val result = FarFieldPcmEnhancer(enabled = true).process(pcm(samples))
        val output = readSamples(result.pcm)

        assertTrue(result.diagnostics.speechLikely)
        assertTrue(output[320 * 4] != 0)
        assertTrue(output[320 * 14] == 0)
    }

    @Test
    fun gainRisesGraduallyAcrossBuffers() {
        val enhancer = FarFieldPcmEnhancer(enabled = true)
        val input = sineWave(amplitude = 400, samples = 800)

        val first = enhancer.process(input).diagnostics.appliedGainDb
        val second = enhancer.process(input).diagnostics.appliedGainDb

        assertEquals(1.5, first, 0.0001)
        assertEquals(3.0, second, 0.0001)
    }

    private fun sineWave(amplitude: Int, samples: Int): ByteArray {
        val values = IntArray(samples) { index ->
            (amplitude * kotlin.math.sin(2.0 * Math.PI * index / 40.0)).roundToInt()
        }
        return pcm(values)
    }

    private fun constantPcm(sample: Int, samples: Int): ByteArray = pcm(IntArray(samples) { sample })

    private fun pcm(samples: IntArray): ByteArray = ByteArray(samples.size * 2).also { bytes ->
        samples.forEachIndexed { index, sample ->
            bytes[index * 2] = (sample and 0xff).toByte()
            bytes[index * 2 + 1] = (sample shr 8 and 0xff).toByte()
        }
    }

    private fun readSamples(bytes: ByteArray): List<Int> = buildList {
        var index = 0
        while (index + 1 < bytes.size) {
            add(((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xff)).toShort().toInt())
            index += 2
        }
    }
}
