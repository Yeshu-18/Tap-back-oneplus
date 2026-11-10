package com.tapback.app.core.detection

import com.tapback.app.domain.models.ProcessedSensorSample
import com.tapback.app.domain.models.RawSensorData
import kotlin.math.sqrt

/**
 * Production-grade Sensor Preprocessor for Bosch BMI26x (~101 Hz on OnePlus Nord 5).
 * Computes gravity-compensated dynamic acceleration, total magnitude, gyroscope activity,
 * and tracks sampling intervals and frequency irregularities.
 */
class SensorPreprocessor(
    var gravityAlpha: Float = 0.90f
) {
    private var isInitialized = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.81f

    private var prevTimestampNs = 0L
    private var rollingFrequencyHz = 101.0f

    /**
     * Ingests a raw sensor data sample and returns the preprocessed sample.
     */
    fun process(raw: RawSensorData): ProcessedSensorSample {
        return process(
            timestampNs = raw.timestampNs,
            ax = raw.ax,
            ay = raw.ay,
            az = raw.az,
            gx = raw.gx,
            gy = raw.gy,
            gz = raw.gz
        )
    }

    /**
     * Hot path ingestion.
     */
    fun process(
        timestampNs: Long,
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float = 0f,
        gy: Float = 0f,
        gz: Float = 0f
    ): ProcessedSensorSample {
        val timestampMs = timestampNs / 1_000_000L

        // Handle timing & sampling frequency tracking
        val deltaMs: Float
        if (!isInitialized || prevTimestampNs == 0L) {
            gravityX = ax
            gravityY = ay
            gravityZ = az
            deltaMs = 10.0f
            rollingFrequencyHz = 101.0f
            isInitialized = true
        } else {
            val rawDeltaMs = (timestampNs - prevTimestampNs) / 1_000_000f
            // Handle sensor pause or large timestamp jump (> 500ms)
            if (rawDeltaMs > 500f || rawDeltaMs <= 0f) {
                // Re-initialize gravity vector baseline after gap
                gravityX = ax
                gravityY = ay
                gravityZ = az
                deltaMs = 10.0f
            } else {
                deltaMs = rawDeltaMs
                val instantHz = 1000f / deltaMs.coerceAtLeast(1.0f)
                // Smooth sampling frequency tracking
                rollingFrequencyHz = 0.95f * rollingFrequencyHz + 0.05f * instantHz
            }
        }
        prevTimestampNs = timestampNs

        // Low-pass filter for gravity estimation
        gravityX = gravityAlpha * gravityX + (1.0f - gravityAlpha) * ax
        gravityY = gravityAlpha * gravityY + (1.0f - gravityAlpha) * ay
        gravityZ = gravityAlpha * gravityZ + (1.0f - gravityAlpha) * az

        // Dynamic acceleration (transient mechanical shock)
        val dynamicAx = ax - gravityX
        val dynamicAy = ay - gravityY
        val dynamicAz = az - gravityZ

        val dynamicMagnitude = sqrt(dynamicAx * dynamicAx + dynamicAy * dynamicAy + dynamicAz * dynamicAz)
        val totalMagnitude = sqrt(ax * ax + ay * ay + az * az)
        val gyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)

        return ProcessedSensorSample(
            timestampNs = timestampNs,
            timestampMs = timestampMs,
            ax = ax,
            ay = ay,
            az = az,
            gx = gx,
            gy = gy,
            gz = gz,
            gravityX = gravityX,
            gravityY = gravityY,
            gravityZ = gravityZ,
            dynamicAx = dynamicAx,
            dynamicAy = dynamicAy,
            dynamicAz = dynamicAz,
            dynamicMagnitude = dynamicMagnitude,
            totalMagnitude = totalMagnitude,
            gyroMagnitude = gyroMagnitude,
            deltaMs = deltaMs,
            samplingFrequencyHz = rollingFrequencyHz
        )
    }

    fun reset() {
        isInitialized = false
        prevTimestampNs = 0L
        gravityX = 0f
        gravityY = 0f
        gravityZ = 9.81f
        rollingFrequencyHz = 101.0f
    }
}

