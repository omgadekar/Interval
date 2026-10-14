package com.om.int3r.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.int3r.data.LibraryStore
import com.om.int3r.data.Prefs
import com.om.int3r.engine.PaceCoach
import com.om.int3r.exercise.ExerciseService
import com.om.int3r.exercise.PaceStatus
import com.om.int3r.exercise.Phase
import com.om.int3r.exercise.RunSession
import com.om.int3r.exercise.RunUiState
import com.om.int3r.workout.Describe
import com.om.int3r.workout.Edit
import com.om.int3r.workout.PaceTarget
import com.om.int3r.workout.formatDistance
import com.om.int3r.workout.formatDuration
import com.om.int3r.workout.formatPace
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

/** A dot that breathes while waiting, and settles green when the sensor is ready. */
@Composable
private fun StatusDot(ready: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    val color by animateColorAsState(if (ready) Palette.Green else Palette.Amber, tween(500), label = "dot")
    Canvas(Modifier.size(12.dp)) { drawCircle(color, size.minDimension / 2 * if (ready) 1f else scale) }
}

@Composable
fun PreRunScreen(workoutId: String, date: LocalDate?, run: RunUiState, permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val library by LibraryStore.get(context).library.collectAsStateWithLifecycle()
    val settings by Prefs.get(context).settings.collectAsStateWithLifecycle()
    val saved = library.workout(workoutId) ?: return
    val usesGoal = saved.workout.blocks.any { b ->
        (Edit.innerSteps(b) ?: listOfNotNull(b as? com.om.int3r.workout.Step)).any { it.target is PaceTarget.Goal }
    }

    // Warm up the sensors once per visit; coming back to this screen after a run must not start another.
    var prepared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workoutId, permissionsGranted) {
        if (!prepared && permissionsGranted && RunSession.state.value.phase == Phase.IDLE) {
            ExerciseService.send(context, ExerciseService.ACTION_PREPARE, workoutId, date?.toString())
            prepared = true
        }
    }
    DisposableEffect(workoutId) {
        onDispose {
            if (RunSession.state.value.phase == Phase.PREPARING) ExerciseService.send(context, ExerciseService.ACTION_CANCEL)
        }
    }

    val hrReady = run.heartRateReady || run.heartRate != null
    ListScreen {
        item { Text(saved.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.appear(0)) }
        item { WorkoutShape(saved.workout, settings.goalSecPerKm, Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 14.dp, vertical = 4.dp)) }
        if (!permissionsGranted) {
            paragraph("Heart rate, location and activity access are needed to record.", Palette.Amber)
            item { Button(onClick = onRequestPermissions, label = { Text("Allow sensors") }, modifier = Modifier.fillMaxWidth()) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(1)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(run.gpsReady)
                        Text(" GPS", style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(hrReady)
                        Text(" ♥ ${run.heartRate ?: "--"}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Button(
                    onClick = { ExerciseService.send(context, ExerciseService.ACTION_START) },
                    colors = Buttons.go(),
                    enabled = run.phase == Phase.PREPARING,
                    label = {
                        Text(if (run.gpsReady) "Start" else "Start without GPS", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                    modifier = Modifier.fillMaxWidth().appear(2),
                )
            }
        }
        if (usesGoal && settings.goalSecPerKm == null) paragraph("Set a goal pace in Settings to coach goal-pace steps.", Palette.Amber)
        run.error?.let { paragraph(it, Palette.Amber) }
        Describe.lines(saved.workout, settings.goalSecPerKm).forEach { paragraph(it) }
    }
}

@Composable
fun RunScreen(run: RunUiState, ambient: Boolean) {
    if (ambient) {
        AmbientRun(run)
        return
    }
    val type = run.segmentType
    val tint by animateColorAsState(
        if (run.phase == Phase.ACTIVE) run.paceStatus.tint else Color.Transparent,
        tween(900),
        label = "tint",
    )
    // The tint breathes slowly (about 4.8 s per breath) so it reads as alive, never as a flash.
    val breath = rememberInfiniteTransition(label = "breath")
    val inhale by breath.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inhale",
    )
    val ringColor by animateColorAsState(
        when {
            run.workoutComplete -> Palette.Green
            run.phase == Phase.PAUSED -> Palette.Secondary
            else -> type?.color ?: Palette.Accent
        },
        tween(600),
        label = "ring",
    )
    // Controls sit one swipe to the right of the workout, as on Apple Watch.
    val pager = rememberPagerState(initialPage = 1, pageCount = { 5 })
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                if (tint.alpha > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = tint.alpha * 0.5f * inhale), Color.Transparent),
                            center = Offset(size.width / 2, size.height * 0.45f),
                            radius = size.minDimension * (0.5f + 0.18f * inhale),
                        ),
                    )
                }
            },
    ) {
        EdgeRing(run.segmentProgress, run.segmentNumber, ringColor, Modifier.fillMaxSize())
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ControlsPage(run)
                1 -> SegmentPage(run)
                2 -> LivePage(run)
                3 -> TotalsPage(run)
                else -> MusicPage(isCurrent = pager.currentPage == 4)
            }
        }
        HorizontalPageIndicator(pagerState = pager, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

/** One live metric: name and current value above a graph that scrolls with time. */
@Composable
private fun LiveRow(label: String, value: String, unit: String, color: Color, values: List<Float?>, chartHeight: androidx.compose.ui.unit.Dp, invert: Boolean = false, minSpan: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = 1.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 1.dp))
        }
        LiveChart(values, color, Modifier.fillMaxWidth().height(chartHeight).padding(top = 2.dp), invert = invert, minSpan = minSpan)
    }
}

