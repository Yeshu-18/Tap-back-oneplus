package com.tapback.app.domain.models

/**
 * Recognizable physical gestures on the rear chassis.
 */
enum class GestureType(val displayName: String) {
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Double Tap"),
    TRIPLE_TAP("Triple Tap")
}

/**
 * Pre-defined sensitivity presets for peak detection thresholds.
 */
enum class SensitivityLevel(val displayName: String, val threshold: Float) {
    HIGH("Gentle", 2.40f),
    MEDIUM("Balanced", 3.00f),
    LOW("Firm", 4.20f),
    EXTRA_FIRM("Heavy", 6.00f),
    CUSTOM("Custom", 3.00f)
}

/**
 * Actions that can be mapped to gestures.
 */
enum class ActionType(
    val title: String,
    val description: String,
    val requiresAccessibility: Boolean,
    val isSystemAction: Boolean
) {
    NONE(
        title = "None",
        description = "No action will be triggered",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    SCREENSHOT(
        title = "Take Screenshot",
        description = "Captures the current screen via Accessibility",
        requiresAccessibility = true,
        isSystemAction = true
    ),
    FLASHLIGHT(
        title = "Toggle Flashlight",
        description = "Turns the rear LED torch on or off",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    OPEN_CAMERA(
        title = "Open Camera",
        description = "Launches the default camera application",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    OPEN_ASSISTANT(
        title = "Launch Gemini / Assistant",
        description = "Launches Google Assistant or Gemini",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    LAUNCH_APP(
        title = "Launch Installed App",
        description = "Launches a user-selected application",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    CALL_CONTACT(
        title = "Call Favorite Contact",
        description = "Instantly dials a chosen contact or phone number",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    MEDIA_PLAY_PAUSE(
        title = "Play / Pause Media",
        description = "Toggles music or video playback",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    MEDIA_NEXT(
        title = "Next Track",
        description = "Skips to the next track in active media player",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    MEDIA_PREV(
        title = "Previous Track",
        description = "Returns to the previous track",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    VOLUME_UP(
        title = "Volume Up",
        description = "Increases media volume by one step",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    VOLUME_DOWN(
        title = "Volume Down",
        description = "Decreases media volume by one step",
        requiresAccessibility = false,
        isSystemAction = false
    ),
    OPEN_NOTIFICATIONS(
        title = "Open Notifications",
        description = "Pulls down the notification shade",
        requiresAccessibility = true,
        isSystemAction = true
    ),
    OPEN_QUICK_SETTINGS(
        title = "Open Quick Settings",
        description = "Pulls down quick settings toggles",
        requiresAccessibility = true,
        isSystemAction = true
    ),
    LOCK_SCREEN(
        title = "Lock Screen",
        description = "Locks the device immediately",
        requiresAccessibility = true,
        isSystemAction = true
    )
}

/**
 * Complete user preferences state for TapBack.
 */
data class TapBackSettings(
    val isEnabled: Boolean = true,
    val singleTapAction: ActionType = ActionType.NONE,
    val singleTapPackage: String? = null,
    val doubleTapAction: ActionType = ActionType.SCREENSHOT,
    val doubleTapPackage: String? = null,
    val tripleTapAction: ActionType = ActionType.OPEN_ASSISTANT,
    val tripleTapPackage: String? = null,
    val screenOffDoubleTapAction: ActionType = ActionType.FLASHLIGHT,
    val screenOffTripleTapAction: ActionType = ActionType.NONE,
    val sensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    val customThreshold: Float = 2.80f,
    val minTapIntervalMs: Long = 135L,
    val maxTapIntervalMs: Long = 480L,
    val cooldownPeriodMs: Long = 350L,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val startOnBoot: Boolean = true,
    val screenOffDetection: Boolean = false,
    val debugMode: Boolean = true
) {
    val activeThreshold: Float
        get() {
            val raw = if (sensitivity == SensitivityLevel.CUSTOM) customThreshold else sensitivity.threshold
            // If raw > 8.0f, convert from legacy magnitude space (~14.5) to dynamic space (~4.7)
            return if (raw > 8.0f) (raw - 9.8f).coerceAtLeast(2.0f) else raw
        }
}

/**
 * Raw sensor snapshot.
 */
data class SensorSample(
    val timestampNs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f
)

/**
 * Clean data model for raw input sensors.
 */
data class RawSensorData(
    val timestampNs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f
) {
    val timestampMs: Long get() = timestampNs / 1_000_000L
}

/**
 * Preprocessed sensor sample containing gravity-compensated dynamic acceleration,
 * calculated magnitudes, and sampling metrics.
 */
data class ProcessedSensorSample(
    val timestampNs: Long,
    val timestampMs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val dynamicAx: Float,
    val dynamicAy: Float,
    val dynamicAz: Float,
    val dynamicMagnitude: Float,
    val totalMagnitude: Float,
    val gyroMagnitude: Float,
    val deltaMs: Float,
    val samplingFrequencyHz: Float
)

/**
 * Discrete physical impact event detected on device rear chassis.
 */
data class ImpactEvent(
    val timestampNs: Long,
    val timestampMs: Long,
    val dynamicZStrength: Float,
    val dynamicMagnitude: Float,
    val gyroActivity: Float,
    val zDominanceRatio: Float,
    val confidence: Float
)

/**
 * Fully classified gesture sequence produced by the temporal state machine.
 */
data class GestureClassification(
    val gestureType: GestureType,
    val constituentImpacts: List<ImpactEvent>,
    val confidence: Float,
    val timestampMs: Long,
    val interTapIntervalsMs: List<Long> = emptyList()
)

/**
 * Comprehensive benchmark replay result for offline CSV sensor datasets.
 */
data class ReplayBenchmarkResult(
    val datasetName: String,
    val isTuningDataset: Boolean,
    val totalSamples: Int,
    val durationSeconds: Float,
    val samplingFrequencyHz: Float,
    val detectedImpactCount: Int,
    val classifiedSingles: Int,
    val classifiedDoubles: Int,
    val classifiedTriples: Int,
    val expectedSingles: Int,
    val expectedDoubles: Int,
    val expectedTriples: Int,
    val falsePositives: Int,
    val missedEvents: Int,
    val averageConfidence: Float,
    val impacts: List<ImpactEvent> = emptyList(),
    val gestures: List<GestureClassification> = emptyList()
) {
    val isAccuracyPerfect: Boolean
        get() = falsePositives == 0 && missedEvents == 0
}

/**
 * Diagnostic event captured by the detection engine.
 */
data class DetectedPeak(
    val timestampMs: Long,
    val amplitude: Float,
    val confidence: Float,
    val zRatio: Float
)

/**
 * Full detection event record for the UI and diagnostics.
 */
data class GestureTriggerEvent(
    val id: Long = System.currentTimeMillis(),
    val timestampMs: Long = System.currentTimeMillis(),
    val gestureType: GestureType,
    val peakCount: Int,
    val peakAmplitudes: List<Float>,
    val confidence: Float,
    val actionExecuted: ActionType
)
