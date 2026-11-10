package com.tapback.app.core.detection

import com.tapback.app.domain.models.ActionType
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.TapBackSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TapDetectorEngineTest {

    private lateinit var engine: TapDetectorEngine
    private var simulatedTimeNs = 1_000_000_000L

    @Before
    fun setup() {
        val settings = TapBackSettings(
            isEnabled = true,
            doubleTapAction = ActionType.SCREENSHOT,
            tripleTapAction = ActionType.OPEN_ASSISTANT,
            customThreshold = 12.0f,
            minTapIntervalMs = 120L,
            maxTapIntervalMs = 500L,
            cooldownPeriodMs = 800L
        )
        engine = TapDetectorEngine(settings)
        simulatedTimeNs = 1_000_000_000L
    }

    private fun feedBaseline(samples: Int) {
        for (i in 0 until samples) {
            simulatedTimeNs += 16_000_000L
            engine.onSensorData(simulatedTimeNs, 0.05f, 0.05f, 9.8f)
        }
    }

    private fun simulateTap(peakAmplitude: Float) {
        simulatedTimeNs += 16_000_000L
        engine.onSensorData(simulatedTimeNs, 0.05f, 0.05f, 9.8f + peakAmplitude * 0.4f)
        simulatedTimeNs += 16_000_000L
        engine.onSensorData(simulatedTimeNs, 0.05f, 0.05f, 9.8f + peakAmplitude)
        simulatedTimeNs += 16_000_000L
        engine.onSensorData(simulatedTimeNs, 0.05f, 0.05f, 9.8f - (peakAmplitude * 0.3f))
        simulatedTimeNs += 16_000_000L
        engine.onSensorData(simulatedTimeNs, 0.05f, 0.05f, 9.8f)
    }

    @Test
    fun testCircularBufferWrapAroundAndVariance() {
        val buffer = CircularFloatBuffer(5)
        assertEquals(0, buffer.size)

        buffer.push(10f)
        buffer.push(20f)
        buffer.push(30f)
        buffer.push(40f)
        buffer.push(50f)
        assertEquals(5, buffer.size)
        assertTrue(buffer.isFull())
        assertEquals(50f, buffer.latest(), 0.001f)

        buffer.push(60f)
        assertEquals(5, buffer.size)
        assertEquals(20f, buffer[0], 0.001f)
        assertEquals(60f, buffer[4], 0.001f)
        assertEquals(60f, buffer.latest(), 0.001f)

        val constBuffer = CircularFloatBuffer(4)
        constBuffer.push(5f)
        constBuffer.push(5f)
        constBuffer.push(5f)
        constBuffer.push(5f)
        assertEquals(0f, constBuffer.variance(), 0.001f)
    }

    @Test
    fun testHighPassFilterRemovesDCGravity() {
        val filter = HighPassFilter(cutoffHz = 3.0f, sampleRateHz = 60f)
        var lastOutput = 0f
        for (i in 0..40) {
            lastOutput = filter.filter(9.8f)
        }
        assertTrue("DC gravity should be suppressed: $lastOutput", Math.abs(lastOutput) < 0.1f)
    }

    @Test
    fun testPeakDetectorRespondsToSharpImpulse() {
        val detector = PeakDetector(threshold = 12.0f, minPeakSpacingMs = 100L)
        assertNull(detector.processSample(0.5f, 1000L))
        assertNull(detector.processSample(-0.3f, 1016L))
        assertNull(detector.processSample(0.1f, 1032L))

        assertNull(detector.processSample(10.0f, 1048L))
        assertNull(detector.processSample(22.0f, 1064L))
        val peak = detector.processSample(5.0f, 1080L)

        assertNotNull("Should detect peak above threshold", peak)
        assertEquals(22.0f, peak!!, 0.01f)
    }

    @Test
    fun testFalsePositiveRejectionForTableDrop() {
        val classifier = FalsePositiveClassifier(zDominanceRatioMin = 1.2f)
        val result = classifier.evaluateTapConfidence(
            peakZAmplitude = 16.0f,
            filteredAx = 15.0f,
            filteredAy = 15.0f,
            gx = 0.1f,
            gy = 0.1f,
            gz = 0.1f
        )
        assertFalse("Table drop should be rejected due to low Z-dominance", result.isValid)
        assertTrue(result.zRatio < 1.0f)
    }

    @Test
    fun testFalsePositiveRejectionForHighAngularRotation() {
        val classifier = FalsePositiveClassifier(gyroThresholdRadSec = 2.5f)
        val result = classifier.evaluateTapConfidence(
            peakZAmplitude = 18.0f,
            filteredAx = 0.1f,
            filteredAy = 0.1f,
            gx = 3.5f,
            gy = 1.2f,
            gz = 0.8f
        )
        assertFalse("High gyro rotation should be rejected", result.isValid)
    }

    @Test
    fun testAuthenticDoubleTapDetected() = runTest {
        val gestures = mutableListOf<TapDetectorEngine.GestureDetected>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.gestureEvents.collect { gestures.add(it) }
        }

        feedBaseline(20)

        // Tap 1
        simulateTap(22f)
        feedBaseline(15) // ~240ms interval

        // Tap 2
        simulateTap(24f)

        // Wait past max interval (500ms) without 3rd tap to confirm double tap
        feedBaseline(35)

        assertEquals("Should trigger exactly 1 double tap", 1, gestures.size)
        assertEquals(GestureType.DOUBLE_TAP, gestures[0].gestureType)
        assertEquals(2, gestures[0].peakCount)

        job.cancel()
    }

    @Test
    fun testAuthenticTripleTapDetected() = runTest {
        val gestures = mutableListOf<TapDetectorEngine.GestureDetected>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.gestureEvents.collect { gestures.add(it) }
        }

        feedBaseline(20)

        // Tap 1
        simulateTap(22f)
        feedBaseline(14) // ~224ms interval

        // Tap 2
        simulateTap(24f)
        feedBaseline(14) // ~224ms interval

        // Tap 3
        simulateTap(23f)
        feedBaseline(10)

        assertEquals("Should trigger exactly 1 triple tap", 1, gestures.size)
        assertEquals(GestureType.TRIPLE_TAP, gestures[0].gestureType)
        assertEquals(3, gestures[0].peakCount)

        job.cancel()
    }

    @Test
    fun testCooldownRejectsSubsequentSpuriousTaps() = runTest {
        val gestures = mutableListOf<TapDetectorEngine.GestureDetected>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.gestureEvents.collect { gestures.add(it) }
        }

        feedBaseline(20)

        // Double tap sequence
        simulateTap(22f)
        feedBaseline(14)
        simulateTap(24f)

        // Wait past max interval to finalize double tap
        feedBaseline(35)
        assertEquals(1, gestures.size)

        // Spurious tap at ~200ms into cooldown (cooldown is 800ms)
        feedBaseline(10)
        simulateTap(25f)
        feedBaseline(10)
        simulateTap(25f)

        // Should STILL only be 1 gesture because cooldown is active
        assertEquals(1, gestures.size)

        job.cancel()
    }
}
