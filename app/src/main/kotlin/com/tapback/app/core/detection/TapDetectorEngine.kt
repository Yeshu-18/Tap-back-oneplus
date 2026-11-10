package com.tapback.app.core.detection

import com.tapback.app.domain.models.ActionType
import com.tapback.app.domain.models.DetectedPeak
import com.tapback.app.domain.models.GestureClassification
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent
import com.tapback.app.domain.models.ProcessedSensorSample
import com.tapback.app.domain.models.SensorSample
import com.tapback.app.domain.models.TapBackSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 2 Tap Detection Engine for OnePlus Nord 5 (CPH2707).
 *
 * Coordinates:
 * - [SensorPreprocessor]: Gravity compensation, dynamic acceleration, gyro magnitude, and frequency tracking.
 * - [MotionRejectionFilter]: Sustained motion, orientation tilt, and angular rotation false-positive suppression.
 * - [ImpactDetector]: Adaptive thresholding, debouncing, and local extrema peak detection.
 * - [TapSequenceClassifier]: Non-eager temporal sequence state machine (Single, Double, and Triple tap).
 * - [DetectionDiagnostics]: Real-time waveforms, impact markers, and telemetry state store.
 */
class TapDetectorEngine(
    var settings: TapBackSettings = TapBackSettings()
) {
    val preprocessor = SensorPreprocessor(gravityAlpha = 0.90f)
    val motionFilter = MotionRejectionFilter(
        minZDominanceRatio = 1.25f,
        maxCurrentGyroRadSec = 1.45f,
        maxIdleSustainedGyroRadSec = 0.70f,
        maxSequenceSustainedGyroRadSec = 1.35f,
        maxOrientationShiftMps2 = 3.80f,
        maxAccVarianceMps4 = 60.00f
    )
    val impactDetector = ImpactDetector(
        baseThreshold = settings.activeThreshold,
        minImpactSpacingMs = 95L,
        motionFilter = motionFilter
    )
    val sequenceClassifier = TapSequenceClassifier(
        minTapIntervalMs = settings.minTapIntervalMs,
        maxTapIntervalMs = settings.maxTapIntervalMs,
        cooldownPeriodMs = settings.cooldownPeriodMs,
        allowSingleTap = settings.singleTapAction != ActionType.NONE || settings.debugMode,
        allowTripleTap = settings.tripleTapAction != ActionType.NONE || settings.debugMode
    )
    val diagnostics = DetectionDiagnostics()

    // Live telemetry flows
    private val _liveFilteredZ = MutableStateFlow(0f)
    val liveFilteredZ: StateFlow<Float> = _liveFilteredZ.asStateFlow()

    private val _liveRawSample = MutableStateFlow(SensorSample(0L, 0f, 0f, 0f))
    val liveRawSample: StateFlow<SensorSample> = _liveRawSample.asStateFlow()

    private val _latestProcessedSample = MutableStateFlow<ProcessedSensorSample?>(null)
    val latestProcessedSample: StateFlow<ProcessedSensorSample?> = _latestProcessedSample.asStateFlow()

    // Event flows
    private val _peakEvents = MutableSharedFlow<DetectedPeak>(extraBufferCapacity = 64)
    val peakEvents: SharedFlow<DetectedPeak> = _peakEvents.asSharedFlow()

    private val _impactEvents = MutableSharedFlow<ImpactEvent>(extraBufferCapacity = 64)
    val impactEvents: SharedFlow<ImpactEvent> = _impactEvents.asSharedFlow()

    private val _gestureEvents = MutableSharedFlow<GestureDetected>(extraBufferCapacity = 16)
    val gestureEvents: SharedFlow<GestureDetected> = _gestureEvents.asSharedFlow()

    private val _classifiedGestures = MutableSharedFlow<GestureClassification>(extraBufferCapacity = 16)
    val classifiedGestures: SharedFlow<GestureClassification> = _classifiedGestures.asSharedFlow()

    data class GestureDetected(
        val gestureType: GestureType,
        val peakCount: Int,
        val amplitudes: List<Float>,
        val confidence: Float,
        val timestampMs: Long
    )

    /**
     * Updates engine calibration parameters in real-time.
     */
    fun updateSettings(newSettings: TapBackSettings) {
        this.settings = newSettings
        impactDetector.updateThreshold(newSettings.activeThreshold)
        sequenceClassifier.minTapIntervalMs = newSettings.minTapIntervalMs
        sequenceClassifier.maxTapIntervalMs = newSettings.maxTapIntervalMs
        sequenceClassifier.cooldownPeriodMs = newSettings.cooldownPeriodMs
        sequenceClassifier.allowSingleTap = newSettings.singleTapAction != ActionType.NONE || newSettings.debugMode
        sequenceClassifier.allowTripleTap = newSettings.tripleTapAction != ActionType.NONE || newSettings.debugMode
    }

    /**
     * Hot path: Ingests raw accelerometer and gyroscope samples.
     */
    @Synchronized
    fun onSensorData(
        timestampNs: Long,
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float = 0f,
        gy: Float = 0f,
        gz: Float = 0f
    ) {
        val processed = preprocessor.process(
            timestampNs = timestampNs,
            ax = ax,
            ay = ay,
            az = az,
            gx = gx,
            gy = gy,
            gz = gz
        )

        _latestProcessedSample.value = processed
        _liveFilteredZ.value = processed.dynamicAz
        _liveRawSample.value = SensorSample(timestampNs, ax, ay, az, gx, gy, gz)
        diagnostics.onSampleProcessed(processed)

        // 1. Tick state machine with current sample timestamp to check for timeouts
        sequenceClassifier.onSampleTick(processed.timestampMs)?.let { completedGesture ->
            dispatchGesture(completedGesture)
        }

        // 2. Evaluate physical impact detection
        val impact = impactDetector.processSample(processed, sequenceClassifier.isInSequence)
        if (impact != null) {
            _impactEvents.tryEmit(impact)
            _peakEvents.tryEmit(
                DetectedPeak(
                    timestampMs = impact.timestampMs,
                    amplitude = impact.dynamicZStrength,
                    confidence = impact.confidence,
                    zRatio = impact.zDominanceRatio
                )
            )
            diagnostics.onImpactDetected(impact)

            // Feed impact into sequence classifier
            sequenceClassifier.onImpact(impact)?.let { completedGesture ->
                dispatchGesture(completedGesture)
            }
        }

        // 3. Update real-time sequence progress state
        when (sequenceClassifier.state) {
            TapSequenceClassifier.State.AWAITING_SECOND_TAP -> {
                val rem = sequenceClassifier.getWindowRemainingMs(processed.timestampMs)
                diagnostics.updateSequenceProgress(
                    text = "1st TAP • Waiting for 2nd tap (${rem}ms)",
                    inProgress = true,
                    remainingMs = rem
                )
            }
            TapSequenceClassifier.State.AWAITING_THIRD_TAP -> {
                val rem = sequenceClassifier.getWindowRemainingMs(processed.timestampMs)
                diagnostics.updateSequenceProgress(
                    text = "2nd TAP • Waiting for 3rd tap (${rem}ms)",
                    inProgress = true,
                    remainingMs = rem
                )
            }
            TapSequenceClassifier.State.COOLDOWN -> {
                // Keep confirmed status while in cooldown
            }
            TapSequenceClassifier.State.IDLE -> {
                if (diagnostics.sequenceState.value.inProgress) {
                    diagnostics.updateSequenceProgress(
                        text = "IDLE • Ready for Back Tap",
                        inProgress = false,
                        remainingMs = 0L
                    )
                }
            }
        }
    }

    private fun dispatchGesture(gesture: GestureClassification) {
        _classifiedGestures.tryEmit(gesture)
        diagnostics.onGestureConfirmed(gesture)

        val legacyGesture = GestureDetected(
            gestureType = gesture.gestureType,
            peakCount = gesture.constituentImpacts.size,
            amplitudes = gesture.constituentImpacts.map { it.dynamicZStrength },
            confidence = gesture.confidence,
            timestampMs = gesture.timestampMs
        )
        _gestureEvents.tryEmit(legacyGesture)
    }

    /**
     * Flushes trailing gesture if stream or session ends.
     */
    fun flushPendingGesture(finalTimestampMs: Long) {
        sequenceClassifier.flushPendingGesture(finalTimestampMs)?.let { gesture ->
            dispatchGesture(gesture)
        }
    }

    fun reset() {
        preprocessor.reset()
        motionFilter.reset()
        impactDetector.reset()
        sequenceClassifier.reset()
        diagnostics.reset()
    }
}
