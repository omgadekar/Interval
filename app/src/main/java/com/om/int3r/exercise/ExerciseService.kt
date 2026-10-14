package com.om.int3r.exercise

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeCapabilities
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.health.services.client.data.WarmUpConfig
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.om.int3r.MainActivity
import com.om.int3r.Permissions
import com.om.int3r.R
import com.om.int3r.data.LibraryStore
import com.om.int3r.data.Prefs
import com.om.int3r.data.RunStore
import com.om.int3r.engine.EngineEvent
import com.om.int3r.engine.IntervalEngine
import com.om.int3r.engine.PaceCoach
import com.om.int3r.engine.SpeedSmoother
import com.om.int3r.record.Fix
import com.om.int3r.record.GpxWriter
import com.om.int3r.record.LapRecord
import com.om.int3r.record.RunStreams
import com.om.int3r.record.TcxWriter
import com.om.int3r.record.TimedValue
import com.om.int3r.record.Track
import com.om.int3r.tile.TodayTileService
import com.om.int3r.workout.End
import com.om.int3r.workout.Pace
import com.om.int3r.workout.PaceBand
import com.om.int3r.workout.SavedWorkout
import com.om.int3r.workout.Step
import com.om.int3r.workout.StepType
import com.om.int3r.workout.formatDistance
import com.om.int3r.workout.formatDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Runs one workout: Health Services records the exercise, a 1 s ticker drives the interval engine
 * and cues, and a partial wake lock keeps the ticker on time while the screen is off.
 * The foreground notification is promoted to an Ongoing Activity so it shows on the watch face.
 */
class ExerciseService : LifecycleService() {

    companion object {
        private const val TAG = "ExerciseService"
        const val ACTION_PREPARE = "com.om.int3r.PREPARE"
        const val ACTION_START = "com.om.int3r.START"
        const val ACTION_PAUSE = "com.om.int3r.PAUSE"
        const val ACTION_RESUME = "com.om.int3r.RESUME"
        const val ACTION_SKIP = "com.om.int3r.SKIP"
        const val ACTION_END = "com.om.int3r.END"
        const val ACTION_CANCEL = "com.om.int3r.CANCEL"
        const val EXTRA_WORKOUT = "workout"
        const val EXTRA_DATE = "date"

        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 1

        /** [date] is the calendar day the workout was planned on; it gets ticked when the run is saved. */
        fun send(context: Context, action: String, workoutId: String? = null, date: String? = null) {
            val intent = Intent(context, ExerciseService::class.java)
                .setAction(action)
                .putExtra(EXTRA_WORKOUT, workoutId)
                .putExtra(EXTRA_DATE, date)
            if (action == ACTION_PREPARE) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        private const val HR_TRAIL = 180

        private val WANTED: List<DataType<*, *>> = listOf(
            DataType.HEART_RATE_BPM,
            DataType.SPEED,
            DataType.DISTANCE_TOTAL,
            DataType.LOCATION,
            DataType.ABSOLUTE_ELEVATION,
            DataType.ELEVATION_GAIN_TOTAL,
            DataType.STEPS_PER_MINUTE,
            DataType.CALORIES_TOTAL,
        )
    }

    private lateinit var client: ExerciseClient
    private lateinit var cues: Cues
    private lateinit var store: RunStore
    private lateinit var prefs: Prefs

    private val state get() = RunSession.mutable

    private var saved: SavedWorkout? = null
    private var plannedDate: String? = null
    private val hrTrail = ArrayDeque<Int>()
    private val paceTrail = ArrayDeque<Float?>()
    private val cadenceTrail = ArrayDeque<Int>()
    private var capabilities: ExerciseTypeCapabilities? = null
    private var ongoing: OngoingActivity? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickJob: Job? = null
    private var endedSignal: CompletableDeferred<Unit>? = null
    private var callbackRegistered = false

    // Session data
    private var engine: IntervalEngine? = null
    private val coach = PaceCoach()
    private val smoother = SpeedSmoother()
    private var streams = RunStreams()
    private val laps = mutableListOf<LapRecord>()
    private var rawLog: BufferedWriter? = null
    private var sessionId = ""
    private var startEpochMs = 0L
    private var goalSecPerKm: Int? = null
    private var finishedAtActiveMs: Long? = null
    private var finishedAtMeters = 0.0
    private var lastVerdict: Pair<PaceCoach.Verdict, Long>? = null

    // Stopwatch for active (unpaused) time
    private var activeAccumMs = 0L
    private var runningSince: Long? = null

    // Distance between Health Services batches is extrapolated from speed
    private var lastDistance = 0.0
    private var lastDistanceAt = 0L
    private var lastSpeed = 0.0
    private var engineMeters = 0.0

    private fun <T> ArrayDeque<T>.push(value: T) {
        addLast(value)
        while (size > HR_TRAIL) removeFirst()
    }

    private val bootEpochMs: Long get() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    override fun onCreate() {
        super.onCreate()
        client = HealthServices.getClient(this).exerciseClient
        cues = Cues(this)
        store = RunStore(this)
        prefs = Prefs.get(this)
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.channel_name))
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PREPARE -> prepare(intent.getStringExtra(EXTRA_WORKOUT), intent.getStringExtra(EXTRA_DATE))
            ACTION_START -> lifecycleScope.launch { start() }
            ACTION_PAUSE -> lifecycleScope.launch { pause() }
            ACTION_RESUME -> lifecycleScope.launch { resume() }
            ACTION_SKIP -> skip()
            ACTION_END -> lifecycleScope.launch { end() }
            ACTION_CANCEL -> lifecycleScope.launch { cancel() }
            else -> if (state.value.phase == Phase.IDLE) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---- Lifecycle of a session --------------------------------------------------------------

