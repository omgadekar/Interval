package com.om.interval.engine

import com.om.interval.workout.Block
import com.om.interval.workout.End
import com.om.interval.workout.Repeat
import com.om.interval.workout.RepeatUntilDistance
import com.om.interval.workout.Step
import com.om.interval.workout.Workout

/** Position inside a workout: block index, 0-based round, step index within the round. */
data class Pos(val block: Int, val round: Int, val step: Int)

/** Walks the block structure of a [Workout] one step at a time. Repeat-until-distance blocks are open-ended here; the engine closes them. */
class WorkoutCursor(private val workout: Workout) {
    private val blocks: List<Block> get() = workout.blocks

    fun first(): Pos? = if (blocks.isEmpty()) null else Pos(0, 0, 0)

    fun next(pos: Pos): Pos? = when (val block = blocks[pos.block]) {
        is Step -> nextBlock(pos.block)
        is Repeat -> {
            val lastRound = pos.round >= block.times - 1
            val stepsThisRound = if (lastRound) block.steps.size - block.skipLastOnFinal else block.steps.size
            when {
                pos.step + 1 < stepsThisRound -> pos.copy(step = pos.step + 1)
                !lastRound -> Pos(pos.block, pos.round + 1, 0)
                else -> nextBlock(pos.block)
            }
        }
        is RepeatUntilDistance ->
            if (pos.step + 1 < block.steps.size) pos.copy(step = pos.step + 1) else Pos(pos.block, pos.round + 1, 0)
    }

    fun nextBlock(block: Int): Pos? = if (block + 1 < blocks.size) Pos(block + 1, 0, 0) else null

    fun stepAt(pos: Pos): Step = when (val block = blocks[pos.block]) {
        is Step -> block
        is Repeat -> block.steps[pos.step]
        is RepeatUntilDistance -> block.steps[pos.step]
    }

    /** Number of rounds of the block, or null when it repeats until a distance. */
    fun roundsAt(pos: Pos): Int? = when (val block = blocks[pos.block]) {
        is Step -> 1
        is Repeat -> block.times
        is RepeatUntilDistance -> null
    }

    fun capAt(pos: Pos): Double? = (blocks[pos.block] as? RepeatUntilDistance)?.meters

    private val isFinite: Boolean get() = blocks.none { it is RepeatUntilDistance }

    /** All steps in order; only defined when the workout has no open-ended blocks. */
    fun linearSteps(): List<Step>? {
        if (!isFinite) return null
        val out = mutableListOf<Step>()
        var p = first()
        while (p != null) {
            out += stepAt(p)
            p = next(p)
        }
        return out
    }

    /** Total planned seconds when every step is timed; null if any step ends on distance. */
    fun plannedSeconds(): Long? {
        val steps = linearSteps() ?: return null
        return steps.sumOf { (it.end as? End.Time)?.seconds?.toLong() ?: return null }
    }
}

data class Segment(
    /** 1-based order in the session. */
    val number: Int,
    val pos: Pos,
    val step: Step,
    /** 1-based round within its block. */
    val round: Int,
    val rounds: Int?,
    val blockCapMeters: Double?,
)

sealed interface EngineEvent {
    data class SegmentStarted(val segment: Segment, val next: Step?) : EngineEvent
    data class SegmentEnded(
        val segment: Segment,
        val startMs: Long,
        val endMs: Long,
        val startMeters: Double,
        val endMeters: Double,
    ) : EngineEvent
    data class Warning(val segment: Segment) : EngineEvent
    data object Finished : EngineEvent
}

data class EngineSnapshot(
    val segment: Segment?,
    val elapsedInSegmentMs: Long,
    val remainingMs: Long?,
    val remainingMeters: Double?,
    val blockRemainingMeters: Double?,
    val next: Step?,
    val finished: Boolean,
)

/**
 * Advances through a workout on active time (ms, excluding pauses) and distance (m).
 * Pure logic so it can be unit-tested; the service feeds it once a second.
 */
