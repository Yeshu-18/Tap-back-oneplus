package com.tapback.app.core.detection

import com.tapback.app.domain.models.GestureClassification
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent
import com.tapback.app.domain.models.ProcessedSensorSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic state store aggregating real-time telemetry, waveforms, impact markers,
 * gesture sequence events, and exportable logs.
 */
class DetectionDiagnostics {

    data class DiagnosticLogEntry(
        val timestampMs: Long,
        val formattedTime: String,
        val tag: String,
        val message: String
    )

    data class SequenceDisplayState(
        val activeGesture: GestureType? = null,
        val statusText: String = "IDLE • Ready for Back Tap",
        val confidence: Float = 0f,
        val timestampMs: Long = 0L,
        val inProgress: Boolean = false,
        val windowRemainingMs: Long = 0L
    )

    // Live preprocessed sample telemetry
    private val _latestSample = MutableStateFlow<ProcessedSensorSample?>(null)
    val latestSample: StateFlow<ProcessedSensorSample?> = _latestSample.asStateFlow()

    // Dynamic Z waveform buffer for visual graph (capacity 100 points)
    private val _dynamicZWaveform = MutableStateFlow<List<Float>>(emptyList())
    val dynamicZWaveform: StateFlow<List<Float>> = _dynamicZWaveform.asStateFlow()
    private val waveBuffer = FloatArray(100)
    private var waveHead = 0
    private var waveCount = 0

    // Recent impact markers for overlaying onto waveform
    private val _recentImpacts = MutableStateFlow<List<ImpactEvent>>(emptyList())
    val recentImpacts: StateFlow<List<ImpactEvent>> = _recentImpacts.asStateFlow()
    private val impactHistory = ArrayList<ImpactEvent>(30)

    // Recent classified gestures
    private val _recentGestures = MutableStateFlow<List<GestureClassification>>(emptyList())
    val recentGestures: StateFlow<List<GestureClassification>> = _recentGestures.asStateFlow()
    private val gestureHistory = ArrayList<GestureClassification>(20)

    // Current sequence status
    private val _sequenceState = MutableStateFlow(SequenceDisplayState())
    val sequenceState: StateFlow<SequenceDisplayState> = _sequenceState.asStateFlow()

    // Metrics counters
    private val _totalSamples = MutableStateFlow(0L)
    val totalSamples: StateFlow<Long> = _totalSamples.asStateFlow()

    private val _singleTapCount = MutableStateFlow(0)
    val singleTapCount: StateFlow<Int> = _singleTapCount.asStateFlow()

    private val _doubleTapCount = MutableStateFlow(0)
    val doubleTapCount: StateFlow<Int> = _doubleTapCount.asStateFlow()

    private val _tripleTapCount = MutableStateFlow(0)
    val tripleTapCount: StateFlow<Int> = _tripleTapCount.asStateFlow()

    // Exportable diagnostic log
    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()
    private val logHistory = ArrayList<DiagnosticLogEntry>(100)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun onSampleProcessed(sample: ProcessedSensorSample) {
        _latestSample.value = sample
        _totalSamples.value++

        // Push to ring buffer for graphing
        waveBuffer[waveHead] = sample.dynamicAz
        waveHead = (waveHead + 1) % waveBuffer.size
        if (waveCount < waveBuffer.size) waveCount++

        if (_totalSamples.value % 2 == 0L) { // update graph every 2nd sample for smooth 50fps UI
            val list = ArrayList<Float>(waveCount)
            val start = if (waveCount == waveBuffer.size) waveHead else 0
            for (i in 0 until waveCount) {
                list.add(waveBuffer[(start + i) % waveBuffer.size])
            }
            _dynamicZWaveform.value = list
        }
    }

    @Synchronized
    fun onImpactDetected(impact: ImpactEvent) {
        impactHistory.add(0, impact)
        if (impactHistory.size > 30) impactHistory.removeAt(impactHistory.lastIndex)
        _recentImpacts.value = ArrayList(impactHistory)

        addLog("IMPACT", "Peak: ${"%.2f".format(impact.dynamicZStrength)} m/s², Gyro: ${"%.2f".format(impact.gyroActivity)} rad/s, Z-ratio: ${"%.2f".format(impact.zDominanceRatio)}, Conf: ${"%.0f".format(impact.confidence * 100)}%")
    }

    @Synchronized
    fun onGestureConfirmed(gesture: GestureClassification) {
        gestureHistory.add(0, gesture)
        if (gestureHistory.size > 20) gestureHistory.removeAt(gestureHistory.lastIndex)
        _recentGestures.value = ArrayList(gestureHistory)

        when (gesture.gestureType) {
            GestureType.SINGLE_TAP -> _singleTapCount.value++
            GestureType.DOUBLE_TAP -> _doubleTapCount.value++
            GestureType.TRIPLE_TAP -> _tripleTapCount.value++
        }

        _sequenceState.value = SequenceDisplayState(
            activeGesture = gesture.gestureType,
            statusText = "${gesture.gestureType.displayName.uppercase()} CONFIRMED!",
            confidence = gesture.confidence,
            timestampMs = gesture.timestampMs
        )

        val intervalStr = if (gesture.interTapIntervalsMs.isNotEmpty()) {
            " intervals: [${gesture.interTapIntervalsMs.joinToString(", ")} ms]"
        } else ""

        addLog("GESTURE", "CONFIRMED ${gesture.gestureType.displayName} (Conf: ${"%.0f".format(gesture.confidence * 100)}%)$intervalStr")
    }

    @Synchronized
    fun onStateTransition(stateName: String) {
        _sequenceState.value = _sequenceState.value.copy(statusText = stateName)
    }

    @Synchronized
    fun updateSequenceProgress(text: String, inProgress: Boolean, remainingMs: Long) {
        _sequenceState.value = _sequenceState.value.copy(
            statusText = text,
            inProgress = inProgress,
            windowRemainingMs = remainingMs
        )
    }

    private fun addLog(tag: String, message: String) {
        val entry = DiagnosticLogEntry(
            timestampMs = System.currentTimeMillis(),
            formattedTime = timeFormat.format(Date()),
            tag = tag,
            message = message
        )
        logHistory.add(0, entry)
        if (logHistory.size > 100) logHistory.removeAt(logHistory.lastIndex)
        _diagnosticLogs.value = ArrayList(logHistory)
    }

    fun exportLogsAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== TapBack Diagnostics Log ===")
        sb.appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Total Samples: ${_totalSamples.value}")
        sb.appendLine("Single Taps: ${_singleTapCount.value}, Double Taps: ${_doubleTapCount.value}, Triple Taps: ${_tripleTapCount.value}")
        sb.appendLine("----------------------------------------")
        _diagnosticLogs.value.forEach { entry ->
            sb.appendLine("[${entry.formattedTime}] [${entry.tag}] ${entry.message}")
        }
        return sb.toString()
    }

    @Synchronized
    fun reset() {
        waveHead = 0
        waveCount = 0
        waveBuffer.fill(0f)
        _dynamicZWaveform.value = emptyList()
        impactHistory.clear()
        _recentImpacts.value = emptyList()
        gestureHistory.clear()
        _recentGestures.value = emptyList()
        _sequenceState.value = SequenceDisplayState()
        _totalSamples.value = 0L
        _singleTapCount.value = 0
        _doubleTapCount.value = 0
        _tripleTapCount.value = 0
        logHistory.clear()
        _diagnosticLogs.value = emptyList()
    }
}

