package com.tapback.app.core.detection

import com.tapback.app.domain.models.ProcessedSensorSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MotionRejectionFilterTest {

    private lateinit var filter: MotionRejectionFilter

    @Before
    fun setup() {
        filter = MotionRejectionFilter()
    }

    private fun createSample(
        dynamicAx: Float = 0.1f,
        dynamicAy: Float = 0.1f,
        dynamicAz: Float = 6.0f,
        gx: Float = 0.1f,
        gy: Float = 0.1f,
        gz: Float = 0.1f,
        gyroMag: Float = 0.17f,
        totalMag: Float = 10.0f,
        gravZ: Float = 9.8f
    ): ProcessedSensorSample {
        return ProcessedSensorSample(
            timestampNs = 1_000_000_000L,
            timestampMs = 1000L,
            ax = dynamicAx,
            ay = dynamicAy,
            az = dynamicAz + gravZ,
            gx = gx,
            gy = gy,
            gz = gz,
            gravityX = 0f,
            gravityY = 0f,
            gravityZ = gravZ,
            dynamicAx = dynamicAx,
            dynamicAy = dynamicAy,
            dynamicAz = dynamicAz,
            dynamicMagnitude = dynamicAz,
            totalMagnitude = totalMag,
            gyroMagnitude = gyroMag,
            deltaMs = 10f,
            samplingFrequencyHz = 101f
        )
    }

    @Test
    fun testAuthenticBackTapAccepted() {
        // Feed quiet baseline samples
        for (i in 0 until 20) {
            filter.onNewSample(createSample(dynamicAz = 0.1f, gyroMag = 0.1f))
        }

        // High Z impact, low planar shock, quiet gyro
        val tap = createSample(
            dynamicAx = 0.2f,
            dynamicAy = 0.2f,
            dynamicAz = 6.5f,
            gyroMag = 0.3f
        )
        val decision = filter.evaluateImpact(tap, isInSequence = false)

        assertTrue("Authentic tap should be accepted", decision.isAccepted)
        assertTrue("Confidence should be high (> 0.8)", decision.confidence > 0.80f)
        assertTrue("Z dominance should be high", decision.zDominanceRatio > 5.0f)
    }

    @Test
    fun testRejectionWhenCurrentAngularVelocityHigh() {
        for (i in 0 until 20) {
            filter.onNewSample(createSample(dynamicAz = 0.1f, gyroMag = 0.2f))
        }

        val highGyroTap = createSample(
            dynamicAz = 7.0f,
            gyroMag = 2.5f // Exceeds 1.45 rad/s
        )
        val decision = filter.evaluateImpact(highGyroTap, isInSequence = false)

        assertFalse("High instantaneous gyro should be rejected", decision.isAccepted)
        assertTrue(decision.rejectionReason?.contains("angular velocity") == true)
    }

    @Test
    fun testRejectionWhenSustainedRotationHighInIdle() {
        // Pre-feed sustained rotation (~0.9 rad/s > 0.7 rad/s idle limit)
        for (i in 0 until 25) {
            filter.onNewSample(createSample(dynamicAz = 0.1f, gyroMag = 0.9f))
        }

        val tapInIdle = createSample(
            dynamicAz = 6.0f,
            gyroMag = 0.5f // Instant gyro is low, but sustained rotation was active
        )
        val decision = filter.evaluateImpact(tapInIdle, isInSequence = false)

        assertFalse("Sustained rotation in idle must be rejected", decision.isAccepted)
    }

    @Test
    fun testRejectionWhenLowZDominanceTableDrop() {
        for (i in 0 until 20) {
            filter.onNewSample(createSample(dynamicAz = 0.1f, gyroMag = 0.1f))
        }

        // Table drop or edge shock has equal or larger planar dynamic shock
        val tableDrop = createSample(
            dynamicAx = 6.0f,
            dynamicAy = 5.5f,
            dynamicAz = 4.0f, // Planar shock exceeds Z
            gyroMag = 0.2f
        )
        val decision = filter.evaluateImpact(tableDrop, isInSequence = false)

        assertFalse("Table drop should be rejected due to low Z-dominance", decision.isAccepted)
        assertTrue(decision.zDominanceRatio < 1.0f)
    }
}

