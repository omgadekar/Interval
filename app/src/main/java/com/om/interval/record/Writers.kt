package com.om.interval.record

import java.time.Instant
import java.util.Locale

private fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

private fun Double.f(digits: Int) = String.format(Locale.US, "%.${digits}f", this)

private fun esc(s: String) = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/** Garmin Training Center XML: one Lap per interval, so Strava / Runalyze show the workout structure. */
object TcxWriter {
    fun write(summary: RunSummary, laps: List<LapRecord>, points: List<TrackPoint>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """)
        sb.append("""xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">""").append('\n')
        sb.append("<Activities>\n<Activity Sport=\"Running\">\n")
        sb.append("<Id>").append(iso(summary.startMs)).append("</Id>\n")
        val lapSummaries = summary.laps.associateBy { it.number }
        laps.forEachIndexed { index, lap ->
            val last = index == laps.lastIndex
            val lapPoints = points.filter { it.timeMs >= lap.startMs && (it.timeMs < lap.endMs || (last && it.timeMs <= lap.endMs)) }
            val ls = lapSummaries[lap.number]
            sb.append("<Lap StartTime=\"").append(iso(lap.startMs)).append("\">\n")
            sb.append("<TotalTimeSeconds>").append((lap.activeMs / 1000.0).f(1)).append("</TotalTimeSeconds>\n")
            sb.append("<DistanceMeters>").append(lap.meters.f(1)).append("</DistanceMeters>\n")
            lapPoints.mapNotNull { it.speed }.maxOrNull()?.let {
                sb.append("<MaximumSpeed>").append(it.f(2)).append("</MaximumSpeed>\n")
            }
            sb.append("<Calories>0</Calories>\n")
            ls?.avgHeartRate?.let { sb.append("<AverageHeartRateBpm><Value>").append(it).append("</Value></AverageHeartRateBpm>\n") }
            ls?.maxHeartRate?.let { sb.append("<MaximumHeartRateBpm><Value>").append(it).append("</Value></MaximumHeartRateBpm>\n") }
            sb.append("<Intensity>").append(if (lap.isWork) "Active" else "Resting").append("</Intensity>\n")
            sb.append("<TriggerMethod>Manual</TriggerMethod>\n")
            if (lapPoints.isNotEmpty()) {
                sb.append("<Track>\n")
                lapPoints.forEach { appendPoint(sb, it) }
                sb.append("</Track>\n")
            }
            sb.append("<Notes>").append(esc(lap.label)).append("</Notes>\n")
            sb.append("</Lap>\n")
        }
        sb.append("<Notes>").append(esc(summary.title)).append("</Notes>\n")
        sb.append("</Activity>\n</Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun appendPoint(sb: StringBuilder, p: TrackPoint) {
        sb.append("<Trackpoint><Time>").append(iso(p.timeMs)).append("</Time>")
        if (p.lat != null && p.lon != null) {
            sb.append("<Position><LatitudeDegrees>").append(p.lat.f(7)).append("</LatitudeDegrees>")
            sb.append("<LongitudeDegrees>").append(p.lon.f(7)).append("</LongitudeDegrees></Position>")
        }
        p.altitude?.let { sb.append("<AltitudeMeters>").append(it.f(1)).append("</AltitudeMeters>") }
        p.distance?.let { sb.append("<DistanceMeters>").append(it.f(1)).append("</DistanceMeters>") }
        p.heartRate?.let { sb.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>") }
        if (p.speed != null || p.cadence != null) {
            sb.append("<Extensions><ns3:TPX>")
            p.speed?.let { sb.append("<ns3:Speed>").append(it.f(2)).append("</ns3:Speed>") }
            // Garmin RunCadence counts one foot: steps per minute / 2.
            p.cadence?.let { sb.append("<ns3:RunCadence>").append(it / 2).append("</ns3:RunCadence>") }
            sb.append("</ns3:TPX></Extensions>")
        }
        sb.append("</Trackpoint>\n")
    }
}

/** GPX 1.1 track with heart rate and cadence in Garmin TrackPointExtension. Only points with a GPS fix. */
object GpxWriter {
    fun write(summary: RunSummary, points: List<TrackPoint>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="Interval" xmlns="http://www.topografix.com/GPX/1/1" """)
        sb.append("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""").append('\n')
        sb.append("<metadata><name>").append(esc(summary.title)).append("</name><time>")
            .append(iso(summary.startMs)).append("</time></metadata>\n")
        sb.append("<trk><name>").append(esc(summary.title)).append("</name><type>running</type><trkseg>\n")
        points.filter { it.lat != null && it.lon != null }.forEach { p ->
            sb.append("<trkpt lat=\"").append(p.lat!!.f(7)).append("\" lon=\"").append(p.lon!!.f(7)).append("\">")
            p.altitude?.let { sb.append("<ele>").append(it.f(1)).append("</ele>") }
            sb.append("<time>").append(iso(p.timeMs)).append("</time>")
            if (p.heartRate != null || p.cadence != null) {
                sb.append("<extensions><gpxtpx:TrackPointExtension>")
                p.heartRate?.let { sb.append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr>") }
                p.cadence?.let { sb.append("<gpxtpx:cad>").append(it / 2).append("</gpxtpx:cad>") }
                sb.append("</gpxtpx:TrackPointExtension></extensions>")
            }
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }
}