package com.tapback.app.core.detection

import com.tapback.app.domain.models.ProcessedSensorSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Validates transient impacts against physical false positives:
 * - Sustained cyclic movement (walking/shaking acceleration variance)
 * - Rapid orientation changes (shifting gravity vector during handling)
 * - Excessive angular rotation (gyroscope slew rates)
 * - Multi-axis impacts (table drops, pocket collisions, edge bumps)
 */
class MotionRejectionFilter(
    var minZDominanceRatio: Float = 1.25f,
    var maxCurrentGyroRadSec: Float = 1.45f,
    var maxIdleSustainedGyroRadSec: Float = 0.70f,
    var maxSequenceSustainedGyroRadSec: Float = 1.35f,
    var maxOrientationShiftMps2: Float = 3.80f,
    var maxAccVarianceMps4: Float = 60.00f
) {
    // Rolling buffers for movement and orientation analysis (~25-30 samples @ 101 Hz ≈ 250-300 ms)
    private val bufferSize = 28
    private val accMagBuffer = CircularFloatBuffer(bufferSize)
    private val gyroMagBuffer = CircularFloatBuffer(bufferSize)
    private val gravXBuffer = CircularFloatBuffer(bufferSize)
    private val gravYBuffer = CircularFloatBuffer(bufferSize)
    private val gravZBuffer = CircularFloatBuffer(bufferSize)

    data class FilterDecision(
        val isAccepted: Boolean,
        val zDominanceRatio: Float,
        val confidence: Float,
        val rejectionReason: String? = null
    )

    /**
     * Feeds preprocessed sample into rolling motion history.
     */
    fun onNewSample(sample: ProcessedSensorSample) {
        accMagBuffer.push(sample.totalMagnitude)
        gyroMagBuffer.push(sample.gyroMagnitude)
        gravXBuffer.push(sample.gravityX)
        gravYBuffer.push(sample.gravityY)
        gravZBuffer.push(sample.gravityZ)
    }

    /**
     * Evaluates whether an impact candidate on dynamic Z represents an intentional back tap.
     * @param isInSequence Whether a gesture sequence is already in progress (AWAITING_SECOND or AWAITING_THIRD).
     *                     When true, permits higher sustained angular velocity due to tap recoil.
     */
    fun evaluateImpact(sample: ProcessedSensorSample, isInSequence: Boolean = false): FilterDecision {
        val dazAbs = abs(sample.dynamicAz)
        val planarMag = sqrt(sample.dynamicAx * sample.dynamicAx + sample.dynamicAy * sample.dynamicAy) + 0.001f
        val zDominanceRatio = dazAbs / planarMag

        // 1. Z-axis Dominance: A rear chassis tap directly acts upon the Z-axis
        val requiredZRatio = if (isInSequence) 1.10f else minZDominanceRatio
        if (zDominanceRatio < requiredZRatio) {
            return FilterDecision(
                isAccepted = false,
                zDominanceRatio = zDominanceRatio,
                confidence = 0.20f,
                rejectionReason = "Low Z-dominance (${"%.2f".format(zDominanceRatio)} < $requiredZRatio)"
            )
        }

        // 2. Instantaneous Gyroscope Check: High rotational rate during tap indicates vigorous manipulation
        val currentMaxGyro = if (isInSequence) 1.75f else maxCurrentGyroRadSec
        if (sample.gyroMagnitude > currentMaxGyro) {
            return FilterDecision(
                isAccepted = false,
                zDominanceRatio = zDominanceRatio,
                confidence = 0.15f,
                rejectionReason = "High angular velocity (${"%.2f".format(sample.gyroMagnitude)} rad/s > $currentMaxGyro)"
            )
        }

        // 3. Sustained Angular Rotation Check (average over preceding ~250ms)
        val maxAllowedGyro = if (isInSequence) maxSequenceSustainedGyroRadSec else maxIdleSustainedGyroRadSec
        if (gyroMagBuffer.size >= 10) {
            var sumGyro = 0f
            for (i in 0 until gyroMagBuffer.size) {
                sumGyro += gyroMagBuffer[i]
            }
            val avgGyro = sumGyro / gyroMagBuffer.size
            if (avgGyro > maxAllowedGyro) {
                return FilterDecision(
                    isAccepted = false,
                    zDominanceRatio = zDominanceRatio,
                    confidence = 0.25f,
                    rejectionReason = "Sustained device rotation (${"%.2f".format(avgGyro)} rad/s > $maxAllowedGyro)"
                )
            }
        }

        // 4. Orientation Shift Check: Change in gravity vector over preceding window
        if (gravXBuffer.size >= 15) {
            val oldestIdx = 0
            val latestIdx = gravXBuffer.size - 1
            val dgx = gravXBuffer[latestIdx] - gravXBuffer[oldestIdx]
            val dgy = gravYBuffer[latestIdx] - gravYBuffer[oldestIdx]
            val dgz = gravZBuffer[latestIdx] - gravZBuffer[oldestIdx]
            val orientationShift = sqrt(dgx * dgx + dgy * dgy + dgz * dgz)

            if (orientationShift > maxOrientationShiftMps2) {
                return FilterDecision(
                    isAccepted = false,
                    zDominanceRatio = zDominanceRatio,
                    confidence = 0.25f,
                    rejectionReason = "Active orientation tilt (${"%.2f".format(orientationShift)} m/s²)"
                )
            }
        }

        // 5. Sustained Cyclic Movement / Walking Check (variance of acceleration magnitude)
        if (accMagBuffer.isFull()) {
            val variance = accMagBuffer.variance()
            if (variance > maxAccVarianceMps4) {
                return FilterDecision(
                    isAccepted = false,
                    zDominanceRatio = zDominanceRatio,
                    confidence = 0.30f,
                    rejectionReason = "Sustained motion energy (var=${"%.1f".format(variance)} m²/s⁴)"
                )
            }
        }

        // Compute high-confidence normalized score [0.70 - 0.99]
        val zBonus = ((zDominanceRatio - minZDominanceRatio) / 8.0f).coerceIn(0f, 0.20f)
        val gyroPenalty = (sample.gyroMagnitude / maxCurrentGyroRadSec) * 0.12f
        val confidence = (0.80f + zBonus - gyroPenalty).coerceIn(0.60f, 0.99f)

        return FilterDecision(
            isAccepted = true,
            zDominanceRatio = zDominanceRatio,
            confidence = confidence,
            rejectionReason = null
        )
    }

    fun reset() {
        accMagBuffer.clear()
        gyroMagBuffer.clear()
        gravXBuffer.clear()
        gravYBuffer.clear()
        gravZBuffer.clear()
    }
}
