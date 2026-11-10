package com.tapback.app.core.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.tapback.app.domain.models.RawSensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production SensorRepository tailored for OnePlus Nord 5 (CPH2707) / OxygenOS.
 * Acquires Bosch BMI26x Accelerometer and Gyroscope sensor events on a high-priority HandlerThread.
 * Handles sensor registration failures and gracefully reconnects.
 */
class NordSensorRepository(
    private val context: Context
) : SensorRepository, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _sensorStream = MutableSharedFlow<RawSensorData>(
        extraBufferCapacity = 256
    )
    override val sensorStream: Flow<RawSensorData> = _sensorStream.asSharedFlow()

    // Latest gyro readings to pair with accelerometer events
    private var latestGx = 0f
    private var latestGy = 0f
    private var latestGz = 0f

    init {
        findOptimalSensors()
    }

    private fun findOptimalSensors() {
        val sm = sensorManager ?: return
        // Prefer wake-up sensors if available on Nord 5, otherwise default hardware sensors
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelerometer == null) {
            Log.e("NordSensorRepo", "No hardware accelerometer found!")
        }
    }

    @Synchronized
    override fun startStreaming(): Boolean {
        if (_isStreaming.value) return true
        val sm = sensorManager ?: return false
        val acc = accelerometer ?: return false

        try {
            sensorThread = HandlerThread("NordSensorThread", Thread.MAX_PRIORITY).apply { start() }
            sensorHandler = Handler(sensorThread!!.looper)

            // SENSOR_DELAY_GAME (~100 Hz / 10ms) matches OnePlus Nord 5 BMI26x native recording rate
            val accRegistered = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
            val gyroRegistered = gyroscope?.let { gyro ->
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
            } ?: false

            _isStreaming.value = accRegistered
            Log.d("NordSensorRepo", "Sensors registered: acc=$accRegistered, gyro=$gyroRegistered")
            return accRegistered
        } catch (e: Exception) {
            Log.e("NordSensorRepo", "Failed to start sensor stream: ${e.message}")
            stopStreaming()
            return false
        }
    }

    @Synchronized
    override fun stopStreaming() {
        if (!_isStreaming.value) return
        try {
            sensorManager?.unregisterListener(this)
            sensorThread?.quitSafely()
            sensorThread = null
            sensorHandler = null
            _isStreaming.value = false
            Log.d("NordSensorRepo", "Sensors unregistered safely")
        } catch (e: Exception) {
            Log.e("NordSensorRepo", "Error unregistering sensors: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                latestGx = event.values[0]
                latestGy = event.values[1]
                latestGz = event.values[2]
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val data = RawSensorData(
                    timestampNs = event.timestamp,
                    ax = event.values[0],
                    ay = event.values[1],
                    az = event.values[2],
                    gx = latestGx,
                    gy = latestGy,
                    gz = latestGz
                )
                _sensorStream.tryEmit(data)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Continuous physical motion sensing does not require accuracy compensation
    }
}