    private fun prepare(workoutId: String?, date: String?) {
        if (state.value.isRecording) {
            goForeground("Workout in progress")
            return
        }
        val workout = LibraryStore.get(this).workout(workoutId) ?: run {
            stopSelf()
            return
        }
        saved = workout
        plannedDate = date
        state.value = RunUiState(phase = Phase.PREPARING, workoutId = workout.id, date = date, title = workout.name)
        goForeground("Getting ready")
        registerCallback()
        lifecycleScope.launch {
            try {
                val caps = client.getCapabilitiesAsync().await().getExerciseTypeCapabilities(ExerciseType.RUNNING)
                capabilities = caps
                val warmUpTypes = setOf<DeltaDataType<*, *>>(DataType.HEART_RATE_BPM, DataType.LOCATION)
                    .filter { it in caps.supportedDataTypes && permitted(it) }
                    .toSet()
                client.prepareExerciseAsync(WarmUpConfig(ExerciseType.RUNNING, warmUpTypes)).await()
            } catch (e: Exception) {
                Log.w(TAG, "prepare failed", e)
                state.value = state.value.copy(error = "Sensors not ready: ${e.message}")
            }
        }
    }

    private suspend fun start() {
        val plan = saved ?: return
        if (state.value.phase != Phase.PREPARING) return
        val supported = capabilities?.supportedDataTypes
        val types = WANTED.filter { (supported == null || it in supported) && permitted(it) }.toSet()
        val config = ExerciseConfig.builder(ExerciseType.RUNNING)
            .setDataTypes(types)
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(DataType.LOCATION in types)
            .build()
        try {
            client.startExerciseAsync(config).await()
        } catch (e: Exception) {
            Log.w(TAG, "start failed", e)
            state.value = state.value.copy(error = "Could not start: ${e.message}")
            return
        }

        val settings = prefs.settings.value
        goalSecPerKm = settings.goalSecPerKm
        cues.sound = settings.sound
        streams = RunStreams()
        laps.clear()
        smoother.clear()
        hrTrail.clear()
        paceTrail.clear()
        cadenceTrail.clear()
        finishedAtActiveMs = null
        lastVerdict = null
        activeAccumMs = 0
        runningSince = SystemClock.elapsedRealtime()
        lastDistance = 0.0
        lastDistanceAt = SystemClock.elapsedRealtime()
        lastSpeed = 0.0
        engineMeters = 0.0
        startEpochMs = System.currentTimeMillis()
        sessionId = buildString {
            val started = Instant.ofEpochMilli(startEpochMs).atZone(ZoneId.systemDefault())
            append(started.toLocalDate())
            append('_').append(plan.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "run" }.take(24))
            append('_').append(DateTimeFormatter.ofPattern("HHmm").format(started))
        }
        rawLog = runCatching { store.openRawLog(sessionId) }.getOrNull()

