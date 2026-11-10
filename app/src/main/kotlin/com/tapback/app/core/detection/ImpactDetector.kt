package com.tapback.app.core.detection

import com.tapback.app.domain.models.ImpactEvent
import com.tapback.app.domain.models.ProcessedSensorSample
import kotlin.math.abs

/**
 * Detects discrete mechanical shock peaks on dynamic Z-axis acceleration,
 * utilizing adaptive thresholding and refractory debouncing to suppress chassis ring-down.
 */
class ImpactDetector(
    var baseThreshold: Float = 2.80f,
    var minImpactSpacingMs: Long = 95L,
    private val motionFilter: MotionRejectionFilter = MotionRejectionFilter()
) {
    private var lastImpactTimestampMs = 0L

    private var prevDynamicAz = 0f
    private var prevSlope = 0f
    private var prevSample: ProcessedSensorSample? = null

    // Rolling noise floor tracking for adaptive thresholding
    private val noiseBuffer = CircularFloatBuffer(20)

    /**
     * Updates threshold dynamically (e.g. from user calibration or preset).
     */
    fun updateThreshold(newThreshold: Float) {
        this.baseThreshold = newThreshold.coerceAtLeast(2.0f)
    }

    /**
     * Ingests a preprocessed sensor sample and returns an ImpactEvent if a valid physical
     * impact peak is confirmed, or null otherwise.
     */
    fun processSample(sample: ProcessedSensorSample, isInSequence: Boolean = false): ImpactEvent? {
        // Feed rolling motion filter
        motionFilter.onNewSample(sample)

        // Track rolling baseline noise when not in transient impact state
        if (abs(sample.dynamicAz) < 2.2f) {
            noiseBuffer.push(abs(sample.dynamicAz))
        }

        val currentSlope = sample.dynamicAz - prevDynamicAz
        val isLocalPeak = prevSlope > 0f && currentSlope <= 0f
        val peakVal = prevDynamicAz
        val peakSample = prevSample

        // Update tracking state
        prevSlope = currentSlope
        prevDynamicAz = sample.dynamicAz
        prevSample = sample

        if (isLocalPeak && peakSample != null) {
            // Adaptive threshold calculation:
            // When in sequence (awaiting 2nd or 3rd tap), use relaxed threshold without noise floor penalty
            // so consecutive lighter taps register effortlessly without requiring extra finger force.
            val effectiveThreshold = if (isInSequence) {
                (baseThreshold * 0.85f).coerceAtLeast(2.10f)
            } else {
                val noiseFloor = if (noiseBuffer.isFull()) noiseBuffer.mean() else 0.2f
                val adaptiveAdjustment = if (noiseFloor > 0.8f) (noiseFloor - 0.8f) * 0.5f else 0f
                (baseThreshold + adaptiveAdjustment).coerceAtMost(baseThreshold + 1.5f)
            }

            if (peakVal >= effectiveThreshold) {
                val elapsedSinceLast = peakSample.timestampMs - lastImpactTimestampMs
                if (elapsedSinceLast >= minImpactSpacingMs) {
                    // Check motion rejection filter
                    val filterDecision = motionFilter.evaluateImpact(peakSample, isInSequence)
                    if (filterDecision.isAccepted) {
                        lastImpactTimestampMs = peakSample.timestampMs
                        return ImpactEvent(
                            timestampNs = peakSample.timestampNs,
                            timestampMs = peakSample.timestampMs,
                            dynamicZStrength = peakVal,
                            dynamicMagnitude = peakSample.dynamicMagnitude,
                            gyroActivity = peakSample.gyroMagnitude,
                            zDominanceRatio = filterDecision.zDominanceRatio,
                            confidence = filterDecision.confidence
                        )
                    }
                }
            }
        }

        return null
    }

    fun reset() {
        lastImpactTimestampMs = 0L
        prevDynamicAz = 0f
        prevSlope = 0f
        prevSample = null
        noiseBuffer.clear()
        motionFilter.reset()
    }
}
