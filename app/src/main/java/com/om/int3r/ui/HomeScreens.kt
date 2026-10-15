package com.om.int3r.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.om.int3r.data.LibraryStore
import com.om.int3r.data.Prefs
import com.om.int3r.data.RunStore
import com.om.int3r.workout.Describe
import com.om.int3r.workout.formatDistance
import com.om.int3r.workout.formatDuration
import com.om.int3r.workout.formatPace
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val longDay = DateTimeFormatter.ofPattern("EEEE d MMM")
private val shortDay = DateTimeFormatter.ofPattern("EEE d")
private val monthDay = DateTimeFormatter.ofPattern("d MMM")

/** Mon-Sun of the current week: a dot in the workout's colour, a ring for today, filled when done. */
@Composable
private fun WeekStrip(open: (String) -> Unit) {
    val library by LibraryStore.get(LocalContext.current).library.collectAsStateWithLifecycle()
    val done by Prefs.get(LocalContext.current).done.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (0L until 7L).map { monday.plusDays(it) }.forEach { date ->
            val planned = library.scheduledOn(date)
            val isDone = date.toString() in done
            val color = planned?.let { mainType(it)?.color } ?: Palette.Track
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(CircleShape).clickable { open(Routes.day(date)) }.padding(2.dp),
            ) {
                Text(
                    date.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(Modifier.size(16.dp)) {
                    val r = size.minDimension / 2
                    if (isDone) {
                        drawCircle(color, r)
                    } else {
                        drawCircle(color.copy(alpha = if (planned != null) 0.35f else 1f), r * 0.55f)
                    }
                    if (date == today) drawCircle(Color.White, r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(permissionsGranted: Boolean, onRequestPermissions: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val library by LibraryStore.get(context).library.collectAsStateWithLifecycle()
    val settings by Prefs.get(context).settings.collectAsStateWithLifecycle()
    val done by Prefs.get(context).done.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val planned = library.scheduledOn(today)
    val lastRun = remember { RunStore(context).list().firstOrNull() }

    ListScreen {
        item {
            Text(
                today.format(longDay),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.appear(0),
            )
        }
        item { Box(Modifier.appear(1)) { WeekStrip(open) } }
        if (planned != null) {
            item {
                WorkoutCard(planned, settings.goalSecPerKm, Modifier.appear(2)) { open(Routes.day(today)) }
            }
            item {
                Button(
                    onClick = { open(Routes.prerun(planned.id, today)) },
                    colors = Buttons.go(),
                    label = { Text(if (today.toString() in done) "Run it again" else "Start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.fillMaxWidth().appear(3),
                )
            }
        } else if (library.workouts.isEmpty()) {
            item {
                Button(
                    onClick = { open(Routes.newWorkout(today)) },
                    label = { Text("Build your first workout") },
                    secondaryLabel = { Text("Intervals, easy runs, repeats") },
                    modifier = Modifier.fillMaxWidth().appear(2),
                )
            }
        } else {
            item {
                Button(
                    onClick = { open(Routes.pick(today)) },
                    label = { Text("Nothing planned") },
                    secondaryLabel = { Text("Pick a workout for today") },
                    modifier = Modifier.fillMaxWidth().appear(2),
                )
            }
        }
        if (!permissionsGranted) {
            item {
                FilledTonalButton(
                    onClick = onRequestPermissions,
                    label = { Text("Allow sensors") },
                    secondaryLabel = { Text("GPS, heart rate, activity") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { NavButton("Calendar", "Plan your days", 4) { open(Routes.CALENDAR) } }
        item { NavButton("Workouts", "${library.workouts.size} saved", 5) { open(Routes.LIBRARY) } }
        item {
            NavButton(
                "History",
                lastRun?.let { "Last: ${formatDistance(it.meters)} · ${formatDuration(it.activeMs / 1000)}" } ?: "No runs yet",
                6,
            ) { open(Routes.HISTORY) }
        }
        item { NavButton("Settings", "Goal pace, alerts", 7) { open(Routes.SETTINGS) } }
    }
}

@Composable
private fun NavButton(label: String, secondary: String, index: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        label = { Text(label) },
        secondaryLabel = { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth().appear(index),
    )
}

/** Two weeks back to ten weeks ahead, grouped by week, opening on today. */
@Composable
fun CalendarScreen(open: (String) -> Unit) {
    val library by LibraryStore.get(LocalContext.current).library.collectAsStateWithLifecycle()
    val done by Prefs.get(LocalContext.current).done.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (-2L..10L).map { thisMonday.plusWeeks(it) }
    // Each week is one header + seven days; today's week is index 2.
    val state = rememberScalingLazyListState(initialCenterItemIndex = 2 * 8 + 1 + today.dayOfWeek.ordinal)

    ListScreen(state) {
        weeks.forEach { monday ->
            val weeksAway = ChronoUnit.WEEKS.between(thisMonday, monday)
            header(
                when (weeksAway) {
                    0L -> "This week"
                    1L -> "Next week"
                    -1L -> "Last week"
                    else -> "Week of ${monday.format(monthDay)}"
                },
            )
            items((0L until 7L).map { monday.plusDays(it) }) { date ->
                val planned = library.scheduledOn(date)
                val isDone = date.toString() in done
                Button(
                    onClick = { open(Routes.day(date)) },
                    colors = when {
                        date == today -> ButtonDefaults.buttonColors()
                        planned != null -> ButtonDefaults.filledTonalButtonColors()
                        else -> ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF121214))
                    },
                    icon = planned?.let { p -> { mainType(p)?.let { TypeDot(it, 12) } } },
                    label = { Text((if (isDone) "✓ " else "") + date.format(shortDay)) },
                    secondaryLabel = { Text(planned?.name ?: "Rest", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DayScreen(date: LocalDate, open: (String) -> Unit) {
    val store = LibraryStore.get(LocalContext.current)
    val prefs = Prefs.get(LocalContext.current)
    val library by store.library.collectAsStateWithLifecycle()
    val done by prefs.done.collectAsStateWithLifecycle()
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val planned = library.scheduledOn(date)
    var clearing by remember { mutableStateOf(false) }
    DeletedConfirmation(clearing, "Removed from day") {
        if (clearing) {
            clearing = false
            store.update { it.plan(date, null) }
        }
    }

    ListScreen {
        header(if (date == LocalDate.now()) "Today" else date.format(longDay))
        if (planned != null) {
            item { WorkoutCard(planned, settings.goalSecPerKm, Modifier.appear(0)) { open(Routes.workout(planned.id, date)) } }
            item {
                Button(
                    onClick = { open(Routes.prerun(planned.id, date)) },
                    colors = Buttons.go(),
                    label = { Text("Start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.fillMaxWidth().appear(1),
                )
            }
            Describe.lines(planned.workout, settings.goalSecPerKm).forEach { paragraph(it) }
            item {
                CheckboxButton(
                    checked = date.toString() in done,
                    onCheckedChange = { prefs.setDone(date, it) },
                    label = { Text("Done") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(onClick = { open(Routes.pick(date)) }, label = { Text("Change workout") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                FilledTonalButton(
                    onClick = { clearing = true },
                    label = { Text("Make it a rest day") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                Text("Rest day", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(0))
            }
            item {
                Button(
                    onClick = { open(Routes.pick(date)) },
                    label = { Text("Add a workout") },
                    modifier = Modifier.fillMaxWidth().appear(1),
                )
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val prefs = Prefs.get(LocalContext.current)
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    ListScreen {
        header("Settings")
        item {
            FilledTonalButton(
                onClick = { picking = true },
                label = { Text("Goal pace") },
                secondaryLabel = { Text(settings.goalSecPerKm?.let { "${formatPace(it)} /km" } ?: "Not set") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        paragraph("Steps with a \"Goal\" target are coached around this pace.")
        if (settings.goalSecPerKm != null) {
            item {
                FilledTonalButton(
                    onClick = { prefs.update { it.copy(goalSecPerKm = null) } },
                    label = { Text("Clear goal pace") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        header("Cues")
        item {
            SwitchButton(
                checked = settings.paceAlerts,
                onCheckedChange = { on -> prefs.update { it.copy(paceAlerts = on) } },
                label = { Text("Pace alerts") },
                secondaryLabel = { Text("Buzz when off target") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SwitchButton(
                checked = settings.sound,
                onCheckedChange = { on -> prefs.update { it.copy(sound = on) } },
                label = { Text("Beeps") },
                secondaryLabel = { Text("Sound with buzzes") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        header("While running")
        item { LegendRow(Palette.Green, "On pace") }
        item { LegendRow(Palette.Blue, "Too fast") }
        item { LegendRow(Palette.Amber, "Too slow") }
        paragraph("Runs are saved to " + RunStore(context).dir.absolutePath)
        item { BrandFooter() }
    }

    WheelDialog(
        visible = picking,
        title = "Goal pace /km",
        wheels = paceWheels(settings.goalSecPerKm ?: 7 * 60),
        separator = ":",
        onDismiss = { picking = false },
    ) { idx ->
        prefs.update { it.copy(goalSecPerKm = paceFrom(idx)) }
        picking = false
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    val animated by animateColorAsState(color, tween(600), label = "legend")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) { drawRoundRect(animated.copy(alpha = 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())) }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).width(120.dp))
    }
}

@Suppress("unused")
private val unusedHeight = Modifier.height(0.dp)