class IntervalEngine(
    workout: Workout,
    private val warnSeconds: Int = 10,
    private val warnMeters: Double = 100.0,
) {
    private val cursor = WorkoutCursor(workout)

    val totalSegments: Int? = cursor.linearSteps()?.size

    var current: Segment? = null
        private set
    var finished = false
        private set

    private var segStartMs = 0L
    private var segStartMeters = 0.0
    private var blockStartMeters = 0.0
    private var warned = false
    private var counter = 0

    fun start(nowMs: Long, meters: Double): List<EngineEvent> {
        val first = cursor.first() ?: run {
            finished = true
            return listOf(EngineEvent.Finished)
        }
        blockStartMeters = meters
        return listOf(begin(first, nowMs, meters))
    }

    fun update(nowMs: Long, meters: Double): List<EngineEvent> {
        val events = mutableListOf<EngineEvent>()
        var guard = 0
        while (!finished && guard++ < 100) {
            val seg = current ?: break
            val capHit = seg.blockCapMeters?.let { meters - blockStartMeters >= it } ?: false
            val endMs = when (val end = seg.step.end) {
                is End.Time -> segStartMs + end.seconds * 1000L
                is End.Distance -> null
            }
            val done = capHit || when (val end = seg.step.end) {
                is End.Time -> nowMs >= endMs!!
                is End.Distance -> meters - segStartMeters >= end.meters
            }
            if (!done) {
                if (!warned && shouldWarn(seg, nowMs, meters)) {
                    warned = true
                    events += EngineEvent.Warning(seg)
                }
                break
            }
            // Timed segments end exactly on schedule so ticks never add drift.
            val boundaryMs = if (!capHit && endMs != null) endMs else nowMs
            events += advance(boundaryMs, meters, capHit)
        }
        return events
    }

    /** Ends the current segment now and moves on (user pressed Skip). */
    fun skip(nowMs: Long, meters: Double): List<EngineEvent> =
        if (current == null || finished) emptyList() else advance(nowMs, meters, capHit = false)

    /** Ends the session where it is (user pressed End); returns the partial segment, if any. */
    fun close(nowMs: Long, meters: Double): EngineEvent.SegmentEnded? {
        val seg = current ?: return null
        current = null
        finished = true
        return EngineEvent.SegmentEnded(seg, segStartMs, nowMs, segStartMeters, meters)
    }

    fun snapshot(nowMs: Long, meters: Double): EngineSnapshot {
        val seg = current
            ?: return EngineSnapshot(null, 0, null, null, null, null, finished)
        val elapsed = nowMs - segStartMs
        val remainingMs = (seg.step.end as? End.Time)?.let { (it.seconds * 1000L - elapsed).coerceAtLeast(0) }
        val remainingM = (seg.step.end as? End.Distance)?.let { (it.meters - (meters - segStartMeters)).coerceAtLeast(0.0) }
        val blockRemaining = seg.blockCapMeters?.let { (it - (meters - blockStartMeters)).coerceAtLeast(0.0) }
        return EngineSnapshot(seg, elapsed, remainingMs, remainingM, blockRemaining, peekNext(seg), false)
    }

    private fun peekNext(seg: Segment): Step? = cursor.next(seg.pos)?.let(cursor::stepAt)

    private fun shouldWarn(seg: Segment, nowMs: Long, meters: Double): Boolean = when (val end = seg.step.end) {
        is End.Time -> end.seconds >= 30 && segStartMs + end.seconds * 1000L - nowMs <= warnSeconds * 1000L
        is End.Distance -> end.meters >= 400 && end.meters - (meters - segStartMeters) <= warnMeters
    }

    private fun advance(nowMs: Long, meters: Double, capHit: Boolean): List<EngineEvent> {
        val seg = current!!
        val events = mutableListOf<EngineEvent>(
            EngineEvent.SegmentEnded(seg, segStartMs, nowMs, segStartMeters, meters),
        )
        val nextPos = if (capHit) cursor.nextBlock(seg.pos.block) else cursor.next(seg.pos)
        if (nextPos == null) {
            current = null
            finished = true
            events += EngineEvent.Finished
        } else {
            if (nextPos.block != seg.pos.block) blockStartMeters = meters
            events += begin(nextPos, nowMs, meters)
        }
        return events
    }

    private fun begin(pos: Pos, nowMs: Long, meters: Double): EngineEvent.SegmentStarted {
        val seg = Segment(
            number = ++counter,
            pos = pos,
            step = cursor.stepAt(pos),
            round = pos.round + 1,
            rounds = cursor.roundsAt(pos),
            blockCapMeters = cursor.capAt(pos),
        )
        current = seg
        segStartMs = nowMs
        segStartMeters = meters
        warned = false
        return EngineEvent.SegmentStarted(seg, peekNext(seg))
    }
}