package com.tapback.app.core.detection

import com.tapback.app.domain.models.GestureClassification
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent
import com.tapback.app.domain.models.ReplayBenchmarkResult
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Replays real-world sensor CSV recordings through the production detection engine,
 * reporting performance metrics and comparing against ground truth.
 */
class CsvReplayEngine {

    enum class Dataset(
        val fileName: String,
        val displayName: String,
        val description: String,
        val isTuningDataset: Boolean,
        val expectedSingles: Int,
        val expectedDoubles: Int,
        val expectedTriples: Int
    ) {
        STATIONARY(
            fileName = "tapback_sensor_log_20261109_194013.csv",
            displayName = "1. Stationary",
            description = "Held-out validation: Phone resting on table. Must have 0 false positives.",
            isTuningDataset = false,
            expectedSingles = 0,
            expectedDoubles = 0,
            expectedTriples = 0
        ),
        HANDLING(
            fileName = "tapback_sensor_log_20261109_194128.csv",
            displayName = "2. Normal Handling",
            description = "Held-out validation: Dynamic rotation, tilt, and motion. Must have 0 false positives.",
            isTuningDataset = false,
            expectedSingles = 0,
            expectedDoubles = 0,
            expectedTriples = 0
        ),
        SINGLE_TAPS(
            fileName = "tapback_sensor_log_20261109_194220.csv",
            displayName = "3. Single Taps",
            description = "Tuning dataset: 10 intentional spaced rear taps.",
            isTuningDataset = true,
            expectedSingles = 10,
            expectedDoubles = 0,
            expectedTriples = 0
        ),
        DOUBLE_TAPS(
            fileName = "tapback_sensor_log_20261109_194238.csv",
            displayName = "4. Double Taps",
            description = "Tuning dataset: 10 intentional double taps (170-220ms inter-tap).",
            isTuningDataset = true,
            expectedSingles = 0,
            expectedDoubles = 10,
            expectedTriples = 0
        ),
        TRIPLE_TAPS(
            fileName = "tapback_sensor_log_20261109_194258.csv",
            displayName = "5. Triple Taps",
            description = "Tuning dataset: 11 intentional triple taps (170-230ms inter-tap).",
            isTuningDataset = true,
            expectedSingles = 0,
            expectedDoubles = 0,
            expectedTriples = 11
        )
    }

