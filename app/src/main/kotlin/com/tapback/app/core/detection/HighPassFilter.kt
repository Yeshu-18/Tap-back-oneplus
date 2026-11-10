package com.tapback.app.core.detection

import kotlin.math.PI

/**
 * First-order IIR High-Pass Filter for removing DC gravity (0 Hz) and slow hand tilting (< 1 Hz)
 * while preserving high-frequency mechanical shock impulses (15-40 Hz).
 */
class HighPassFilter(cutoffHz: Float = 3.0f, sampleRateHz: Float = 60f) {
    private var alpha: Float = 0.8f
    private var prevInput = 0f
    private var prevOutput = 0f
    private var initialized = false

    init {
        updateCutoff(cutoffHz, sampleRateHz)
    }

    fun updateCutoff(cutoffHz: Float, sampleRateHz: Float) {
        val dt = 1.0f / sampleRateHz.coerceAtLeast(10f)
        val rc = 1.0f / (2.0f * PI.toFloat() * cutoffHz.coerceAtLeast(0.5f))
        alpha = rc / (rc + dt)
    }

    fun filter(input: Float): Float {
        if (!initialized) {
            prevInput = input
            prevOutput = 0f
            initialized = true
            return 0f
        }
        val output = alpha * (prevOutput + input - prevInput)
        prevInput = input
        prevOutput = output
        return output
    }

    fun reset() {
        prevInput = 0f
        prevOutput = 0f
        initialized = false
    }
}
