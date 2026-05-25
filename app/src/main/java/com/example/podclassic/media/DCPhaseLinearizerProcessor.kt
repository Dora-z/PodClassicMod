package com.example.podclassic.media

class DCPhaseLinearizerProcessor {
    var enabled: Boolean = false
    var correctionStrength: Float = 0.0f
    var lowFreqDelay: Float = 0.0f
    var midFreqDelay: Float = 0.0f
    var highFreqDelay: Float = 0.0f
    var crossoverFreq: Float = 500.0f
    var highCrossoverFreq: Float = 2000.0f

    fun processAudio(buffer: ShortArray, size: Int): ShortArray {
        if (!enabled || size <= 0) {
            return buffer
        }

        val out = buffer.copyOf()
        val maxIndex = size.coerceAtMost(out.size)
        val blend = correctionStrength.coerceIn(0f, 1f) * 0.12f

        for (i in 1 until maxIndex) {
            val current = out[i].toInt()
            val previous = out[i - 1].toInt()
            val smoothed = current + ((previous - current) * blend).toInt()
            out[i] = smoothed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return out
    }

    fun applyPreset(preset: DCPhasePreset) {
        correctionStrength = preset.strength
        lowFreqDelay = preset.lowDelay
        midFreqDelay = preset.midDelay
        highFreqDelay = preset.highDelay
        crossoverFreq = preset.crossover
        highCrossoverFreq = preset.highCrossover
        enabled = preset != DCPhasePreset.NONE
    }

    fun reset() {
    }
}