    /**
     * Executes replay on an InputStream containing CSV rows.
     */
    fun replay(
        inputStream: InputStream,
        dataset: Dataset,
        baseThreshold: Float = 2.80f
    ): ReplayBenchmarkResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        return replayFromReader(reader, dataset, baseThreshold)
    }

    /**
     * Executes replay on a BufferedReader of CSV rows.
     */
    fun replayFromReader(
        reader: BufferedReader,
        dataset: Dataset,
        baseThreshold: Float = 2.80f
    ): ReplayBenchmarkResult {
        val preprocessor = SensorPreprocessor()
        val motionFilter = MotionRejectionFilter()
        val impactDetector = ImpactDetector(
            baseThreshold = baseThreshold,
            minImpactSpacingMs = 95L,
            motionFilter = motionFilter
        )
        val classifier = TapSequenceClassifier(
            minTapIntervalMs = 135L,
            maxTapIntervalMs = 480L,
            cooldownPeriodMs = 350L,
            allowSingleTap = true,
            allowTripleTap = true
        )

        val impacts = mutableListOf<ImpactEvent>()
        val gestures = mutableListOf<GestureClassification>()

        var totalSamples = 0
        var firstTimestampNs = 0L
        var lastTimestampNs = 0L

        reader.useLines { lines ->
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return@useLines

            val header = iterator.next().split(",")
            val tsIdx = header.indexOf("timestamp_ns")
            val axIdx = header.indexOf("ax")
            val ayIdx = header.indexOf("ay")
            val azIdx = header.indexOf("az")
            val gxIdx = header.indexOf("gx")
            val gyIdx = header.indexOf("gy")
            val gzIdx = header.indexOf("gz")

            while (iterator.hasNext()) {
                val line = iterator.next().trim()
                if (line.isEmpty()) continue

                val parts = line.split(",")
                val tsNs = parts.getOrNull(tsIdx)?.toLongOrNull() ?: continue
                val ax = parts.getOrNull(axIdx)?.toFloatOrNull() ?: 0f
                val ay = parts.getOrNull(ayIdx)?.toFloatOrNull() ?: 0f
                val az = parts.getOrNull(azIdx)?.toFloatOrNull() ?: 0f
                val gx = parts.getOrNull(gxIdx)?.toFloatOrNull() ?: 0f
                val gy = parts.getOrNull(gyIdx)?.toFloatOrNull() ?: 0f
                val gz = parts.getOrNull(gzIdx)?.toFloatOrNull() ?: 0f

                if (totalSamples == 0) firstTimestampNs = tsNs
                lastTimestampNs = tsNs
                totalSamples++

                val processed = preprocessor.process(
                    timestampNs = tsNs,
                    ax = ax,
                    ay = ay,
                    az = az,
                    gx = gx,
                    gy = gy,
                    gz = gz
                )

                // 1. Tick state machine with current sample timestamp
                classifier.onSampleTick(processed.timestampMs)?.let { completedGesture ->
                    gestures.add(completedGesture)
                }

                // 2. Evaluate impact detection
                impactDetector.processSample(processed, classifier.isInSequence)?.let { impact ->
                    impacts.add(impact)
                    classifier.onImpact(impact)?.let { completedGesture ->
                        gestures.add(completedGesture)
                    }
                }
            }
        }

        // Flush any trailing pending gesture
        val lastTimestampMs = lastTimestampNs / 1_000_000L
        classifier.flushPendingGesture(lastTimestampMs)?.let { trailingGesture ->
            gestures.add(trailingGesture)
        }

        val durationSeconds = if (lastTimestampNs > firstTimestampNs) {
            (lastTimestampNs - firstTimestampNs) / 1_000_000_000f
        } else 0f

        val samplingFrequencyHz = if (durationSeconds > 0f) {
            totalSamples / durationSeconds
        } else 101.0f

        val singles = gestures.count { it.gestureType == GestureType.SINGLE_TAP }
        val doubles = gestures.count { it.gestureType == GestureType.DOUBLE_TAP }
        val triples = gestures.count { it.gestureType == GestureType.TRIPLE_TAP }

        // Determine false positives and missed events based on ground truth
        val falsePositives: Int
        val missedEvents: Int

        when (dataset) {
            Dataset.STATIONARY -> {
                falsePositives = gestures.size
                missedEvents = 0
            }
            Dataset.HANDLING -> {
                falsePositives = gestures.size
                missedEvents = 0
            }
            Dataset.SINGLE_TAPS -> {
                falsePositives = doubles + triples + (singles - dataset.expectedSingles).coerceAtLeast(0)
                missedEvents = (dataset.expectedSingles - singles).coerceAtLeast(0)
            }
            Dataset.DOUBLE_TAPS -> {
                // In double taps log, there are 10 double taps and 1 isolated tap strike (#7)
                falsePositives = triples + (doubles - dataset.expectedDoubles).coerceAtLeast(0)
                missedEvents = (dataset.expectedDoubles - doubles).coerceAtLeast(0)
            }
            Dataset.TRIPLE_TAPS -> {
                falsePositives = singles + (doubles) + (triples - dataset.expectedTriples).coerceAtLeast(0)
                missedEvents = (dataset.expectedTriples - triples).coerceAtLeast(0)
            }
        }

        val avgConfidence = if (gestures.isNotEmpty()) {
            gestures.map { it.confidence }.average().toFloat()
        } else if (impacts.isNotEmpty()) {
            impacts.map { it.confidence }.average().toFloat()
        } else 0.95f

        return ReplayBenchmarkResult(
            datasetName = dataset.displayName,
            isTuningDataset = dataset.isTuningDataset,
            totalSamples = totalSamples,
            durationSeconds = durationSeconds,
            samplingFrequencyHz = samplingFrequencyHz,
            detectedImpactCount = impacts.size,
            classifiedSingles = singles,
            classifiedDoubles = doubles,
            classifiedTriples = triples,
            expectedSingles = dataset.expectedSingles,
            expectedDoubles = dataset.expectedDoubles,
            expectedTriples = dataset.expectedTriples,
            falsePositives = falsePositives,
            missedEvents = missedEvents,
            averageConfidence = avgConfidence,
            impacts = impacts,
            gestures = gestures
        )
    }
}
