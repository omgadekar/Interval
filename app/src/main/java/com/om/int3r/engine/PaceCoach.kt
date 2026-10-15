package com.om.int3r.engine

import com.om.int3r.workout.PaceBand

/** Mean speed over a sliding window, from timestamped samples (m/s). */
class SpeedSmoother(private val windowMs: Long = 30_000) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(timeMs: Long, metersPerSecond: Double) {
        samples.addLast(timeMs to metersPerSecond)
        while (samples.isNotEmpty() && samples.first().first < timeMs - windowMs) samples.removeFirst()
    }

    fun clear() = samples.clear()

    /**
     * Smoothed pace in s/km, or null when stopped or without data. With [nowMs], readings older than
     * the window (held back while the screen was off) don't count, so a stale pace is never reported.
     */
    fun paceSecPerKm(nowMs: Long? = null): Double? {
        val recent = if (nowMs == null) samples.toList() else samples.filter { it.first >= nowMs - windowMs }
        if (recent.isEmpty()) return null
        val mean = recent.sumOf { it.second } / recent.size
        return if (mean < MIN_MOVING_SPEED) null else 1000.0 / mean
    }

    companion object {
        /** Below this (about 33 min/km) you are standing at a crossing, not running slowly. */
        const val MIN_MOVING_SPEED = 0.5
    }
}

/**
 * Decides when to buzz "too fast" or "too slow". Fires only after the pace has been out of band
 * for [outOfBandMs], not within [settleMs] of a segment start, and at most once per [cooldownMs].
 */
class PaceCoach(
    private val outOfBandMs: Long = 20_000,
    private val cooldownMs: Long = 60_000,
    private val settleMs: Long = 30_000,
) {
    enum class Verdict { TOO_FAST, TOO_SLOW }

    private var segmentStartMs = 0L
    private var outSince: Long? = null
    private var outDirection: Verdict? = null
    private var lastAlertMs: Long? = null

    fun onSegmentStart(nowMs: Long) {
        segmentStartMs = nowMs
        outSince = null
        outDirection = null
    }

    fun update(nowMs: Long, paceSecPerKm: Double?, band: PaceBand?): Verdict? {
        if (band == null || paceSecPerKm == null || nowMs - segmentStartMs < settleMs) {
            outSince = null
            outDirection = null
            return null
        }
        val direction = when {
            paceSecPerKm < band.minSecPerKm -> Verdict.TOO_FAST
            paceSecPerKm > band.maxSecPerKm -> Verdict.TOO_SLOW
            else -> null
        }
        if (direction == null) {
            outSince = null
            outDirection = null
            return null
        }
        if (direction != outDirection) {
            outDirection = direction
            outSince = nowMs
        }
        val since = outSince ?: nowMs
        val cooledDown = lastAlertMs?.let { nowMs - it >= cooldownMs } ?: true
        if (nowMs - since >= outOfBandMs && cooledDown) {
            lastAlertMs = nowMs
            return direction
        }
        return null
    }
}