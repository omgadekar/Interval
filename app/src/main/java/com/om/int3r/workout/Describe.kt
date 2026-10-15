package com.om.int3r.workout

/** Human-readable lines for a workout, e.g. "6 x Run 3:00, Walk 1:00 between". */
object Describe {
    fun end(end: End): String = when (end) {
        is End.Time -> formatDuration(end.seconds.toLong())
        is End.Distance -> formatDistance(end.meters)
    }

    fun target(target: PaceTarget?, goalSecPerKm: Int?): String? = when (target) {
        null -> null
        is PaceTarget.Range -> "@ ${Pace.resolve(target, null)}"
        is PaceTarget.Goal -> when {
            goalSecPerKm != null -> "@ ${formatPace(goalSecPerKm + target.offsetSec)}"
            target.offsetSec > 0 -> "@ goal +${target.offsetSec}s"
            target.offsetSec < 0 -> "@ goal ${target.offsetSec}s"
            else -> "@ goal pace"
        }
    }

    fun step(step: Step, goalSecPerKm: Int?): String =
        listOfNotNull(step.label, end(step.end), target(step.target, goalSecPerKm)).joinToString(" ")

    fun block(block: Block, goalSecPerKm: Int?): String = when (block) {
        is Step -> step(block, goalSecPerKm)
        is Repeat -> {
            val work = block.steps.dropLast(block.skipLastOnFinal).joinToString(" + ") { step(it, goalSecPerKm) }
            val between = block.steps.takeLast(block.skipLastOnFinal).joinToString(" + ") { step(it, goalSecPerKm) }
            if (between.isEmpty()) "${block.times} x $work" else "${block.times} x $work, $between between"
        }
        is RepeatUntilDistance ->
            "To ${formatDistance(block.meters)}: " + block.steps.joinToString(" + ") { step(it, goalSecPerKm) }
    }

    fun lines(workout: Workout, goalSecPerKm: Int?): List<String> = workout.blocks.map { block(it, goalSecPerKm) }

    /** One-line summary for lists: "≈ 42 min · 6 x Run 3:00 ...". */
    fun summary(workout: Workout, goalSecPerKm: Int?): String {
        if (workout.blocks.isEmpty()) return "Empty"
        val minutes = Math.round(Timeline.totalSeconds(workout, goalSecPerKm) / 60.0)
        // Describe the hardest part (the reps, the tempo), not the warm-up.
        val main = workout.blocks.maxByOrNull { block ->
            when (block) {
                is Step -> block.type.intensity
                is Repeat -> block.steps.maxOf { it.type.intensity } + 0.01f
                is RepeatUntilDistance -> block.steps.maxOf { it.type.intensity } + 0.01f
            }
        } ?: workout.blocks.first()
        return "≈ $minutes min · ${block(main, goalSecPerKm)}"
    }
}