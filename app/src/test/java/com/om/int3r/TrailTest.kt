package com.om.int3r

import com.om.int3r.record.TimedValue
import com.om.int3r.record.Trails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailTest {
    private val now = 1_800_000_000_000L

    @Test
    fun batchedReadingsLandInTheirOwnSeconds() {
        // Screen off: nothing arrives live, then a batch with one reading per second for the last 30 s.
        val series = (30 downTo 1).map { s -> TimedValue(now - s * 1000L + 200, 100.0 + s) }
        val trail = Trails.build(series, now, seconds = 60)
        assertEquals(60, trail.values.size)
        // The first 30 seconds had no data; the last 30 hold the real, changing values (not a flat line).
        assertNull(trail.values[0])
        assertEquals(130f, trail.values[30]!!, 0.01f)
        assertEquals(101f, trail.values[59]!!, 0.01f)
    }

    @Test
    fun shortGapsAreBridgedLongOnesStayEmpty() {
        val series = listOf(
            TimedValue(now - 50_000, 100.0),
            TimedValue(now - 46_000, 140.0), // 3 s gap -> bridged
            TimedValue(now - 10_000, 120.0), // 35 s gap -> stays empty
        )
        val trail = Trails.build(series, now, seconds = 60, maxGapSeconds = 15)
        assertEquals(110f, trail.values[11]!!, 0.01f)
        assertEquals(130f, trail.values[13]!!, 0.01f)
        assertNull(trail.values[30])
        assertNull(trail.values[59]) // nothing after the last reading
    }

    @Test
    fun speedBecomesPaceAndStopsAreGaps() {
        val series = (60 downTo 1).map { s -> TimedValue(now - s * 1000L, if (s > 30) 2.5 else 0.2) }
        val trail = Trails.build(series, now, seconds = 60, transform = { v -> if (v >= 0.5) 1000 / v else null })
        assertEquals(400f, trail.values[0]!!, 0.01f) // 2.5 m/s = 6:40 /km
        assertNull(trail.values[45]) // standing still: no pace
    }

    @Test
    fun timeOfBinsCountsBackFromTheEnd() {
        val trail = Trails.build(listOf(TimedValue(now - 500, 1.0)), now, seconds = 10)
        assertEquals(now - 500, trail.timeOf(9))
        assertEquals(now - 9_500, trail.timeOf(0))
    }
}