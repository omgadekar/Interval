package com.om.int3r

import com.om.int3r.engine.EngineEvent
import com.om.int3r.engine.IntervalEngine
import com.om.int3r.engine.PaceCoach
import com.om.int3r.workout.End
import com.om.int3r.workout.Pace
import com.om.int3r.workout.PaceBand
import com.om.int3r.workout.PaceTarget
import com.om.int3r.workout.Repeat
import com.om.int3r.workout.RepeatUntilDistance
import com.om.int3r.workout.Step
import com.om.int3r.workout.StepType
import com.om.int3r.workout.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun timed(type: StepType, s: Int) = Step(type, End.Time(s))

    /** Feeds the engine once a second at a steady speed and returns all events. */
    private fun simulate(engine: IntervalEngine, seconds: Int, speed: Double): List<EngineEvent> {
        val events = engine.start(0, 0.0).toMutableList()
        for (t in 1..seconds) events += engine.update(t * 1000L, t * speed)
        return events
    }

    private fun started(events: List<EngineEvent>) =
        events.filterIsInstance<EngineEvent.SegmentStarted>().map { it.segment.step.label }

    @Test
    fun timedRepeatsAdvanceOnSchedule() {
        val w = Workout(listOf(Repeat(2, listOf(timed(StepType.JOG, 60), timed(StepType.WALK, 30)))))
        val engine = IntervalEngine(w)
        val events = simulate(engine, 200, 2.5)
        assertEquals(listOf("Jog", "Walk", "Jog", "Walk"), started(events))
        val ended = events.filterIsInstance<EngineEvent.SegmentEnded>()
        assertEquals(listOf(60_000L, 90_000L, 150_000L, 180_000L), ended.map { it.endMs })
        assertTrue(events.last() is EngineEvent.Finished)
        assertTrue(engine.finished)
    }

    @Test
    fun timedSegmentsDoNotDriftWithLateTicks() {
        val w = Workout(listOf(timed(StepType.JOG, 60), timed(StepType.WALK, 60)))
        val engine = IntervalEngine(w)
        engine.start(0, 0.0)
        // A late batch: nothing for 61.7 s.
        val events = engine.update(61_700, 150.0)
        val ended = events.filterIsInstance<EngineEvent.SegmentEnded>().single()
        assertEquals(60_000L, ended.endMs)
        assertEquals(58_300L, engine.snapshot(61_700, 150.0).remainingMs)
    }

    @Test
    fun distanceStepEndsOnDistance() {
        val w = Workout(listOf(Step(StepType.EASY, End.Distance(1000.0)), timed(StepType.WALK, 60)))
        val engine = IntervalEngine(w)
        engine.start(0, 0.0)
        assertTrue(engine.update(300_000, 999.0).none { it is EngineEvent.SegmentEnded })
        val events = engine.update(301_000, 1001.0)
        assertEquals("Walk", (events.last() as EngineEvent.SegmentStarted).segment.step.label)
    }

    @Test
    fun repeatUntilDistanceStopsMidStepAtTheCap() {
        // Run 10 / walk 1 until 4 km, between 5 min walks, at 2 m/s (1.2 km per 10 min run).
        val w = Workout(
            listOf(
                Step(StepType.WALK, End.Time(300), name = "Warm-up walk"),
                RepeatUntilDistance(4000.0, listOf(timed(StepType.RUN, 600), timed(StepType.WALK, 60))),
                Step(StepType.WALK, End.Time(300), name = "Cool-down walk"),
            ),
        )
        val engine = IntervalEngine(w)
        assertNull(engine.totalSegments)
        val events = simulate(engine, 60 * 60, 2.0)
        val labels = started(events)
        assertEquals("Warm-up walk", labels.first())
        assertEquals("Cool-down walk", labels.last())
        val capEnd = events.filterIsInstance<EngineEvent.SegmentEnded>().first { it.segment.step.label == "Cool-down walk" }
        assertTrue(capEnd.endMs > 0)
        // Block starts after the 5 min warm-up walk (600 m at 2 m/s) and must stop at 4 km inside the block.
        val blockEnded = events.filterIsInstance<EngineEvent.SegmentEnded>().last { it.segment.blockCapMeters != null }
        assertEquals(4600.0, blockEnded.endMeters, 2.0)
    }

    @Test
    fun repeatSkipsTrailingRecoveryOnFinalRound() {
        val w = Workout(listOf(Repeat(3, listOf(timed(StepType.FAST, 60), timed(StepType.RECOVER, 120)), skipLastOnFinal = 1)))
        val engine = IntervalEngine(w)
        assertEquals(5, engine.totalSegments)
    }

    @Test
    fun warnsTenSecondsBeforeATimedSegmentEnds() {
        val w = Workout(listOf(timed(StepType.JOG, 60), timed(StepType.WALK, 20)))
        val engine = IntervalEngine(w)
        engine.start(0, 0.0)
        assertTrue(engine.update(49_000, 0.0).isEmpty())
        assertTrue(engine.update(50_000, 0.0).single() is EngineEvent.Warning)
        assertTrue(engine.update(51_000, 0.0).isEmpty())
    }

    @Test
    fun skipMovesToNextSegment() {
        val w = Workout(listOf(timed(StepType.WALK, 300), timed(StepType.JOG, 60)))
        val engine = IntervalEngine(w)
        engine.start(0, 0.0)
        val events = engine.skip(10_000, 10.0)
        assertEquals("Jog", (events.last() as EngineEvent.SegmentStarted).segment.step.label)
    }
}

class PaceTest {
    @Test
    fun resolveTargets() {
        assertNull(Pace.resolve(PaceTarget.Goal(), null))
        assertEquals(PaceBand(407, 423), Pace.resolve(PaceTarget.Goal(), 415))
        assertEquals(PaceBand(417, 433), Pace.resolve(PaceTarget.Goal(10), 415))
        // Range edges entered the wrong way round still give fast..slow.
        assertEquals(PaceBand(495, 525), Pace.resolve(PaceTarget.Range(525, 495), null))
    }

    @Test
    fun coachWaitsThenFiresOncePerCooldown() {
        val coach = PaceCoach()
        val band = PaceBand(495, 525)
        coach.onSegmentStart(0)
        // Settling: no verdicts in the first 30 s.
        assertNull(coach.update(10_000, 400.0, band))
        assertNull(coach.update(30_000, 400.0, band))
        assertNull(coach.update(49_000, 400.0, band))
        assertEquals(PaceCoach.Verdict.TOO_FAST, coach.update(50_000, 400.0, band))
        // Still too fast but inside the 60 s cooldown.
        assertNull(coach.update(80_000, 400.0, band))
        assertEquals(PaceCoach.Verdict.TOO_FAST, coach.update(110_000, 400.0, band))
    }

    @Test
    fun coachResetsWhenBackInBand() {
        val coach = PaceCoach()
        val band = PaceBand(495, 525)
        coach.onSegmentStart(0)
        assertNull(coach.update(40_000, 600.0, band))
        assertNull(coach.update(55_000, 510.0, band))
        assertNull(coach.update(60_000, 600.0, band))
        assertEquals(PaceCoach.Verdict.TOO_SLOW, coach.update(80_000, 600.0, band))
    }
}