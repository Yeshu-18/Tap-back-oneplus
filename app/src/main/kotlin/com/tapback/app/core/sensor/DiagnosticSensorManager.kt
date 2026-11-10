package com.tapback.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class DiagnosticSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    val accelerometerInfo: SensorDeviceInfo?
    val gyroscopeInfo: SensorDeviceInfo?

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Target: 10ms sampling = 10,000 microseconds (~100 Hz)
    var samplingPeriodUs: Int = 10_000

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _latestAcc = MutableStateFlow(AccSample(0L, 0L, 0f, 0f, 9.8f, 9.8f, 0f, 0f))
    val latestAcc: StateFlow<AccSample> = _latestAcc.asStateFlow()

    private val _latestGyro = MutableStateFlow(GyroSample(0L, 0L, 0f, 0f, 0f, 0f, 0f, 0f))
    val latestGyro: StateFlow<GyroSample> = _latestGyro.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<AccSample>>(emptyList())
    val recentEvents: StateFlow<List<AccSample>> = _recentEvents.asStateFlow()

    private val _magnitudeWaveform = MutableStateFlow<List<Float>>(emptyList())
    val magnitudeWaveform: StateFlow<List<Float>> = _magnitudeWaveform.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    // Recording session state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val recordedLog = mutableListOf<String>()

    // Frequency & timing tracking
    private var accSampleIndex = 0L
    private var lastAccTimestampNs = 0L
    private var gyroSampleIndex = 0L
    private var lastGyroTimestampNs = 0L

    private var emaAccHz = 100f
    private var emaGyroHz = 100f

    private var minMag = Float.MAX_VALUE
    private var maxMag = 0f

    // Internal ring buffer for graph (capacity 80)
    private val waveBuffer = FloatArray(80)
    private var waveHead = 0
    private var waveCount = 0

    // Internal buffer for scrolling history (capacity 50)
    private val eventHistory = ArrayList<AccSample>(50)

    init {
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelerometerInfo = SensorDeviceInfo.from(accelerometer)
        gyroscopeInfo = SensorDeviceInfo.from(gyroscope)

        Log.i("DiagnosticSensor", "Hardware Accelerometer: ${accelerometerInfo?.name} by ${accelerometerInfo?.vendor}")
        Log.i("DiagnosticSensor", "Hardware Gyroscope: ${gyroscopeInfo?.name} by ${gyroscopeInfo?.vendor}")
    }

    @Synchronized
    fun start(): Boolean {
        if (_isMonitoring.value) return true
        val sm = sensorManager ?: return false
        val acc = accelerometer ?: return false

        try {
            sensorThread = HandlerThread("SensorDiagnosticThread", Process.THREAD_PRIORITY_MORE_FAVORABLE).apply {
                start()
            }
            sensorHandler = Handler(sensorThread!!.looper)

            val accOk = sm.registerListener(this, acc, samplingPeriodUs, sensorHandler)
            val gyroOk = gyroscope?.let { sm.registerListener(this, it, samplingPeriodUs, sensorHandler) } ?: false

            _isMonitoring.value = accOk
            Log.i("DiagnosticSensor", "Sensors registered: acc=$accOk ($samplingPeriodUs us), gyro=$gyroOk")
            return accOk
        } catch (e: Exception) {
            Log.e("DiagnosticSensor", "Failed to start monitoring: ${e.message}")
            stop()
            return false
        }
    }

    @Synchronized
    fun stop() {
        if (!_isMonitoring.value) return
        try {
            sensorManager?.unregisterListener(this)
            sensorThread?.quitSafely()
            sensorThread = null
            sensorHandler = null
            _isMonitoring.value = false
            Log.i("DiagnosticSensor", "Sensors unregistered and stopped")
        } catch (e: Exception) {
            Log.e("DiagnosticSensor", "Error stopping: ${e.message}")
        }
    }

    @Synchronized
    fun reset() {
        accSampleIndex = 0L
        gyroSampleIndex = 0L
        lastAccTimestampNs = 0L
        lastGyroTimestampNs = 0L
        minMag = Float.MAX_VALUE
        maxMag = 0f
        waveHead = 0
        waveCount = 0
        eventHistory.clear()
        recordedLog.clear()
        _recentEvents.value = emptyList()
        _magnitudeWaveform.value = emptyList()
        _sessionStats.value = SessionStats()
    }

    fun startRecording() {
        recordedLog.clear()
        recordedLog.add("index,timestamp_ns,ax,ay,az,magnitude,delta_ms,frequency_hz,gx,gy,gz")
        _isRecording.value = true
        Log.i("DiagnosticSensor", "Started recording diagnostic session")
    }

    fun stopRecording(): File? {
        _isRecording.value = false
        if (recordedLog.isEmpty()) return null
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val logDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            val logFile = File(logDir, "tapback_sensor_log_$timestamp.csv")
            logFile.writeText(recordedLog.joinToString("\n"))
            Log.i("DiagnosticSensor", "Diagnostic log saved: ${logFile.absolutePath} (${recordedLog.size} rows)")
            logFile
        } catch (e: Exception) {
            Log.e("DiagnosticSensor", "Failed to export diagnostic log: ${e.message}")
            null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timestampNs = event.timestamp

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accSampleIndex++
                val deltaNs = if (lastAccTimestampNs > 0L) timestampNs - lastAccTimestampNs else 10_000_000L
                lastAccTimestampNs = timestampNs
                val deltaMs = deltaNs / 1_000_000.0f

                val instantaneousHz = if (deltaMs > 0.05f) 1000.0f / deltaMs else 100f
                emaAccHz = emaAccHz * 0.9f + instantaneousHz * 0.1f

                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)

                if (mag < minMag) minMag = mag
                if (mag > maxMag) maxMag = mag

                val sample = AccSample(
                    index = accSampleIndex,
                    timestampNs = timestampNs,
                    ax = ax,
                    ay = ay,
                    az = az,
                    magnitude = mag,
                    deltaMs = deltaMs,
                    frequencyHz = emaAccHz
                )
                _latestAcc.value = sample

                // Update magnitude waveform buffer
                waveBuffer[waveHead] = mag
                waveHead = (waveHead + 1) % waveBuffer.size
                if (waveCount < waveBuffer.size) waveCount++
                val snapshotList = ArrayList<Float>(waveCount)
                val start = if (waveCount < waveBuffer.size) 0 else waveHead
                for (i in 0 until waveCount) {
                    snapshotList.add(waveBuffer[(start + i) % waveBuffer.size])
                }
                _magnitudeWaveform.value = snapshotList

                // Update scrolling event history
                if (eventHistory.size >= 50) {
                    eventHistory.removeAt(eventHistory.size - 1)
                }
                eventHistory.add(0, sample)
                _recentEvents.value = ArrayList(eventHistory)

                // Update session stats
                _sessionStats.value = SessionStats(
                    sampleCount = accSampleIndex,
                    currentHz = emaAccHz,
                    minMagnitude = if (minMag == Float.MAX_VALUE) 0f else minMag,
                    maxMagnitude = maxMag,
                    peakToPeak = if (minMag == Float.MAX_VALUE) 0f else (maxMag - minMag)
                )

                // Log if recording
                if (_isRecording.value) {
                    val gyro = _latestGyro.value
                    recordedLog.add(
                        "$accSampleIndex,$timestampNs,$ax,$ay,$az,$mag,$deltaMs,$emaAccHz,${gyro.gx},${gyro.gy},${gyro.gz}"
                    )
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                gyroSampleIndex++
                val deltaNs = if (lastGyroTimestampNs > 0L) timestampNs - lastGyroTimestampNs else 10_000_000L
                lastGyroTimestampNs = timestampNs
                val deltaMs = deltaNs / 1_000_000.0f
                val instantaneousHz = if (deltaMs > 0.05f) 1000.0f / deltaMs else 100f
                emaGyroHz = emaGyroHz * 0.9f + instantaneousHz * 0.1f

                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val gyroMag = sqrt(gx * gx + gy * gy + gz * gz)

                _latestGyro.value = GyroSample(
                    index = gyroSampleIndex,
                    timestampNs = timestampNs,
                    gx = gx,
                    gy = gy,
                    gz = gz,
                    magnitude = gyroMag,
                    deltaMs = deltaMs,
                    frequencyHz = emaGyroHz
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
