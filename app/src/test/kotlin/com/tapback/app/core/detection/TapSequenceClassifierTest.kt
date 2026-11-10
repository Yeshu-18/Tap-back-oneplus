package com.tapback.app.core.detection

import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TapSequenceClassifierTest {

    private lateinit var classifier: TapSequenceClassifier

    @Before
    fun setup() {
        classifier = TapSequenceClassifier(
            minTapIntervalMs = 120L,
            maxTapIntervalMs = 320L,
            cooldownPeriodMs = 400L,
            allowSingleTap = true,
            allowTripleTap = true
        )
    }

    private fun impact(tMs: Long, strength: Float = 6.0f): ImpactEvent {
        return ImpactEvent(
            timestampNs = tMs * 1_000_000L,
            timestampMs = tMs,
            dynamicZStrength = strength,
            dynamicMagnitude = strength,
            gyroActivity = 0.1f,
            zDominanceRatio = 8.0f,
            confidence = 0.95f
        )
    }

    @Test
    fun testSingleTapNotTriggeredImmediatelyNonEager() {
        // First impact arrives at t=1000ms
        val immediateResult = classifier.onImpact(impact(1000L))

        // Must NOT confirm immediately! Double/triple tap might follow
        assertNull("Single tap must not trigger immediately", immediateResult)
        assertEquals(true, classifier.isInSequence)

        // Time ticks forward to t=1100ms (100ms elapsed < maxTapInterval 320ms)
        assertNull(classifier.onSampleTick(1100L))

        // Time ticks to t=1250ms (250ms elapsed < maxTapInterval 320ms)
        assertNull(classifier.onSampleTick(1250L))

        // Time ticks past timeout to t=1330ms (330ms elapsed > maxTapInterval 320ms)
        val confirmedSingle = classifier.onSampleTick(1330L)
        assertNotNull("Should confirm Single Tap after timeout expires", confirmedSingle)
        assertEquals(GestureType.SINGLE_TAP, confirmedSingle!!.gestureType)
        assertEquals(1, confirmedSingle.constituentImpacts.size)
    }

    @Test
    fun testDoubleTapConfirmedAfterSecondTap() {
        // Tap 1 at t=1000ms
        assertNull(classifier.onImpact(impact(1000L)))

        // Tap 2 arrives at t=1180ms (180ms interval, within [120..320ms])
        val resultTap2 = classifier.onImpact(impact(1180L))

        // Since triple tap is allowed, waits briefly to check if 3rd tap follows
        assertNull("Should wait to see if 3rd tap arrives", resultTap2)
        assertEquals(true, classifier.isInSequence)

        // Time ticks past maxTapInterval from tap 2 (1180 + 330 = 1510ms)
        val confirmedDouble = classifier.onSampleTick(1510L)
        assertNotNull("Should confirm Double Tap when no third tap arrives", confirmedDouble)
        assertEquals(GestureType.DOUBLE_TAP, confirmedDouble!!.gestureType)
        assertEquals(2, confirmedDouble.constituentImpacts.size)
        assertEquals(180L, confirmedDouble.interTapIntervalsMs[0])
    }

    @Test
    fun testTripleTapConfirmedImmediatelyOnThirdImpact() {
        // Tap 1 at t=1000ms
        assertNull(classifier.onImpact(impact(1000L)))

        // Tap 2 at t=1190ms (190ms interval)
        assertNull(classifier.onImpact(impact(1190L)))

        // Tap 3 at t=1380ms (190ms interval)
        val confirmedTriple = classifier.onImpact(impact(1380L))

        assertNotNull("Triple tap should confirm immediately upon 3rd impact", confirmedTriple)
        assertEquals(GestureType.TRIPLE_TAP, confirmedTriple!!.gestureType)
        assertEquals(3, confirmedTriple.constituentImpacts.size)
        assertEquals(listOf(190L, 190L), confirmedTriple.interTapIntervalsMs)
    }

    @Test
    fun testCooldownSuppressesSpuriousTaps() {
        // Perform triple tap
        classifier.onImpact(impact(1000L))
        classifier.onImpact(impact(1180L))
        val triple = classifier.onImpact(impact(1360L))
        assertNotNull(triple)

        // Spurious tap at 150ms into cooldown (cooldown is 400ms, until 1760ms)
        assertNull(classifier.onImpact(impact(1510L)))
        assertNull(classifier.onSampleTick(1520L))

        // Time expires past cooldown to t=1800ms
        classifier.onSampleTick(1800L)
        assertEquals(false, classifier.isInSequence)

        // New legitimate tap can now start
        assertNull(classifier.onImpact(impact(1850L)))
        assertEquals(true, classifier.isInSequence)
    }
}

