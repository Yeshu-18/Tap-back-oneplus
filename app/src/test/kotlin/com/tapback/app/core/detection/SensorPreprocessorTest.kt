package com.tapback.app.core.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SensorPreprocessorTest {

    private lateinit var preprocessor: SensorPreprocessor

    @Before
    fun setup() {
        preprocessor = SensorPreprocessor(gravityAlpha = 0.90f)
    }

    @Test
    fun testInitialSampleInitializesGravity() {
        val sample = preprocessor.process(
            timestampNs = 1_000_000_000L,
            ax = 0.1f,
            ay = 0.2f,
            az = 9.8f,
            gx = 0.01f,
            gy = 0.02f,
            gz = 0.03f
        )

        assertEquals(0.1f, sample.gravityX, 0.001f)
        assertEquals(0.2f, sample.gravityY, 0.001f)
        assertEquals(9.8f, sample.gravityZ, 0.001f)
        assertEquals(0f, sample.dynamicAz, 0.001f)
        assertEquals(0f, sample.dynamicMagnitude, 0.001f)
        assertEquals(10.0f, sample.deltaMs, 0.001f)
        assertEquals(101.0f, sample.samplingFrequencyHz, 0.001f)
    }

    @Test
    fun testDynamicAccelerationCalculationOnImpulse() {
        // Feed 10 steady baseline samples at 10ms intervals (~100 Hz)
        var tNs = 1_000_000_000L
        for (i in 0 until 10) {
            tNs += 10_000_000L
            preprocessor.process(tNs, 0f, 0f, 9.81f)
        }

        // Apply a sharp mechanical impulse on Z axis: az increases by 6.0 m/s^2
        tNs += 10_000_000L
        val impactSample = preprocessor.process(tNs, 0f, 0f, 9.81f + 6.0f)

        // Dynamic Az should reflect the mechanical transient shock (~5.4 m/s^2 after alpha=0.9)
        assertTrue("Dynamic Az should capture the spike: ${impactSample.dynamicAz}", impactSample.dynamicAz > 5.0f)
        assertTrue("Dynamic magnitude should match spike: ${impactSample.dynamicMagnitude}", impactSample.dynamicMagnitude > 5.0f)
        assertTrue("Total magnitude should include gravity: ${impactSample.totalMagnitude}", impactSample.totalMagnitude > 15.0f)
    }

    @Test
    fun testSamplingFrequencyTracking() {
        var tNs = 1_000_000_000L
        var lastSample = preprocessor.process(tNs, 0f, 0f, 9.8f)

        // Feed 30 samples spaced by exactly 9.9ms (~101 Hz)
        for (i in 0 until 30) {
            tNs += 9_900_000L
            lastSample = preprocessor.process(tNs, 0f, 0f, 9.8f)
        }

        assertEquals(9.9f, lastSample.deltaMs, 0.1f)
        assertTrue("Frequency should converge near 101 Hz (was ${lastSample.samplingFrequencyHz})",
            abs(lastSample.samplingFrequencyHz - 101.0f) < 2.5f)
    }

    @Test
    fun testTimestampGapResetsGracefully() {
        preprocessor.process(1_000_000_000L, 0f, 0f, 9.8f)
        preprocessor.process(1_010_000_000L, 0f, 0f, 9.8f)

        // Simulate sensor pause or large gap of 2 seconds
        val resumedSample = preprocessor.process(3_010_000_000L, 0f, 0f, 9.8f)

        // Should not produce massive deltaMs or unstable filter output
        assertEquals(10.0f, resumedSample.deltaMs, 0.001f)
        assertEquals(0f, resumedSample.dynamicAz, 0.01f)
    }
}

