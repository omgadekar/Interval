package com.om.interval.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.interval.data.RunStore
import com.om.interval.record.LapSummary
import com.om.interval.record.RunSummary
import com.om.interval.workout.formatDistance
import com.om.interval.workout.formatDuration
import com.om.interval.workout.formatPace
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val runDate = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")

/** One metric: a quiet caption over a value in the metric colour, on a faintly tinted tile. */
@Composable
private fun StatTile(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    val caption = color.copy(alpha = 0.8f).compositeOver(Color.White.copy(alpha = 0.6f))
    val tile = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(color.copy(alpha = 0.12f).compositeOver(Palette.Surface))
    val valueWithUnit: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.numeralExtraSmall, color = color, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) {
                Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
    if (screenSize() == ScreenSize.SMALL) {
        // Small screens: one stat per row, caption left and value right.
        Row(tile.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = caption, maxLines = 1, modifier = Modifier.weight(1f))
            valueWithUnit()
        }
    } else {
        Column(tile.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = caption, maxLines = 1)
            valueWithUnit()
        }
    }
}

@Composable
private fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    if (screenSize() == ScreenSize.SMALL) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            left(Modifier.fillMaxWidth())
            right(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            left(Modifier.weight(1f))
            right(Modifier.weight(1f))
        }
    }
}