@Composable
private fun LivePage(run: RunUiState) {
    val size = screenSize()
    val small = size == ScreenSize.SMALL
    val chart = when (size) {
        ScreenSize.SMALL -> 20.dp
        ScreenSize.REGULAR -> 28.dp
        ScreenSize.LARGE -> 34.dp
    }
    Column(
        // Round screens are narrowest at the top and bottom rows; inset more there on small watches.
        modifier = Modifier.fillMaxSize().padding(horizontal = if (small) 34.dp else 30.dp, vertical = if (small) 30.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (small) 2.dp else 4.dp, Alignment.CenterVertically),
    ) {
        LiveRow(if (small) "HR" else "Heart rate", run.heartRate?.toString() ?: "--", "bpm", Palette.Heart, run.hrTrail.map { it.toFloat() }, chart, minSpan = 12f)
        LiveRow("Pace", paceText(run.paceSecPerKm), "/km", Palette.PaceMetric, run.paceTrail, chart, invert = true, minSpan = 30f)
        LiveRow(if (small) "Cad" else "Cadence", run.cadence?.toString() ?: "--", "spm", Palette.Cadence, run.cadenceTrail.map { it.toFloat() }, chart, minSpan = 10f)
    }
}

private fun remainingText(run: RunUiState): String = when {
    run.workoutComplete -> "Done"
    run.remainingMs != null -> formatDuration((run.remainingMs + 999) / 1000)
    run.remainingMeters != null -> formatDistance(run.remainingMeters)
    else -> "--"
}

private fun paceText(pace: Double?) = pace?.let { formatPace(it.roundToInt()) } ?: "-:--"

