package com.om.int3r.exercise

import com.om.int3r.engine.PaceCoach
import com.om.int3r.record.Trail
import com.om.int3r.workout.PaceBand
import com.om.int3r.workout.StepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, PREPARING, ACTIVE, PAUSED, ENDING, ENDED }

/** Where the current pace sits against the segment's target; tints the run screen. */
enum class PaceStatus { NONE, ON, FAST, SLOW }

/** Everything the run screens show. Published by [ExerciseService] once a second and on sensor updates. */
data class RunUiState(
    val phase: Phase = Phase.IDLE,
    val workoutId: String? = null,
    val date: String? = null,
    val title: String = "",
    val gpsReady: Boolean = false,
    val heartRateReady: Boolean = false,
    val heartRate: Int? = null,
    val activeMs: Long = 0,
    val meters: Double = 0.0,
    val paceSecPerKm: Double? = null,
    val avgPaceSecPerKm: Int? = null,
    val altitude: Double? = null,
    val elevationGain: Double? = null,
    val cadence: Int? = null,
    val calories: Double? = null,
    val segmentLabel: String? = null,
    val segmentType: StepType? = null,
    val round: Int = 0,
    val rounds: Int? = null,
    val segmentNumber: Int = 0,
    val totalSegments: Int? = null,
    val remainingMs: Long? = null,
    val remainingMeters: Double? = null,
    val blockRemainingMeters: Double? = null,
    val nextLabel: String? = null,
    val band: PaceBand? = null,
    val verdict: PaceCoach.Verdict? = null,
    val workoutComplete: Boolean = false,
    /** 0..1 through the current segment. */
    val segmentProgress: Float = 0f,
    val paceStatus: PaceStatus = PaceStatus.NONE,
    /** Live graphs, one point per second built from timestamped readings. */
    val hrTrail: Trail = Trail(),
    /** Pace in s/km; null while standing. */
    val paceTrail: Trail = Trail(),
    val cadenceTrail: Trail = Trail(),
    val summaryId: String? = null,
    val error: String? = null,
) {
    val isRecording: Boolean get() = phase == Phase.ACTIVE || phase == Phase.PAUSED || phase == Phase.ENDING
}

object RunSession {
    internal val mutable = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = mutable.asStateFlow()

    /** Called by the UI after it has shown the summary. */
    fun reset() {
        if (!mutable.value.isRecording && mutable.value.phase != Phase.PREPARING) mutable.value = RunUiState()
    }
}