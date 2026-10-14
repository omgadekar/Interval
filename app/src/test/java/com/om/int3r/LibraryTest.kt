package com.om.int3r

import com.om.int3r.workout.Edit
import com.om.int3r.workout.End
import com.om.int3r.workout.Library
import com.om.int3r.workout.LibraryJson
import com.om.int3r.workout.PaceTarget
import com.om.int3r.workout.Repeat
import com.om.int3r.workout.RepeatUntilDistance
import com.om.int3r.workout.SavedWorkout
import com.om.int3r.workout.Step
import com.om.int3r.workout.StepPath
import com.om.int3r.workout.StepType
import com.om.int3r.workout.Templates
import com.om.int3r.workout.Timeline
import com.om.int3r.workout.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LibraryTest {
    private val intervals = Workout(
        listOf(
            Step(StepType.WALK, End.Time(300), name = "Warm-up"),
            Repeat(6, listOf(Step(StepType.FAST, End.Time(60), PaceTarget.Range(390, 420)), Step(StepType.RECOVER, End.Time(120))), skipLastOnFinal = 1),
            RepeatUntilDistance(2000.0, listOf(Step(StepType.RUN, End.Distance(500.0), PaceTarget.Goal(10)))),
        ),
    )
    private val day = LocalDate.of(2026, 10, 20)

    @Test
    fun jsonRoundTripKeepsEverything() {
        val lib = Library().upsert(SavedWorkout("a", "Intervals", intervals, 1)).plan(day, "a")
        val text = LibraryJson.encode(lib)
        assertEquals(lib, LibraryJson.decode(text))
        // Steps carry a "type" field, so blocks are tagged with "kind".
        assertTrue(text.contains("\"kind\":\"repeat\""))
    }

    @Test
    fun brokenJsonGivesAnEmptyLibrary() {
        assertEquals(Library(), LibraryJson.decode("{not json"))
    }

    @Test
    fun deletingAWorkoutClearsItsDays() {
        val lib = Library().upsert(SavedWorkout("a", "A", intervals)).plan(day, "a").plan(day.plusDays(2), "a")
        val after = lib.delete("a")
        assertTrue(after.workouts.isEmpty())
        assertTrue(after.schedule.isEmpty())
    }

    @Test
    fun planningAndClearingADay() {
        val lib = Library().upsert(SavedWorkout("a", "A", intervals)).plan(day, "a")
        assertEquals("A", lib.scheduledOn(day)?.name)
        assertNull(lib.plan(day, null).scheduledOn(day))
    }

    @Test
    fun duplicateSitsNextToTheOriginal() {
        val lib = Library().upsert(SavedWorkout("a", "A", intervals)).upsert(SavedWorkout("b", "B", Workout()))
        val after = lib.duplicate("a", newId = "c")
        assertEquals(listOf("a", "c", "b"), after.workouts.map { it.id })
        assertEquals("A copy", after.workout("c")?.name)
    }

    @Test
    fun editStepsInsideARepeat() {
        val path = StepPath(1, 1)
        val edited = Edit.replaceStep(intervals, path, Step(StepType.WALK, End.Time(90)))
        assertEquals(StepType.WALK, Edit.stepAt(edited, path)?.type)
        val moved = Edit.moveStep(intervals, StepPath(1, 1), -1)
        assertEquals(StepType.RECOVER, Edit.stepAt(moved, StepPath(1, 0))?.type)
        val added = Edit.addInner(intervals, 1, Step(StepType.JOG, End.Time(30)))
        assertEquals(3, Edit.innerSteps(added.blocks[1])!!.size)
    }

    @Test
    fun aRepeatKeepsItsLastStep() {
        val single = Edit.replace(intervals, 2, RepeatUntilDistance(2000.0, listOf(Step(StepType.RUN, End.Time(60)))))
        assertSame(single, Edit.removeStep(single, StepPath(2, 0)))
    }

    @Test
    fun removingRecoveryTrimsSkipLast() {
        val trimmed = Edit.removeStep(intervals, StepPath(1, 1))
        assertEquals(0, (trimmed.blocks[1] as Repeat).skipLastOnFinal)
    }

    @Test
    fun moveAndDuplicateTopLevelBlocks() {
        val moved = Edit.move(intervals, 0, 1)
        assertTrue(moved.blocks[1] is Step)
        assertEquals(4, Edit.duplicate(intervals, 0).blocks.size)
        // Moving past the ends does nothing.
        assertSame(intervals.blocks, Edit.move(intervals, 0, -1).blocks.let { if (it == intervals.blocks) intervals.blocks else it })
    }

    @Test
    fun timelineEstimatesDurationAndDistance() {
        // 5:00 walk + 6 x 1:00 + 5 x 2:00 recoveries + 2 km at goal+10 (no goal set -> typical run pace 7:00).
        val seconds = Timeline.totalSeconds(intervals)
        assertEquals(300.0 + 6 * 60 + 5 * 120 + 2 * 420, seconds, 0.5)
        val bars = Timeline.bars(intervals)
        assertEquals(1 + 11 + 4, bars.size)
    }

    @Test
    fun templatesAreUsableShapes() {
        Templates.ALL.filter { it.second.blocks.isNotEmpty() }.forEach { (name, w) ->
            assertTrue(name, Timeline.totalSeconds(w) > 0)
        }
    }
}