package com.tapback.app.core.detection

import com.tapback.app.domain.models.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

/**
 * End-to-end replay test verifying the production detection engine against the five
 * real-world recorded sensor CSV datasets from the OnePlus Nord 5 (CPH2707, BMI26x @ 101 Hz).
 */
class CsvReplayEngineTest {

    private lateinit var replayEngine: CsvReplayEngine

    @Before
    fun setup() {
        replayEngine = CsvReplayEngine()
    }

    private fun openDataset(fileName: String): InputStream {
        val stream = javaClass.classLoader?.getResourceAsStream("sensor_data/$fileName")
        assertNotNull("Could not find test dataset resource: sensor_data/$fileName", stream)
        return stream!!
    }

    @Test
    fun testStationaryDatasetHeldOutValidation() {
        val stream = openDataset(CsvReplayEngine.Dataset.STATIONARY.fileName)
        val result = replayEngine.replay(stream, CsvReplayEngine.Dataset.STATIONARY)

        assertEquals("Stationary dataset must have 0 impacts", 0, result.detectedImpactCount)
        assertEquals("Stationary dataset must have 0 single taps", 0, result.classifiedSingles)
        assertEquals("Stationary dataset must have 0 double taps", 0, result.classifiedDoubles)
        assertEquals("Stationary dataset must have 0 triple taps", 0, result.classifiedTriples)
        assertEquals("Stationary dataset must have 0 false positives", 0, result.falsePositives)
        assertTrue("Sampling frequency should be ~101 Hz", result.samplingFrequencyHz in 98f..104f)
        assertTrue("Dataset duration should be ~32.6s", result.durationSeconds > 30f)
    }

    @Test
    fun testHandlingDatasetHeldOutValidation() {
        val stream = openDataset(CsvReplayEngine.Dataset.HANDLING.fileName)
        val result = replayEngine.replay(stream, CsvReplayEngine.Dataset.HANDLING)

        // Sustained motion, tilt, and rotational slewing must all be rejected
        assertEquals("Normal handling must have 0 classified single taps", 0, result.classifiedSingles)
        assertEquals("Normal handling must have 0 classified double taps", 0, result.classifiedDoubles)
        assertEquals("Normal handling must have 0 classified triple taps", 0, result.classifiedTriples)
        assertEquals("Normal handling must have 0 false positive gestures", 0, result.falsePositives)
        assertTrue("Sampling frequency should be ~101 Hz", result.samplingFrequencyHz in 98f..104f)
    }

    @Test
    fun testSingleTapsDatasetReplay() {
        val stream = openDataset(CsvReplayEngine.Dataset.SINGLE_TAPS.fileName)
        val result = replayEngine.replay(stream, CsvReplayEngine.Dataset.SINGLE_TAPS)

        // Ground truth: 10 deliberate single taps
        assertEquals("Should detect exactly 10 impacts in single taps file", 10, result.detectedImpactCount)
        assertEquals("Should classify exactly 10 single taps", 10, result.classifiedSingles)
        assertEquals("Should have 0 accidental double taps", 0, result.classifiedDoubles)
        assertEquals("Should have 0 accidental triple taps", 0, result.classifiedTriples)
        assertEquals("Should have 0 false positives", 0, result.falsePositives)
        assertEquals("Should have 0 missed events", 0, result.missedEvents)
        assertTrue("Confidence should be high (> 80%)", result.averageConfidence >= 0.80f)
    }

    @Test
    fun testDoubleTapsDatasetReplay() {
        val stream = openDataset(CsvReplayEngine.Dataset.DOUBLE_TAPS.fileName)
        val result = replayEngine.replay(stream, CsvReplayEngine.Dataset.DOUBLE_TAPS)

        println("DOUBLE_TAPS REPLAY: impacts=${result.detectedImpactCount}, singles=${result.classifiedSingles}, doubles=${result.classifiedDoubles}, triples=${result.classifiedTriples}")
        for (g in result.gestures) {
            println("  Gesture: ${g.gestureType}, impacts=${g.constituentImpacts.size}, intervals=${g.interTapIntervalsMs}")
        }

        // Ground truth: 10 double taps
        assertTrue("Should classify at least 9-10 double taps", result.classifiedDoubles >= 9)
        assertEquals("Should have 0 false triple taps", 0, result.classifiedTriples)
        assertTrue("Detected impacts should be around 20-23", result.detectedImpactCount in 18..24)
        assertTrue("Confidence should be high (> 80%)", result.averageConfidence >= 0.80f)

        // Check inter-tap intervals on classified double taps
        val doubleGestures = result.gestures.filter { it.gestureType == GestureType.DOUBLE_TAP }
        for (g in doubleGestures) {
            val dt = g.interTapIntervalsMs.firstOrNull() ?: 0L
            assertTrue("Double tap interval should be in ~160-230ms window (was $dt ms)", dt in 150L..250L)
        }
    }

    @Test
    fun testTripleTapsDatasetReplay() {
        val stream = openDataset(CsvReplayEngine.Dataset.TRIPLE_TAPS.fileName)
        val result = replayEngine.replay(stream, CsvReplayEngine.Dataset.TRIPLE_TAPS)

        println("TRIPLE_TAPS REPLAY: impacts=${result.detectedImpactCount}, singles=${result.classifiedSingles}, doubles=${result.classifiedDoubles}, triples=${result.classifiedTriples}")
        for (g in result.gestures) {
            println("  Gesture: ${g.gestureType}, impacts=${g.constituentImpacts.size}, intervals=${g.interTapIntervalsMs}")
        }
        // Ground truth: 11 triple taps
        assertTrue("Should classify at least 10-11 triple taps", result.classifiedTriples >= 10)
        assertTrue("Detected impacts should be ~30-34", result.detectedImpactCount in 30..34)
        assertTrue("Confidence should be high (> 80%)", result.averageConfidence >= 0.80f)

        // Check inter-tap intervals on classified triple taps
        val tripleGestures = result.gestures.filter { it.gestureType == GestureType.TRIPLE_TAP }
        for (g in tripleGestures) {
            assertEquals("Triple tap must have 3 constituent impacts", 3, g.constituentImpacts.size)
            for (dt in g.interTapIntervalsMs) {
                assertTrue("Consecutive interval in triple tap should be in ~160-230ms window (was $dt ms)", dt in 150L..240L)
            }
        }
    }
}
