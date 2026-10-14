package com.om.interval

import com.om.interval.record.Fix
import com.om.interval.record.GpxWriter
import com.om.interval.record.LapRecord
import com.om.interval.record.RunStreams
import com.om.interval.record.TcxWriter
import com.om.interval.record.TimedValue
import com.om.interval.record.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

class WritersTest {
    private val t0 = 1_791_000_000_000L

    /** 3 minutes at 1 Hz: 1 min jog (2.5 m/s), 2 min walk (1.2 m/s), HR ramps 120 -> 155. */
    private fun streams(): RunStreams = RunStreams().apply {
        var dist = 0.0
        for (s in 0..180) {
            val t = t0 + s * 1000L
            val v = if (s < 60) 2.5 else 1.2
            if (s > 0) dist += v
            fixes += Fix(t, 19.99 + s * 1e-5, 73.78 + s * 1e-5, 560.0 + s / 60.0)
            heartRate += TimedValue(t, 120.0 + s * 35.0 / 180)
            speed += TimedValue(t, v)
            cadence += TimedValue(t, if (s < 60) 160.0 else 110.0)
            distance += TimedValue(t, dist)
        }
        calories = 25.0
    }

    private val laps = listOf(
        LapRecord(1, "Jog", true, t0, t0 + 60_000, 60_000, 150.0, 495, 525),
        LapRecord(2, "Walk", false, t0 + 60_000, t0 + 180_000, 120_000, 144.0),
    )

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(xml.byteInputStream())

    @Test
    fun tcxHasOneLapPerIntervalWithAllPoints() {
        val s = streams()
        val points = Track.build(s)
        val summary = Track.summarize("id", "w1", "2026-10-13", "Walk / Jog", t0, t0 + 180_000, 180_000, 294.0, s, laps)
        val doc = parse(TcxWriter.write(summary, laps, points))
        val ns = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
        val lapNodes = doc.getElementsByTagNameNS(ns, "Lap")
        assertEquals(2, lapNodes.length)
        assertEquals(181, doc.getElementsByTagNameNS(ns, "Trackpoint").length)
        assertEquals("Active", doc.getElementsByTagNameNS(ns, "Intensity").item(0).textContent)
        assertEquals("Resting", doc.getElementsByTagNameNS(ns, "Intensity").item(1).textContent)
        // Lap 1 average HR covers seconds 0..59 only.
        assertEquals(125, summary.laps[0].avgHeartRate)
        assertEquals(25.0, summary.calories!!, 0.0)
        assertEquals(400, summary.laps[0].paceSecPerKm)
    }

    @Test
    fun gpxIsWellFormedWithHeartRate() {
        val s = streams()
        val points = Track.build(s)
        val summary = Track.summarize("id", null, null, "Test & run", t0, t0 + 180_000, 180_000, 294.0, s, laps)
        val doc = parse(GpxWriter.write(summary, points))
        assertEquals(181, doc.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").length)
        assertEquals("120", doc.getElementsByTagNameNS("http://www.garmin.com/xmlschemas/TrackPointExtension/v1", "hr").item(0).textContent)
    }

    @Test
    fun seriesDownsamplesForGraphs() {
        val s = streams()
        val series = Track.series(s, t0, t0 + 180_000, maxPoints = 30)
        // 3 minutes in 30 slices of 6 s.
        assertEquals(30, series.size)
        assertEquals(6, series[1].t)
        assertEquals(0, series.first().t)
        // Jogging at 2.5 m/s = 6:40 /km, walking at 1.2 m/s = 13:53 /km.
        assertEquals(400, series.first().pace)
        assertEquals(833, series.last().pace)
        assertEquals(560.0, series.first().alt!!, 0.1)
    }

    @Test
    fun trackFallsBackToHeartRateTimesWithoutGps() {
        val s = streams().apply { fixes.clear() }
        val points = Track.build(s)
        assertEquals(181, points.size)
        assertNull(points.first().lat)
    }

    @Test
    fun interpolateAndNearest() {
        val series = listOf(TimedValue(0, 0.0), TimedValue(10_000, 100.0))
        assertEquals(50.0, Track.interpolate(series, 5_000)!!, 1e-9)
        assertEquals(100.0, Track.interpolate(series, 20_000)!!, 1e-9)
        assertEquals(0.0, Track.nearest(series, 4_000)!!, 1e-9)
        assertNull(Track.nearest(series, 25_000))
    }

    @Test
    fun lapDistancesComeFromTheDistanceStream() {
        val s = streams()
        // Live distances were wrong (a batch arrived late); the stream has the true split.
        val live = laps.map { it.copy(meters = 0.0) }
        val fixed = Track.withStreamDistances(live, s.distance)
        assertEquals(148.7, fixed[0].meters, 0.01)
        assertEquals(144.0, fixed[1].meters, 0.01)
    }

    @Test
    fun altitudeSentinelsAreDropped() {
        assertNull(Track.saneAltitude(Double.MAX_VALUE))
        assertNull(Track.saneAltitude(Double.NaN))
        assertEquals(560.0, Track.saneAltitude(560.0)!!, 0.0)
    }

    @Test
    fun elevationGainIgnoresNoise() {
        val noisy = listOf(100.0, 101.0, 100.0, 101.5, 103.0, 102.0, 106.0).mapIndexed { i, v -> TimedValue(i * 1000L, v) }
        assertEquals(6.0, Track.elevationGain(noisy)!!, 1e-9)
    }
}