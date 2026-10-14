package com.om.interval.workout

import kotlin.math.roundToInt

/** Resolved pace band in seconds per km; min is the fast edge. */
data class PaceBand(val minSecPerKm: Int, val maxSecPerKm: Int) {
    fun contains(secPerKm: Double) = secPerKm >= minSecPerKm && secPerKm <= maxSecPerKm
    override fun toString() =
        if (minSecPerKm == maxSecPerKm) formatPace(minSecPerKm) else "${formatPace(minSecPerKm)}-${formatPace(maxSecPerKm)}"
}

object Pace {
    /** Half-width of the band coached around goal pace. */
    const val GOAL_TOLERANCE_SEC = 8

    /** Resolves a step target; goal targets need [goalSecPerKm], otherwise null (no coaching). */
    fun resolve(target: PaceTarget?, goalSecPerKm: Int?): PaceBand? = when (target) {
        null -> null
        is PaceTarget.Range -> PaceBand(
            minOf(target.fastSecPerKm, target.slowSecPerKm),
            maxOf(target.fastSecPerKm, target.slowSecPerKm),
        )
        is PaceTarget.Goal -> goalSecPerKm?.let {
            val centre = it + target.offsetSec
            PaceBand(centre - GOAL_TOLERANCE_SEC, centre + GOAL_TOLERANCE_SEC)
        }
    }
}

fun formatPace(secPerKm: Int): String = "%d:%02d".format(secPerKm / 60, secPerKm % 60)

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.2f km".format(meters / 1000)