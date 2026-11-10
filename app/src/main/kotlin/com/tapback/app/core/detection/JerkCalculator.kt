package com.tapback.app.core.detection

/**
 * Calculates numerical jerk (derivative of acceleration over time: da/dt).
 */
class JerkCalculator {
    private var lastAcc = 0f
    private var lastTimestampNs = 0L
    private var isFirst = true

    fun calculate(acc: Float, timestampNs: Long): Float {
        if (isFirst) {
            lastAcc = acc
            lastTimestampNs = timestampNs
            isFirst = false
            return 0f
        }

        val dtSec = (timestampNs - lastTimestampNs) / 1_000_000_000.0f
        lastAcc = acc
        lastTimestampNs = timestampNs

        if (dtSec <= 0.0001f || dtSec > 0.2f) {
            // Discontinuity or gap; reset calculation
            return 0f
        }

        return (acc - lastAcc) / dtSec
    }

    fun reset() {
        lastAcc = 0f
        lastTimestampNs = 0L
        isFirst = true
    }
}