/** A lap's pace as a bar against its target window: green inside, blue faster, amber slower. */
@Composable
private fun LapRow(lap: LapSummary) {
    val pace = lap.paceSecPerKm
    val min = lap.targetMinSecPerKm
    val max = lap.targetMaxSecPerKm
    val color = when {
        pace == null || min == null || max == null -> if (lap.isWork) Palette.Accent else Palette.Cyan
        pace < min -> Palette.Blue
        pace > max -> Palette.Amber
        else -> Palette.Green
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("${lap.number}. ${lap.label}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(pace?.let { formatPace(it) } ?: "-", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${formatDuration(lap.activeMs / 1000)} · ${formatDistance(lap.meters)}" + (lap.avgHeartRate?.let { " · ♥$it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (min != null && max != null) {
                Text("${formatPace(min)}-${formatPace(max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Interval-by-interval pace bars: height is speed, so faster laps stand taller. */
@Composable
private fun LapBars(laps: List<LapSummary>) {
    val paces = laps.map { it.paceSecPerKm }
    val fastest = paces.filterNotNull().minOrNull() ?: return
    val slowest = paces.filterNotNull().maxOrNull() ?: return
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(900)) }
    Canvas(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp)) {
        val total = laps.sumOf { it.activeMs }.coerceAtLeast(1L).toFloat()
        val gap = 2.dp.toPx()
        var x = 0f
        laps.forEach { lap ->
            val w = ((size.width - gap * laps.size) * lap.activeMs / total).coerceAtLeast(2f)
            val p = lap.paceSecPerKm
            val norm = if (p == null || slowest == fastest) 0.3f else 0.25f + 0.75f * (slowest - p).toFloat() / (slowest - fastest)
            val h = size.height * norm * grow.value
            drawRoundRect(
                if (lap.isWork) Palette.Accent else Palette.Cyan,
                Offset(x, size.height - h),
                Size(w, h),
                CornerRadius(2.dp.toPx()),
            )
            x += w + gap
        }
    }
}

@Composable
fun SummaryScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RunStore(context) }
    val summary = remember(id) { store.load(id) }
    if (summary == null) {
        ListScreen { paragraph("Run not found.") }
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    val km = rememberCountUp((summary.meters / 1000).toFloat())
    val series = summary.series

    ListScreen {
        item { Text(summary.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.appear(0)) }
        paragraph(Instant.ofEpochMilli(summary.startMs).atZone(ZoneId.systemDefault()).format(runDate))
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.appear(1)) {
                Text("%.2f".format(km), style = MaterialTheme.typography.numeralLarge, color = Palette.Accent)
                Text(" km", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        item {
            Column(Modifier.appear(2)) {
                TileRow(
                    { StatTile("Time", formatDuration(summary.activeMs / 1000), "", Palette.Time, it) },
                    { StatTile("Pace", summary.paceSecPerKm?.let { formatPace(it) } ?: "--", "/km", Palette.PaceMetric, it) },
                )
                TileRow(
                    { StatTile("Avg HR", summary.avgHeartRate?.toString() ?: "--", "bpm", Palette.Heart, it) },
                    { StatTile("Max HR", summary.maxHeartRate?.toString() ?: "--", "bpm", Palette.Heart, it) },
                )
                TileRow(
                    { StatTile("Climb", summary.elevationGain?.roundToInt()?.toString() ?: "--", "m", Palette.Climb, it) },
                    { StatTile("Cadence", summary.avgCadence?.toString() ?: "--", "spm", Palette.Cadence, it) },
                )
                summary.calories?.let { kcal ->
                    TileRow(
                        { StatTile("Energy", kcal.roundToInt().toString(), "kcal", Palette.Energy, it) },
                        { StatTile("Laps", summary.laps.size.toString(), "", Palette.Secondary, it) },
                    )
                }
            }
        }
        val hr = series.map { it.hr?.toFloat() }
        if (hr.count { it != null } >= 2) {
            item {
                Card(onClick = {}, modifier = Modifier.fillMaxWidth().appear(3)) {
                    ChartBlock(
                        "Heart rate", "avg ${summary.avgHeartRate ?: "-"}", Palette.Red, hr,
                        low = "${hr.filterNotNull().minOrNull()?.roundToInt() ?: ""}", high = "${hr.filterNotNull().maxOrNull()?.roundToInt() ?: ""} bpm",
                    )
                }
            }
        }
        val pace = series.map { it.pace?.toFloat() }
        if (pace.count { it != null } >= 2) {
            item {
                Card(onClick = {}, modifier = Modifier.fillMaxWidth().appear(4)) {
                    ChartBlock(
                        "Pace", summary.paceSecPerKm?.let { "${formatPace(it)} /km" } ?: "", Palette.Cyan, pace, invert = true,
                        low = pace.filterNotNull().maxOrNull()?.let { formatPace(it.roundToInt()) } ?: "",
                        high = pace.filterNotNull().minOrNull()?.let { "best ${formatPace(it.roundToInt())}" } ?: "",
                    )
                }
            }
        }
        val alt = series.map { it.alt?.toFloat() }
        if (alt.count { it != null } >= 2) {
            item {
                Card(onClick = {}, modifier = Modifier.fillMaxWidth().appear(5)) {
                    ChartBlock(
                        "Elevation", summary.elevationGain?.let { "+${it.roundToInt()} m" } ?: "", Palette.Purple, alt,
                        low = "${alt.filterNotNull().minOrNull()?.roundToInt() ?: ""} m", high = "${alt.filterNotNull().maxOrNull()?.roundToInt() ?: ""} m",
                    )
                }
            }
        }
        if (summary.laps.size > 1) {
            header("Intervals")
            item { LapBars(summary.laps) }
            items(summary.laps) { LapRow(it) }
        }
        item {
            Button(
                onClick = { open(Routes.export(id)) },
                colors = Buttons.primary(),
                label = { Text("Export") },
                secondaryLabel = { Text("Send to your phone") },
                modifier = Modifier.fillMaxWidth().appear(6),
            )
        }
        item {
            Button(
                onClick = {
                    if (confirmDelete) {
                        store.delete(id)
                        back()
                    } else {
                        confirmDelete = true
                    }
                },
                colors = Buttons.destructive(confirmDelete),
                label = { Text(if (confirmDelete) "Tap again to delete" else "Delete run") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HistoryScreen(open: (String) -> Unit) {
    val context = LocalContext.current
    val runs = remember { RunStore(context).list() }
    ListScreen {
        header("History")
        if (runs.isEmpty()) paragraph("No runs yet.")
        items(runs.size) { i -> HistoryCard(runs[i], Modifier.appear(i)) { open(Routes.summary(runs[i].id)) } }
    }
}

@Composable
private fun HistoryCard(run: RunSummary, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text(run.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatDistance(run.meters), style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
            }
            Text(
                Instant.ofEpochMilli(run.startMs).atZone(ZoneId.systemDefault()).format(runDate) + " · " + formatDuration(run.activeMs / 1000),
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hr = run.series.mapNotNull { it.hr }
            if (hr.size >= 2) Sparkline(hr, Palette.Red, Modifier.fillMaxWidth().height(20.dp).padding(top = 4.dp))
        }
    }
}