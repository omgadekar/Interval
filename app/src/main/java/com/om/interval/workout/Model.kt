package com.om.interval.workout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a segment asks you to do. [isWork] drives cue patterns and coaching;
 * [intensity] (0..1) is only for drawing workout shapes.
 */
@Serializable
enum class StepType(val label: String, val isWork: Boolean, val intensity: Float, val typicalSecPerKm: Int) {
    WALK("Walk", false, 0.22f, 720),
    RECOVER("Recover", false, 0.38f, 540),
    JOG("Jog", true, 0.52f, 480),
    EASY("Easy", true, 0.6f, 450),
    RUN("Run", true, 0.7f, 420),
    TEMPO("Tempo", true, 0.84f, 360),
    FAST("Fast", true, 0.94f, 330),
    STRIDE("Stride", true, 1f, 300),
}

@Serializable
sealed interface End {
    @Serializable
    @SerialName("time")
    data class Time(val seconds: Int) : End

    @Serializable
    @SerialName("distance")
    data class Distance(val meters: Double) : End
}

/** Pace targets in seconds per km. */
@Serializable
sealed interface PaceTarget {
    /** A fixed band; [fastSecPerKm] is the quicker edge. */
    @Serializable
    @SerialName("range")
    data class Range(val fastSecPerKm: Int, val slowSecPerKm: Int) : PaceTarget

    /** Your goal pace from Settings, shifted by [offsetSec] (positive = slower). */
    @Serializable
    @SerialName("goal")
    data class Goal(val offsetSec: Int = 0) : PaceTarget
}

@Serializable
sealed interface Block

@Serializable
@SerialName("step")
data class Step(
    val type: StepType,
    val end: End,
    val target: PaceTarget? = null,
    /** Optional custom name; otherwise the type's label is shown. */
    val name: String? = null,
) : Block {
    val label: String get() = name ?: type.label
}

/** [times] rounds of [steps]; the last [skipLastOnFinal] steps are dropped on the final round ("recover between"). */
@Serializable
@SerialName("repeat")
data class Repeat(val times: Int, val steps: List<Step>, val skipLastOnFinal: Int = 0) : Block

/** Cycle through [steps] until [meters] have been covered inside this block. */
@Serializable
@SerialName("repeatUntil")
data class RepeatUntilDistance(val meters: Double, val steps: List<Step>) : Block

@Serializable
data class Workout(val blocks: List<Block> = emptyList())

@Serializable
data class SavedWorkout(
    val id: String,
    val name: String,
    val workout: Workout,
    val createdMs: Long = 0,
)