package com.tapback.app.core.sensor

import android.hardware.Sensor

data class SensorDeviceInfo(
    val name: String,
    val vendor: String,
    val version: Int,
    val type: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val isWakeup: Boolean
) {
    companion object {
        fun from(sensor: Sensor?): SensorDeviceInfo? {
            if (sensor == null) return null
            return SensorDeviceInfo(
                name = sensor.name,
                vendor = sensor.vendor,
                version = sensor.version,
                type = sensor.type,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayUs = sensor.minDelay,
                maxDelayUs = sensor.maxDelay,
                isWakeup = sensor.isWakeUpSensor
            )
        }
    }
}

data class AccSample(
    val index: Long,
    val timestampNs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val magnitude: Float,
    val deltaMs: Float,
    val frequencyHz: Float
)

data class GyroSample(
    val index: Long,
    val timestampNs: Long,
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val magnitude: Float,
    val deltaMs: Float,
    val frequencyHz: Float
)

data class SessionStats(
    val sampleCount: Long = 0L,
    val currentHz: Float = 0f,
    val minMagnitude: Float = Float.MAX_VALUE,
    val maxMagnitude: Float = 0f,
    val peakToPeak: Float = 0f
)
