package com.tapback.app.core.detection

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Validates whether a detected candidate peak represents an intentional finger tap
 * on the phone's rear chassis, or a false positive (walking, handling, table drop, scrolling).
 */
class FalsePositiveClassifier(
    private val gyroThresholdRadSec: Float = 3.2f,
    private val zDominanceRatioMin: Float = 1.15f
) {
    // Rolling baseline buffer before the transient impact
    private val preImpactAccBuffer = CircularFloatBuffer(40)
    private var isWalkingInhibited = false

    fun onNewSample(
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float,
        gy: Float,
        gz: Float
    ) {
        val totalAcc = sqrt(ax * ax + ay * ay + az * az)
        preImpactAccBuffer.push(totalAcc)

        // Walking detection: Sustained cyclic motion over the past 40 samples (approx 600-800ms)
        // Normal walking/running produces continuous variance in gravity vector > 55 m²/s⁴
        if (preImpactAccBuffer.isFull()) {
            val variance = preImpactAccBuffer.variance()
            isWalkingInhibited = variance > 65.0f
        }
    }

    /**
     * Checks if current physical state allows a tap gesture trigger.
     * Evaluates peak amplitude against planar energy and rotational velocity.
     */
    fun evaluateTapConfidence(
        peakZAmplitude: Float,
        filteredAx: Float,
        filteredAy: Float,
        gx: Float,
        gy: Float,
        gz: Float
    ): TapValidationResult {
        val absZ = abs(peakZAmplitude)
        val planarEnergy = abs(filteredAx) + abs(filteredAy) + 0.001f
        val zRatio = absZ / planarEnergy

        // 1. Gyroscope check: High rotational speed indicates vigorous handling / arm swing
        val currentGyro = sqrt(gx * gx + gy * gy + gz * gz)
        if (currentGyro > gyroThresholdRadSec) {
            return TapValidationResult(
                isValid = false,
                confidence = 0.1f,
                rejectionReason = "High angular rotation (${"%.1f".format(currentGyro)} rad/s)",
                zRatio = zRatio
            )
        }

        // 2. Table drop / Multiaxis impact check:
        // A table drop produces roughly equal or planar-dominant shock across all axes.
        // An authentic back tap directly presses into the Z-axis with high relative ratio.
        if (zRatio < zDominanceRatioMin) {
            return TapValidationResult(
                isValid = false,
                confidence = 0.25f,
                rejectionReason = "Low Z-dominance ratio (${"%.2f".format(zRatio)} < $zDominanceRatioMin)",
                zRatio = zRatio
            )
        }

        // 3. Sustained walking / jogging oscillation check:
        if (isWalkingInhibited) {
            return TapValidationResult(
                isValid = false,
                confidence = 0.3f,
                rejectionReason = "Device in continuous walking/jogging motion",
                zRatio = zRatio
            )
        }

        // Compute normalized confidence score [0.6 - 1.0]
        val ratioBonus = ((zRatio - zDominanceRatioMin) / 3.0f).coerceIn(0f, 0.25f)
        val gyroPenalty = (currentGyro / gyroThresholdRadSec) * 0.15f
        val confidence = (0.75f + ratioBonus - gyroPenalty).coerceIn(0.5f, 0.99f)

        return TapValidationResult(
            isValid = true,
            confidence = confidence,
            rejectionReason = null,
            zRatio = zRatio
        )
    }

    fun reset() {
        preImpactAccBuffer.clear()
        isWalkingInhibited = false
    }
}

data class TapValidationResult(
    val isValid: Boolean,
    val confidence: Float,
    val rejectionReason: String?,
    val zRatio: Float
)
