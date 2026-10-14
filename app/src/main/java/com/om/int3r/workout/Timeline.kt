package com.om.int3r.workout

/** One bar of a workout's shape: what kind of effort and roughly how long. */
data class Bar(val type: StepType, val seconds: Double)

/**
 * Estimates how a workout will play out, for drawing its shape and showing "about 40 min".
 * Distance steps use the target pace, or a typical pace for the step type.
 */
object Timeline {
    private const val MAX_OPEN_ROUNDS = 50

    fun paceFor(step: Step, goalSecPerKm: Int?): Int =
        Pace.resolve(step.target, goalSecPerKm)?.let { (it.minSecPerKm + it.maxSecPerKm) / 2 } ?: step.type.typicalSecPerKm

    fun seconds(step: Step, goalSecPerKm: Int?): Double = when (val end = step.end) {
        is End.Time -> end.seconds.toDouble()
        is End.Distance -> end.meters / 1000.0 * paceFor(step, goalSecPerKm)
    }

    fun meters(step: Step, goalSecPerKm: Int?): Double = when (val end = step.end) {
        is End.Distance -> end.meters
        is End.Time -> end.seconds * 1000.0 / paceFor(step, goalSecPerKm)
    }

    fun bars(w: Workout, goalSecPerKm: Int? = null): List<Bar> = w.blocks.flatMap { block ->
        when (block) {
            is Step -> listOf(Bar(block.type, seconds(block, goalSecPerKm)))
            is Repeat -> (0 until block.times).flatMap { round ->
                val steps = if (round == block.times - 1) block.steps.dropLast(block.skipLastOnFinal) else block.steps
                steps.map { Bar(it.type, seconds(it, goalSecPerKm)) }
            }
            is RepeatUntilDistance -> buildList {
                var covered = 0.0
                var rounds = 0
                while (covered < block.meters && rounds < MAX_OPEN_ROUNDS && block.steps.isNotEmpty()) {
                    for (s in block.steps) {
                        if (covered >= block.meters) break
                        val m = meters(s, goalSecPerKm)
                        val fraction = ((block.meters - covered) / m).coerceAtMost(1.0)
                        add(Bar(s.type, seconds(s, goalSecPerKm) * fraction))
                        covered += m * fraction
                    }
                    rounds++
                }
            }
        }
    }

    fun totalSeconds(w: Workout, goalSecPerKm: Int? = null): Double = bars(w, goalSecPerKm).sumOf { it.seconds }

    fun totalMeters(w: Workout, goalSecPerKm: Int? = null): Double = w.blocks.sumOf { block ->
        when (block) {
            is Step -> meters(block, goalSecPerKm)
            is Repeat -> (0 until block.times).sumOf { round ->
                val steps = if (round == block.times - 1) block.steps.dropLast(block.skipLastOnFinal) else block.steps
                steps.sumOf { meters(it, goalSecPerKm) }
            }
            is RepeatUntilDistance -> block.meters
        }
    }
}