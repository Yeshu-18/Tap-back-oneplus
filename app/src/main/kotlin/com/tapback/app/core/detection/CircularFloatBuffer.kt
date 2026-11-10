package com.tapback.app.core.detection

/**
 * High-performance circular buffer for float sensor data.
 * Zero-allocation in hot path.
 */
class CircularFloatBuffer(val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var head = 0
    private var count = 0

    fun push(value: Float) {
        buffer[head] = value
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    val size: Int get() = count

    fun isFull(): Boolean = count == capacity

    fun clear() {
        head = 0
        count = 0
    }

    /**
     * Get item at index from oldest (0) to newest (count - 1).
     */
    operator fun get(index: Int): Float {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Index: $index, Count: $count")
        }
        val start = if (count < capacity) 0 else head
        val actualIndex = (start + index) % capacity
        return buffer[actualIndex]
    }

    /**
     * Get latest item pushed.
     */
    fun latest(): Float {
        if (count == 0) return 0f
        val idx = if (head == 0) capacity - 1 else head - 1
        return buffer[idx]
    }

    /**
     * Compute mean of elements in buffer.
     */
    fun mean(): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            sum += this[i]
        }
        return (sum / count).toFloat()
    }

    /**
     * Compute variance of elements in buffer.
     */
    fun variance(): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            sum += this[i]
        }
        val mean = (sum / count).toFloat()
        var varSum = 0.0
        for (i in 0 until count) {
            val diff = this[i] - mean
            varSum += diff * diff
        }
        return (varSum / count).toFloat()
    }
}
