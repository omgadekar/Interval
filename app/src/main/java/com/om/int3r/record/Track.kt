package com.om.int3r.record

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

data class TimedValue(val timeMs: Long, val value: Double)

data class Fix(val timeMs: Long, val lat: Double, val lon: Double, val altitude: Double?)

/** Raw sensor streams for one run, timestamps in epoch ms. Filled by the service, read at export. */
class RunStreams {
    val heartRate = mutableListOf<TimedValue>()
    val speed = mutableListOf<TimedValue>()
    val cadence = mutableListOf<TimedValue>()
    val elevation = mutableListOf<TimedValue>()
    /** Cumulative distance in metres. */
    val distance = mutableListOf<TimedValue>()
    val fixes = mutableListOf<Fix>()
    var elevationGain: Double? = null
    var calories: Double? = null
}

/** One interval of the session, timed in wall-clock epoch ms. */
data class LapRecord(
    val number: Int,
    val label: String,
    val isWork: Boolean,
    val startMs: Long,
    val endMs: Long,
    val activeMs: Long,
    val meters: Double,
    val targetMinSecPerKm: Int? = null,
    val targetMaxSecPerKm: Int? = null,
)

data class TrackPoint(
    val timeMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitude: Double? = null,
    val distance: Double? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val speed: Double? = null,
)

@Serializable
data class LapSummary(
    val number: Int,
    val label: String,
    val isWork: Boolean,
    val activeMs: Long,
    val meters: Double,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val targetMinSecPerKm: Int?,
    val targetMaxSecPerKm: Int?,
) {
    val paceSecPerKm: Int? get() = paceOf(activeMs, meters)
}

/** One point of the summary graphs: seconds from the start, then averages over that slice. */
@Serializable
data class SeriesPoint(
    val t: Int,
    val hr: Int? = null,
    val pace: Int? = null,
    val alt: Double? = null,
)

@Serializable
data class RunSummary(
    val id: String,
    val workoutId: String? = null,
    /** The calendar day this run was planned on, if it was started from the calendar. */
    val date: String? = null,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val activeMs: Long,
    val meters: Double,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val elevationGain: Double?,
    val calories: Double?,
    val avgCadence: Int?,
    val laps: List<LapSummary>,
    val series: List<SeriesPoint> = emptyList(),
) {
    val paceSecPerKm: Int? get() = paceOf(activeMs, meters)
}

fun paceOf(activeMs: Long, meters: Double): Int? =
    if (meters < 50) null else ((activeMs / 1000.0) / (meters / 1000.0)).toInt()

object Track {
    private const val NEAREST_WINDOW_MS = 10_000L

    /** Latest value at or before [t] (within the window), else the first one just after. */
    fun nearest(series: List<TimedValue>, t: Long): Double? {
        if (series.isEmpty()) return null
        val i = series.binarySearchBy(t) { it.timeMs }.let { if (it >= 0) it else -it - 2 }
        val before = series.getOrNull(i)
        if (before != null && t - before.timeMs <= NEAREST_WINDOW_MS) return before.value
        val after = series.getOrNull(i + 1)
        if (after != null && after.timeMs - t <= NEAREST_WINDOW_MS) return after.value
        return null
    }

    /** Linear interpolation of a cumulative series, clamped at both ends. */
    fun interpolate(series: List<TimedValue>, t: Long): Double? {
        if (series.isEmpty()) return null
        if (t <= series.first().timeMs) return series.first().value
        if (t >= series.last().timeMs) return series.last().value
        val i = series.binarySearchBy(t) { it.timeMs }.let { if (it >= 0) return series[it].value else -it - 2 }
        val a = series[i]
        val b = series[i + 1]
        val f = (t - a.timeMs).toDouble() / (b.timeMs - a.timeMs)
        return a.value + (b.value - a.value) * f
    }

    /** One point per GPS fix, or per heart-rate sample when there is no GPS (treadmill, no fix). */
    fun build(s: RunStreams): List<TrackPoint> {
        val hr = s.heartRate.sortedBy { it.timeMs }
        val cad = s.cadence.sortedBy { it.timeMs }
        val spd = s.speed.sortedBy { it.timeMs }
        val ele = s.elevation.sortedBy { it.timeMs }
        val dist = s.distance.sortedBy { it.timeMs }
        val times: List<Pair<Long, Fix?>> =
            if (s.fixes.isNotEmpty()) s.fixes.sortedBy { it.timeMs }.map { it.timeMs to it }
            else hr.map { it.timeMs to null }
        return times.distinctBy { it.first }.map { (t, fix) ->
            TrackPoint(
                timeMs = t,
                lat = fix?.lat,
                lon = fix?.lon,
                altitude = fix?.altitude ?: nearest(ele, t),
                distance = interpolate(dist, t),
                heartRate = nearest(hr, t)?.toInt(),
                cadence = nearest(cad, t)?.toInt(),
                speed = nearest(spd, t),
            )
        }
    }

