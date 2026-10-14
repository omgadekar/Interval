package com.om.interval.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.interval.data.LibraryStore
import com.om.interval.data.Prefs
import com.om.interval.workout.Describe
import com.om.interval.workout.Library
import com.om.interval.workout.SavedWorkout
import com.om.interval.workout.Templates
import com.om.interval.workout.Timeline
import com.om.interval.workout.formatDistance
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

/** A workout as a card: name, its shape, and a one-line summary. */
@Composable
fun WorkoutCard(w: SavedWorkout, goal: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column {
            Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WorkoutShape(w.workout, goal, Modifier.fillMaxWidth().height(26.dp).padding(vertical = 4.dp))
            Text(
                Describe.summary(w.workout, goal),
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LibraryScreen(open: (String) -> Unit) {
    val library by LibraryStore.get(LocalContext.current).library.collectAsStateWithLifecycle()
    val settings by Prefs.get(LocalContext.current).settings.collectAsStateWithLifecycle()
    ListScreen {
        header("Workouts")
        item {
            Button(
                onClick = { open(Routes.newWorkout(null)) },
                label = { Text("New workout") },
                modifier = Modifier.fillMaxWidth().appear(0),
            )
        }
        if (library.workouts.isEmpty()) paragraph("Nothing saved yet. Start from a template or a blank page.")
        items(library.workouts.size) { i ->
            val w = library.workouts[i]
            WorkoutCard(w, settings.goalSecPerKm, Modifier.appear(i + 1)) { open(Routes.workout(w.id)) }
        }
    }
}

/** Pick a starting point. Creating saves straight away and opens the editor. */
@Composable
fun NewWorkoutScreen(date: LocalDate?, created: (String) -> Unit) {
    val store = LibraryStore.get(LocalContext.current)
    val settings by Prefs.get(LocalContext.current).settings.collectAsStateWithLifecycle()
    ListScreen {
        header("Start from")
        items(Templates.ALL.size) { i ->
            val (name, workout) = Templates.ALL[i]
            val preview = SavedWorkout("preview", name, workout)
            WorkoutCard(preview, settings.goalSecPerKm, Modifier.appear(i)) {
                val id = Library.newId()
                val saved = SavedWorkout(id, if (workout.blocks.isEmpty()) "My workout" else name, workout, System.currentTimeMillis())
                store.update { lib -> lib.upsert(saved).let { if (date != null) it.plan(date, id) else it } }
                created(id)
            }
        }
    }
}

@Composable
fun WorkoutScreen(id: String, date: LocalDate?, open: (String) -> Unit, back: () -> Unit) {
    val store = LibraryStore.get(LocalContext.current)
    val library by store.library.collectAsStateWithLifecycle()
    val settings by Prefs.get(LocalContext.current).settings.collectAsStateWithLifecycle()
    val w = library.workout(id)
    if (w == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val goal = settings.goalSecPerKm
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    val bars = Timeline.bars(w.workout, goal)
    val km = Timeline.totalMeters(w.workout, goal)

    ListScreen {
        item {
            Text(w.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.appear(0))
        }
        item {
            WorkoutShape(w.workout, goal, Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (bars.isNotEmpty()) {
            paragraph("≈ ${Math.round(bars.sumOf { it.seconds } / 60.0)} min · ≈ ${formatDistance(km)}")
        }
        item {
            Button(
                onClick = { open(Routes.prerun(id, date)) },
                enabled = w.workout.blocks.isNotEmpty(),
                label = { Text("Start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                modifier = Modifier.fillMaxWidth().appear(1),
            )
        }
        Describe.lines(w.workout, goal).forEach { paragraph(it) }
        item {
            FilledTonalButton(onClick = { open(Routes.edit(id)) }, label = { Text("Edit") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            FilledTonalButton(
                onClick = { open(Routes.schedule(id)) },
                label = { Text("Plan on a day") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilledTonalButton(
                onClick = { store.update { it.duplicate(id) } },
                label = { Text("Duplicate") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    if (confirmDelete) {
                        store.update { it.delete(id) }
                        back()
                    } else {
                        confirmDelete = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmDelete) Palette.Red else Palette.SurfaceHigh,
                    contentColor = if (confirmDelete) Color.White else Palette.Red,
                ),
                label = { Text(if (confirmDelete) "Tap again to delete" else "Delete") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Choose a day for a workout: the next four weeks. */
@Composable
fun ScheduleScreen(id: String, back: () -> Unit) {
    val store = LibraryStore.get(LocalContext.current)
    val library by store.library.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    ListScreen {
        header("Plan on…")
        items((0L until 28L).map { today.plusDays(it) }) { date ->
            val planned = library.scheduledOn(date)
            FilledTonalButton(
                onClick = {
                    store.update { it.plan(date, id) }
                    back()
                },
                icon = planned?.let { p -> { mainType(p)?.let { TypeDot(it) } } },
                label = { Text(if (date == today) "Today" else date.format(shortDay)) },
                secondaryLabel = { Text(planned?.let { "Replaces ${it.name}" } ?: "Free", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Choose which saved workout goes on [date]. */
@Composable
fun PickWorkoutScreen(date: LocalDate, open: (String) -> Unit, back: () -> Unit) {
    val store = LibraryStore.get(LocalContext.current)
    val library by store.library.collectAsStateWithLifecycle()
    val settings by Prefs.get(LocalContext.current).settings.collectAsStateWithLifecycle()
    ListScreen {
        header(date.format(shortDay))
        item {
            Button(
                onClick = { open(Routes.newWorkout(date)) },
                label = { Text("New workout") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(library.workouts.size) { i ->
            val w = library.workouts[i]
            WorkoutCard(w, settings.goalSecPerKm, Modifier.appear(i)) {
                store.update { it.plan(date, w.id) }
                back()
            }
        }
    }
}

/** The most intense step type in a workout, used for its colour dot. */
fun mainType(w: SavedWorkout) = Timeline.bars(w.workout).maxByOrNull { it.type.intensity }?.type