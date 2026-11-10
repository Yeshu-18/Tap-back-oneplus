package com.tapback.app.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.tapback.app.core.detection.TapDetectorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensor acquisition layer tailored for OnePlus Nord 5 / OxygenOS.
 * Acquires Accelerometer and Gyroscope streams with low overhead on a dedicated background thread.
 */
class NordSensorController(
    private val context: Context,
    private val detectionEngine: TapDetectorEngine
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var proximity: Sensor? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var latestGx = 0f
    private var latestGy = 0f
    private var latestGz = 0f
    private var isProximityNear = false

    init {
        findOptimalSensors()
    }

    private fun findOptimalSensors() {
        val sm = sensorManager ?: return
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    @Synchronized
    fun startListening(): Boolean {
        if (_isListening.value) return true
        val sm = sensorManager ?: return false
        val acc = accelerometer ?: return false

        try {
            sensorThread = HandlerThread("NordSensorThread", Thread.MAX_PRIORITY).apply { start() }
            sensorHandler = Handler(sensorThread!!.looper)

            // Bosch BMI26x produces ~101 Hz on SENSOR_DELAY_GAME/FASTEST on Nord 5
            val accRegistered = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, sensorHandler)

            gyroscope?.let { gyro ->
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
            }

            proximity?.let { prox ->
                sm.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
            }

            _isListening.value = accRegistered
            Log.d("NordSensorController", "Sensor listeners registered: acc=$accRegistered")
            return accRegistered
        } catch (e: Exception) {
            Log.e("NordSensorController", "Failed to register sensors: ${e.message}")
            stopListening()
            return false
        }
    }

    @Synchronized
    fun stopListening() {
        if (!_isListening.value) return
        try {
            sensorManager?.unregisterListener(this)
            sensorThread?.quitSafely()
            sensorThread = null
            sensorHandler = null
            _isListening.value = false
            isProximityNear = false
            detectionEngine.reset()
            Log.d("NordSensorController", "Sensors unregistered")
        } catch (e: Exception) {
            Log.e("NordSensorController", "Error unregistering sensors: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                isProximityNear = distance < 3.0f || distance < maxRange / 2.0f
            }

            Sensor.TYPE_GYROSCOPE -> {
                latestGx = event.values[0]
                latestGy = event.values[1]
                latestGz = event.values[2]
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // Pocket rejection: if proximity sensor is obstructed (e.g. inside pocket or facedown on mattress),
                // suppress tap detection to save CPU and prevent spurious actions.
                if (isProximityNear) return

                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                detectionEngine.onSensorData(
                    timestampNs = event.timestamp,
                    ax = ax,
                    ay = ay,
                    az = az,
                    gx = latestGx,
                    gy = latestGy,
                    gz = latestGz
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for continuous physical motion sensing
    }
}
