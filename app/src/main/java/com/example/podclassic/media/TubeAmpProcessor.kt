package com.example.podclassic.media

import kotlin.math.abs

class TubeAmpProcessor {
    var enabled: Boolean = false
    var tubeGain: Float = 1.0f
    var saturationAmount: Float = 0.0f
    var harmonicContent: Float = 0.0f
    var compressionRatio: Float = 1.0f
    var attackTimeMs: Float = 10.0f
    var releaseTimeMs: Float = 100.0f

    private var warmth: Float = 0.0f

    fun processAudio(buffer: ShortArray, size: Int): ShortArray {
        if (!enabled || size <= 0) {
            return buffer
        }

        val out = buffer.copyOf()
        val maxIndex = size.coerceAtMost(out.size)
        val gain = tubeGain.coerceAtLeast(0f)
        val saturation = saturationAmount.coerceIn(0f, 1f)
        val harmonics = harmonicContent.coerceIn(0f, 1f)
        val warmthFactor = warmth.coerceIn(0f, 1f)
        val compression = compressionRatio.coerceAtLeast(1f)

        for (i in 0 until maxIndex) {
            val sample = out[i].toFloat() / Short.MAX_VALUE
            val shaped = tanhLike(sample * gain, saturation, harmonics, warmthFactor)
            val compressed = shaped / (1f + (abs(shaped) * (compression - 1f) * 0.5f))
            out[i] = (compressed.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

        return out
    }

    fun updateWarmth(value: Float) {
        warmth = value
    }

    fun applyPreset(preset: TubeAmpPreset) {
        tubeGain = preset.gain
        saturationAmount = preset.saturation
        harmonicContent = preset.harmonics
        compressionRatio = preset.ratio
        attackTimeMs = preset.attackMs
        releaseTimeMs = preset.releaseMs
        warmth = preset.warmth
        enabled = preset != TubeAmpPreset.NONE
    }

    fun reset() {
    }

    private fun tanhLike(sample: Float, saturation: Float, harmonics: Float, warmth: Float): Float {
        val softClip = sample / (1f + abs(sample) * (0.8f + saturation * 2.2f))
        val evenHarmonic = sample * sample * sample.sign() * harmonics * 0.35f
        val warmBias = sample * (1f - warmth * 0.18f) + softClip * warmth * 0.18f
        return (warmBias + softClip * saturation * 0.7f + evenHarmonic).coerceIn(-1.2f, 1.2f)
    }

    private fun Float.sign(): Float = when {
        this > 0f -> 1f
        this < 0f -> -1f
        else -> 0f
    }
}