    /** Plausible altitude in metres, or null for "unavailable" sentinels (NaN, Double.MAX_VALUE). */
    fun saneAltitude(value: Double): Double? = value.takeIf { it.isFinite() && it > -500 && it < 9_000 }

    /**
     * Lap distances from the timestamped distance stream. Health Services batches samples while the
     * screen is off, so the live (extrapolated) distance at a lap boundary can be off by a batch.
     */
    fun withStreamDistances(laps: List<LapRecord>, distance: List<TimedValue>): List<LapRecord> {
        if (distance.size < 2) return laps
        val series = distance.sortedBy { it.timeMs }
        return laps.map { lap ->
            val start = interpolate(series, lap.startMs) ?: return@map lap
            val end = interpolate(series, lap.endMs) ?: return@map lap
            lap.copy(meters = (end - start).coerceAtLeast(0.0))
        }
    }

    /** Climb with a 2 m dead band so barometer noise does not count as hills. */
    fun elevationGain(series: List<TimedValue>, deadBand: Double = 2.0): Double? {
        if (series.size < 2) return null
        var gain = 0.0
        var ref = series.first().value
        for (v in series.drop(1)) {
            val diff = v.value - ref
            if (abs(diff) >= deadBand) {
                if (diff > 0) gain += diff
                ref = v.value
            }
        }
        return gain
    }

    /**
     * Downsamples the run into at most [maxPoints] slices for the summary graphs.
     * Pace is left empty for slices where you were standing still.
     */
    fun series(s: RunStreams, startMs: Long, endMs: Long, maxPoints: Int = 120): List<SeriesPoint> {
        val span = endMs - startMs
        if (span <= 0) return emptyList()
        val slice = max(5_000L, span / maxPoints)
        val altitudes = s.elevation.ifEmpty {
            s.fixes.mapNotNull { f -> f.altitude?.let { TimedValue(f.timeMs, it) } }
        }
        fun avg(series: List<TimedValue>, from: Long, to: Long): Double? =
            series.filter { it.timeMs >= from && it.timeMs < to }.map { it.value }.takeIf { it.isNotEmpty() }?.average()
        return (0 until ((span + slice - 1) / slice).toInt()).mapNotNull { k ->
            val from = startMs + k * slice
            val to = from + slice
            val hr = avg(s.heartRate.filter { it.value > 0 }, from, to)
            val speed = avg(s.speed, from, to)
            val alt = avg(altitudes, from, to)
            if (hr == null && speed == null && alt == null) return@mapNotNull null
            SeriesPoint(
                t = ((from - startMs) / 1000).toInt(),
                hr = hr?.toInt(),
                pace = speed?.takeIf { it >= 0.5 }?.let { (1000 / it).toInt() },
                alt = alt,
            )
        }
    }

    fun summarize(
        id: String,
        workoutId: String?,
        date: String?,
        title: String,
        startMs: Long,
        endMs: Long,
        activeMs: Long,
        meters: Double,
        streams: RunStreams,
        laps: List<LapRecord>,
    ): RunSummary {
        val hrValues = streams.heartRate.map { it.value }.filter { it > 0 }
        val cadValues = streams.cadence.map { it.value }.filter { it > 0 }
        return RunSummary(
            id = id,
            workoutId = workoutId,
            date = date,
            title = title,
            startMs = startMs,
            endMs = endMs,
            activeMs = activeMs,
            meters = meters,
            avgHeartRate = hrValues.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            maxHeartRate = hrValues.maxOrNull()?.toInt(),
            elevationGain = streams.elevationGain ?: elevationGain(streams.elevation.sortedBy { it.timeMs }),
            calories = streams.calories,
            avgCadence = cadValues.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            laps = laps.map { lap ->
                val inLap = streams.heartRate.filter { it.timeMs >= lap.startMs && it.timeMs < lap.endMs && it.value > 0 }
                LapSummary(
                    number = lap.number,
                    label = lap.label,
                    isWork = lap.isWork,
                    activeMs = lap.activeMs,
                    meters = lap.meters,
                    avgHeartRate = inLap.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.toInt(),
                    maxHeartRate = inLap.maxOfOrNull { it.value }?.toInt(),
                    targetMinSecPerKm = lap.targetMinSecPerKm,
                    targetMaxSecPerKm = lap.targetMaxSecPerKm,
                )
            },
            series = series(streams, startMs, endMs),
        )
    }
}