        acquireWakeLock()
        val eng = IntervalEngine(plan.workout)
        engine = eng
        state.value = state.value.copy(phase = Phase.ACTIVE, totalSegments = eng.totalSegments, error = null)
        handle(eng.start(0, 0.0), 0, 0.0)

        tickJob = lifecycleScope.launch {
            while (isActive) {
                tick()
                // Align to the next whole second of active time.
                delay(1000 - activeMs() % 1000)
            }
        }
    }

    private suspend fun pause() {
        if (state.value.phase != Phase.ACTIVE) return
        runCatching { client.pauseExerciseAsync().await() }
        runningSince?.let { activeAccumMs += SystemClock.elapsedRealtime() - it }
        runningSince = null
        smoother.clear()
        state.value = state.value.copy(phase = Phase.PAUSED, paceSecPerKm = null)
        updateStatus("Paused")
    }

    private suspend fun resume() {
        if (state.value.phase != Phase.PAUSED) return
        runCatching { client.resumeExerciseAsync().await() }
        runningSince = SystemClock.elapsedRealtime()
        lastDistanceAt = SystemClock.elapsedRealtime()
        state.value = state.value.copy(phase = Phase.ACTIVE)
        coach.onSegmentStart(activeMs())
    }

    private fun skip() {
        val eng = engine ?: return
        if (state.value.phase != Phase.ACTIVE) return
        val now = activeMs()
        handle(eng.skip(now, engineMeters), now, engineMeters)
        publish(now)
    }

    private suspend fun end() {
        val phase = state.value.phase
        if (phase == Phase.PREPARING) return cancel()
        if (phase != Phase.ACTIVE && phase != Phase.PAUSED) return
        tickJob?.cancel()
        val now = activeMs()
        runningSince?.let { activeAccumMs += SystemClock.elapsedRealtime() - it }
        runningSince = null
        state.value = state.value.copy(phase = Phase.ENDING)
        updateStatus("Saving")

        val signal = CompletableDeferred<Unit>().also { endedSignal = it }
        runCatching { client.endExerciseAsync().await() }.onFailure { Log.w(TAG, "end failed", it) }
        // The final update carries the last batch of samples and the totals.
        withTimeoutOrNull(5_000) { signal.await() }

        // Prefer Health Services' own total; fall back to the extrapolated one if it never reported.
        val meters = if (lastDistance > 0) lastDistance else engineMeters
        closeLaps(now, meters)
        val summaryId = runCatching { save(now, meters) }.onFailure { Log.e(TAG, "save failed", it) }.getOrNull()
        if (summaryId != null) {
            val date = plannedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date != null) {
                prefs.setDone(date, true)
                TodayTileService.refresh(this)
            }
        }
        cues.finished()
        state.value = state.value.copy(
            phase = Phase.ENDED,
            summaryId = summaryId,
            error = if (summaryId == null) "Run could not be saved" else null,
        )
        shutdown()
    }

    private suspend fun cancel() {
        if (state.value.isRecording) return
        runCatching { client.endExerciseAsync().await() }
        state.value = RunUiState()
        shutdown()
    }

    private fun shutdown() {
        tickJob?.cancel()
        if (callbackRegistered) {
            client.clearUpdateCallbackAsync(callback)
            callbackRegistered = false
        }
        runCatching { rawLog?.close() }
        rawLog = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ongoing = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        cues.release()
        super.onDestroy()
    }

    // ---- Ticking -----------------------------------------------------------------------------

    private fun activeMs(): Long = activeAccumMs + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0)

    private fun currentMeters(): Double {
        if (runningSince == null) return engineMeters
        val gapSec = min(SystemClock.elapsedRealtime() - lastDistanceAt, 15_000L) / 1000.0
        engineMeters = max(engineMeters, lastDistance + lastSpeed * gapSec)
        return engineMeters
    }

    private fun tick() {
        val eng = engine ?: return
        if (state.value.phase != Phase.ACTIVE) return
        val now = activeMs()
        val meters = currentMeters()
        val events = eng.update(now, meters)
        handle(events, now, meters)

        val seg = eng.current
        val band = seg?.step?.let(::coachedBand)
        // Skip coaching on a tick that already buzzed, so a pace alert never cancels a segment or warning cue.
        if (prefs.settings.value.paceAlerts && events.isEmpty()) {
            coach.update(now, smoother.paceSecPerKm(), band)?.let {
                cues.pace(it)
                lastVerdict = it to now
            }
        }
        publish(now)
    }

    /** No coaching on walks, recoveries or strides: only the steps that carry a pace target. */
    private fun coachedBand(step: Step): PaceBand? =
        if (!step.type.isWork || step.type == StepType.STRIDE) null else Pace.resolve(step.target, goalSecPerKm)

    private fun handle(events: List<EngineEvent>, nowMs: Long, meters: Double) {
        for (e in events) when (e) {
            is EngineEvent.SegmentStarted -> {
                if (e.segment.step.type.isWork) cues.work() else cues.ease()
                coach.onSegmentStart(nowMs)
                updateStatus(e.segment.step.label)
            }
            is EngineEvent.SegmentEnded -> {
                addLap(e)
                client.markLapAsync()
            }
            is EngineEvent.Warning -> cues.warning()
            EngineEvent.Finished -> {
                finishedAtActiveMs = nowMs
                finishedAtMeters = meters
                cues.finished()
                updateStatus("Workout complete")
            }
        }
    }

    private fun epochForActive(activeMs: Long): Long = System.currentTimeMillis() - (activeMs() - activeMs)

    private fun addLap(e: EngineEvent.SegmentEnded) {
        val band = coachedBand(e.segment.step)
        laps += LapRecord(
            number = laps.size + 1,
            label = e.segment.step.label,
            isWork = e.segment.step.type.isWork,
            startMs = epochForActive(e.startMs),
            endMs = epochForActive(e.endMs),
            activeMs = e.endMs - e.startMs,
            meters = (e.endMeters - e.startMeters).coerceAtLeast(0.0),
            targetMinSecPerKm = band?.minSecPerKm,
            targetMaxSecPerKm = band?.maxSecPerKm,
        )
    }

    /** Closes the segment in progress, or adds an "After workout" lap for recording past the plan. */
    private fun closeLaps(nowMs: Long, meters: Double) {
        engine?.close(nowMs, meters)?.let(::addLap)
        val finishedAt = finishedAtActiveMs
        if (finishedAt != null && nowMs - finishedAt > 5_000) {
            laps += LapRecord(
                number = laps.size + 1,
                label = "After workout",
                isWork = false,
                startMs = epochForActive(finishedAt),
                endMs = epochForActive(nowMs),
                activeMs = nowMs - finishedAt,
                meters = (meters - finishedAtMeters).coerceAtLeast(0.0),
            )
        }
    }

    private fun save(activeMs: Long, meters: Double): String {
        val plan = saved!!
        val endMs = System.currentTimeMillis()
        val laps = Track.withStreamDistances(laps, streams.distance)
        val summary = Track.summarize(
            id = sessionId,
            workoutId = plan.id,
            date = plannedDate,
            title = plan.name,
            startMs = startEpochMs,
            endMs = endMs,
            activeMs = activeMs,
            meters = meters,
            streams = streams,
            laps = laps,
        )
        val points = Track.build(streams)
        store.save(
            summary,
            TcxWriter.write(summary, laps, points),
            if (streams.fixes.isNotEmpty()) GpxWriter.write(summary, points) else null,
        )
        return summary.id
    }

    private fun publish(nowMs: Long) {
        val eng = engine ?: return
        val snap = eng.snapshot(nowMs, engineMeters)
        val seg = snap.segment
        val pace = smoother.paceSecPerKm()
        val meters = max(lastDistance, engineMeters)
        val band = seg?.step?.let(::coachedBand)
        state.value.heartRate?.let { hrTrail.push(it) }
        paceTrail.push(pace?.toFloat())
        state.value.cadence?.let { cadenceTrail.push(it) }
        val progress = when (val end = seg?.step?.end) {
            is End.Time -> snap.elapsedInSegmentMs / (end.seconds * 1000f)
            is End.Distance -> 1f - ((snap.remainingMeters ?: end.meters) / end.meters).toFloat()
            null -> if (eng.finished) 1f else 0f
        }.coerceIn(0f, 1f)
        state.value = state.value.copy(
            segmentProgress = progress,
            paceStatus = when {
                band == null || pace == null -> PaceStatus.NONE
                pace < band.minSecPerKm -> PaceStatus.FAST
                pace > band.maxSecPerKm -> PaceStatus.SLOW
                else -> PaceStatus.ON
            },
            hrTrail = hrTrail.toList(),
            paceTrail = paceTrail.toList(),
            cadenceTrail = cadenceTrail.toList(),
            activeMs = nowMs,
            meters = meters,
            paceSecPerKm = pace,
            avgPaceSecPerKm = com.om.int3r.record.paceOf(nowMs, meters),
            segmentLabel = seg?.step?.label,
            segmentType = seg?.step?.type,
            round = seg?.round ?: 0,
            rounds = seg?.rounds,
            segmentNumber = seg?.number ?: 0,
            remainingMs = snap.remainingMs,
            remainingMeters = snap.remainingMeters,
            blockRemainingMeters = snap.blockRemainingMeters,
            nextLabel = snap.next?.let { describe(it) },
            band = band,
            verdict = lastVerdict?.takeIf { nowMs - it.second < 10_000 }?.first,
            workoutComplete = eng.finished,
        )
    }

    private fun describe(step: Step): String = when (val end = step.end) {
        is End.Time -> "${step.label} ${formatDuration(end.seconds.toLong())}"
        is End.Distance -> "${step.label} ${formatDistance(end.meters)}"
    }

    // ---- Health Services ---------------------------------------------------------------------

    private fun permitted(type: DataType<*, *>): Boolean {
        val perm = when (type) {
            DataType.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            DataType.HEART_RATE_BPM -> Permissions.HEART_RATE
            else -> Manifest.permission.ACTIVITY_RECOGNITION
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerCallback() {
        if (callbackRegistered) return
        client.setUpdateCallback(callback)
        callbackRegistered = true
    }

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) = onUpdate(update)
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "callback registration failed", throwable)
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            when {
                dataType == DataType.LOCATION && availability is LocationAvailability -> state.value = state.value.copy(
                    gpsReady = availability == LocationAvailability.ACQUIRED_TETHERED ||
                        availability == LocationAvailability.ACQUIRED_UNTETHERED,
                )
                dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability -> state.value = state.value.copy(
                    heartRateReady = availability == DataTypeAvailability.AVAILABLE,
                )
            }
        }
    }

    private fun log(stream: String, t: Long, vararg values: Any?) {
        val w = rawLog ?: return
        runCatching {
            w.write(stream)
            w.write(",")
            w.write(t.toString())
            for (v in values) {
                w.write(",")
                w.write(v?.toString() ?: "")
            }
            w.write("\n")
        }
    }

    private fun onUpdate(update: ExerciseUpdate) {
        val m = update.latestMetrics
        val boot = bootEpochMs
        val recording = state.value.isRecording
        var hr: Int? = null
        var altitude: Double? = null
        var cadence: Int? = null

        m.getData(DataType.HEART_RATE_BPM).forEach { dp ->
            val t = boot + dp.timeDurationFromBoot.toMillis()
            if (dp.value > 0) {
                hr = dp.value.toInt()
                if (recording) {
                    streams.heartRate += TimedValue(t, dp.value)
                    log("hr", t, dp.value.toInt())
                }
            }
        }
        if (recording) {
            m.getData(DataType.SPEED).forEach { dp ->
                val t = boot + dp.timeDurationFromBoot.toMillis()
                streams.speed += TimedValue(t, dp.value)
                smoother.add(t, dp.value)
                lastSpeed = dp.value
                log("speed", t, dp.value)
            }
            m.getData(DataType.LOCATION).forEach { dp ->
                val t = boot + dp.timeDurationFromBoot.toMillis()
                val loc = dp.value
                val alt = Track.saneAltitude(loc.altitude)
                streams.fixes += Fix(t, loc.latitude, loc.longitude, alt)
                if (alt != null) altitude = alt
                log("loc", t, loc.latitude, loc.longitude, alt)
            }
            m.getData(DataType.ABSOLUTE_ELEVATION).forEach { dp ->
                if (Track.saneAltitude(dp.value) == null) return@forEach
                val t = boot + dp.timeDurationFromBoot.toMillis()
                streams.elevation += TimedValue(t, dp.value)
                altitude = dp.value
                log("ele", t, dp.value)
            }
            m.getData(DataType.STEPS_PER_MINUTE).forEach { dp ->
                val t = boot + dp.timeDurationFromBoot.toMillis()
                streams.cadence += TimedValue(t, dp.value.toDouble())
                cadence = dp.value.toInt()
                log("cad", t, dp.value)
            }
            m.getData(DataType.DISTANCE_TOTAL)?.let { cum ->
                val t = cum.end.toEpochMilli()
                streams.distance += TimedValue(t, cum.total)
                if (cum.total >= lastDistance) {
                    lastDistance = cum.total
                    lastDistanceAt = SystemClock.elapsedRealtime()
                }
                log("dist", t, cum.total)
            }
            m.getData(DataType.ELEVATION_GAIN_TOTAL)?.let { streams.elevationGain = it.total }
            m.getData(DataType.CALORIES_TOTAL)?.let { streams.calories = it.total }
            runCatching { rawLog?.flush() }
        }

        state.value = state.value.copy(
            heartRate = hr ?: state.value.heartRate,
            altitude = altitude ?: state.value.altitude,
            cadence = cadence ?: state.value.cadence,
            elevationGain = streams.elevationGain ?: state.value.elevationGain,
            calories = streams.calories ?: state.value.calories,
            meters = max(state.value.meters, lastDistance),
        )

        val exState = update.exerciseStateInfo.state
        if (exState == ExerciseState.ENDED) {
            endedSignal?.complete(Unit)
            // Ended by someone else (another workout app took over): save what we have.
            if (state.value.phase == Phase.ACTIVE || state.value.phase == Phase.PAUSED) {
                lifecycleScope.launch { end() }
            }
        }
    }

    // ---- Foreground + ongoing activity -------------------------------------------------------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun goForeground(text: String) {
        val title = saved?.name ?: getString(R.string.app_name)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_int3r)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openAppIntent())
        val activity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_int3r)
            .setTouchIntent(openAppIntent())
            .setStatus(status(text))
            .build()
        activity.apply(applicationContext)
        ongoing = activity
        var types = 0
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 34) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), types)
    }

    private fun status(text: String): Status =
        Status.Builder().addTemplate("#label#").addPart("label", Status.TextPart(text)).build()

    private fun updateStatus(text: String) {
        ongoing?.update(applicationContext, status(text))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "INT3R:workout")
            .apply { acquire(4 * 60 * 60 * 1000L) }
    }
}