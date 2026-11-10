package com.tapback.app.core.detection

import com.tapback.app.domain.models.GestureClassification
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent

/**
 * Robust temporal state machine for classifying Single, Double, and Triple back taps.
 *
 * Guarantees non-eager confirmation:
 * - A single tap is NOT confirmed immediately; the engine waits for the inter-tap timeout window
 *   to ensure a double or triple tap is not in progress.
 * - Double tap waits for third tap timeout before confirming, preventing premature double triggers.
 * - Debounces rapid bounces and enforces cooldown periods.
 */
class TapSequenceClassifier(
    var minTapIntervalMs: Long = 135L,
    var maxTapIntervalMs: Long = 480L,
    var cooldownPeriodMs: Long = 350L,
    var allowSingleTap: Boolean = true,
    var allowTripleTap: Boolean = true
) {
    enum class State {
        IDLE,
        AWAITING_SECOND_TAP,
        AWAITING_THIRD_TAP,
        COOLDOWN
    }

    private var currentState = State.IDLE
    private var cooldownStartTimeMs = 0L

    val state: State get() = currentState

    val isInSequence: Boolean
        get() = currentState == State.AWAITING_SECOND_TAP || currentState == State.AWAITING_THIRD_TAP

    val activeTapCount: Int
        get() = currentImpacts.size

    fun getWindowRemainingMs(currentTimeMs: Long): Long {
        return when (currentState) {
            State.AWAITING_SECOND_TAP -> {
                val firstTapTime = currentImpacts.firstOrNull()?.timestampMs ?: return 0L
                (maxTapIntervalMs - (currentTimeMs - firstTapTime)).coerceAtLeast(0L)
            }
            State.AWAITING_THIRD_TAP -> {
                val secondTapTime = currentImpacts.getOrNull(1)?.timestampMs ?: return 0L
                (maxTapIntervalMs - (currentTimeMs - secondTapTime)).coerceAtLeast(0L)
            }
            else -> 0L
        }
    }

    private val currentImpacts = mutableListOf<ImpactEvent>()

    /**
     * Ticks the temporal state machine with the current sensor timestamp.
     * Checks for gesture timeout completions when no new impact arrives.
     * Returns a GestureClassification if a gesture timed out and was confirmed, or null.
     */
    fun onSampleTick(currentTimeMs: Long): GestureClassification? {
        when (currentState) {
            State.COOLDOWN -> {
                if (currentTimeMs - cooldownStartTimeMs >= cooldownPeriodMs) {
                    currentState = State.IDLE
                    currentImpacts.clear()
                }
                return null
            }

            State.AWAITING_SECOND_TAP -> {
                val firstTapTime = currentImpacts.firstOrNull()?.timestampMs ?: 0L
                val elapsed = currentTimeMs - firstTapTime
                if (elapsed > maxTapIntervalMs) {
                    // Timeout reached waiting for second tap
                    val gesture = if (allowSingleTap && currentImpacts.isNotEmpty()) {
                        buildClassification(GestureType.SINGLE_TAP, currentTimeMs)
                    } else null
                    enterCooldown(currentTimeMs)
                    return gesture
                }
            }

            State.AWAITING_THIRD_TAP -> {
                val secondTapTime = currentImpacts.getOrNull(1)?.timestampMs ?: 0L
                val elapsed = currentTimeMs - secondTapTime
                if (elapsed > maxTapIntervalMs) {
                    // Timeout reached waiting for third tap: confirm Double Tap
                    val gesture = buildClassification(GestureType.DOUBLE_TAP, currentTimeMs)
                    enterCooldown(currentTimeMs)
                    return gesture
                }
            }

            State.IDLE -> {
                // Nothing pending
            }
        }
        return null
    }

    /**
     * Ingests a confirmed physical impact event.
     * Returns a GestureClassification if this impact completes a sequence (e.g. Triple Tap), or null.
     */
    fun onImpact(impact: ImpactEvent): GestureClassification? {
        val t = impact.timestampMs

        when (currentState) {
            State.COOLDOWN -> {
                if (t - cooldownStartTimeMs < cooldownPeriodMs) {
                    // Still in refractory cooldown; drop spurious strike
                    return null
                }
                // Cooldown expired, transition to IDLE
                currentState = State.IDLE
                currentImpacts.clear()
                return handleFirstImpact(impact)
            }

            State.IDLE -> {
                return handleFirstImpact(impact)
            }

            State.AWAITING_SECOND_TAP -> {
                val firstTapTime = currentImpacts.first().timestampMs
                val interval = t - firstTapTime

                if (interval < minTapIntervalMs) {
                    // Physical bounce/recoil; reject
                    return null
                }

                if (interval <= maxTapIntervalMs) {
                    currentImpacts.add(impact)
                    if (!allowTripleTap) {
                        // Triple tap disabled, confirm Double Tap immediately
                        val gesture = buildClassification(GestureType.DOUBLE_TAP, t)
                        enterCooldown(t)
                        return gesture
                    } else {
                        // Wait briefly to see if third tap arrives
                        currentState = State.AWAITING_THIRD_TAP
                        return null
                    }
                } else {
                    // Arrived too late for double tap.
                    // Previous tap was a single tap; current tap starts a new candidate sequence.
                    val prevSingle = if (allowSingleTap) buildClassification(GestureType.SINGLE_TAP, t) else null
                    currentImpacts.clear()
                    currentImpacts.add(impact)
                    currentState = State.AWAITING_SECOND_TAP
                    return prevSingle
                }
            }

            State.AWAITING_THIRD_TAP -> {
                val secondTapTime = currentImpacts[1].timestampMs
                val interval = t - secondTapTime

                if (interval < minTapIntervalMs) {
                    // Physical bounce; reject
                    return null
                }

                if (interval <= maxTapIntervalMs) {
                    // Third tap confirmed!
                    currentImpacts.add(impact)
                    val gesture = buildClassification(GestureType.TRIPLE_TAP, t)
                    enterCooldown(t)
                    return gesture
                } else {
                    // Third tap was too late; previous was a double tap
                    val prevDouble = buildClassification(GestureType.DOUBLE_TAP, t)
                    currentImpacts.clear()
                    currentImpacts.add(impact)
                    currentState = State.AWAITING_SECOND_TAP
                    return prevDouble
                }
            }
        }
    }

    private fun handleFirstImpact(impact: ImpactEvent): GestureClassification? {
        currentImpacts.clear()
        currentImpacts.add(impact)
        currentState = State.AWAITING_SECOND_TAP
        return null
    }

    private fun buildClassification(type: GestureType, currentTimestampMs: Long): GestureClassification {
        val impactsCopy = ArrayList(currentImpacts)
        val avgConfidence = if (impactsCopy.isNotEmpty()) {
            impactsCopy.map { it.confidence }.average().toFloat()
        } else 0.85f

        val intervals = mutableListOf<Long>()
        for (i in 1 until impactsCopy.size) {
            intervals.add(impactsCopy[i].timestampMs - impactsCopy[i - 1].timestampMs)
        }

        return GestureClassification(
            gestureType = type,
            constituentImpacts = impactsCopy,
            confidence = avgConfidence,
            timestampMs = currentTimestampMs,
            interTapIntervalsMs = intervals
        )
    }

    private fun enterCooldown(t: Long) {
        currentState = State.COOLDOWN
        cooldownStartTimeMs = t
        currentImpacts.clear()
    }

    /**
     * Flushes any pending unconfirmed gesture at the end of a recording or stream.
     */
    fun flushPendingGesture(finalTimestampMs: Long): GestureClassification? {
        return when (currentState) {
            State.AWAITING_SECOND_TAP -> {
                if (allowSingleTap && currentImpacts.isNotEmpty()) {
                    val g = buildClassification(GestureType.SINGLE_TAP, finalTimestampMs)
                    reset()
                    g
                } else null
            }
            State.AWAITING_THIRD_TAP -> {
                if (currentImpacts.size >= 2) {
                    val g = buildClassification(GestureType.DOUBLE_TAP, finalTimestampMs)
                    reset()
                    g
                } else null
            }
            else -> null
        }
    }

    fun reset() {
        currentState = State.IDLE
        cooldownStartTimeMs = 0L
        currentImpacts.clear()
    }
}
