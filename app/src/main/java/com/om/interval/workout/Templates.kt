package com.om.interval.workout

/** Starting points offered when you create a workout. Generic shapes, edited freely afterwards. */
object Templates {
    private fun min(m: Int) = End.Time(m * 60)
    private fun sec(s: Int) = End.Time(s)
    private fun km(k: Double) = End.Distance(k * 1000)
    private fun walk(m: Int, name: String? = null) = Step(StepType.WALK, min(m), name = name)
    private fun warm() = walk(5, "Warm-up walk")
    private fun cool() = walk(5, "Cool-down walk")

    val ALL: List<Pair<String, Workout>> = listOf(
        "Blank" to Workout(),
        "Run / walk" to Workout(
            listOf(warm(), Repeat(6, listOf(Step(StepType.JOG, min(3)), walk(2))), cool()),
        ),
        "Easy run" to Workout(
            listOf(warm(), Step(StepType.EASY, km(5.0)), cool()),
        ),
        "Intervals" to Workout(
            listOf(
                Step(StepType.EASY, km(1.0), name = "Easy 1 km"),
                Repeat(6, listOf(Step(StepType.FAST, min(1)), Step(StepType.RECOVER, min(2))), skipLastOnFinal = 1),
                Step(StepType.EASY, km(1.0), name = "Easy 1 km"),
            ),
        ),
        "Tempo" to Workout(
            listOf(
                Step(StepType.EASY, min(10), name = "Warm-up"),
                Step(StepType.TEMPO, min(20), PaceTarget.Goal(-10)),
                Step(StepType.EASY, min(10), name = "Cool-down"),
            ),
        ),
        "Run to distance" to Workout(
            listOf(warm(), RepeatUntilDistance(4000.0, listOf(Step(StepType.RUN, min(10)), walk(1))), cool()),
        ),
        "Strides" to Workout(
            listOf(
                Step(StepType.EASY, km(3.0)),
                Repeat(4, listOf(Step(StepType.STRIDE, sec(20)), Step(StepType.WALK, sec(60), name = "Walk back")), skipLastOnFinal = 1),
            ),
        ),
        "Quick test" to Workout(
            listOf(Repeat(3, listOf(Step(StepType.JOG, sec(60)), Step(StepType.WALK, sec(30))))),
        ),
    )
}