@Composable
private fun SegmentPage(run: RunUiState) {
    val color = run.segmentType?.color ?: Palette.Accent
    val paceColor by animateColorAsState(
        when (run.paceStatus) {
            PaceStatus.ON -> Palette.Green
            PaceStatus.FAST -> Palette.Blue
            PaceStatus.SLOW -> Palette.Amber
            PaceStatus.NONE -> Color.White
        },
        tween(500),
        label = "pace",
    )
    // Galaxy Watch 4 40 mm is about 198 dp across; give the big countdown a smaller face there.
    val compact = LocalConfiguration.current.screenWidthDp < 215
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = if (run.workoutComplete) "Workout complete" else run.segmentLabel ?: "",
            transitionSpec = {
                (slideInVertically(tween(350)) { it } + fadeIn(tween(350)) + scaleIn(initialScale = 0.9f))
                    .togetherWith(slideOutVertically(tween(300)) { -it } + fadeOut(tween(250)))
            },
            label = "segment",
        ) { label ->
            Text(label, color = if (run.workoutComplete) Palette.Green else color, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val roundText = when {
            run.phase == Phase.PAUSED -> "Paused"
            run.phase == Phase.ENDING -> "Saving…"
            run.workoutComplete -> "Keep moving or end"
            run.rounds == null && run.blockRemainingMeters != null -> "Round ${run.round} · ${formatDistance(run.blockRemainingMeters)} left"
            run.rounds != null && run.rounds > 1 -> "Round ${run.round} of ${run.rounds}"
            run.totalSegments != null -> "Step ${run.segmentNumber} of ${run.totalSegments}"
            else -> ""
        }
        Text(roundText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RollingText(
            remainingText(run),
            style = if (compact) MaterialTheme.typography.numeralMedium else MaterialTheme.typography.numeralLarge,
            color = Color.White,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(paceText(run.paceSecPerKm), style = MaterialTheme.typography.numeralExtraSmall, color = paceColor)
            Text(" /km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        val verdict = run.verdict
        val hint = when {
            verdict == PaceCoach.Verdict.TOO_FAST -> "Ease off"
            verdict == PaceCoach.Verdict.TOO_SLOW -> "Pick it up"
            // On small screens the tint and pace colour already say where you are.
            run.band != null && !compact -> "target ${run.band}"
            else -> ""
        }
        AnimatedVisibility(visible = hint.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AnimatedContent(targetState = hint, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "hint") { h ->
                Text(h, style = MaterialTheme.typography.labelSmall, color = if (verdict != null) paceColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("♥ ${run.heartRate ?: "--"}   ${formatDistance(run.meters)}", style = MaterialTheme.typography.labelMedium)
        run.nextLabel?.takeIf { !run.workoutComplete }?.let {
            Text("Next  $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TotalsPage(run: RunUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RollingText(formatDuration(run.activeMs / 1000), style = MaterialTheme.typography.numeralMedium, color = Palette.Yellow, rollDown = false)
        Text(formatDistance(run.meters), style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
        Text("avg ${run.avgPaceSecPerKm?.let { formatPace(it) } ?: "-:--"} /km", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("♥ ${run.heartRate ?: "--"} ", style = MaterialTheme.typography.labelMedium, color = Palette.Red)
            if (run.hrTrail.size >= 2) Sparkline(run.hrTrail, Palette.Red, Modifier.width(70.dp).height(18.dp))
        }
        Text(
            listOfNotNull(run.altitude?.let { "${it.roundToInt()} m" }, run.elevationGain?.let { "▲ ${it.roundToInt()} m" }).joinToString("   ").ifEmpty { "alt --" },
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Purple,
        )
        Text(
            listOfNotNull(run.cadence?.let { "$it spm" }, run.calories?.let { "${it.roundToInt()} kcal" }).joinToString("   ").ifEmpty { "cadence --" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlsPage(run: RunUiState) {
    val context = LocalContext.current
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(confirmEnd) {
        if (confirmEnd) {
            delay(3_000)
            confirmEnd = false
        }
    }
    val endColor by animateColorAsState(if (confirmEnd) Palette.Red else Palette.Red.copy(alpha = 0.85f), tween(250), label = "end")
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        val paused = run.phase == Phase.PAUSED
        Text(formatDuration(run.activeMs / 1000), style = MaterialTheme.typography.titleMedium, color = Palette.Yellow)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { ExerciseService.send(context, if (paused) ExerciseService.ACTION_RESUME else ExerciseService.ACTION_PAUSE) },
                colors = if (paused) Buttons.go() else Buttons.secondary(),
                enabled = run.phase == Phase.ACTIVE || paused,
                label = { Text(if (paused) "Resume" else "Pause", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            )
            FilledTonalButton(
                onClick = { ExerciseService.send(context, ExerciseService.ACTION_SKIP) },
                enabled = run.phase == Phase.ACTIVE && !run.workoutComplete,
                label = { Text("Skip", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            )
        }
        Button(
            onClick = { if (confirmEnd) ExerciseService.send(context, ExerciseService.ACTION_END) else confirmEnd = true },
            enabled = run.phase != Phase.ENDING,
            colors = ButtonDefaults.buttonColors(containerColor = endColor, contentColor = Color.White),
            label = { Text(if (confirmEnd) "Tap to end" else "End", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Always-on display: white on black, no fills, only what you need at a glance. */
@Composable
private fun AmbientRun(run: RunUiState) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(run.segmentLabel ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(remainingText(run), color = Color.White, fontSize = 40.sp)
        Text("♥ ${run.heartRate ?: "--"}  ·  ${formatDistance(run.meters)}", color = Color.LightGray)
    }
}