package com.tapback.app.core.detection

import kotlin.math.abs

/**
 * Detects discrete mechanical shock peaks caused by fingertip strikes on the rear phone body.
 */
class PeakDetector(
    var threshold: Float = 14.5f,
    private val minPeakSpacingMs: Long = 110L
) {
    private var lastPeakTimestampMs: Long = 0L
    private var prevSlope = 0f
    private var prevValue = 0f

    /**
     * Inspects current filtered signal sample.
     * Returns the peak amplitude if a valid local extremum exceeding threshold is confirmed, or null otherwise.
     */
    fun processSample(
        value: Float,
        timestampMs: Long
    ): Float? {
        val currentSlope = value - prevValue
        val isLocalPeak = (prevSlope > 0f && currentSlope <= 0f) || (prevSlope < 0f && currentSlope >= 0f)
        val candidateAmplitude = abs(prevValue)

        prevSlope = currentSlope
        prevValue = value

        if (isLocalPeak && candidateAmplitude >= threshold) {
            val elapsedSinceLast = timestampMs - lastPeakTimestampMs
            if (elapsedSinceLast >= minPeakSpacingMs) {
                lastPeakTimestampMs = timestampMs
                return candidateAmplitude
            }
        }
        return null
    }

    fun reset() {
        lastPeakTimestampMs = 0L
        prevSlope = 0f
        prevValue = 0f
    }
}
