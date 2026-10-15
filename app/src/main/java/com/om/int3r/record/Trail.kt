package com.om.int3r.record

/**
 * A live graph's data: one value per [stepMs] bin, the last bin ending at [endMs] (epoch ms).
 * null means no reading in that bin.
 */
data class Trail(val endMs: Long = 0, val stepMs: Long = 1000, val values: List<Float?> = emptyList()) {
    /** Epoch time at the middle of bin [i]. */
    fun timeOf(i: Int): Long = endMs - (values.size - i) * stepMs + stepMs / 2

    val latest: Float? get() = values.lastOrNull { it != null }
}

/**
 * Builds live graphs from timestamped readings rather than "the latest value each second".
 * With the screen off, Wear OS delivers readings in batches; a batch then lands in the right
 * seconds of the graph instead of leaving a flat line where the screen was off.
 */
object Trails {
    /**
     * @param smoothSeconds moving average over this many seconds (0 = none), before [transform].
     * @param maxGapSeconds interior gaps up to this long are bridged by straight lines; longer ones stay empty.
     * @param transform maps the binned value for display (e.g. speed to pace); null drops the point.
     */
    fun build(
        series: List<TimedValue>,
        nowMs: Long,
        seconds: Int,
        smoothSeconds: Int = 0,
        maxGapSeconds: Int = 15,
        transform: (Double) -> Double? = { it },
    ): Trail {
        val start = nowMs - seconds * 1000L
        val sums = DoubleArray(seconds)
        val counts = IntArray(seconds)
        // Readings are appended in time order; walk back from the newest until we leave the window.
        for (i in series.indices.reversed()) {
            val t = series[i].timeMs
            if (t < start) break
            if (t >= nowMs) continue
            val bin = ((t - start) / 1000L).toInt().coerceIn(0, seconds - 1)
            sums[bin] += series[i].value
            counts[bin]++
        }
        var raw: List<Double?> = (0 until seconds).map { if (counts[it] > 0) sums[it] / counts[it] else null }
        if (smoothSeconds > 1) raw = movingAverage(raw, smoothSeconds)
        val filled = bridgeGaps(raw, maxGapSeconds)
        return Trail(nowMs, 1000L, filled.map { v -> v?.let(transform)?.toFloat() })
    }

    private fun movingAverage(values: List<Double?>, window: Int): List<Double?> = values.indices.map { i ->
        if (values[i] == null) return@map null
        val from = (i - window + 1).coerceAtLeast(0)
        val present = (from..i).mapNotNull { values[it] }
        present.average()
    }

    /** Straight lines across short interior gaps; leading, trailing and long gaps stay null. */
    fun bridgeGaps(values: List<Double?>, maxGap: Int): List<Double?> {
        val out = values.toMutableList()
        var last = -1
        for (i in values.indices) {
            val v = values[i] ?: continue
            if (last >= 0 && i - last > 1 && i - last - 1 <= maxGap) {
                val a = values[last]!!
                for (k in last + 1 until i) out[k] = a + (v - a) * (k - last) / (i - last)
            }
            last = i
        }
        return out
    }
}