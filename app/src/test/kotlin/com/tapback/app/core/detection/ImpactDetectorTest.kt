package com.tapback.app.core.detection

import com.tapback.app.domain.models.ProcessedSensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImpactDetectorTest {

    private lateinit var detector: ImpactDetector

    @Before
    fun setup() {
        detector = ImpactDetector(baseThreshold = 3.0f, minImpactSpacingMs = 80L)
    }

    private fun sample(tMs: Long, dynamicAz: Float): ProcessedSensorSample {
        return ProcessedSensorSample(
            timestampNs = tMs * 1_000_000L,
            timestampMs = tMs,
            ax = 0.05f,
            ay = 0.05f,
            az = 9.8f + dynamicAz,
            gx = 0.05f,
            gy = 0.05f,
            gz = 0.05f,
            gravityX = 0f,
            gravityY = 0f,
            gravityZ = 9.8f,
            dynamicAx = 0.05f,
            dynamicAy = 0.05f,
            dynamicAz = dynamicAz,
            dynamicMagnitude = dynamicAz,
            totalMagnitude = 9.8f + dynamicAz,
            gyroMagnitude = 0.08f,
            deltaMs = 10f,
            samplingFrequencyHz = 101f
        )
    }

    @Test
    fun testDetectsPeakExceedingThreshold() {
        assertNull(detector.processSample(sample(1000L, 0.5f)))
        assertNull(detector.processSample(sample(1010L, 1.2f)))
        assertNull(detector.processSample(sample(1020L, 5.8f))) // Rising slope
        // Slope reverses on next sample:
        val impact = detector.processSample(sample(1030L, 1.0f))

        assertNotNull("Should detect local extremum peak exceeding threshold", impact)
        assertEquals(5.8f, impact!!.dynamicZStrength, 0.01f)
        assertEquals(1020L, impact.timestampMs)
        assertTrue(impact.confidence > 0.8f)
    }

    @Test
    fun testDebouncesChassisRingdownWithinWindow() {
        detector.processSample(sample(1000L, 0.5f))
        detector.processSample(sample(1010L, 6.0f))
        val firstImpact = detector.processSample(sample(1020L, 1.0f))
        assertNotNull(firstImpact)

        // Mechanical ring-down spike at 40ms later (< 80ms min spacing)
        detector.processSample(sample(1050L, 4.5f))
        val bouncedImpact = detector.processSample(sample(1060L, 0.5f))
        assertNull("Ring-down strike within 80ms should be debounced", bouncedImpact)

        // Legitimate second tap at 180ms later
        detector.processSample(sample(1190L, 6.2f))
        val secondImpact = detector.processSample(sample(1200L, 0.8f))
        assertNotNull("Subsequent tap after debounce window should be detected", secondImpact)
    }
}

