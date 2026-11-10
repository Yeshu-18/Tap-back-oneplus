package com.tapback.app.core.repository

import com.tapback.app.domain.models.RawSensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean repository interface for streaming hardware or replay sensor data.
 */
interface SensorRepository {
    /**
     * Whether the sensor stream is actively capturing/replaying.
     */
    val isStreaming: StateFlow<Boolean>

    /**
     * Continuous stream of raw sensor measurements.
     */
    val sensorStream: Flow<RawSensorData>

    /**
     * Starts listening to sensor updates. Returns true if successfully started.
     */
    fun startStreaming(): Boolean

    /**
     * Stops listening to sensor updates.
     */
    fun stopStreaming()
}

